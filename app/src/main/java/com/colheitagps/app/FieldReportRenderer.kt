package com.colheitagps.app

import android.graphics.*
import android.graphics.pdf.PdfDocument
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** A4 vector PDF and paginated 2x PNG use the same layout; long rows continue without losing text. */
internal object FieldReportRenderer {
    private const val WIDTH=595
    private const val HEIGHT=842
    private const val BOTTOM=790f
    fun render(report:FieldReport,folder:File,pdf:Boolean):List<File> {
        folder.mkdirs();val files=mutableListOf<File>();val document=if(pdf) PdfDocument() else null
        var page:PdfDocument.Page?=null;var bitmap:Bitmap?=null;var canvas:Canvas?=null;var number=0;var y=126f
        val paint=Paint(Paint.ANTI_ALIAS_FLAG).apply {typeface=Typeface.create(Typeface.SANS_SERIF,Typeface.NORMAL)}
        fun text(value:String,x:Float,top:Float,size:Float=11f,color:Int=Color.BLACK,bold:Boolean=false) {
            paint.textSize=size;paint.color=color;paint.style=Paint.Style.FILL;paint.typeface=Typeface.create(Typeface.SANS_SERIF,if(bold) Typeface.BOLD else Typeface.NORMAL)
            canvas!!.drawText(value,x,top,paint)
        }
        fun wrap(raw:String,width:Float,size:Float=10.5f):List<String> {
            paint.textSize=size;paint.typeface=Typeface.create(Typeface.SANS_SERIF,Typeface.NORMAL)
            return raw.lines().flatMap {line->
                val parts=mutableListOf<String>();var rest=line
                while(rest.isNotEmpty()) {
                    var count=paint.breakText(rest,true,width,null).coerceAtLeast(1)
                    if(count<rest.length) {val space=rest.lastIndexOf(' ',count-1);if(space>count/2) count=space+1}
                    parts.add(rest.take(count).trimEnd());rest=rest.drop(count)
                }
                parts.ifEmpty {listOf("")}
            }
        }
        fun finish() {
            if(canvas==null) return
            if(pdf) document!!.finishPage(page!!) else {
                val file=File(folder,"DM-AGRO-pagina-${number.toString().padStart(2,'0')}.png")
                file.outputStream().use {check(bitmap!!.compress(Bitmap.CompressFormat.PNG,100,it))};files.add(file);bitmap!!.recycle();bitmap=null
            }
            canvas=null
        }
        fun newPage() {
            finish();number++
            if(pdf) {page=document!!.startPage(PdfDocument.PageInfo.Builder(WIDTH,HEIGHT,number).create());canvas=page!!.canvas}
            else {bitmap=Bitmap.createBitmap(WIDTH*2,HEIGHT*2,Bitmap.Config.ARGB_8888);canvas=Canvas(bitmap!!).apply {scale(2f,2f)}}
            canvas!!.drawColor(Color.WHITE)
            paint.color=0xFF123E2A.toInt();canvas!!.drawRect(0f,0f,WIDTH.toFloat(),94f,paint)
            text("DM AGRO • COLHEITA GPS",28f,27f,12f,0xFFCBEDBF.toInt(),true)
            wrap(report.title,535f,19f).take(2).forEachIndexed {i,line->text(line,28f,53f+i*23,19f,Color.WHITE,true)}
            text(report.team,28f,111f,11f,0xFF245039.toInt(),true);y=130f
            if(report.period.isNotBlank()) for(line in wrap(report.period,535f,10f)) {text(line,28f,y,10f,0xFF52665B.toInt());y+=13f}
            val date=SimpleDateFormat("dd/MM/yyyy HH:mm",Locale("pt","BR")).format(Date())
            text("DM AGRO • $date • Página $number",28f,822f,9f,0xFF52665B.toInt())
        }
        fun space(height:Float) {if(canvas==null||y+height>BOTTOM) newPage()}
        fun heading(section:ReportSection) {
            space(60f);text(section.title,28f,y+15f,13f,0xFF174D32.toInt(),true);y+=26f
            val cell=539f/section.columns.size
            val labels=section.columns.map {wrap(it,cell-12f,10f)}
            val height=(labels.maxOfOrNull {it.size}?:1)*13f+10f
            paint.color=0xFFE4F2E6.toInt();canvas!!.drawRect(28f,y,567f,y+height,paint)
            labels.forEachIndexed {index,lines->lines.forEachIndexed {n,line->text(line,34f+index*cell,y+15f+n*13f,10f,0xFF174D32.toInt(),true)}}
            y+=height
        }
        try {
            newPage()
            for(section in report.sections) {
                if(section.columns.isEmpty()) continue
                heading(section)
                if(section.rows.isEmpty()) {space(25f);text("Sem registros neste período.",34f,y+17f,11f,0xFF52665B.toInt());y+=28f}
                val cell=539f/section.columns.size
                for((index,row) in section.rows.withIndex()) {
                    val lines=section.columns.indices.map {col->wrap(row.getOrElse(col){""},cell-12f)}
                    val total=lines.maxOfOrNull {it.size}?:1;var offset=0
                    while(offset<total) {
                        if(y+24f>BOTTOM) {newPage();heading(section)}
                        val count=minOf(total-offset,((BOTTOM-y-10f)/14f).toInt().coerceAtLeast(1));val height=count*14f+10f
                        paint.style=Paint.Style.FILL;paint.color=if(index%2==0) 0xFFF4F7F4.toInt() else Color.WHITE
                        canvas!!.drawRect(28f,y,567f,y+height,paint)
                        for(col in section.columns.indices) {
                            val content=lines[col].drop(offset).take(count).ifEmpty {if(col==0&&offset>0) listOf("(continuação)") else emptyList()}
                            for((n,line) in content.withIndex()) text(line,34f+col*cell,y+16f+n*14f,10.5f)
                            paint.style=Paint.Style.STROKE;paint.strokeWidth=0.5f;paint.color=0xFFD9E3DA.toInt();canvas!!.drawRect(28f+col*cell,y,28f+(col+1)*cell,y+height,paint);paint.style=Paint.Style.FILL
                        }
                        y+=height;offset+=count
                    }
                };y+=14f
            }
            for(picture in report.pictures) {
                newPage()
                for(line in wrap(picture.caption,535f,13f).take(8)) {text(line,28f,y+15f,13f,0xFF174D32.toInt(),true);y+=18f};y+=10f
                val image=StockShareFiles.decode(picture.file,1600)
                if(image==null) text("Foto não disponível neste aparelho.",28f,y+20f) else try {
                    val scale=minOf(539f/image.width,(BOTTOM-y)/image.height)
                    canvas!!.drawBitmap(image,null,RectF(28f,y,28f+image.width*scale,y+image.height*scale),Paint(3))
                } finally {image.recycle()}
            }
            finish()
            if(pdf) {val file=File(folder,"DM-AGRO-relatorio.pdf");file.outputStream().use {document!!.writeTo(it)};files.add(file)}
            return files
        } catch(error:Exception) {files.forEach {it.delete()};throw error}
        finally {bitmap?.recycle();document?.close()}
    }
}
