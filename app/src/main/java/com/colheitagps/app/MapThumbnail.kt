package com.colheitagps.app

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import android.widget.ImageView
import java.io.File
import java.util.concurrent.Executors

internal object MapThumbnail {
    private val worker=Executors.newSingleThreadExecutor {job->Thread(job,"dm-agro-map-thumbnails").apply {isDaemon=true}}
    fun load(view:ImageView,pdf:File,painted:File,token:String) {
        view.tag=token
        view.setImageDrawable(FieldIconDrawable(view.context,FieldIcon.MAP,AppTheme.colors(view.context).accent))
        worker.execute {
            var image:Bitmap?=null
            try {
                image=if(painted.isFile) StockShareFiles.decode(painted,240) else {
                    ParcelFileDescriptor.open(pdf,ParcelFileDescriptor.MODE_READ_ONLY).use {descriptor->PdfRenderer(descriptor).use {renderer->renderer.openPage(0).use {page->
                        val width=160;val height=(page.height.toFloat()/page.width*width).toInt().coerceIn(1,400)
                        Bitmap.createBitmap(width,height,Bitmap.Config.ARGB_8888).apply {eraseColor(Color.WHITE);page.render(this,null,null,PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)}
                    }}}
                }
            } catch(_:Exception) {} catch(_:OutOfMemoryError) {}
            val result=image
            view.post {if(view.tag==token) {if(result!=null) view.setImageBitmap(result)} else result?.recycle()}
        }
    }
}
