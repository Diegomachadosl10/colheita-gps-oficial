package com.colheitagps.app

import org.json.JSONObject
import java.security.MessageDigest
import java.util.concurrent.Executors

/** Resolve the existing Worker's event hash against verified sync metadata, not notification text. */
internal object NotificationRoutes {
    private val worker=Executors.newSingleThreadExecutor {task->Thread(task,"dm-agro-notification-link").apply {isDaemon=true}}
    fun resolveAsync(event:String,team:String,metadata:Map<String,*>,done:(Record?)->Unit) {
        worker.execute {done(try {resolve(event,team,metadata)} catch(_:Exception) {null})}
    }
    data class Record(val group: String, val id: String)
    fun event(team: String, key: String, revision: Long): String = hash("record/$team/$key" + if (revision > 1) "/$revision" else "")
    private fun hash(text: String) = MessageDigest.getInstance("SHA-256").digest(text.toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it) }
    fun authorized(access: TeamAccess, uid: String, team: String): Boolean = access.uid == uid && access.manages &&
        team.substringBeforeLast('_') in TeamPolicy.fronts && team.substringAfterLast('_') in TeamPolicy.shifts && access.mayUse(team)
    fun resolve(eventId: String, team: String, metadata: Map<String, *>): Record? {
        if (!eventId.matches(Regex("[a-f0-9]{64}"))) return null
        for ((key, raw) in metadata) {
            if (!key.startsWith("b_") || raw !is String) continue
            val row = try { JSONObject(raw) } catch (_: Exception) { continue }
            if (row.optString("equipeId") != team || row.optString("tipo") != "item" || row.optString("grupo") !in TeamPolicy.recordGroups) continue
            val id = row.optString("chave"); val revision = row.optLong("versao")
            if (id.isBlank() || revision < 1) continue
            // A recent notification may be opened after newer edits. Keep the current record as destination.
            val revisions = (revision downTo maxOf(1L, revision - 99)).toList() + 1L
            if (revisions.any { event(team, key.removePrefix("b_"), it) == eventId }) return Record(row.getString("grupo"), id)
        }
        return null
    }
}
