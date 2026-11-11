package com.colheitagps.app

import android.graphics.Canvas
import android.graphics.PointF
import android.view.MotionEvent
import kotlin.math.*

/** Only the view changes. PDF pixels, drawings and georeferencing remain in map coordinates. */
internal class MapViewport {
    var scale=1f;private set
    var rotation=0f;private set
    var offsetX=0f;private set
    var offsetY=0f;private set
    fun fit(mapWidth:Int,mapHeight:Int,viewWidth:Int,viewHeight:Int) {
        if(minOf(mapWidth,mapHeight,viewWidth,viewHeight)<=0) return
        rotation=0f;scale=minOf(viewWidth.toFloat()/mapWidth,viewHeight.toFloat()/mapHeight)
        offsetX=(viewWidth-mapWidth*scale)/2f;offsetY=(viewHeight-mapHeight*scale)/2f
    }
    private fun rotated(x:Float,y:Float,angle:Float):PointF {
        val rad=Math.toRadians(angle.toDouble());val c=cos(rad);val s=sin(rad)
        return PointF((x*c-y*s).toFloat(),(x*s+y*c).toFloat())
    }
    fun toScreen(x:Float,y:Float):PointF=rotated(x*scale,y*scale,rotation).let {PointF(it.x+offsetX,it.y+offsetY)}
    fun toMap(x:Float,y:Float):PointF=rotated(x-offsetX,y-offsetY,-rotation).let {PointF(it.x/scale,it.y/scale)}
    fun pan(x:Float,y:Float) {if(x.isFinite()&&y.isFinite()) {offsetX+=x;offsetY+=y}}
    fun centerOn(x:Float,y:Float,screenX:Float,screenY:Float) {
        val p=rotated(x*scale,y*scale,rotation);offsetX=screenX-p.x;offsetY=screenY-p.y
    }
    fun gesture(oldX:Float,oldY:Float,newX:Float,newY:Float,factor:Float,degrees:Float) {
        if(!listOf(oldX,oldY,newX,newY,factor,degrees).all {it.isFinite()}||factor<=0f) return
        val anchor=toMap(oldX,oldY)
        scale=(scale*factor).coerceIn(0.05f,16f)
        rotation=((rotation+degrees)%360f+540f)%360f-180f
        centerOn(anchor.x,anchor.y,newX,newY)
    }
    fun resetRotation(screenX:Float,screenY:Float)=gesture(screenX,screenY,screenX,screenY,1f,-rotation)
    fun apply(canvas:Canvas) {canvas.translate(offsetX,offsetY);canvas.rotate(rotation);canvas.scale(scale,scale)}
}

/** Track pointer IDs, including replacement by a third finger, to avoid 180° jumps. */
internal class MapGestureController(private val viewport:MapViewport,private val minSpan:Float=20f) {
    var active=false;private set
    private var first=-1;private var second=-1
    private var x1=0f;private var y1=0f;private var x2=0f;private var y2=0f
    private fun rebase(event:MotionEvent,skip:Int=-1) {
        val remaining=(0 until event.pointerCount).filter {it!=skip}
        if(remaining.size<2) {first=-1;second=-1;return}
        val a=remaining[0];val b=remaining[1]
        first=event.getPointerId(a);second=event.getPointerId(b)
        x1=event.getX(a);y1=event.getY(a);x2=event.getX(b);y2=event.getY(b)
    }
    fun onTouch(event:MotionEvent):Boolean {
        if(event.actionMasked==MotionEvent.ACTION_DOWN) {active=false;first=-1;second=-1;return false}
        if(!active&&event.pointerCount<2) return false
        active=true
        when(event.actionMasked) {
            MotionEvent.ACTION_POINTER_DOWN->rebase(event)
            MotionEvent.ACTION_POINTER_UP->rebase(event,event.actionIndex)
            MotionEvent.ACTION_MOVE->{
                val a=event.findPointerIndex(first);val b=event.findPointerIndex(second)
                if(a<0||b<0) rebase(event) else {
                    val ax=event.getX(a);val ay=event.getY(a);val bx=event.getX(b);val by=event.getY(b)
                    val oldSpan=hypot(x2-x1,y2-y1);val newSpan=hypot(bx-ax,by-ay)
                    val valid=oldSpan>=minSpan&&newSpan>=minSpan
                    val angle=if(valid) Math.toDegrees((atan2((by-ay).toDouble(),(bx-ax).toDouble())-atan2((y2-y1).toDouble(),(x2-x1).toDouble()))).toFloat() else 0f
                    val delta=(angle+540f)%360f-180f
                    viewport.gesture((x1+x2)/2f,(y1+y2)/2f,(ax+bx)/2f,(ay+by)/2f,if(valid) newSpan/oldSpan else 1f,delta)
                    x1=ax;y1=ay;x2=bx;y2=by
                }
            }
            MotionEvent.ACTION_UP,MotionEvent.ACTION_CANCEL->{active=false;first=-1;second=-1}
        }
        return true
    }
}
