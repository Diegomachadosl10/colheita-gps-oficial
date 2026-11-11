package com.colheitagps.app

import android.app.Activity
import android.app.AlertDialog
import android.content.ClipData
import android.content.Intent
import android.widget.Toast
import androidx.core.content.FileProvider
import java.io.File
import java.util.UUID

internal object FieldReportSharing {
    fun choose(activity:Activity,report:FieldReport,cache:File,allowed:()->Boolean={true}) {
        AlertDialog.Builder(activity).setTitle("Compartilhar ${report.title}")
            .setItems(arrayOf("PDF organizado","Imagem organizada (PNG)")) {_,index->share(activity,report,cache,index==0,allowed)}
            .setNegativeButton("Cancelar",null).show()
    }
    fun share(activity:Activity,report:FieldReport,cache:File,pdf:Boolean,allowed:()->Boolean={true}) {
        if(!allowed()) return
        Toast.makeText(activity,"Preparando ${if(pdf) "PDF" else "imagem"}…",Toast.LENGTH_SHORT).show()
        Thread {
            try {
                val files=FieldReportRenderer.render(report,File(cache,"relatorios/${UUID.randomUUID()}"),pdf)
                activity.runOnUiThread {
                    if(activity.isDestroyed||activity.isFinishing||!allowed()) return@runOnUiThread
                    val uris=ArrayList(files.map {FileProvider.getUriForFile(activity,"${activity.packageName}.files",it)})
                    val intent=Intent(if(uris.size>1) Intent.ACTION_SEND_MULTIPLE else Intent.ACTION_SEND).apply {
                        type=if(pdf) "application/pdf" else "image/png";putExtra(Intent.EXTRA_TEXT,"${report.title} • ${report.team}\n${report.period}")
                        if(uris.size==1) putExtra(Intent.EXTRA_STREAM,uris.first()) else putParcelableArrayListExtra(Intent.EXTRA_STREAM,uris)
                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                        clipData=ClipData.newUri(activity.contentResolver,report.title,uris.first()).apply {uris.drop(1).forEach {addItem(ClipData.Item(it))}}
                    }
                    try {activity.startActivity(Intent.createChooser(intent,"Compartilhar relatório DM AGRO"))} catch(_:Exception) {Toast.makeText(activity,"Não foi possível abrir o compartilhamento.",Toast.LENGTH_LONG).show()}
                }
            } catch(_:Exception) {activity.runOnUiThread {if(!activity.isDestroyed) Toast.makeText(activity,"Não foi possível preparar o relatório. Tente novamente.",Toast.LENGTH_LONG).show()}}
        }.start()
    }
}
