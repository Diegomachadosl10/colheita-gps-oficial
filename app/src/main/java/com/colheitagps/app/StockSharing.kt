package com.colheitagps.app

import android.app.Activity
import android.content.Intent
import android.widget.Toast
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.Executors

/** Stock shipments retain editable text and the original photos/painted maps as attachments. */
internal object StockSharing {
    private val worker=Executors.newSingleThreadExecutor {work->Thread(work,"dm-agro-stock-share").apply {isDaemon=true}}
    fun deliver(activity:Activity,intent:Intent,allowed:()->Boolean):Boolean = try {
        if(activity.isDestroyed||activity.isFinishing||!allowed()) false
        else {activity.startActivity(Intent.createChooser(intent,"Enviar estoque: mensagem e fotos"));true}
    } catch(_:Exception) {failure(activity);false}
    private fun failure(activity:Activity) {
        if(!activity.isDestroyed&&!activity.isFinishing) Toast.makeText(activity,"Não foi possível compartilhar o estoque. Tente novamente.",Toast.LENGTH_LONG).show()
    }
    fun share(activity:Activity,store:TeamStore,records:List<JSONObject>,text:String?=null,allowed:()->Boolean) {
        if(activity.isDestroyed||activity.isFinishing||!allowed()) return
        val selected=records.map {JSONObject(it.toString())}
        if(selected.isEmpty()||selected.any {!StockBatch.complete(it)||RecordArchive.archived(it)}) {failure(activity);return}
        val date=SimpleDateFormat("dd/MM/yyyy HH:mm",Locale("pt","BR")).format(Date())
        val message=text?:StockBatch.text(selected,TeamPolicy.label(store.team),date)
        Toast.makeText(activity,"Preparando mensagem e fotos…",Toast.LENGTH_SHORT).show()
        worker.execute {
            try {
                val intent=StockShareFiles.message(activity.applicationContext,store.cache,store.files,selected,message)
                activity.runOnUiThread {deliver(activity,intent,allowed)}
            } catch(_:Exception) {activity.runOnUiThread {failure(activity)}}
        }
    }
}
