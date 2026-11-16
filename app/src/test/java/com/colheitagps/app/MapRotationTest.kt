package com.colheitagps.app

import android.app.Application
import android.graphics.*
import android.os.Looper
import android.view.MotionEvent
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.robolectric.util.ReflectionHelpers
import java.io.File
import kotlin.math.*

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[28],manifest=Config.NONE,application=Application::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class MapRotationTest {
    private fun close(a:PointF,b:PointF,tolerance:Float=0.01f) {assertEquals(a.x,b.x,tolerance);assertEquals(a.y,b.y,tolerance)}
    private fun event(action:Int,vararg points:Triple<Int,Float,Float>):MotionEvent {
        val properties=points.map {MotionEvent.PointerProperties().apply {id=it.first;toolType=MotionEvent.TOOL_TYPE_FINGER}}.toTypedArray()
        val coords=points.map {MotionEvent.PointerCoords().apply {x=it.second;y=it.third;pressure=1f;size=1f}}.toTypedArray()
        return MotionEvent.obtain(0,100,action,points.size,properties,coords,0,0,1f,1f,0,0,0,0)
    }
    private fun controller(g:MapGestureController,action:Int,vararg p:Triple<Int,Float,Float>) {event(action,*p).also {g.onTouch(it);it.recycle()}}
    @Test fun aCombinedRotationZoomAndPanKeepTheSameMapPointsUnderBothFingersEvenWhenPointerIndexesChange() {
        val v=MapViewport();v.fit(400,400,400,400);val g=MapGestureController(v)
        controller(g,MotionEvent.ACTION_DOWN,Triple(7,100f,100f))
        controller(g,MotionEvent.ACTION_POINTER_DOWN or (1 shl 8),Triple(7,100f,100f),Triple(42,300f,100f))
        controller(g,MotionEvent.ACTION_MOVE,Triple(42,250f,300f),Triple(7,250f,-100f))
        close(PointF(250f,-100f),v.toScreen(100f,100f));close(PointF(250f,300f),v.toScreen(300f,100f))
        assertEquals(2f,v.scale,0.001f);assertEquals(90f,v.rotation,0.001f)
        val before=v.toScreen(150f,150f)
        controller(g,MotionEvent.ACTION_POINTER_UP,Triple(42,250f,300f),Triple(7,250f,-100f))
        controller(g,MotionEvent.ACTION_MOVE,Triple(7,700f,600f));close(before,v.toScreen(150f,150f))
        controller(g,MotionEvent.ACTION_UP,Triple(7,700f,600f));assertFalse(g.active)
    }
    @Test fun changingToAThirdFingerAndCrossingTheAngleBoundaryDoNotJumpTheMap() {
        val v=MapViewport();val g=MapGestureController(v)
        fun positions(angle:Double):Array<Triple<Int,Float,Float>> = arrayOf(Triple(2,200f,200f),Triple(9,(200+100*cos(angle)).toFloat(),(200+100*sin(angle)).toFloat()))
        controller(g,MotionEvent.ACTION_POINTER_DOWN or (1 shl 8),*positions(Math.toRadians(179.0)))
        controller(g,MotionEvent.ACTION_MOVE,*positions(Math.toRadians(-179.0)))
        assertEquals(2f,v.rotation,0.001f)
        val p=v.toScreen(50f,60f)
        controller(g,MotionEvent.ACTION_POINTER_DOWN or (2 shl 8),*positions(Math.toRadians(-179.0)),Triple(12,400f,400f));close(p,v.toScreen(50f,60f))
        controller(g,MotionEvent.ACTION_POINTER_UP,*positions(Math.toRadians(-179.0)),Triple(12,400f,400f));close(p,v.toScreen(50f,60f))
        controller(g,MotionEvent.ACTION_MOVE,Triple(9,100.015f,198.255f),Triple(12,400f,400f));close(p,v.toScreen(50f,60f),0.02f)
    }
    @Test fun screenToMapIsTheInverseOfTheCanvasTransformAtArbitraryAnglesAndResetKeepsTheCenterPoint() {
        val v=MapViewport();v.fit(1200,1800,720,1280)
        for(angle in listOf(37.3f,-179.9f,90f,270f,9.3f)) {
            val anchor=v.toMap(330f,400f);v.gesture(330f,400f,370f,460f,1.3f,angle)
            close(anchor,v.toMap(370f,460f))
            for(p in listOf(PointF(0f,0f),PointF(130f,900f),PointF(1200f,1800f))) {
                close(p,v.toMap(v.toScreen(p.x,p.y).x,v.toScreen(p.x,p.y).y))
                val bitmap=Bitmap.createBitmap(10,10,Bitmap.Config.ARGB_8888);val canvas=Canvas(bitmap);v.apply(canvas)
                val actual=floatArrayOf(p.x,p.y);canvas.matrix.mapPoints(actual);close(v.toScreen(p.x,p.y),PointF(actual[0],actual[1]));bitmap.recycle()
            }
        }
        val center=v.toMap(360f,640f);val scale=v.scale
        v.resetRotation(360f,640f);assertEquals(0f,v.rotation,0.001f);assertEquals(scale,v.scale,0f);close(center,v.toMap(360f,640f))
    }
    private fun map():Pair<MapaView,GeoPdf> {
        val app=RuntimeEnvironment.getApplication()
        val file=File(app.cacheDir,"georef-fixture.pdf").apply {writeText("/MediaBox [0 0 400 400] /BBox [0 0 400 400] /Measure << /LPTS [0 0 1 0 1 1 0 1] /GPTS [-22 -52 -22 -51 -21 -51 -21 -52] >>")}
        val geo=GeoPdf.ler(file)!!
        val view=MapaView(app);view.layout(0,0,600,800)
        val image=Bitmap.createBitmap(400,400,Bitmap.Config.ARGB_8888).apply {eraseColor(Color.WHITE)}
        view.carregar(image,"rotation-test",geo);shadowOf(Looper.getMainLooper()).idle()
        return view to geo
    }
    @Test fun gpsPaintingsAreaAndSavedCoordinatesStayAlignedWhileTheViewRotates() {
        val (view,geo)=map();val v=ReflectionHelpers.getField<MapViewport>(view,"viewport")
        val areas=ReflectionHelpers.getField<MutableList<MutableList<PointF>>>(view,"areas")
        areas.add(mutableListOf(PointF(210f,210f),PointF(260f,210f),PointF(260f,270f),PointF(210f,270f)))
        ReflectionHelpers.getField<MutableList<Int>>(view,"coresAreas").add(Color.RED)
        view.salvar();val saved=RuntimeEnvironment.getApplication().getSharedPreferences("pinturas",0).getString("rotation-test","")
        val ha=view.hectaresPintados()!!;val location=geo.gpsParaPixel(-21.25,-51.75,400,400)!!
        view.atualizarGps(-21.25,-51.75)
        v.gesture(300f,400f,300f,400f,1.1f,43f)
        close(location,ReflectionHelpers.callInstanceMethod<PointF>(view,"gpsPixel"))
        val bitmap=Bitmap.createBitmap(600,800,Bitmap.Config.ARGB_8888);view.draw(Canvas(bitmap))
        val screen=v.toScreen(location.x,location.y)
        assertEquals(0xFF1674E8.toInt(),bitmap.getPixel(screen.x.roundToInt(),screen.y.roundToInt()))
        val painted=v.toScreen(235f,260f);assertEquals(Color.RED,bitmap.getPixel(painted.x.roundToInt(),painted.y.roundToInt()))
        val before=v.toMap(300f,400f);view.iniciar(8);view.adicionarPontoCentral()
        close(before,ReflectionHelpers.getField<MutableList<PointF>>(view,"pontos").single())
        view.centrarGps();close(PointF(300f,400f),v.toScreen(location.x,location.y));assertEquals(43f,v.rotation,0.001f)
        assertEquals(ha,view.hectaresPintados()!!,0.000001);view.salvar()
        assertEquals(saved,RuntimeEnvironment.getApplication().getSharedPreferences("pinturas",0).getString("rotation-test",""))
        System.getenv("DM_AGRO_ROTATION_PREVIEW")?.let {File(it).outputStream().use {stream->bitmap.compress(Bitmap.CompressFormat.PNG,100,stream)}}
        bitmap.recycle()
    }
    @Test fun aConfirmedVoicePolygonUsesOriginalCoordinatesAndKeepsGpsAndViewport() {
        val (view,geo)=map();val viewport=ReflectionHelpers.getField<MapViewport>(view,"viewport")
        viewport.gesture(300f,400f,280f,370f,1.4f,53f)
        val before=viewport.toScreen(200f,200f);val scale=viewport.scale;val rotation=viewport.rotation
        val gps=geo.gpsParaPixel(-21.25,-51.75,400,400)!!;view.atualizarGps(-21.25,-51.75)
        val normalized=listOf(PointF(.1f,.2f),PointF(.3f,.2f),PointF(.3f,.4f),PointF(.1f,.4f),PointF(.1f,.2f))
        view.pintarPorVoz(normalized)
        val areas=ReflectionHelpers.getField<MutableList<MutableList<PointF>>>(view,"areas")
        assertEquals(1,areas.size);close(PointF(40f,80f),areas.single().first())
        close(before,viewport.toScreen(200f,200f));assertEquals(scale,viewport.scale,0f);assertEquals(rotation,viewport.rotation,0f)
        close(gps,ReflectionHelpers.callInstanceMethod<PointF>(view,"gpsPixel"));assertTrue(view.hectaresPintados()!!>0)
        val saved=RuntimeEnvironment.getApplication().getSharedPreferences("pinturas",0).getString("rotation-test","")
        try {view.pintarPorVoz(listOf(PointF(-.1f,.2f),PointF(.3f,.2f),PointF(.3f,.4f),PointF(-.1f,.2f)));fail("Coordenadas inválidas devem ser recusadas")} catch(_:IllegalArgumentException) {}
        assertEquals(saved,RuntimeEnvironment.getApplication().getSharedPreferences("pinturas",0).getString("rotation-test",""));assertEquals(1,areas.size)
        view.iniciar(8);view.adicionarPontoCentral()
        try {view.pintarPorVoz(normalized);fail("Uma medição em andamento deve ser preservada")} catch(_:IllegalStateException) {}
        assertEquals(1,areas.size)
    }
    @Test fun aRealRecognizedHalfUsesTheOriginalPdfGeoreferenceWhenSaved() {
        val path=System.getenv("DM_AGRO_CURVE_PDF")?:return
        val context=RuntimeEnvironment.getApplication();val pdf=File(path)
        val curve=CurveCatalog.load(context,pdf,File(context.cacheDir,"native-real-curves")).matches("8","3,15").single()
        val bounds=curve.polygon.envelopeInternal
        val half=CurveGeometry.half(curve.polygon,org.locationtech.jts.geom.Coordinate(bounds.minX-.01,curve.polygon.centroid.y),595.320007,841.919006)!!
        assertEquals(curve.polygon.area/2,half.area,curve.polygon.area*.0001)
        val geo=GeoPdf.ler(pdf)!!;val view=MapaView(context);view.layout(0,0,600,900)
        view.carregar(Bitmap.createBitmap(595,842,Bitmap.Config.ARGB_8888),"real-half-test",geo)
        shadowOf(Looper.getMainLooper()).idle()
        view.pintarPorVoz(half.coordinates.map {PointF(it.x.toFloat(),it.y.toFloat())})
        assertEquals(curve.measuredHa/2,view.hectaresEmDestaque()!!,.001)
        val picture=CurvePreview.Picture(Bitmap.createBitmap(10,10,Bitmap.Config.ARGB_8888),android.graphics.RectF(.1f,.2f,.8f,.9f))
        assertEquals(.1,picture.point(0f,0f).x,.000001);assertEquals(.9,picture.point(1f,1f).y,.000001)
        picture.bitmap.recycle()
    }
    @Test fun aWholePlotWithTwoPartsSavesOnceAndLeavesItsRoadUnpainted() {
        val (view,_)=map();var saves=0;view.aoSalvar={saves++}
        fun square(x:Float)=listOf(PointF(x,.2f),PointF(x+.15f,.2f),PointF(x+.15f,.4f),PointF(x,.4f),PointF(x,.2f))
        view.pintarConjuntoPorVoz(listOf(square(.1f),square(.5f)))
        assertEquals(1,saves)
        val areas=ReflectionHelpers.getField<MutableList<MutableList<PointF>>>(view,"areas");assertEquals(2,areas.size)
        val viewport=ReflectionHelpers.getField<MapViewport>(view,"viewport");val bitmap=Bitmap.createBitmap(600,800,Bitmap.Config.ARGB_8888);view.draw(Canvas(bitmap))
        val road=viewport.toScreen(160f,120f);val field=viewport.toScreen(60f,120f)
        assertEquals(Color.WHITE,bitmap.getPixel(road.x.toInt(),road.y.toInt()));assertNotEquals(Color.WHITE,bitmap.getPixel(field.x.toInt(),field.y.toInt()))
        val saved=RuntimeEnvironment.getApplication().getSharedPreferences("pinturas",0).getString("rotation-test","")
        try {view.pintarConjuntoPorVoz(listOf(square(.1f),square(1.2f)));fail("Uma parte inválida deve bloquear a operação inteira")} catch(_:IllegalArgumentException) {}
        assertEquals(2,areas.size);assertEquals(1,saves);assertEquals(saved,RuntimeEnvironment.getApplication().getSharedPreferences("pinturas",0).getString("rotation-test",""));bitmap.recycle()
    }
    @Test fun aTwoFingerGestureDoesNotEraseOrPaintAtItsFirstFingerAndOneFingerStillDrawsInMapCoordinates() {
        val (view,_)=map();val v=ReflectionHelpers.getField<MapViewport>(view,"viewport")
        val lines=ReflectionHelpers.getField<MutableList<MutableList<PointF>>>(view,"linhas")
        lines.add(mutableListOf(PointF(100f,100f),PointF(150f,150f)))
        ReflectionHelpers.getField<MutableList<Int>>(view,"coresLinhas").add(Color.RED)
        view.modo=2
        fun touch(action:Int,vararg points:Triple<Int,Float,Float>) {event(action,*points).also {view.onTouchEvent(it);it.recycle()}}
        val p=v.toScreen(100f,100f)
        touch(MotionEvent.ACTION_DOWN,Triple(0,p.x,p.y));assertEquals(1,lines.size)
        touch(MotionEvent.ACTION_POINTER_DOWN or (1 shl 8),Triple(0,p.x,p.y),Triple(1,p.x+100,p.y))
        touch(MotionEvent.ACTION_MOVE,Triple(0,p.x+50,p.y-50),Triple(1,p.x+50,p.y+50))
        touch(MotionEvent.ACTION_POINTER_UP or (1 shl 8),Triple(0,p.x+50,p.y-50),Triple(1,p.x+50,p.y+50))
        touch(MotionEvent.ACTION_UP,Triple(0,p.x+50,p.y-50));assertEquals(1,lines.size)
        view.modo=1
        val a=v.toScreen(40f,40f);val b=v.toScreen(90f,70f)
        touch(MotionEvent.ACTION_DOWN,Triple(0,a.x,a.y));touch(MotionEvent.ACTION_MOVE,Triple(0,b.x,b.y));touch(MotionEvent.ACTION_UP,Triple(0,b.x,b.y))
        assertEquals(2,lines.size);close(PointF(40f,40f),lines.last().first());close(PointF(90f,70f),lines.last().last())
        view.modo=2;val target=v.toScreen(40f,40f)
        touch(MotionEvent.ACTION_DOWN,Triple(0,target.x,target.y));touch(MotionEvent.ACTION_UP,Triple(0,target.x,target.y));assertEquals(1,lines.size)
    }
}
