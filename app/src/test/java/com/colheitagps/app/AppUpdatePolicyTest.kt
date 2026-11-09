package com.colheitagps.app

import android.app.Application
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], manifest = Config.NONE, application = Application::class)
class AppUpdatePolicyTest {
    private val apk = "https://github.com/${AppUpdatePolicy.REPOSITORY}/releases/download/v1.2.0/DM-AGRO-1.2.0.apk"
    private fun manifest() = JSONObject().put("schemaVersion", 1).put("packageName", AppUpdatePolicy.PACKAGE)
        .put("versionCode", 6).put("versionName", "1.2.0").put("minSdk", 26)
        .put("apkUrl", apk).put("sha256", "a".repeat(64)).put("sizeBytes", 12345).put("changes", "Melhorias")
    private fun rejected(block: () -> Unit) { try { block(); fail("Deveria recusar a atualização") } catch (_: IllegalArgumentException) {} }
    @Test fun onlyHttpsAssetsFromTheChosenRepositoryMayBeDownloaded() {
        assertTrue(AppUpdatePolicy.trustedApk(apk))
        assertTrue(AppUpdatePolicy.trustedRedirect("https://release-assets.githubusercontent.com/asset?sig=exemplo"))
        for (url in listOf(apk.replace("https:", "http:"), apk.replace("github.com", "github.com.evil.example"),
            apk.replace(AppUpdatePolicy.REPOSITORY, "outro/projeto"), apk + "?redirect=outro", apk + "#frag",
            apk.replace("github.com", "alguem@github.com"), apk.replace("v1.2.0", "%2e%2e"))) {
            assertFalse(url, AppUpdatePolicy.trustedApk(url))
        }
    }
    @Test fun validManifestRequiresPackageVersionChecksumAndDownloadBoundaries() {
        assertEquals(6L, AppUpdatePolicy.parse(manifest().toString()).versionCode)
        for ((field, value) in listOf("packageName" to "outro.app", "schemaVersion" to 9,
            "versionCode" to 0, "sha256" to "sem-hash", "sizeBytes" to AppUpdatePolicy.MAX_APK_BYTES + 1)) {
            rejected { AppUpdatePolicy.parse(manifest().put(field, value).toString()) }
        }
    }
    @Test fun installerRejectsOtherPackagesDowngradesAndDifferentSignatures() {
        val info = AppUpdatePolicy.parse(manifest().toString())
        AppUpdatePolicy.verifyCandidate(info, AppUpdatePolicy.PACKAGE, 6, 5, 28, setOf("A"), setOf("A"))
        rejected { AppUpdatePolicy.verifyCandidate(info, "outro.app", 6, 5, 28, setOf("A"), setOf("A")) }
        rejected { AppUpdatePolicy.verifyCandidate(info, AppUpdatePolicy.PACKAGE, 6, 6, 28, setOf("A"), setOf("A")) }
        rejected { AppUpdatePolicy.verifyCandidate(info, AppUpdatePolicy.PACKAGE, 6, 5, 28, setOf("A"), setOf("B")) }
        rejected { AppUpdatePolicy.verifyCandidate(info, AppUpdatePolicy.PACKAGE, 6, 5, 28, setOf("A", "B"), setOf("A")) }
        rejected { AppUpdatePolicy.verifyCandidate(info, AppUpdatePolicy.PACKAGE, 6, 5, 25, setOf("A"), setOf("A")) }
    }
}
