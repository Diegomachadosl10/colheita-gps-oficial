package com.colheitagps.app

import org.json.JSONObject
import java.util.UUID

internal object OrderRecords {
    const val GROUP="historico_os"
    fun closed(record:JSONObject)=record.optString("statusOs")=="encerrada"&&!RecordArchive.archived(record)
    fun historical(record:JSONObject)=record.optString("statusOs") in setOf("encerrada","reaberta")&&!RecordArchive.archived(record)
    fun close(mapId:String,mapName:String,number:String,fields:String,uid:String,name:String,date:String,stamp:Long):JSONObject {
        require(mapId.isNotBlank()&&number.isNotBlank())
        return JSONObject().put("id",UUID.randomUUID().toString()).put("categoriaRegistro","ordem_servico")
            .put("mapaId",mapId).put("nomeMapa",mapName).put("numeroOs",number).put("talhoes",fields)
            .put("statusOs","encerrada").put("encerradaEm",date).put("encerradaTimestamp",stamp)
            .put("encerradaPorUid",uid).put("encerradaPorNome",name)
    }
    fun reopen(record:JSONObject,number:String,fields:String,uid:String,name:String,date:String):JSONObject {
        require(closed(record))
        return JSONObject(record.toString()).put("statusOs","reaberta").put("reabertaComo",number).put("talhoesReabertura",fields)
            .put("reabertaEm",date).put("reabertaPorUid",uid).put("reabertaPorNome",name)
    }
}
