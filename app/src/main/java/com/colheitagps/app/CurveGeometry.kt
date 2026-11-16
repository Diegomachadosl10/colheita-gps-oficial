package com.colheitagps.app

import org.locationtech.jts.geom.*
import org.locationtech.jts.operation.linemerge.LineMerger
import org.locationtech.jts.operation.polygonize.Polygonizer
import org.locationtech.jts.operation.distance.DistanceOp
import org.locationtech.jts.operation.union.UnaryUnionOp
import org.locationtech.jts.precision.GeometryPrecisionReducer
import kotlin.math.*

/** Geometry remains in PDF coordinates. Viewport rotation and GPS transforms are never changed. */
internal object CurveGeometry {
    val factory=GeometryFactory()
    fun polygon(points:List<Coordinate>):Polygon? = try {
        if(points.size<3) null else factory.createPolygon((points+if(!points.first().equals2D(points.last())) listOf(points.first()) else emptyList()).toTypedArray()).takeIf {it.isValid&&it.area>0}
    } catch(_:Exception) {null}
    fun cells(plot:Polygon,lines:List<LineString>):List<Polygon> {
        if(lines.isEmpty()) return emptyList()
        val clipped=UnaryUnionOp.union(lines).intersection(plot)
        var network=UnaryUnionOp.union(listOf(clipped,plot.boundary))
        network=UnaryUnionOp.union(GeometryPrecisionReducer.reduce(network,PrecisionModel(1000.0)))
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
            if(links.isNotEmpty()) network=UnaryUnionOp.union(listOf(network)+links)
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
