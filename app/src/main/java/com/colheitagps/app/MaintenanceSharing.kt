package com.colheitagps.app

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.core.content.FileProvider
import org.json.JSONObject
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID
import java.util.concurrent.Executors

internal object MaintenanceSharing {
    private val worker=Executors.newSingleThreadExecutor {job->Thread(job,"dm-agro-maintenance-share").apply {isDaemon=true}}
    fun prepare(context:Context,store:TeamStore,rows:List<JSONObject>,image:Boolean,photos:Boolean,date:String):Intent {
        val records=MaintenanceShareData.ordered(rows);require(records.isNotEmpty())
        val message=MaintenanceShareData.message(records,TeamPolicy.label(store.team),date)
        // Text includes every selected issue, even an issue without a local photo.
        val illustrated=if(image) records else if(photos) records.filter {MaintenanceShareData.photos(store.files,it).isNotEmpty()} else emptyList()
        if(illustrated.isEmpty()) return Intent(Intent.ACTION_SEND).apply {type="text/plain";putExtra(Intent.EXTRA_TEXT,message)}
        val folder=File(store.cache,"pendencias_compartilhadas/${UUID.randomUUID()}")
        try {
            val files=MaintenanceShareRenderer.render(folder,store.files,illustrated,TeamPolicy.label(store.team),date,photos)
            val app=context.applicationContext
            val uris=ArrayList(files.map {FileProvider.getUriForFile(app,"${app.packageName}.files",it)})
            return Intent(if(uris.size==1) Intent.ACTION_SEND else Intent.ACTION_SEND_MULTIPLE).apply {
                type="image/png";putExtra(Intent.EXTRA_TEXT,if(image) "Pendências de manutenção • ${TeamPolicy.label(store.team)}\n${records.size} pendência${if(records.size==1) "" else "s"}" else message)
                if(uris.size==1) putExtra(Intent.EXTRA_STREAM,uris.first()) else putParcelableArrayListExtra(Intent.EXTRA_STREAM,uris)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                clipData=android.content.ClipData.newUri(app.contentResolver,"Pendências DM AGRO",uris.first()).apply {uris.drop(1).forEach {addItem(android.content.ClipData.Item(it))}}
            }
        } catch(error:Exception) {folder.deleteRecursively();throw error}
    }
    fun deliver(activity:Activity,intent:Intent,allowed:()->Boolean):Boolean = try {
        if(activity.isFinishing||activity.isDestroyed||!allowed()) false else {
            activity.startActivity(Intent.createChooser(intent,"Compartilhar pendências • escolha o WhatsApp"));true
        }
    } catch(_:Exception) {failure(activity,"Não foi possível compartilhar. Tente novamente.");false}
    private fun failure(activity:Activity,message:String) {
        if(!activity.isFinishing&&!activity.isDestroyed) Toast.makeText(activity,message,Toast.LENGTH_LONG).show()
    }
    fun share(activity:Activity,store:TeamStore,rows:List<JSONObject>,image:Boolean,photos:Boolean,allowed:()->Boolean) {
        if(activity.isFinishing||activity.isDestroyed||!allowed()) return
        val selected=rows.map {JSONObject(it.toString())}
        if(selected.isEmpty()) {failure(activity,"Nenhuma pendência disponível para compartilhar.");return}
        val date=SimpleDateFormat("dd/MM/yyyy HH:mm",Locale("pt","BR")).format(Date())
        Toast.makeText(activity,"Preparando pendências…",Toast.LENGTH_SHORT).show()
        worker.execute {
            try {
                val intent=prepare(activity.applicationContext,store,selected,image,photos,date)
                activity.runOnUiThread {SharePreview.review(activity,intent,allowed) {deliver(activity,intent,allowed)}}
            } catch(_:OutOfMemoryError) {activity.runOnUiThread {failure(activity,"Não foi possível preparar as fotos. Tente enviar sem fotos.")}}
            catch(error:Exception) {activity.runOnUiThread {failure(activity,if(error is IllegalArgumentException) error.message?:"Não foi possível preparar as pendências." else "Não foi possível preparar as pendências. Tente novamente.")}}
        }
    }
}
