package com.colheitagps.app

import android.graphics.*
import org.json.JSONObject
import java.io.File

/** Each ordinary issue is one compact image containing both its full description and its photos. */
internal object MaintenanceShareRenderer {
    private const val WIDTH=1000
    private const val MARGIN=40f
    private const val CONTENT=920f
    private const val STEP=36f
    fun render(folder:File,root:File,rows:List<JSONObject>,team:String,date:String,photos:Boolean):List<File> {
        folder.mkdirs();val files=mutableListOf<File>()
        try {
            for((index,row) in rows.withIndex()) {
                val paint=Paint(Paint.ANTI_ALIAS_FLAG).apply {typeface=Typeface.create(Typeface.SANS_SERIF,Typeface.NORMAL);textSize=26f}
                fun wrap(value:String,size:Float):List<String> {
                    paint.textSize=size
                    return value.lines().flatMap {line->
                        val lines=mutableListOf<String>();var rest=line
                        while(rest.isNotEmpty()) {
                            var count=paint.breakText(rest,true,CONTENT,null).coerceAtLeast(1)
                            if(count<rest.length) {val space=rest.lastIndexOf(' ',count-1);if(space>count/2) count=space+1}
                            lines.add(rest.take(count).trimEnd());rest=rest.drop(count)
                        }
                        lines.ifEmpty {listOf("")}
                    }
                }
                val teamLines=wrap(team,26f)
                paint.typeface=Typeface.create(Typeface.SANS_SERIF,Typeface.BOLD)
                val title=wrap("Equipamento ${row.optString("equipamento").ifBlank {"não informado"}}",34f)
                paint.typeface=Typeface.create(Typeface.SANS_SERIF,Typeface.NORMAL)
                val body=wrap(MaintenanceShareData.details(row),26f)
                val pictures=if(photos) MaintenanceShareData.photos(root,row) else emptyList()
                val columns=if(pictures.size<=1) 1 else 2
                val photoRows=(pictures.size+columns-1)/columns
                val cellHeight=if(columns==1) 820f else 440f
                val photoHeight=if(pictures.isEmpty()) 0f else 60f+photoRows*(cellHeight+16f)
                val textBottom=130f+teamLines.size*STEP+24f+title.size*44f+20f+body.size*STEP+24f
                val height=(textBottom+photoHeight+70f).toInt().coerceAtLeast(460)
                require(height<=10000) {"Esta pendência tem muito conteúdo para uma imagem. Envie como mensagem sem fotos."}
                val bitmap=Bitmap.createBitmap(WIDTH,height,Bitmap.Config.ARGB_8888)
                try {
                    val canvas=Canvas(bitmap);canvas.drawColor(Color.WHITE)
                    paint.color=0xFF103F28.toInt();canvas.drawRect(0f,0f,WIDTH.toFloat(),108f,paint)
                    fun text(value:String,y:Float,size:Float=26f,color:Int=0xFF163C28.toInt(),bold:Boolean=false) {
                        paint.color=color;paint.textSize=size;paint.typeface=Typeface.create(Typeface.SANS_SERIF,if(bold) Typeface.BOLD else Typeface.NORMAL)
                        canvas.drawText(value,MARGIN,y,paint)
                    }
                    text("DM AGRO • PENDÊNCIAS",49f,30f,Color.WHITE,true)
                    text("${index+1} de ${rows.size} • $date",84f,23f,0xFFD1E8D3.toInt())
                    var y=149f
                    for(line in teamLines) {text(line,y,26f,0xFF52665B.toInt());y+=STEP}
                    y+=16f
                    for(line in title) {text(line,y,34f,bold=true);y+=44f}
                    y+=12f
                    for(line in body) {text(line,y);y+=STEP}
                    if(pictures.isNotEmpty()) {
                        y+=24f;text("Fotos da pendência",y,25f,bold=true);y+=20f
                        val cellWidth=(CONTENT-(columns-1)*16f)/columns
                        for((photoIndex,file) in pictures.withIndex()) {
                            val left=MARGIN+(photoIndex%columns)*(cellWidth+16f)
                            val top=y+(photoIndex/columns)*(cellHeight+16f)
                            val image=StockShareFiles.decode(file,1000)
                            if(image==null) {
                                paint.color=0xFFEDF3ED.toInt();canvas.drawRect(left,top,left+cellWidth,top+cellHeight,paint)
                                paint.color=0xFF52665B.toInt();paint.textSize=22f;canvas.drawText("Foto indisponível",left+16,top+40,paint)
                            } else try {
                                val scale=minOf(cellWidth/image.width,cellHeight/image.height)
                                val w=image.width*scale;val h=image.height*scale
                                canvas.drawBitmap(image,null,RectF(left+(cellWidth-w)/2,top+(cellHeight-h)/2,left+(cellWidth+w)/2,top+(cellHeight+h)/2),Paint(3))
                            } finally {image.recycle()}
                        }
                    }
                    text("DM AGRO • Colheita GPS",height-24f,20f,0xFF52665B.toInt())
                    val file=File(folder,"pendencia-${(index+1).toString().padStart(3,'0')}.png")
                    file.outputStream().use {check(bitmap.compress(Bitmap.CompressFormat.PNG,100,it))};files.add(file)
                } finally {bitmap.recycle()}
            }
            return files
        } catch(error:Exception) {files.forEach {it.delete()};throw error}
    }
}
