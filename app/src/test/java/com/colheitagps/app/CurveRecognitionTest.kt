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
    @Test fun harvestingHalfFollowsBothCurvedBordersAcrossTheEntireLength() {
        val top=listOf(Coordinate(0.0,20.0),Coordinate(25.0,0.0),Coordinate(50.0,40.0),Coordinate(75.0,10.0),Coordinate(100.0,30.0))
        val polygon=CurveGeometry.polygon(top+top.reversed().map {Coordinate(it.x,it.y+20)})!!
        val upper=org.locationtech.jts.operation.union.UnaryUnionOp.union(CurveGeometry.harvestHalf(polygon,true,1.0,1.0))
        val lower=org.locationtech.jts.operation.union.UnaryUnionOp.union(CurveGeometry.harvestHalf(polygon,false,1.0,1.0))
        assertEquals(polygon.area/2,upper.area,.00001);assertEquals(polygon.area/2,lower.area,.00001)
        assertEquals(0.0,upper.intersection(lower).area,.00001);assertEquals(0.0,upper.union(lower).symDifference(polygon).area,.00001)
        for(i in 0 until top.lastIndex) {
            val a=top[i];val b=top[i+1];val x=(a.x+b.x)/2;val y=(a.y+b.y)/2
            val high=CurveGeometry.factory.createPoint(Coordinate(x,y+5));val low=CurveGeometry.factory.createPoint(Coordinate(x,y+15))
            assertTrue("A faixa superior deve chegar a toda a curva",upper.covers(high));assertFalse(upper.covers(low))
            assertTrue(lower.covers(low));assertFalse(lower.covers(high))
        }
    }
    @Test fun smallVectorGapsCanBeRepairedWithoutMergingTheAdjacentTerraces() {
        val plot=box(0.0,0.0,100.0,100.0)
        val line=CurveGeometry.factory.createLineString(arrayOf(Coordinate(.01,50.0),Coordinate(100.0,50.0)))
        val cells=CurveGeometry.cells(plot,listOf(line));assertEquals(2,cells.size);assertTrue(cells.all {abs(it.area-5000)<1})
    }
    @Test fun interruptedTerracesReconnectOnlyAcrossAlignedMutualEndpoints() {
        fun line(vararg p:Coordinate)=CurveGeometry.factory.createLineString(p)
        val a=line(Coordinate(0.0,0.0),Coordinate(0.0,10.0));val b=line(Coordinate(.1,12.5),Coordinate(.1,20.0))
        assertEquals(3,CurveGeometry.connectTerraceEnds(listOf(a,b)).size)
        val wrong=line(Coordinate(1.0,10.0),Coordinate(1.0,20.0))
        assertEquals(2,CurveGeometry.connectTerraceEnds(listOf(a,wrong)).size)
        val far=line(Coordinate(0.0,14.0),Coordinate(0.0,20.0))
        assertEquals(2,CurveGeometry.connectTerraceEnds(listOf(a,far)).size)
    }
    @Test fun oneAreaLabelPerCellAndAnIndependentAreaCheckAreRequiredBeforeRecognizingACurve() {
        val points=box(0.0,0.0,100.0,100.0).coordinates.toList()
        val parsed=PdfCurveReader.Companion.Result(100.0,100.0,listOf(PdfCurveReader.Label("9",Coordinate(20.0,20.0)),PdfCurveReader.Label("1,00",Coordinate(30.0,30.0)),PdfCurveReader.Label("1,00",Coordinate(30.0,70.0))),
            listOf(PdfCurveReader.Outline("Talhões",points,false,true),PdfCurveReader.Outline("Linhas",listOf(Coordinate(0.0,50.0),Coordinate(100.0,50.0)),true,false)))
        val catalog=CurveCatalog.recognize(parsed) {it.area/5000}
        assertEquals(2,catalog.matches("9","1,00").size);assertNotEquals(catalog.curves[0].id,catalog.curves[1].id)
        assertTrue(CurveCatalog.recognize(parsed) {it.area/100}.curves.isEmpty())
    }
    @Test fun disjointPartsBelongTogetherOnlyWhenThePdfGroupsThemAndTheirTotalAreaAgrees() {
        val a=box(0.0,0.0,40.0,100.0);val b=box(60.0,0.0,40.0,100.0)
        val labels=listOf(PdfCurveReader.Label("11",Coordinate(80.0,50.0)),PdfCurveReader.Label("8,00",Coordinate(20.0,50.0)))
        val paths=listOf(PdfCurveReader.Outline("Talhões",a.coordinates.toList(),false,true,7),PdfCurveReader.Outline("Talhões",b.coordinates.toList(),false,true,7))
        val parsed=PdfCurveReader.Companion.Result(100.0,100.0,labels,paths)
        val catalog=CurveCatalog.recognize(parsed) {it.area/1000}
        assertEquals(2,catalog.plots.single().polygons.size);assertEquals(8.0,catalog.plots.single().measuredHa,.001)
        assertTrue(CurveCatalog.recognize(parsed.copy(paths=listOf(paths[0],paths[1].copy(group=8)))) {it.area/1000}.plots.isEmpty())
        assertTrue(CurveCatalog.recognize(parsed.copy(labels=labels+PdfCurveReader.Label("12",Coordinate(25.0,60.0)))) {it.area/1000}.plots.isEmpty())
    }
    @Test fun spatialTiesAreNotResolvedArbitrarilyAndDistanceUsesOriginalMapProportions() {
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
        val plot=result.plots.single {it.number=="11"}
        assertEquals("Talhão 11 tem duas partes separadas por carreador",2,plot.polygons.size)
        assertEquals("8,52",plot.printedHa);assertEquals(8.52,plot.measuredHa,.15)
        assertEquals("Curva real 2,99 do talhão 11",1,result.matches("11","2,99").size)
        val curve=result.matches("11","2,99").single()
        assertEquals(2.49,curve.measuredHa,.08);assertFalse("A diferença entre a área impressa e o contorno deve ser informada",curve.areaAgrees)
        assertTrue(result.contains(VoiceCommands.parse("Deneago, quero que pinte o talhão 11 do K39 inteiro")))
        assertTrue(result.contains(VoiceCommands.parse("Dê-me água, quero que pinte a curva de 2,99 do talhão 11 do K39")))
        assertTrue(result.contains(VoiceCommands.parse("Dê-me água, quero que pinte metade da curva de 2,99 do talhão 11 do K39")))
        val halves=CurveGeometry.harvestHalf(curve.polygon,true,result.width,result.height)
        assertTrue("A curva 2,99 pode ser pintada pela metade",halves.isNotEmpty())
        assertEquals(curve.polygon.area/2,halves.sumOf {it.area},curve.polygon.area*.0001)
        val photographed=result.matches("15","2,52").single()
        val high=CurveGeometry.harvestHalf(photographed.polygon,true,result.width,result.height)
        val low=CurveGeometry.harvestHalf(photographed.polygon,false,result.width,result.height)
        assertEquals(photographed.polygon.area/2,high.sumOf {it.area},photographed.polygon.area*.00001)
        assertEquals(photographed.polygon.area/2,low.sumOf {it.area},photographed.polygon.area*.00001)
        val united=org.locationtech.jts.operation.union.UnaryUnionOp.union(high+low)
        assertEquals(0.0,united.symDifference(photographed.polygon).area,photographed.polygon.area*.00001)
        assertEquals(photographed.polygon.envelopeInternal.minX,high.minOf {it.envelopeInternal.minX},.000001)
        assertEquals(photographed.polygon.envelopeInternal.maxX,high.maxOf {it.envelopeInternal.maxX},.000001)
        System.getenv("DM_AGRO_CURVE_REPORT")?.let {report->
            fun points(polygons:List<Polygon>)=org.json.JSONArray(polygons.map {p->org.json.JSONArray(p.coordinates.map {org.json.JSONArray().put(it.x).put(it.y)})})
            File(report+".harvest.json").writeText(org.json.JSONObject().put("original",points(listOf(photographed.polygon))).put("top",points(high)).put("bottom",points(low)).toString(2))
        }
        val cached=CurveCatalog.load(context,pdf,File(context.cacheDir,"real-map-check"));assertEquals(result.json().toString(),cached.json().toString())
        System.getenv("DM_AGRO_CURVE_REPORT")?.let {report->
            val examples=org.json.JSONObject().put("plot11",org.json.JSONArray(plot.polygons.map {p->org.json.JSONArray(p.coordinates.map {org.json.JSONArray().put(it.x).put(it.y)})}))
                .put("curve299",org.json.JSONArray(curve.polygon.coordinates.map {org.json.JSONArray().put(it.x).put(it.y)}))
                .put("half299",org.json.JSONArray(halves.map {p->org.json.JSONArray(p.coordinates.map {org.json.JSONArray().put(it.x).put(it.y)})}))
            File(report+".examples.json").writeText(examples.toString(2))
        }
    }
}
