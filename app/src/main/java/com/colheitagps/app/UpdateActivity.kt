package com.colheitagps.app

import android.app.Activity
import android.app.AlertDialog
import android.content.ClipData
import android.content.Intent
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import androidx.core.content.FileProvider
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileNotFoundException
import java.net.URL
import java.security.MessageDigest
import java.util.UUID
import java.util.concurrent.Executors
import javax.net.ssl.HttpsURLConnection

internal object UpdateWorker { val executor = Executors.newSingleThreadExecutor() }
internal data class UpdateState(val stage: String, val message: String, val info: AppUpdateInfo? = null, val file: File? = null)

internal class UpdateController(private val app: android.content.Context) {
    @Volatile var canceled = false
    var listener: ((UpdateState) -> Unit)? = null
    var state = UpdateState("initial", "")
        private set
    private val handler = Handler(Looper.getMainLooper())
    private fun publish(value: UpdateState) {
        handler.post { if (!canceled) { state = value; listener?.invoke(value) } }
    }
    private fun connection(initial: String): HttpsURLConnection {
        var target = initial
        repeat(6) {
            check(!canceled)
            require(AppUpdatePolicy.trustedRedirect(target)) { "Endereço de atualização não autorizado." }
            val conn = URL(target).openConnection() as HttpsURLConnection
            conn.instanceFollowRedirects = false
            conn.connectTimeout = 15000; conn.readTimeout = 30000
            conn.useCaches = false
            conn.setRequestProperty("Cache-Control", "no-cache")
            conn.setRequestProperty("User-Agent", "DM-AGRO-Android")
            val code = conn.responseCode
            if (code in setOf(301,302,303,307,308)) {
                val next = conn.getHeaderField("Location") ?: error("Redirecionamento sem endereço.")
                target = URL(URL(target), next).toString()
                conn.disconnect()
            } else {
                if (code == 404) { conn.disconnect(); throw FileNotFoundException() }
                if (code != 200) { conn.disconnect(); error("Serviço de atualização indisponível.") }
                return conn
            }
        }
        error("Não foi possível localizar o download.")
    }
    fun check() {
        if (state.stage in setOf("checking", "downloading")) return
        publish(UpdateState("checking", "Consultando novas versões…"))
        UpdateWorker.executor.execute {
            try {
                val conn = connection(AppUpdatePolicy.FEED_URL)
                val text = try {
                    val bytes = ByteArrayOutputStream()
                    conn.inputStream.use { input ->
                        val buffer = ByteArray(4096)
                        while (true) {
                            check(!canceled)
                            val n = input.read(buffer); if (n < 0) break
                            require(bytes.size() + n <= 65536)
                            bytes.write(buffer, 0, n)
                        }
                    }
                    bytes.toString("UTF-8")
                } finally { conn.disconnect() }
                val update = AppUpdatePolicy.parse(text)
                when {
                    update.versionCode <= installedVersion(app) -> publish(UpdateState("current", "Você está usando a versão mais recente publicada."))
                    update.minSdk > Build.VERSION.SDK_INT -> publish(UpdateState("error", "A nova versão exige uma versão mais recente do Android."))
                    else -> publish(UpdateState("available", "Nova versão ${update.versionName} disponível.", update))
                }
            } catch (_: FileNotFoundException) {
                publish(UpdateState("unpublished", "Ainda não há atualização publicada neste canal. O administrador precisa publicar a nova versão."))
            } catch (_: Exception) { publish(UpdateState("error", "Não foi possível verificar a atualização. Confira a internet e tente novamente.")) }
        }
    }
    fun download(info: AppUpdateInfo) {
        if (state.stage == "downloading") return
        publish(UpdateState("downloading", "Preparando o download…", info))
        UpdateWorker.executor.execute {
            val dir = File(app.cacheDir, "atualizacoes").apply { mkdirs() }
            val part = File(dir, "${UUID.randomUUID()}.download.apk")
            try {
                val conn = connection(info.apkUrl)
                try {
                    if (conn.contentLengthLong >= 0) require(conn.contentLengthLong == info.sizeBytes)
                    val digest = MessageDigest.getInstance("SHA-256")
                    var total = 0L; var lastPercent = -1
                    conn.inputStream.use { input -> part.outputStream().use { output ->
                        val buffer = ByteArray(32768)
                        while (true) {
                            check(!canceled)
                            val n = input.read(buffer); if (n < 0) break
                            total += n
                            require(total <= info.sizeBytes && total <= AppUpdatePolicy.MAX_APK_BYTES)
                            digest.update(buffer, 0, n); output.write(buffer, 0, n)
                            val percent = (total * 100 / info.sizeBytes).toInt()
                            if (percent != lastPercent) { lastPercent = percent; publish(UpdateState("downloading", "Baixando atualização: $percent%", info)) }
                        }
                    } }
                    require(total == info.sizeBytes)
                    require(digest.digest().joinToString("") { "%02x".format(it) } == info.sha256) { "Download incompleto ou alterado." }
                } finally { conn.disconnect() }
                verifyApk(app, part, info)
                check(!canceled)
                val ready = File(dir, "DM-AGRO-atualizacao.apk")
                part.copyTo(ready, overwrite = true)
                publish(UpdateState("install", "Download verificado. Toque em Instalar atualização.", info, ready))
            } catch (_: Exception) { publish(UpdateState("error", "Não foi possível validar a atualização. O arquivo não será instalado. Confira a internet e tente novamente.", info)) }
            finally { part.delete() }
        }
    }
    companion object {
        @Suppress("DEPRECATION")
        fun installedVersion(context: android.content.Context): Long = version(context.packageManager.getPackageInfo(context.packageName, 0))
        @Suppress("DEPRECATION")
        fun version(info: PackageInfo): Long = if (Build.VERSION.SDK_INT >= 28) info.longVersionCode else info.versionCode.toLong()
        @Suppress("DEPRECATION")
        private fun signers(info: PackageInfo): Set<String> {
            val signatures = if (Build.VERSION.SDK_INT >= 28) info.signingInfo?.apkContentsSigners else info.signatures
            return signatures.orEmpty().map { signature -> MessageDigest.getInstance("SHA-256").digest(signature.toByteArray()).joinToString("") { "%02x".format(it) } }.toSet()
        }
        @Suppress("DEPRECATION")
        fun verifyApk(context: android.content.Context, file: File, info: AppUpdateInfo) {
            val flags = if (Build.VERSION.SDK_INT >= 28) PackageManager.GET_SIGNING_CERTIFICATES else PackageManager.GET_SIGNATURES
            val current = context.packageManager.getPackageInfo(context.packageName, flags)
            val candidate = context.packageManager.getPackageArchiveInfo(file.absolutePath, flags) ?: error("APK inválido.")
            AppUpdatePolicy.verifyCandidate(info, candidate.packageName, version(candidate), version(current), Build.VERSION.SDK_INT, signers(current), signers(candidate))
            if (Build.VERSION.SDK_INT >= 24) require(candidate.applicationInfo?.minSdkVersion?.let { it <= Build.VERSION.SDK_INT } == true)
        }
    }
}

class UpdateActivity : Activity() {
    private lateinit var ui: AccessUi
    private lateinit var controller: UpdateController
    private var waitingPermission = false
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        ui = AccessUi(this)
        controller = lastNonConfigurationInstance as? UpdateController ?: UpdateController(applicationContext)
        controller.listener = { render(it) }
        if (controller.state.stage == "initial") controller.check() else render(controller.state)
    }
    @Deprecated("Deprecated in Java")
    override fun onRetainNonConfigurationInstance(): Any = controller
    override fun onDestroy() {
        controller.listener = null
        if (isFinishing) controller.canceled = true
        super.onDestroy()
    }
    override fun onResume() {
        super.onResume()
        if (waitingPermission) {
            waitingPermission = false
            if (packageManager.canRequestPackageInstalls()) install()
            else render(controller.state.copy(message = "A permissão não foi ativada. Você pode tentar novamente ou voltar ao aplicativo."))
        }
    }
    @Suppress("DEPRECATION")
    private fun render(state: UpdateState) {
        if (isDestroyed || isFinishing) return
        val installed = packageManager.getPackageInfo(packageName, 0).versionName.orEmpty()
        val card = ui.page("Atualizar DM AGRO", "Versão instalada: $installed\n${state.message}")
        state.info?.let { info ->
            if (info.changes.isNotBlank()) ui.add(card, ui.text("Melhorias\n${info.changes}"))
            ui.add(card, ui.text("A atualização mantém seus dados. O Android pedirá sua confirmação para instalar."))
        }
        when (state.stage) {
            "available" -> ui.button(card, "Baixar atualização") { controller.download(state.info!!) }
            "install" -> ui.button(card, "Instalar atualização") { install() }
            "checking", "downloading" -> Unit
            else -> ui.button(card, "Verificar novamente") { controller.check() }
        }
        ui.button(card, "Voltar", true) { finish() }
    }
    private fun install() {
        val state = controller.state
        val file = state.file ?: return
        val info = state.info ?: return
        if (!packageManager.canRequestPackageInstalls()) {
            AlertDialog.Builder(this).setTitle("Permitir atualização do DM AGRO")
                .setMessage("Na próxima tela do Android, permita instalar atualizações por este aplicativo. Depois volte para continuar.")
                .setNegativeButton("Agora não", null).setPositiveButton("Abrir configuração") { _, _ ->
                    waitingPermission = true
                    try { startActivity(Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:$packageName"))) }
                    catch (_: Exception) { waitingPermission = false; render(state.copy(message = "Este aparelho não permite abrir a configuração de instalação. Procure o administrador.")) }
                }.show()
            return
        }
        try {
            UpdateController.verifyApk(applicationContext, file, info)
            val uri = FileProvider.getUriForFile(applicationContext, "$packageName.files", file)
            startActivity(Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, "application/vnd.android.package-archive")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                clipData = ClipData.newUri(contentResolver, "Atualização do DM AGRO", uri)
            })
        } catch (_: Exception) { render(state.copy(message = "Não foi possível abrir a instalação. Toque em Verificar novamente para tentar um novo download.", stage = "error")) }
    }
}
