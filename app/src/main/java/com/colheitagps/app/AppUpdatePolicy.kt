package com.colheitagps.app

import org.json.JSONObject
import java.net.URI
import java.util.Locale

internal data class AppUpdateInfo(val versionCode: Long, val versionName: String, val minSdk: Int,
    val apkUrl: String, val sha256: String, val sizeBytes: Long, val changes: String)

internal object AppUpdatePolicy {
    const val REPOSITORY = "Diegomachadosl10/colheita-gps-oficial"
    const val PACKAGE = "com.colheitagps.app"
    const val FEED_URL = "https://github.com/$REPOSITORY/releases/latest/download/dm-agro-update.json"
    const val MAX_APK_BYTES = 250L * 1024 * 1024
    private val cdnHosts = setOf("release-assets.githubusercontent.com", "objects.githubusercontent.com")
    fun trustedRedirect(url: String): Boolean = try {
        val uri = URI(url)
        uri.scheme == "https" && uri.userInfo == null && uri.fragment == null && uri.port in setOf(-1, 443) &&
            ((uri.host == "github.com" && uri.rawPath.startsWith("/$REPOSITORY/releases/") && !uri.rawPath.contains('%') &&
                uri.rawPath.split('/').none { it == "." || it == ".." }) || uri.host in cdnHosts)
    } catch (_: Exception) { false }
    fun trustedApk(url: String): Boolean = try {
        val uri = URI(url)
        trustedRedirect(url) && uri.host == "github.com" && uri.rawQuery == null &&
            uri.rawPath.startsWith("/$REPOSITORY/releases/download/") && uri.rawPath.split('/').size == 7 &&
            uri.rawPath.endsWith(".apk")
    } catch (_: Exception) { false }
    fun parse(text: String): AppUpdateInfo {
        val json = JSONObject(text)
        require(json.getInt("schemaVersion") == 1 && json.getString("packageName") == PACKAGE) { "Atualização de outro aplicativo." }
        val code = json.getLong("versionCode")
        val name = json.getString("versionName").trim()
        val sdk = json.getInt("minSdk")
        val url = json.getString("apkUrl")
        val digest = json.getString("sha256").lowercase(Locale.ROOT)
        val size = json.getLong("sizeBytes")
        require(code > 0 && name.isNotBlank() && name.length <= 64 && sdk >= 26)
        require(trustedApk(url)) { "Endereço de atualização não autorizado." }
        require(digest.matches(Regex("[0-9a-f]{64}")) && size in 1..MAX_APK_BYTES) { "Informações de download inválidas." }
        return AppUpdateInfo(code, name, sdk, url, digest, size, json.optString("changes").take(4000))
    }
    fun verifyCandidate(expected: AppUpdateInfo, packageName: String, version: Long, currentVersion: Long,
        sdk: Int, installedSigners: Set<String>, candidateSigners: Set<String>) {
        require(packageName == PACKAGE && version == expected.versionCode && version > currentVersion) { "O arquivo não corresponde à atualização esperada." }
        require(expected.minSdk <= sdk) { "A atualização exige uma versão mais recente do Android." }
        require(installedSigners.isNotEmpty() && candidateSigners == installedSigners) { "A assinatura não corresponde ao DM AGRO instalado." }
    }
}
