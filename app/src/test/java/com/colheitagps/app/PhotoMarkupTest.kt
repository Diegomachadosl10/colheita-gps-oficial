package com.colheitagps.app

import android.app.Application
import android.content.Intent
import android.graphics.*
import android.view.MotionEvent
import android.view.View
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.*
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.robolectric.util.ReflectionHelpers
import java.io.File
import java.security.MessageDigest

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[28,35],manifest=Config.NONE,application=Application::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class PhotoMarkupTest {
    private val app get()=RuntimeEnvironment.getApplication()
    private val root get()=TeamStore(app,"santo_inacio_C").files
    private fun photo():File {
        val file=File(root,"original-${java.util.UUID.randomUUID()}.jpg")
        val image=Bitmap.createBitmap(400,200,Bitmap.Config.ARGB_8888).apply {eraseColor(Color.WHITE)}
        file.outputStream().use {image.compress(Bitmap.CompressFormat.JPEG,100,it)};image.recycle();return file
    }
    private fun digest(file:File)=MessageDigest.getInstance("SHA-256").digest(file.readBytes()).toList()
    private val marks get()=listOf(PhotoMark("arrow",.1f,.5f,.9f,.5f),PhotoMark("circle",.1f,.1f,.3f,.3f),PhotoMark("text",.6f,.1f,.6f,.1f,"Trinca no braço"))
    @Test fun savingMarksCreatesAnIllustratedCopyAndReopeningKeepsTheOriginalAndEditableMarks() {
        val source=photo();val hash=digest(source)
        val output=PhotoMarkup.save(root,source,marks)
        assertNotEquals(source.path,output.path);assertEquals(hash,digest(source));assertTrue(File(output.path+".markup.json").isFile)
        val reopened=PhotoMarkup.open(root,output)
        assertEquals(source.canonicalPath,reopened.original.canonicalPath);assertEquals(marks,reopened.marks)
        val annotated=PhotoMarkup.decode(output)
        assertEquals(400,annotated.width);assertEquals(200,annotated.height)
        val center=annotated.getPixel(200,100)
        assertTrue(Color.red(center)>150);assertTrue(Color.green(center)<100);assertTrue(Color.blue(center)<100);annotated.recycle()
        val changedAgain=PhotoMarkup.save(root,reopened.original,reopened.marks+PhotoMark("arrow",.3f,.7f,.5f,.9f))
        assertEquals(source.canonicalPath,PhotoMarkup.open(root,changedAgain).original.canonicalPath);assertEquals(hash,digest(source))
        System.getenv("DM_AGRO_UPGRADE_PREVIEW")?.let {folder->File(folder).mkdirs();output.copyTo(File(folder,"foto-marcada.jpg"),overwrite=true)}
    }
    @Test fun cameraOrientationIsAppliedBeforeCoordinatesSoAnArrowStaysOnTheCorrectPhotoPosition() {
        val source=photo();android.media.ExifInterface(source.path).apply {setAttribute(android.media.ExifInterface.TAG_ORIENTATION,"6");saveAttributes()}
        val originalHash=digest(source)
        val output=PhotoMarkup.save(root,source,listOf(PhotoMark("arrow",.1f,.5f,.9f,.5f)))
        val oriented=PhotoMarkup.decode(output)
        assertEquals(200,oriented.width);assertEquals(400,oriented.height)
        assertTrue(Color.green(oriented.getPixel(100,200))<100);oriented.recycle();assertEquals(originalHash,digest(source))
    }
    @Test fun drawingOnALetterboxedPhotoUsesPhotoCoordinatesAndUndoDoesNotModifyTheBitmap() {
        val bitmap=Bitmap.createBitmap(400,200,Bitmap.Config.ARGB_8888).apply {eraseColor(Color.WHITE)}
        val view=PhotoMarkupView(app,bitmap,emptyList());view.layout(0,0,400,400)
        val display=Bitmap.createBitmap(400,400,Bitmap.Config.ARGB_8888);view.draw(Canvas(display))
        fun touch(action:Int,x:Float,y:Float) {val e=MotionEvent.obtain(0,0,action,x,y,0);view.onTouchEvent(e);e.recycle()}
        touch(MotionEvent.ACTION_DOWN,100f,150f);touch(MotionEvent.ACTION_MOVE,300f,250f);touch(MotionEvent.ACTION_UP,300f,250f)
        assertEquals(PhotoMark("arrow",.25f,.25f,.75f,.75f),view.marks.single())
        assertTrue(view.changed);assertEquals(Color.WHITE,bitmap.getPixel(200,100))
        view.undo();assertTrue(view.marks.isEmpty());assertFalse(view.changed);display.recycle();view.release()
    }
    @Test fun attachmentReplacementPreservesOtherPhotosAndTheMarkedIssueStillSharesAsOneImage() {
        val source=photo();val output=PhotoMarkup.save(root,source,marks)
        val record=JSONObject().put("id","issue").put("equipamento","21571").put("descricao","Trinca no braço do trator").put("foto",source.path).put("fotos",JSONArray().put(source.path).put(source.path))
        val updated=PhotoMarkup.replace(record,source.path,output.path)
        assertEquals(source.path,record.getString("foto"));assertEquals(output.path,updated.getString("foto"))
        assertEquals(listOf(output.canonicalPath),MaintenanceShareData.photos(root,updated).map {it.canonicalPath})
        val pages=MaintenanceShareRenderer.render(File(root,"saida-teste"),root,listOf(updated),"Santo Inácio • Turno C","16/11/2026",true)
        assertEquals(1,pages.size);val image=BitmapFactory.decodeFile(pages.single().path);assertEquals(1000,image.width);assertTrue(image.height>800);image.recycle()
        try {PhotoMarkup.replace(updated,"nao-anexada.jpg",output.path);fail("Must reject a stale attachment") } catch(_:IllegalArgumentException) {}
    }
    @Test fun anotherTeamsPhotoOrAMaliciousSidecarCannotBeOpenedOrOverwritten() {
        val source=photo();val another=TeamStore(app,"colorado_C").files
        try {PhotoMarkup.checked(another,source);fail("Cross-team source") } catch(_:IllegalArgumentException) {}
        File(source.path+".markup.json").writeText(JSONObject().put("original","../fora.jpg").put("marks",JSONArray()).toString())
        try {PhotoMarkup.open(root,source);fail("Traversal") } catch(_:IllegalArgumentException) {}
        try {PhotoMark.read(PhotoMark("arrow",Float.NaN,0f,1f,1f).json());fail("Invalid coordinate") } catch(_:Exception) {}
    }
    @Test fun aSavedMarkedPhotoIsAttachedToTheSameIssueAndChangesAreAuditedWithoutLosingItsOtherPhotos() {
        val a=Robolectric.buildActivity(MainActivity::class.java).get();AppTheme.apply(a)
        ReflectionHelpers.setField(a,"teamStore",TeamStore(app,"santo_inacio_C"))
        ReflectionHelpers.setField(a,"teamAccess",TeamAccess("sup",mapOf("papel" to "supervisor","status" to "aprovado","frenteId" to "santo_inacio","turno" to "C")))
        ReflectionHelpers.setField(a,"papelAtual","supervisor");ReflectionHelpers.setField(a,"acessoConfirmado",true)
        val source=photo();val other=photo();val marked=PhotoMarkup.save(root,source,marks)
        val rows=JSONArray().put(JSONObject().put("id","issue").put("equipamento","00123").put("descricao","Trinca").put("foto",source.path).put("fotos",JSONArray().put(source.path).put(other.path)))
        a.getSharedPreferences("registros_v3",0).edit().putString("pendencias",rows.toString()).commit()
        ReflectionHelpers.setField(a,"photoEditTarget",JSONObject().put("uid","sup").put("team","santo_inacio_C").put("group","pendencias").put("id","issue").put("source",source.path))
        ReflectionHelpers.setField(a,"photoEditResult",Intent().putExtra("markedPhoto",marked.path).putExtra("sourcePhoto",source.path))
        a.javaClass.getDeclaredMethod("processarFotoMarcada").apply {isAccessible=true}.invoke(a)
        val saved=FieldReports.rows(JSONArray(a.getSharedPreferences("registros_v3",0).getString("pendencias","[]"))).single {it.optString("id")=="issue"}
        assertEquals(marked.path,saved.getString("foto"));assertEquals(other.path,saved.getJSONArray("fotos").getString(1))
        assertTrue(RecordEdits.text(saved).contains("Anexos atualizados"));assertTrue(source.isFile)
        for(night in listOf(false,true)) {
            AppTheme.setNight(a,night);AppTheme.apply(a)
            a.javaClass.getDeclaredMethod("passagemTurno",java.time.LocalDate::class.java).apply {isAccessible=true}.invoke(a,null)
            val decor=a.window.decorView;val density=a.resources.displayMetrics.density;val width=(393*density).toInt();val height=(820*density).toInt()
            decor.measure(View.MeasureSpec.makeMeasureSpec(width,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(height,View.MeasureSpec.EXACTLY));decor.layout(0,0,width,height)
            fun views(v:View):List<View> = listOf(v)+(if(v is android.view.ViewGroup) (0 until v.childCount).flatMap {views(v.getChildAt(it))} else emptyList())
            val help=views(decor).single {it.contentDescription=="Ajuda desta tela"};assertTrue(help.width>=44*density)
            for(button in views(decor).filterIsInstance<android.widget.Button>()) {assertTrue(button.width>0);assertTrue(button.height>=48*density)}
            System.getenv("DM_AGRO_UPGRADE_PREVIEW")?.let {folder->
                val image=Bitmap.createBitmap(width,height,Bitmap.Config.ARGB_8888);decor.draw(Canvas(image));File(folder).mkdirs()
                File(folder,"passagem-${if(night) "noturno" else "claro"}.png").outputStream().use {image.compress(Bitmap.CompressFormat.PNG,100,it)};image.recycle()
            }
        }
    }
}
