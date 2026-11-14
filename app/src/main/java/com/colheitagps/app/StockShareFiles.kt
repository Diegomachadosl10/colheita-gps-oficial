package com.colheitagps.app

import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.graphics.*
import android.graphics.pdf.PdfDocument
import android.net.Uri
import androidx.core.content.FileProvider
import org.json.JSONObject
import java.io.File
import java.util.UUID

internal object StockShareFiles {
    fun photo(root:File,record:JSONObject):File? {
        val path=record.optString("fotoEstoque");if(path.isBlank()) return null
        return try { File(path).canonicalFile.takeIf { it.isFile&&it.path.startsWith(root.canonicalPath+File.separator) } } catch(_:Exception) { null }
    }
    fun copyImage(context:Context,root:File,uri:Uri):File {
        val folder=File(root,"fotos_estoque").apply { mkdirs() }
        val draft=File(folder,"${UUID.randomUUID()}.tmp")
        try {
            val input=context.contentResolver.openInputStream(uri)?:error("Não foi possível ler a foto.")
            input.use { source->draft.outputStream().use { output->
                val buffer=ByteArray(8192);var total=0L
                while(true) {val count=source.read(buffer);if(count<0) break;total+=count;require(total<=20L*1024*1024) { "Escolha uma foto de até 20 MB." };output.write(buffer,0,count)}
            } }
            val bounds=BitmapFactory.Options().apply { inJustDecodeBounds=true };BitmapFactory.decodeFile(draft.path,bounds)
            require(bounds.outWidth>0&&bounds.outHeight>0) { "Este arquivo não é uma imagem válida." }
            val ext=when(bounds.outMimeType) {"image/png"->"png";"image/webp"->"webp";"image/gif"->"gif";"image/bmp"->"bmp";"image/heif","image/heic"->"heic";else->"jpg"}
            val destination=File(folder,"${UUID.randomUUID()}.$ext")
            check(draft.renameTo(destination)) { "Não foi possível guardar a foto." }
            return destination
        } catch(error:Exception) {draft.delete();throw error}
    }
    fun decode(file:File,maxEdge:Int):Bitmap? {
        val bounds=BitmapFactory.Options().apply { inJustDecodeBounds=true };BitmapFactory.decodeFile(file.path,bounds)
        var sample=1;while(maxOf(bounds.outWidth,bounds.outHeight)/sample>maxEdge) sample*=2
        val original=BitmapFactory.decodeFile(file.path,BitmapFactory.Options().apply { inSampleSize=sample })?:return null
        val orientation=try { android.media.ExifInterface(file.path).getAttributeInt(android.media.ExifInterface.TAG_ORIENTATION,1) } catch(_:Exception) { 1 }
        val matrix=Matrix().apply { when(orientation) {
            2->setScale(-1f,1f);3->setRotate(180f);4->setScale(1f,-1f)
            5->{setRotate(90f);postScale(-1f,1f)};6->setRotate(90f)
            7->{setRotate(-90f);postScale(-1f,1f)};8->setRotate(-90f)
        } }
        if(matrix.isIdentity) return original
        return Bitmap.createBitmap(original,0,0,original.width,original.height,matrix,true).also { if(it!==original) original.recycle() }
    }
    fun message(context:Context,cache:File,root:File,records:List<JSONObject>,text:String):Intent {
        val photos=records.mapNotNull { photo(root,it) }.distinctBy { it.path }
        val uris=ArrayList<Uri>()
        try {
            cache.mkdirs()
            for(file in photos) {
                val copy=File(cache,"estoque_${UUID.randomUUID()}.${file.extension.ifBlank { "jpg" }}")
                file.copyTo(copy);uris.add(FileProvider.getUriForFile(context.applicationContext,"${context.packageName}.files",copy))
            }
            return Intent(if(uris.size>1) Intent.ACTION_SEND_MULTIPLE else Intent.ACTION_SEND).apply {
                type=if(uris.isEmpty()) "text/plain" else "image/*"
                putExtra(Intent.EXTRA_TEXT,text)
                if(uris.size==1) putExtra(Intent.EXTRA_STREAM,uris.first())
                else if(uris.size>1) putParcelableArrayListExtra(Intent.EXTRA_STREAM,uris)
                if(uris.isNotEmpty()) {
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    clipData=ClipData.newUri(context.contentResolver,"Fotos dos estoques",uris.first()).apply { uris.drop(1).forEach { addItem(ClipData.Item(it)) } }
                }
            }
        } catch(error:Exception) { throw IllegalStateException("Não foi possível preparar as fotos para compartilhar. Tente novamente.",error) }
    }
    fun pdf(cache:File,root:File,records:List<JSONObject>,text:String):File {
        val doc=PdfDocument();var pageNumber=0
        try {
            val paint=Paint(Paint.ANTI_ALIAS_FLAG).apply { color=Color.BLACK;textSize=11f }
            val lines=text.lines().flatMap { raw->
                val result=mutableListOf<String>();var remaining=raw
                while(remaining.isNotEmpty()) {val count=paint.breakText(remaining,true,535f,null).coerceAtLeast(1);result.add(remaining.take(count));remaining=remaining.drop(count)}
                result.ifEmpty { listOf("") }
            }
            for(block in lines.chunked(46)) {
                val page=doc.startPage(PdfDocument.PageInfo.Builder(595,842,++pageNumber).create())
                block.forEachIndexed { index,line->page.canvas.drawText(line,30f,35f+index*16,paint) };doc.finishPage(page)
            }
            for(record in records) {
                val file=photo(root,record)?:continue
                val bitmap=decode(file,1600)?:error("Não foi possível ler a foto do lote ${record.optString("os")}.")
                try {
                    val page=doc.startPage(PdfDocument.PageInfo.Builder(595,842,++pageNumber).create())
                    page.canvas.drawText("Lote: ${record.optString("os")}".take(90),30f,35f,paint)
                    val scale=minOf(535f/bitmap.width,750f/bitmap.height)
                    page.canvas.drawBitmap(bitmap,null,RectF(30f,55f,30f+bitmap.width*scale,55f+bitmap.height*scale),Paint(3));doc.finishPage(page)
                } finally {bitmap.recycle()}
            }
            cache.mkdirs();val file=File(cache,"estoques_${UUID.randomUUID()}.pdf")
            try {file.outputStream().use { doc.writeTo(it) };return file} catch(error:Exception) {file.delete();throw error}
        } finally { doc.close() }
    }
}
