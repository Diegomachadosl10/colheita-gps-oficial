package com.colheitagps.app

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import org.locationtech.jts.geom.*
import java.io.File
import java.security.MessageDigest
import kotlin.math.*

internal data class MapCurve(val id:String,val plot:String,val hectares:String,val polygon:Polygon,val measuredHa:Double)
internal data class CurveCatalog(val width:Double,val height:Double,val curves:List<MapCurve>,val labels:Int,val rejected:Int) {
    fun matches(plot:String,area:String):List<MapCurve> = curves.filter {(plot.isBlank()||it.plot==plot)&&
        (area.isBlank()||it.hectares.replace(',','.').toBigDecimalOrNull()?.compareTo(area.replace(',','.').toBigDecimalOrNull())==0)}
    companion object {
        private fun norm(s:String)=java.text.Normalizer.normalize(s,java.text.Normalizer.Form.NFD).replace(Regex("\\p{M}"),"").lowercase()
        private fun hash(s:ByteArray)=MessageDigest.getInstance("SHA-256").digest(s).joinToString("") {"%02x".format(it)}
        fun load(context:Context,pdf:File,cache:File):CurveCatalog {
            val digest=pdf.inputStream().use {input->val d=MessageDigest.getInstance("SHA-256");val buffer=ByteArray(8192);while(true) {val n=input.read(buffer);if(n<0) break;d.update(buffer,0,n)};d.digest().joinToString("") {"%02x".format(it)}}
            val file=File(cache,"curvas-v1/$digest.json")
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
            val plots=mutableListOf<Pair<String,Polygon>>()
            for(path in parsed.paths) {
                if(!path.black||!norm(path.layer).contains("talh")||path.points.size<4||path.points.first().distance(path.points.last())>.1) continue
                val polygon=CurveGeometry.polygon(path.points)?:continue
                if(polygon.area<100) continue
                val numbers=integerLabels.filter {polygon.covers(CurveGeometry.factory.createPoint(it.at))}
                if(numbers.size!=1) continue
                if(plots.none {it.first==numbers.single().text&&abs(it.second.area-polygon.area)<1}) plots.add(numbers.single().text to polygon)
            }
            val terraces=parsed.paths.filter {it.red}.mapNotNull {path->try {CurveGeometry.factory.createLineString(path.points.toTypedArray()).takeIf {it.length>0}} catch(_:Exception) {null}}
            val result=mutableListOf<MapCurve>();var rejected=0
            for((number,plot) in plots) {
                val printed=decimalLabels.filter {plot.covers(CurveGeometry.factory.createPoint(it.at))}
                val plotHa=measure(plot)?:continue
                // The boxed total area of the plot is not the identifier of an individual curve.
                val labels=printed.filter {label->val value=label.text.replace(',','.').toDouble();abs(value-plotHa)>maxOf(.04,plotHa*.025)}
                val candidates=try {CurveGeometry.cells(plot,terraces.filter {it.envelopeInternal.intersects(plot.envelopeInternal)})} catch(_:Exception) {emptyList()}
                for(cell in candidates) {
                    val holes=(0 until cell.numInteriorRing).sumOf {CurveGeometry.factory.createPolygon(cell.getInteriorRingN(it).coordinates).area}
                    if(holes>maxOf(.000001,cell.area*.0000001)) continue
                    val within=labels.filter {cell.covers(CurveGeometry.factory.createPoint(it.at))}
                    if(within.size!=1) continue
                    val label=within.single();val ha=measure(cell)?:continue;val printedHa=label.text.replace(',','.').toDouble()
                    if(abs(ha-printedHa)>maxOf(.025,printedHa*.025)) {rejected++;continue}
                    val normalized=CurveGeometry.polygon(cell.exteriorRing.coordinates.map {Coordinate(it.x/parsed.width,it.y/parsed.height)})?:continue
                    val id=hash((number+"/"+label.text+"/"+normalized.toText()).toByteArray()).take(24)
                    result.add(MapCurve(id,number,label.text,normalized,ha))
                }
            }
            return CurveCatalog(parsed.width,parsed.height,result.distinctBy {it.id},decimalLabels.size,rejected)
        }
        private fun decode(json:JSONObject):CurveCatalog {
            val all=json.getJSONArray("curves");val curves=(0 until all.length()).map {i->
                val row=all.getJSONObject(i);val points=row.getJSONArray("points")
                val polygon=CurveGeometry.polygon((0 until points.length()).map {n->val a=points.getJSONArray(n);Coordinate(a.getDouble(0),a.getDouble(1))})?:error("Curva inválida")
                require(polygon.coordinates.all {it.x in 0.0..1.0&&it.y in 0.0..1.0})
                MapCurve(row.getString("id"),row.getString("plot"),row.getString("ha"),polygon,row.getDouble("measured"))
            }
            return CurveCatalog(json.getDouble("width"),json.getDouble("height"),curves,json.getInt("labels"),json.getInt("rejected"))
        }
    }
    fun json()=JSONObject().put("width",width).put("height",height).put("labels",labels).put("rejected",rejected).put("curves",JSONArray(curves.map {curve->
        JSONObject().put("id",curve.id).put("plot",curve.plot).put("ha",curve.hectares).put("measured",curve.measuredHa)
            .put("points",JSONArray(curve.polygon.exteriorRing.coordinates.map {JSONArray().put(it.x).put(it.y)}))
    }))
}
