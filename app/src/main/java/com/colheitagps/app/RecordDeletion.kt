package com.colheitagps.app

import org.json.JSONObject

/** A timed-out creation may have reached Firestore before the user deletes its local copy. */
internal object RecordDeletion {
    private fun sameRecord(change: JSONObject, remote: JSONObject) =
        listOf("tipo", "grupo", "chave", "autorUid").all { change.optString(it) == remote.optString(it) }

    fun alreadyDeleted(change: JSONObject, remote: JSONObject?) =
        change.optBoolean("excluido") && remote != null && remote.optBoolean("excluido") && sameRecord(change, remote) &&
            (change.optString("grupo") != "pendencias" || change.optString("conteudo") == remote.optString("conteudo"))

    fun canDeleteUnconfirmed(change: JSONObject, remote: JSONObject?): Boolean =
        change.optBoolean("excluido") && change.optLong("base") == 0L &&
            (remote == null || (sameRecord(change, remote) && remote.optLong("versao") == 1L &&
                remote.optString("conteudo") == change.optString("conteudo")))
}
