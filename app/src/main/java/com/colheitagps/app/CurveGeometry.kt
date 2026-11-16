package com.colheitagps.app

import org.locationtech.jts.geom.*
import org.locationtech.jts.operation.linemerge.LineMerger
import org.locationtech.jts.operation.polygonize.Polygonizer
import org.locationtech.jts.operation.distance.DistanceOp
import org.locationtech.jts.operation.union.UnaryUnionOp
import org.locationtech.jts.operation.overlayng.UnaryUnionNG
import kotlin.math.*

/** Geometry remains in PDF coordinates. Viewport rotation and GPS transforms are never changed. */
internal object CurveGeometry {
    val factory=GeometryFactory()
    fun polygon(points:List<Coordinate>):Polygon? = try {
        if(points.size<3) null else factory.createPolygon((points+if(!points.first().equals2D(points.last())) listOf(points.first()) else emptyList()).toTypedArray()).takeIf {it.isValid&&it.area>0}
    } catch(_:Exception) {null}
    /** Rejoin small interruptions in a terrace line only when both ends face each
     * other and uniquely select each other. Plot/road boundaries are never moved. */
    fun connectTerraceEnds(lines:List<LineString>):List<LineString> {
        data class End(val line:Int,val at:Coordinate,val dx:Double,val dy:Double)
        val ends=mutableListOf<End>()
        for((index,line) in lines.withIndex()) {
            if(line.length<5||line.isClosed) continue
            val points=line.coordinates
            for(ordered in listOf(points.toList(),points.reversed())) {
                val point=ordered.first();val inner=ordered.drop(1).firstOrNull {it.distance(point)>=1.0}?:continue
                val d=inner.distance(point);ends.add(End(index,point,(point.x-inner.x)/d,(point.y-inner.y)/d))
            }
        }
        fun compatible(a:End,b:End):Double? {
            if(a.line==b.line) return null
            val d=a.at.distance(b.at);if(d<.001||d>3.0) return null
            val x=(b.at.x-a.at.x)/d;val y=(b.at.y-a.at.y)/d
            return d.takeIf {a.dx*x+a.dy*y>.7&&b.dx*(-x)+b.dy*(-y)>.7}
        }
        val nearest=ends.mapIndexed {i,a->
            val choices=ends.indices.filter {it!=i}.mapNotNull {j->compatible(a,ends[j])?.let {j to it}}.sortedBy {it.second}
            choices.firstOrNull()?.takeIf {choices.size==1||choices[1].second-it.second>.1}?.first
        }
        val result=lines.toMutableList()
        for(i in ends.indices) {
            val j=nearest[i]?:continue
            if(j<=i||nearest[j]!=i) continue
            val bridge=factory.createLineString(arrayOf(ends[i].at,ends[j].at))
            if(lines.any {it.crosses(bridge)}) continue
            result.add(bridge)
        }
        return result
    }
    fun cells(plot:Polygon,lines:List<LineString>):List<Polygon> {
        if(lines.isEmpty()) return emptyList()
        val clipped=UnaryUnionOp.union(lines).intersection(plot)
        val precision=PrecisionModel(1000.0)
        var network=UnaryUnionNG.union(factory.buildGeometry(listOf(clipped,plot.boundary)),precision)
        // Repair only small cartographic gaps: at most 0.35 PDF point. Every result is
        // separately validated against one printed label and the georeferenced area.
        for(tolerance in listOf(.05,.15,.35)) {
            val merger=LineMerger();merger.add(network)
            val chains=merger.mergedLineStrings.filterIsInstance<LineString>()
            val counts=mutableMapOf<Coordinate,Int>()
            for(line in chains) for(point in listOf(line.startPoint.coordinate,line.endPoint.coordinate)) counts[point]=(counts[point]?:0)+1
            val links=mutableListOf<Geometry>()
            for(line in chains) for(point in listOf(line.startPoint,line.endPoint)) {
                if(counts[point.coordinate]!=1) continue
                var nearest:Coordinate?=null;var distance=tolerance
                for(other in chains) {
                    if(other===line||!other.envelopeInternal.intersects(Envelope(point.coordinate).apply {expandBy(tolerance)})) continue
                    val d=other.distance(point)
                    if(d>1e-7&&d<distance) {distance=d;nearest=DistanceOp.nearestPoints(point,other)[1]}
                }
                nearest?.let {links.add(factory.createLineString(arrayOf(point.coordinate,it)))}
            }
            if(links.isNotEmpty()) network=UnaryUnionNG.union(factory.buildGeometry(listOf(network)+links),precision)
        }
        val polygonizer=Polygonizer();polygonizer.add(network)
        return polygonizer.polygons.filterIsInstance<Polygon>().filter {plot.covers(it.interiorPoint)&&it.area>1}
    }
    /** Half of every vertical cross-section, following both curved borders across
     * the entire strip. PDF Y grows downward; this never means left/right progress. */
    fun harvestHalf(polygon:Polygon,fromTop:Boolean,width:Double,height:Double):List<Polygon> {
        require(width.isFinite()&&height.isFinite()&&width>0&&height>0)
        require(polygon.isValid&&polygon.numInteriorRing==0&&polygon.numPoints<=10000)
        val transform=org.locationtech.jts.geom.util.AffineTransformation.scaleInstance(width,height)
        val original=transform.transform(polygon) as Polygon
        val coordinates=original.exteriorRing.coordinates
        val edges=coordinates.toList().zipWithNext().filter {abs(it.first.x-it.second.x)>1e-12}
        val xs=coordinates.map {it.x}.distinct().sorted()
        val pieces=mutableListOf<Polygon>()
        fun y(edge:Pair<Coordinate,Coordinate>,x:Double):Double {
            val (a,b)=edge;return a.y+(x-a.x)*(b.y-a.y)/(b.x-a.x)
        }
        for(i in 0 until xs.lastIndex) {
            val left=xs[i];val right=xs[i+1];val middle=(left+right)/2
            if(right-left<1e-10) continue
            val crossed=edges.filter {middle>min(it.first.x,it.second.x)&&middle<max(it.first.x,it.second.x)}.sortedBy {y(it,middle)}
            check(crossed.size%2==0) {"Não foi possível conferir a faixa desta curva."}
            for(j in crossed.indices step 2) {
                val upper=crossed[j];val lower=crossed[j+1]
                val ul=y(upper,left);val ur=y(upper,right);val ll=y(lower,left);val lr=y(lower,right)
                val ml=(ul+ll)/2;val mr=(ur+lr)/2
                val points=if(fromTop) listOf(Coordinate(left,ul),Coordinate(right,ur),Coordinate(right,mr),Coordinate(left,ml))
                    else listOf(Coordinate(left,ml),Coordinate(right,mr),Coordinate(right,lr),Coordinate(left,ll))
                CurveGeometry.polygon(points)?.let(pieces::add)
            }
        }
        if(pieces.isEmpty()) return emptyList()
        val precision=PrecisionModel(10000000.0)
        val union=UnaryUnionNG.union(factory.buildGeometry(pieces),precision)
        val result=org.locationtech.jts.operation.overlayng.OverlayNG.overlay(union,original,org.locationtech.jts.operation.overlayng.OverlayNG.INTERSECTION,precision)
        check(result.isValid&&abs(result.area/original.area-.5)<.00001) {"Não foi possível conferir a metade desta curva."}
        // Discard only numerical dust that would collapse to zero when Android
        // stores pixels as Float; retain small, genuine pieces of the band.
        val parts=(0 until result.numGeometries).map {result.getGeometryN(it)}.filterIsInstance<Polygon>().filter {it.area>maxOf(1e-8,original.area*1e-8)}.sortedBy {it.area}
        check(abs(parts.sumOf {it.area}-result.area)<original.area*.000001) {"Não foi possível conferir todos os trechos da faixa."}
        check(parts.isNotEmpty()&&parts.all {it.numInteriorRing==0}) {"Confira a curva manualmente antes de pintar."}
        return parts.map {transform.inverse.transform(it) as Polygon}
    }
    fun uniqueNearest(curves:List<Polygon>,reference:Coordinate,tieDistance:Double,scaleX:Double=1.0,scaleY:Double=1.0):Int? {
        if(scaleX!=1.0||scaleY!=1.0) {
            require(scaleX.isFinite()&&scaleY.isFinite()&&scaleX>0&&scaleY>0)
            val transform=org.locationtech.jts.geom.util.AffineTransformation.scaleInstance(scaleX,scaleY)
            return uniqueNearest(curves.map {transform.transform(it) as Polygon},Coordinate(reference.x*scaleX,reference.y*scaleY),tieDistance)
        }
        val point=factory.createPoint(reference)
        val order=curves.indices.sortedBy {curves[it].distance(point)}
        if(order.isEmpty()) return null
        return order.first().takeIf {order.size==1||curves[order[1]].distance(point)-curves[it].distance(point)>tieDistance}
    }
}
