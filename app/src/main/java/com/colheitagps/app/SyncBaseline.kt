package com.colheitagps.app

import org.json.JSONObject
import java.security.MessageDigest

/** A server baseline is adopted only for identical data, never as a conflict resolution. */
internal object SyncBaseline {
    private val preferenceGroups=setOf("mapas","pinturas","ordens_servico","talhoes_por_os")
    fun id(kind:String,group:String,key:String)=MessageDigest.getInstance("SHA-256")
        .digest("$kind|$group|$key".toByteArray(Charsets.UTF_8)).joinToString("") {"%02x".format(it)}

    fun decode(documentId:String,wire:Map<String,Any>,team:String):JSONObject {
        require(wire["formato"]==2L||wire["formato"]==2) {"Formato inválido"}
        require(wire["equipeId"]==team&&wire["frenteId"]==team.substringBeforeLast('_')&&wire["turno"]==team.substringAfterLast('_')) {"Equipe inválida"}
        val kind=wire["tipo"] as? String ?: throw IllegalArgumentException("Tipo inválido")
        val group=wire["grupo"] as? String ?: throw IllegalArgumentException("Grupo inválido")
        val key=wire["chave"] as? String ?: throw IllegalArgumentException("Chave inválida")
        require(key.isNotBlank()&&key.length<250&&documentId==id(kind,group,key)) {"Identidade inválida"}
        require((kind=="item"&&group in TeamPolicy.recordGroups)||(kind=="preferencia"&&group in preferenceGroups)) {"Grupo inválido"}
        require(wire["autorUid"] is String&&(wire["autorUid"] as String).isNotBlank()) {"Autor inválido"}
        require(wire["editadoPor"] is String&&(wire["editadoPor"] as String).isNotBlank()) {"Editor inválido"}
        val revision=wire["versao"]
        require((revision is Long&&revision>0)||(revision is Int&&revision>0)) {"Revisão inválida"}
        val deleted=wire["excluido"] as? Boolean ?: throw IllegalArgumentException("Estado inválido")
        val result=BetaRecordCodec.fromWire(wire)
        if(kind=="item") {
            val row=JSONObject(result.getString("conteudo"))
            require(row.opt("id")==key) {"Identidade do conteúdo inválida"}
            require(!row.has("__arquivado")||row.opt("__arquivado") is Boolean) {"Estado do conteúdo inválido"}
            require(RecordArchive.archived(row)==deleted) {"Estado divergente"}
            require(!row.has("concluida")||row.opt("concluida") is Boolean) {"Conclusão inválida"}
        } else require(wire["conteudo"] is String&&!wire.containsKey("dados")) {"Preferência inválida"}
        return result
    }

    fun content(store:TeamStore,record:JSONObject):String {
        val decoded=store.decode(record.getString("conteudo"))
        if(record.getString("tipo")!="item") return store.encode(decoded)
        val row=RecordArchive.withState(JSONObject(decoded),record.getBoolean("excluido"))
        return store.encode((if(TeamPolicy.AUTOMATIC_ATTACHMENTS) row else TeamContent.textOnly(row)).toString())
    }

    private fun same(store:TeamStore,a:JSONObject,b:JSONObject):Boolean =
        listOf("tipo","grupo","chave","excluido").all {a.opt(it)==b.opt(it)}&&
            BetaRecordCodec.sameContent(content(store,a),content(store,b))

    fun mayAdopt(store:TeamStore,current:JSONObject,server:JSONObject,baselineExists:Boolean,
                 queued:JSONObject?,conflictExists:Boolean):Boolean {
        if(baselineExists||conflictExists||!same(store,current,server)) return false
        if(current.has("__syncBase")&&current.getLong("__syncBase")!=server.getLong("versao")) return false
        // A blind scan before the first snapshot may have queued an unchanged create.
        // Changes based on a known revision remain explicit pending work.
        return queued==null||(queued.opt("base")==0L||queued.opt("base")==0)&&same(store,queued,server)
    }
}
