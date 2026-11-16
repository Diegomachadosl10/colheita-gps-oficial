package com.colheitagps.app

import android.app.Application
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.locationtech.jts.geom.*
import java.io.File
import kotlin.math.abs

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[28],manifest=Config.NONE,application=Application::class)
class CurveRecognitionTest {
    private fun box(x:Double,y:Double,w:Double,h:Double)=CurveGeometry.polygon(listOf(Coordinate(x,y),Coordinate(x+w,y),Coordinate(x+w,y+h),Coordinate(x,y+h)))!!
    @Test fun smallVectorGapsCanBeRepairedWithoutMergingTheAdjacentTerraces() {
        val plot=box(0.0,0.0,100.0,100.0)
        val line=CurveGeometry.factory.createLineString(arrayOf(Coordinate(.01,50.0),Coordinate(100.0,50.0)))
        val cells=CurveGeometry.cells(plot,listOf(line));assertEquals(2,cells.size);assertTrue(cells.all {abs(it.area-5000)<1})
    }
    @Test fun oneAreaLabelPerCellAndAnIndependentAreaCheckAreRequiredBeforeRecognizingACurve() {
        val points=box(0.0,0.0,100.0,100.0).coordinates.toList()
        val parsed=PdfCurveReader.Companion.Result(100.0,100.0,listOf(PdfCurveReader.Label("9",Coordinate(20.0,20.0)),PdfCurveReader.Label("1,00",Coordinate(30.0,30.0)),PdfCurveReader.Label("1,00",Coordinate(30.0,70.0))),
            listOf(PdfCurveReader.Outline("Talhões",points,false,true),PdfCurveReader.Outline("Linhas",listOf(Coordinate(0.0,50.0),Coordinate(100.0,50.0)),true,false)))
        val catalog=CurveCatalog.recognize(parsed) {it.area/5000}
        assertEquals(2,catalog.matches("9","1,00").size);assertNotEquals(catalog.curves[0].id,catalog.curves[1].id)
        assertTrue(CurveCatalog.recognize(parsed) {it.area/100}.curves.isEmpty())
    }
    @Test fun halfPaintingUsesAreaAndTheChosenSideAndSpatialTiesAreNotResolvedArbitrarily() {
        val polygon=box(0.0,0.0,100.0,30.0);val left=CurveGeometry.half(polygon,Coordinate(-10.0,15.0))!!
        assertEquals(polygon.area/2,left.area,.01);assertTrue(left.envelopeInternal.maxX<50.01)
        val a=box(0.0,0.0,1.0,1.0);val b=box(2.0,0.0,1.0,1.0)
        assertNull(CurveGeometry.uniqueNearest(listOf(a,b),Coordinate(1.5,.5),.01))
        assertEquals(0,CurveGeometry.uniqueNearest(listOf(a,b),Coordinate(-1.0,.5),.01))
        val c=box(.2,.4,.001,.001);val d=box(.4,.1,.001,.001)
        assertEquals(1,CurveGeometry.uniqueNearest(listOf(c,d),Coordinate(0.0,0.0),.001))
        assertEquals(0,CurveGeometry.uniqueNearest(listOf(c,d),Coordinate(0.0,0.0),.001,2.0,1.0))
    }
    @Test fun originalK39CanBeEvaluatedLocallyWithoutCommittingTheUsersMaps() {
        val path=System.getenv("DM_AGRO_CURVE_PDF")?:return
        val context=RuntimeEnvironment.getApplication()
        val pdf=File(path);val raw=PdfCurveReader.read(context,pdf)
        System.getenv("DM_AGRO_CURVE_REPORT")?.let {report->
            val json=org.json.JSONObject().put("labels",org.json.JSONArray(raw.labels.map {org.json.JSONObject().put("text",it.text).put("x",it.at.x).put("y",it.at.y)}))
                .put("paths",org.json.JSONArray(raw.paths.map {org.json.JSONObject().put("layer",it.layer).put("red",it.red).put("black",it.black).put("points",org.json.JSONArray(it.points.map {p->org.json.JSONArray().put(p.x).put(p.y)}))}))
            File(report+".raw.json").writeText(json.toString())
        }
        assertTrue(raw.labels.any {it.text=="3,15"});assertTrue(raw.paths.any {it.red})
        val result=CurveCatalog.load(context,pdf,File(context.cacheDir,"real-map-check"))
        System.getenv("DM_AGRO_CURVE_REPORT")?.let {report->
            val label=raw.labels.first {it.text=="3,15"};val point=CurveGeometry.factory.createPoint(label.at)
            val plots=raw.paths.filter {it.black&&it.layer.contains("Talh")}.mapNotNull {CurveGeometry.polygon(it.points)}.filter {it.covers(point)&&it.area>100}
            val lines=raw.paths.filter {it.red}.map {CurveGeometry.factory.createLineString(it.points.toTypedArray())}
            val diagnostic=org.json.JSONArray()
            for(plot in plots) for(cell in CurveGeometry.cells(plot,lines)) if(cell.covers(point)) diagnostic.put(org.json.JSONObject().put("area",cell.area).put("plotArea",plot.area).put("holes",cell.numInteriorRing)
                .put("holesArea",(0 until cell.numInteriorRing).sumOf {CurveGeometry.factory.createPolygon(cell.getInteriorRingN(it).coordinates).area})
                .put("labels",org.json.JSONArray(raw.labels.filter {it.text.contains(',')&&cell.covers(CurveGeometry.factory.createPoint(it.at))}.map {it.text})))
            File(report+".cells.json").writeText(diagnostic.toString(2))
        }
        System.getenv("DM_AGRO_CURVE_REPORT")?.let {File(it).writeText(result.json().toString(2))}
        assertTrue("O talhão 8 precisa ser reconhecido",result.curves.any {it.plot=="8"})
        val target=result.matches("8","3,15");assertEquals("Curva real 3,15 do K39",1,target.size)
        assertTrue(abs(target.single().measuredHa-3.15)<.05)
    }
}
