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
    /** Sweep from the indicated road/side until exactly half the polygon area is reached. */
    fun half(polygon:Polygon,reference:Coordinate,scaleX:Double=1.0,scaleY:Double=1.0):Polygon? {
        if(scaleX!=1.0||scaleY!=1.0) {
            require(scaleX.isFinite()&&scaleY.isFinite()&&scaleX>0&&scaleY>0)
            val transform=org.locationtech.jts.geom.util.AffineTransformation.scaleInstance(scaleX,scaleY)
            val result=half(transform.transform(polygon) as Polygon,Coordinate(reference.x*scaleX,reference.y*scaleY))?:return null
            return transform.inverse.transform(result) as? Polygon
        }
        val center=polygon.centroid.coordinate;val distance=center.distance(reference)
        if(distance<1e-8) return null
        val ux=(center.x-reference.x)/distance;val uy=(center.y-reference.y)/distance
        val vx=-uy;val vy=ux
        val coords=polygon.exteriorRing.coordinates
        val projections=coords.map {(it.x-reference.x)*ux+(it.y-reference.y)*uy}
        val across=coords.map {(it.x-reference.x)*vx+(it.y-reference.y)*vy}
        val margin=max(polygon.envelopeInternal.width,polygon.envelopeInternal.height)+1
        val start=projections.min()-margin;val lowV=across.min()-margin;val highV=across.max()+margin
        fun point(u:Double,v:Double)=Coordinate(reference.x+u*ux+v*vx,reference.y+u*uy+v*vy)
        fun cut(end:Double):Geometry {
            val box=factory.createPolygon(arrayOf(point(start,lowV),point(end,lowV),point(end,highV),point(start,highV),point(start,lowV)))
            return polygon.intersection(box)
        }
        var low=projections.min();var high=projections.max()
        repeat(48) {val mid=(low+high)/2;if(cut(mid).area<polygon.area/2) low=mid else high=mid}
        return (cut((low+high)/2) as? Polygon)?.takeIf {it.isValid&&abs(it.area/polygon.area-.5)<.0001}
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
