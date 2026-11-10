package com.colheitagps.app

import android.app.Activity
import android.app.AlertDialog
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Display names come from the team's map catalog; storage IDs stay internal. */
internal object PaintedMapPicker {
    data class Item(val file:File,val title:String,val updated:String) {
        val label get()="$title\nAtualizado: $updated"
    }
    fun items(store:TeamStore):List<Item> {
        val files=store.files.listFiles().orEmpty().filter {
            it.isFile&&it.name.startsWith("mapa_relatorio_")&&it.extension.equals("png",true)&&
                it.canonicalPath.startsWith(store.files.canonicalPath+File.separator)
        }.sortedWith(compareByDescending<File> {it.lastModified()}.thenBy {it.name})
        val catalog=store.prefs("mapas")
        val names=files.map { file->
            val id=file.nameWithoutExtension.removePrefix("mapa_relatorio_")
            catalog.getString(id,null).orEmpty().trim().replace(Regex("(?i)\\.(pdf|png)$"),"").takeUnless {
                it.isBlank()||it.matches(Regex("(?i)(mapa_relatorio_)?[0-9a-f]{64}"))
            }.orEmpty()
        }
        val counts=names.groupingBy {it.lowercase(Locale.ROOT)}.eachCount()
        val date=SimpleDateFormat("dd/MM/yyyy HH:mm",Locale("pt","BR"))
        return files.mapIndexed { index,file->
            val name=names[index]
            val title=if(name.isBlank()) "Mapa pintado ${index+1}" else name+if((counts[name.lowercase(Locale.ROOT)]?:0)>1) " (${index+1})" else ""
            Item(file,title,date.format(Date(file.lastModified())))
        }
    }
    fun show(activity:Activity,items:List<Item>,selected:(File)->Unit) {
        if(items.isEmpty()) return
        AlertDialog.Builder(activity).setTitle("Escolha o mapa pintado")
            .setItems(items.map {it.label}.toTypedArray()) { _,index->preview(activity,items,items[index],selected) }
            .setNegativeButton("Cancelar",null).show()
    }
    private fun preview(activity:Activity,items:List<Item>,item:Item,selected:(File)->Unit) {
        val ui=AccessUi(activity,attach=false)
        val body=LinearLayout(activity).apply {orientation=LinearLayout.VERTICAL;setPadding(ui.dp(18),ui.dp(8),ui.dp(18),ui.dp(12))}
        ui.add(body,ui.text("Confira a imagem antes de usar neste lote.\nAtualizado: ${item.updated}",13f),0)
        val bitmap=StockShareFiles.decode(item.file,1000)
        if(bitmap!=null) ui.add(body,ImageView(activity).apply {setImageBitmap(bitmap);adjustViewBounds=true;maxHeight=ui.dp(360);contentDescription="Prévia do mapa ${item.title}"},12)
        else ui.add(body,ui.text("Não foi possível abrir esta imagem. Escolha outro mapa.",15f),12)
        val dialog=AlertDialog.Builder(activity).setTitle(item.title)
            .setView(ScrollView(activity).apply {addView(body)})
            .setNegativeButton("Escolher outro") { _,_->show(activity,items,selected) }
            .setPositiveButton("Usar este mapa") { _,_->selected(item.file) }.create()
        dialog.setOnShowListener {dialog.getButton(AlertDialog.BUTTON_POSITIVE).isEnabled=bitmap!=null}
        dialog.show()
    }
}
