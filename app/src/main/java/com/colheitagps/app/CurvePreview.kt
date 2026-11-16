package com.colheitagps.app

import android.graphics.*
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import org.locationtech.jts.geom.Polygon
import java.io.File

internal object CurvePreview {
    data class Picture(val bitmap:Bitmap,val bounds:RectF) {
        fun point(x:Float,y:Float)=org.locationtech.jts.geom.Coordinate((bounds.left+x*bounds.width()).toDouble(),(bounds.top+y*bounds.height()).toDouble())
    }
    fun render(pdf:File,polygons:List<Polygon>):Picture {
        val envelope=org.locationtech.jts.geom.Envelope();polygons.forEach {envelope.expandToInclude(it.envelopeInternal)}
        require(!envelope.isNull)
        envelope.expandBy(maxOf(envelope.width,envelope.height)*.18+.01)
        val bounds=RectF(envelope.minX.toFloat().coerceAtLeast(0f),envelope.minY.toFloat().coerceAtLeast(0f),envelope.maxX.toFloat().coerceAtMost(1f),envelope.maxY.toFloat().coerceAtMost(1f))
        ParcelFileDescriptor.open(pdf,ParcelFileDescriptor.MODE_READ_ONLY).use {descriptor->PdfRenderer(descriptor).use {renderer->renderer.openPage(0).use {page->
            val aspect=page.height*bounds.height()/(page.width*bounds.width())
            val w=if(aspect>1.5f) (1200/aspect).toInt().coerceAtLeast(250) else 900
            val h=(w*aspect).toInt().coerceIn(200,1400)
            val bitmap=Bitmap.createBitmap(w,h,Bitmap.Config.ARGB_8888)
            try {
                bitmap.eraseColor(Color.WHITE)
                val scaleX=w/(page.width*bounds.width());val scaleY=h/(page.height*bounds.height())
                val matrix=Matrix().apply {setScale(scaleX,scaleY);postTranslate(-bounds.left*page.width*scaleX,-bounds.top*page.height*scaleY)}
                page.render(bitmap,null,matrix,PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                val canvas=Canvas(bitmap);val paint=Paint(Paint.ANTI_ALIAS_FLAG)
                polygons.forEachIndexed {index,polygon->
                    val path=Path();polygon.exteriorRing.coordinates.forEachIndexed {n,p->val x=(p.x-bounds.left)*page.width*scaleX;val y=(p.y-bounds.top)*page.height*scaleY;if(n==0) path.moveTo(x.toFloat(),y.toFloat()) else path.lineTo(x.toFloat(),y.toFloat())};path.close()
                    paint.color=Color.argb(65,0,110,240);paint.style=Paint.Style.FILL;canvas.drawPath(path,paint)
                    paint.color=Color.rgb(0,70,200);paint.style=Paint.Style.STROKE;paint.strokeWidth=3f;canvas.drawPath(path,paint)
                    if(polygons.size>1) {
                        val c=polygon.interiorPoint.coordinate;val x=((c.x-bounds.left)*page.width*scaleX).toFloat();val y=((c.y-bounds.top)*page.height*scaleY).toFloat()
                        paint.style=Paint.Style.FILL;paint.color=Color.WHITE;canvas.drawCircle(x,y,20f,paint)
                        paint.color=Color.rgb(0,60,180);paint.textSize=28f;paint.typeface=Typeface.DEFAULT_BOLD;paint.textAlign=Paint.Align.CENTER;canvas.drawText((index+1).toString(),x,y+10,paint)
                    }
                }
                return Picture(bitmap,bounds)
            } catch(error:Throwable) {bitmap.recycle();throw error}
        }}}
    }
}
