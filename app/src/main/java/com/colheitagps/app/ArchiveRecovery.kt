package com.colheitagps.app

import android.content.SharedPreferences
import org.json.JSONArray
import org.json.JSONObject

/** Older clients removed non-maintenance tombstones from their local lists, but retained
 * the confirmed record in the sync baseline. Recover that retained content without reviving it. */
internal object ArchiveRecovery {
    fun recover(store:TeamStore,baseline:SharedPreferences,access:TeamAccess) = synchronized(store.lock) {
        for(group in TeamPolicy.recordGroups.filter {access.manages||it in TeamPolicy.operatorGroups}) {
            val prefs=store.prefs(if(group=="movimentos") "controle_colheita" else "registros_v3")
            val all=try {JSONArray(prefs.getString(group,"[]"))} catch(_:Exception) {JSONArray()}
            val ids=(0 until all.length()).mapNotNull {all.optJSONObject(it)?.optString("id")}.toMutableSet()
            var changed=false
            val pending=baseline.edit()
            if(!access.manages) for((key,raw) in baseline.all) {
                if(!key.startsWith("q_")||raw !is String) continue
                try {
                    val queued=JSONObject(raw)
                    if(queued.optString("tipo")!="item"||queued.optString("grupo")!=group||!queued.optBoolean("excluido")) continue
                    val confirmed=baseline.getString("b_"+key.removePrefix("q_"),null)?.let(::JSONObject)
                    val source=confirmed?.takeIf {it.optBoolean("excluido")}?:queued
                    val deleted=confirmed?.optBoolean("excluido")==true
                    val id=source.getString("chave")
                    val index=(0 until all.length()).firstOrNull {all.optJSONObject(it)?.optString("id")==id}
                    val existing=index?.let {all.optJSONObject(it)}
                    val recovered=TeamContent.preserveLocalMedia(RecordArchive.withState(JSONObject(store.decode(source.getString("conteudo"))),deleted).put("id",id),existing)
                    if(index==null) {all.put(recovered);ids.add(id)} else all.put(index,recovered)
                    if(deleted) pending.remove(key)
                    else pending.putString(key,queued.put("excluido",false).put("conteudo",store.encode(TeamContent.textOnly(recovered).toString())).toString())
                    changed=true
                } catch(_:Exception) { /* Preserve malformed data for support. */ }
            }
            for((key,raw) in baseline.all) {
                if(!key.startsWith("b_")||raw !is String) continue
                try {
                    val wire=JSONObject(raw)
                    if(wire.optString("tipo")!="item"||wire.optString("grupo")!=group||!wire.optBoolean("excluido")) continue
                    val id=wire.optString("chave");if(id.isBlank()||id in ids) continue
                    val recovered=RecordArchive.withState(JSONObject(store.decode(wire.getString("conteudo"))),true).put("id",id)
                    all.put(recovered);ids.add(id);changed=true
                } catch(_:Exception) { /* Keep malformed legacy baselines untouched for support. */ }
            }
            if(changed) {check(prefs.edit().putString(group,all.toString()).commit());check(pending.commit())}
        }
    }
}
