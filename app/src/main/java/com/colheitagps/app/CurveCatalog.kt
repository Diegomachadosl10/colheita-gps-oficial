package com.colheitagps.app

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import org.locationtech.jts.geom.*
import java.io.File
import java.security.MessageDigest
import kotlin.math.*

internal data class MapCurve(val id:String,val plot:String,val hectares:String,val polygon:Polygon,val measuredHa:Double) {
    val areaAgrees get()=abs(measuredHa-hectares.replace(',','.').toDouble())<=maxOf(.025,hectares.replace(',','.').toDouble()*.025)
}
internal data class MapPlot(val id:String,val number:String,val polygons:List<Polygon>,val measuredHa:Double,val printedHa:String)
internal data class CurveCatalog(val width:Double,val height:Double,val curves:List<MapCurve>,val labels:Int,val rejected:Int,val plots:List<MapPlot> = emptyList()) {
    fun matches(plot:String,area:String):List<MapCurve> = curves.filter {(plot.isBlank()||it.plot==plot)&&
        (area.isBlank()||it.hectares.replace(',','.').toBigDecimalOrNull()?.compareTo(area.replace(',','.').toBigDecimalOrNull())==0)}
    fun contains(d:VoiceCommands.Draft)=if(d.fields.optString("paintTarget")=="plot") plots.any {it.number==d.fields.optString("plots")} else matches(d.fields.optString("plots"),d.fields.optString("ha")).isNotEmpty()
    companion object {
        private fun norm(s:String)=java.text.Normalizer.normalize(s,java.text.Normalizer.Form.NFD).replace(Regex("\\p{M}"),"").lowercase()
        private fun hash(s:ByteArray)=MessageDigest.getInstance("SHA-256").digest(s).joinToString("") {"%02x".format(it)}
        private fun normalized(polygon:Polygon,width:Double,height:Double)=CurveGeometry.polygon(polygon.exteriorRing.coordinates.map {Coordinate(it.x/width,it.y/height)})?.takeIf {p->p.coordinates.all {it.x in 0.0..1.0&&it.y in 0.0..1.0}}
        fun load(context:Context,pdf:File,cache:File):CurveCatalog {
            val digest=pdf.inputStream().use {input->val d=MessageDigest.getInstance("SHA-256");val buffer=ByteArray(8192);while(true) {val n=input.read(buffer);if(n<0) break;d.update(buffer,0,n)};d.digest().joinToString("") {"%02x".format(it)}}
            val file=File(cache,"curvas-v2/$digest.json")
            if(file.isFile) try {return decode(JSONObject(file.readText()))} catch(_:Exception) {file.delete()}
            val parsed=PdfCurveReader.read(context,pdf)
            val geo=GeoPdf.ler(pdf)?:error("Este PDF não tem a georreferência necessária para conferir as áreas. Continue com a pintura manual.")
            val catalog=recognize(parsed) {polygon->
                val positions=polygon.exteriorRing.coordinates.map {geo.pixelParaMetros((it.x/parsed.width*1_000_000).toFloat(),(it.y/parsed.height*1_000_000).toFloat(),1_000_000,1_000_000)}
                if(positions.any {it==null}) null else {
                    val xy=positions.filterNotNull();abs((0 until xy.size-1).sumOf {i->xy[i].first*xy[i+1].second-xy[i+1].first*xy[i].second})/20000
                }
            }
            file.parentFile!!.mkdirs();val temporary=File(file.path+".tmp");temporary.writeText(catalog.json().toString());check(temporary.renameTo(file))
            return catalog
        }
        fun recognize(parsed:PdfCurveReader.Companion.Result,measure:(Polygon)->Double?):CurveCatalog {
            require(parsed.paths.sumOf {it.points.size}<300000&&parsed.labels.size<10000) {"Este mapa é complexo demais para conferir automaticamente no aparelho. Use a pintura manual."}
            val integerLabels=parsed.labels.filter {it.text.matches(Regex("[0-9]{1,3}"))}
            val decimalLabels=parsed.labels.filter {it.text.matches(Regex("[0-9]+[,.][0-9]{1,4}"))}
            val groups=parsed.paths.withIndex().filter {(_,path)->path.black&&norm(path.layer).contains("talh")}.groupBy {if(it.value.group>0) it.value.group else -it.index-1}
            val plots=mutableListOf<Pair<String,List<Polygon>>>()
            for(paths in groups.values) {
                // A single PDF drawing can contain disjoint parts of the same numbered plot.
                // Keep their separate contours: a road between them must never be painted.
                val parts=paths.mapNotNull {(_,path)->
                    if(path.points.size<4||path.points.first().distance(path.points.last())>.1) null else CurveGeometry.polygon(path.points)?.takeIf {it.area>1}
                }
                if(parts.isEmpty()||parts.sumOf {it.area}<100) continue
                val union=org.locationtech.jts.operation.union.UnaryUnionOp.union(parts)
                if(abs(union.area-parts.sumOf {it.area})>.00001) continue
                val numbers=integerLabels.filter {union.covers(CurveGeometry.factory.createPoint(it.at))}
                if(numbers.size!=1) continue
                plots.add(numbers.single().text to parts)
            }
            val terraces=CurveGeometry.connectTerraceEnds(parsed.paths.filter {it.red}.mapNotNull {path->try {CurveGeometry.factory.createLineString(path.points.toTypedArray()).takeIf {it.length>0}} catch(_:Exception) {null}})
            val result=mutableListOf<MapCurve>();val whole=mutableListOf<MapPlot>();var rejected=0
            for((number,parts) in plots) {
                val measurements=parts.map {measure(it)}
                if(measurements.any {it==null||!it.isFinite()||it<=0}) continue
                val plotHa=measurements.filterNotNull().sum()
                val printed=decimalLabels.filter {label->parts.any {it.covers(CurveGeometry.factory.createPoint(label.at))}}
                val totals=printed.filter {abs(it.text.replace(',','.').toDouble()-plotHa)<=maxOf(.04,plotHa*.025)}
                val polygons=parts.mapNotNull {normalized(it,parsed.width,parsed.height)}
                if(totals.size==1&&polygons.size==parts.size) {
                    val id=hash((number+parts.joinToString {it.toText()}).toByteArray()).take(24)
                    whole.add(MapPlot(id,number,polygons,plotHa,totals.single().text))
                }
                for(plot in parts) {
                    val labels=printed.filter {it !in totals&&plot.covers(CurveGeometry.factory.createPoint(it.at))}
                    val candidates=try {CurveGeometry.cells(plot,terraces.filter {it.envelopeInternal.intersects(plot.envelopeInternal)})} catch(_:Exception) {emptyList()}
                    for(cell in candidates) {
                        val holes=(0 until cell.numInteriorRing).sumOf {CurveGeometry.factory.createPolygon(cell.getInteriorRingN(it).coordinates).area}
                        if(holes>maxOf(.000001,cell.area*.0000001)) continue
                        val within=labels.filter {cell.covers(CurveGeometry.factory.createPoint(it.at))}
                        if(within.size!=1) continue
                        val label=within.single();val ha=measure(cell)?:continue;val printedHa=label.text.replace(',','.').toDouble()
                        // Printed hectares identify the requested strip. Its original GPS
                        // geometry is never stretched to force that label to match.
                        // A discrepancy is disclosed in the preview; gross mismatches stay manual.
                        if(abs(ha-printedHa)>maxOf(.025,printedHa*.35)) {rejected++;continue}
                        val normalized=normalized(cell,parsed.width,parsed.height)?:continue
                        val id=hash((number+"/"+label.text+"/"+normalized.toText()).toByteArray()).take(24)
                        result.add(MapCurve(id,number,label.text,normalized,ha))
                    }
                }
            }
            return CurveCatalog(parsed.width,parsed.height,result.distinctBy {it.id},decimalLabels.size,rejected,whole.distinctBy {it.id})
        }
        private fun points(polygon:Polygon)=JSONArray(polygon.exteriorRing.coordinates.map {JSONArray().put(it.x).put(it.y)})
        private fun polygon(points:JSONArray):Polygon {
            val polygon=CurveGeometry.polygon((0 until points.length()).map {n->val a=points.getJSONArray(n);Coordinate(a.getDouble(0),a.getDouble(1))})?:error("Contorno inválido")
            require(polygon.coordinates.all {it.x in 0.0..1.0&&it.y in 0.0..1.0});return polygon
        }
        private fun decode(json:JSONObject):CurveCatalog {
            val all=json.getJSONArray("curves");val curves=(0 until all.length()).map {i->
                val row=all.getJSONObject(i);MapCurve(row.getString("id"),row.getString("plot"),row.getString("ha"),polygon(row.getJSONArray("points")),row.getDouble("measured"))
            }
            val allPlots=json.getJSONArray("plots");val plots=(0 until allPlots.length()).map {i->
                val row=allPlots.getJSONObject(i);val parts=row.getJSONArray("parts")
                MapPlot(row.getString("id"),row.getString("number"),(0 until parts.length()).map {polygon(parts.getJSONArray(it))},row.getDouble("measured"),row.getString("printed"))
            }
            return CurveCatalog(json.getDouble("width"),json.getDouble("height"),curves,json.getInt("labels"),json.getInt("rejected"),plots)
        }
    }
    fun json()=JSONObject().put("width",width).put("height",height).put("labels",labels).put("rejected",rejected).put("curves",JSONArray(curves.map {curve->
        JSONObject().put("id",curve.id).put("plot",curve.plot).put("ha",curve.hectares).put("measured",curve.measuredHa).put("points",points(curve.polygon))
    })).put("plots",JSONArray(plots.map {plot->JSONObject().put("id",plot.id).put("number",plot.number).put("measured",plot.measuredHa).put("printed",plot.printedHa).put("parts",JSONArray(plot.polygons.map {points(it)}))}))
}
