package com.colheitagps.app

import android.app.Activity
import android.content.ClipData
import android.net.Uri
import android.content.Intent
import android.widget.Toast
import androidx.core.content.FileProvider
import java.io.File
import java.util.UUID
import java.util.concurrent.Executors

internal object FieldReportSharing {
    // Limit bitmap work to one job, including painted maps, when sharing is tapped repeatedly.
    private val worker=Executors.newSingleThreadExecutor {work->Thread(work,"dm-agro-images").apply {isDaemon=true}}
    fun imageIntent(activity:Activity,report:FieldReport,uris:ArrayList<Uri>):Intent {
        require(uris.isNotEmpty())
        return Intent(if(uris.size>1) Intent.ACTION_SEND_MULTIPLE else Intent.ACTION_SEND).apply {
            type="image/png";putExtra(Intent.EXTRA_TEXT,"${report.title} • ${report.team}\n${report.period}")
            if(uris.size==1) putExtra(Intent.EXTRA_STREAM,uris.first()) else putParcelableArrayListExtra(Intent.EXTRA_STREAM,uris)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            clipData=ClipData.newUri(activity.contentResolver,report.title,uris.first()).apply {uris.drop(1).forEach {addItem(ClipData.Item(it))}}
        }
    }
    /** All provider and chooser operations run inside the UI callback's own exception boundary. */
    fun deliverImages(activity:Activity,report:FieldReport,files:List<File>,allowed:()->Boolean={true}):Boolean {
        return try {
            if(activity.isDestroyed||activity.isFinishing||!allowed()) return false
            require(files.isNotEmpty())
            // MainActivity overrides filesDir/cacheDir to point at a team. Provider XML roots
            // belong to the application, so initializing the provider with that Activity nests
            // equipes/ twice and fails. Keep the existing, restricted production XML paths.
            val app=activity.applicationContext
            val uris=ArrayList(files.map {file->
                require(file.isFile&&file.length()>0)
                FileProvider.getUriForFile(app,"${app.packageName}.files",file)
            })
            activity.startActivity(Intent.createChooser(imageIntent(activity,report,uris),"Compartilhar imagem DM AGRO"))
            true
        } catch(_:Exception) {
            failure(activity,"Não foi possível compartilhar a imagem. Tente novamente.")
            false
        }
    }
    private fun failure(activity:Activity,message:String) {
        if(!activity.isDestroyed&&!activity.isFinishing) Toast.makeText(activity,message,Toast.LENGTH_LONG).show()
    }
    fun prepareImages(activity:Activity,report:FieldReport,allowed:()->Boolean={true},prepare:()->List<File>) {
        if(activity.isDestroyed||activity.isFinishing||!allowed()) return
        Toast.makeText(activity,"Preparando imagem…",Toast.LENGTH_SHORT).show()
        worker.execute {
            try {
                val files=prepare()
                activity.runOnUiThread {
                    if(!activity.isFinishing&&!activity.isDestroyed&&allowed()) {
                        val app=activity.applicationContext
                        try {
                            val uris=ArrayList(files.map {FileProvider.getUriForFile(app,"${app.packageName}.files",it)})
                            SharePreview.review(activity,imageIntent(activity,report,uris),allowed) {deliverImages(activity,report,files,allowed)}
                        } catch(_:Exception) {failure(activity,"Não foi possível preparar a prévia. Tente novamente.")}
                    }
                }
            } catch(_:OutOfMemoryError) {
                activity.runOnUiThread {failure(activity,"Memória insuficiente para preparar as imagens. Tente compartilhar menos itens por vez.")}
            } catch(_:Exception) {
                activity.runOnUiThread {failure(activity,"Não foi possível preparar a imagem. Tente novamente.")}
            }
        }
    }
    fun share(activity:Activity,report:FieldReport,cache:File,allowed:()->Boolean={true}) {
        prepareImages(activity,report,allowed) {
            FieldReportRenderer.render(report,File(cache,"relatorios/${UUID.randomUUID()}"),false)
        }
    }
    fun pdfIntent(activity:Activity,report:FieldReport,file:File):Intent {
        require(file.isFile&&file.length()>0)
        val app=activity.applicationContext
        val uri=FileProvider.getUriForFile(app,"${app.packageName}.files",file)
        return Intent(Intent.ACTION_SEND).apply {
            type="application/pdf"
            putExtra(Intent.EXTRA_STREAM,uri)
            putExtra(Intent.EXTRA_TEXT,"${report.title} • ${report.team}\n${report.period}")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            clipData=ClipData.newUri(app.contentResolver,report.title,uri)
        }
    }
    /** A prepared PDF remains private until the user confirms Enviar. */
    fun reviewPdf(activity:Activity,report:FieldReport,file:File,allowed:()->Boolean={true}) {
        if(activity.isDestroyed||activity.isFinishing||!allowed()) return
        try {
            val intent=pdfIntent(activity,report,file)
            SharePreview.review(activity,intent,allowed) {
                try {
                    if(!activity.isDestroyed&&!activity.isFinishing&&allowed())
                        activity.startActivity(Intent.createChooser(intent,"Enviar PDF DM AGRO"))
                } catch(_:Exception) {failure(activity,"Não foi possível enviar o PDF. Tente novamente.")}
            }
        } catch(_:Exception) {failure(activity,"Não foi possível preparar o PDF. Tente novamente.")}
    }
    fun sharePdf(activity:Activity,report:FieldReport,cache:File,allowed:()->Boolean={true}) {
        if(activity.isDestroyed||activity.isFinishing||!allowed()) return
        Toast.makeText(activity,"Preparando PDF…",Toast.LENGTH_SHORT).show()
        worker.execute {
            try {
                val file=FieldReportRenderer.render(report,File(cache,"relatorios/${UUID.randomUUID()}"),true).single()
                activity.runOnUiThread {reviewPdf(activity,report,file,allowed)}
            } catch(_:OutOfMemoryError) {
                activity.runOnUiThread {failure(activity,"Memória insuficiente. Tente enviar menos itens no PDF.")}
            } catch(_:Exception) {
                activity.runOnUiThread {failure(activity,"Não foi possível gerar o PDF. Tente novamente.")}
            }
        }
    }
}
