package com.colheitagps.app

import android.content.Context
import android.os.SystemClock
import android.provider.Settings
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.AtomicFile
import org.json.JSONObject
import java.io.File
import java.security.KeyStore
import java.security.MessageDigest
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/** A bounded lease from a successful server entry, never a password or Firebase token. */
internal object OfflineEntryPolicy {
    const val WINDOW_MS = 24 * 60 * 60 * 1000L
    data class Clock(val wall: Long, val elapsed: Long, val boot: Int)
    fun networkFailure(error: Throwable) = AccessFailures.code(error) in setOf("DNS", "TIMEOUT", "UNAVAILABLE", "HTTP_502", "HTTP_503")
    fun valid(lease: JSONObject, uid: String, now: Clock): Boolean {
        if (lease.optInt("schema") != 1 || lease.optString("environment") != BetaWorkspace.ID || lease.optString("uid") != uid) return false
        val issued = lease.optLong("issuedWall", -1); val seen = lease.optLong("seenWall", -1)
        if (issued < 0 || seen < issued || now.wall < seen - 120_000 || maxOf(now.wall, seen) - issued !in 0 until WINDOW_MS) return false
        if (lease.optInt("boot", -2) == now.boot &&
            (now.elapsed < lease.optLong("seenElapsed", Long.MAX_VALUE) || now.elapsed - lease.optLong("issuedElapsed", -1) !in 0 until WINDOW_MS)) return false
        val profile = lease.optJSONObject("profile") ?: return false
        return TeamPolicy.allowed(uid, profile.keys().asSequence().associateWith { profile.get(it) })
    }
}

/** Non-backed-up, authenticated encryption bound to this installation, environment and UID. */
internal class OfflineEntryStore(context: Context,
    private val key: () -> SecretKey = { deviceKey() },
    private val clock: () -> OfflineEntryPolicy.Clock = {
        OfflineEntryPolicy.Clock(System.currentTimeMillis(), SystemClock.elapsedRealtime(),
            Settings.Global.getInt(context.contentResolver, Settings.Global.BOOT_COUNT, -1))
    }
) {
    private val root=File(context.applicationContext.noBackupFilesDir,"beta_access_lease").apply {mkdirs()}
    private fun file(uid: String): AtomicFile {
        require(uid.isNotBlank())
        val name=MessageDigest.getInstance("SHA-256").digest(uid.toByteArray()).joinToString("") {"%02x".format(it)}
        return AtomicFile(File(root,"$name.enc"))
    }
    private fun aad(uid: String)="dm-agro|${BetaWorkspace.ID}|offline-entry-v1|$uid".toByteArray()
    @Synchronized fun remove(uid: String) { if(uid.isNotBlank()) file(uid).delete() }
    @Synchronized fun clear() {root.listFiles().orEmpty().filter {it.isFile}.forEach {it.delete()}}
    @Synchronized fun remember(uid: String, data: Map<String, Any>?) {
        if(!TeamPolicy.allowed(uid,data)) {remove(uid);return}
        val now=clock()
        val fields=setOf("status","papel","perfil","nome","frenteId","turno")
        val profile=JSONObject(data.orEmpty().filterKeys {it in fields})
        val lease=JSONObject().put("schema",1).put("environment",BetaWorkspace.ID).put("uid",uid).put("profile",profile)
            .put("issuedWall",now.wall).put("seenWall",now.wall).put("issuedElapsed",now.elapsed).put("seenElapsed",now.elapsed).put("boot",now.boot)
        write(uid,lease)
    }
    @Synchronized fun read(uid: String): TeamAccess? = try {
        val bytes=file(uid).readFully()
        require(bytes.size in 30..32768 && bytes[0].toInt()==1)
        val cipher=Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE,key(),GCMParameterSpec(128,bytes.copyOfRange(1,13)));cipher.updateAAD(aad(uid))
        val lease=JSONObject(String(cipher.doFinal(bytes.copyOfRange(13,bytes.size)),Charsets.UTF_8))
        val now=clock()
        if(!OfflineEntryPolicy.valid(lease,uid,now)) {remove(uid);null}
        else {
            lease.put("seenWall",maxOf(lease.getLong("seenWall"),now.wall))
            // After reboot the elapsed clock has a new origin; the original wall deadline remains.
            if(lease.optInt("boot")!=now.boot) {lease.put("issuedElapsed",now.elapsed-(maxOf(now.wall,lease.getLong("seenWall"))-lease.getLong("issuedWall"))).put("boot",now.boot)}
            lease.put("seenElapsed",now.elapsed);write(uid,lease)
            val profile=lease.getJSONObject("profile")
            TeamAccess(uid,profile.keys().asSequence().associateWith {profile.get(it)})
        }
    } catch(_:Exception) {remove(uid);null}

    @Synchronized fun observe(uid: String, data: Map<String, Any>?) {
        val previous=read(uid)?:return
        if(!TeamPolicy.allowed(uid,data)||TeamPolicy.team(data)!=previous.team||AccessPolicy.role(data)!=AccessPolicy.role(previous.profile)) remove(uid)
        // Snapshot observations never extend a lease. Only full server entry can renew it.
    }
    private fun write(uid: String, lease: JSONObject) {
        val cipher=Cipher.getInstance("AES/GCM/NoPadding");cipher.init(Cipher.ENCRYPT_MODE,key());cipher.updateAAD(aad(uid))
        require(cipher.iv.size==12)
        val bytes=byteArrayOf(1)+cipher.iv+cipher.doFinal(lease.toString().toByteArray(Charsets.UTF_8))
        val target=file(uid);val output=target.startWrite()
        try {output.write(bytes);target.finishWrite(output)} catch(error:Exception) {target.failWrite(output);throw error}
    }
    companion object {
        private const val ALIAS="dm_agro_beta_access_v1"
        @Synchronized private fun deviceKey():SecretKey {
            val store=KeyStore.getInstance("AndroidKeyStore").apply {load(null)}
            (store.getKey(ALIAS,null) as? SecretKey)?.let {return it}
            return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES,"AndroidKeyStore").apply {
                init(KeyGenParameterSpec.Builder(ALIAS,KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                    .setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                    .setRandomizedEncryptionRequired(true).build())
            }.generateKey()
        }
    }
}
