package com.colheitagps.app

import org.json.JSONArray
import org.json.JSONObject

/** Operational records retain their ID, author, local media and lifecycle history. */
internal object RecordArchive {
    val labels=linkedMapOf("pendencias" to "Pendências e equipamentos","ocorrencias_campo" to "Ocorrências e chuva",
        "estoques_salvos" to "Estoques","historico_estoque" to "Histórico de estoque","relatorios_turno" to "Relatórios de turno",
        "caminhoes" to "Caminhões","movimentos" to "Movimentações","historico_os" to "Ordens de serviço")
    fun archived(record:JSONObject)=record.optBoolean("__arquivado")
    fun withState(record:JSONObject,deleted:Boolean)=JSONObject(record.toString()).apply {
        if(deleted) put("__arquivado",true) else remove("__arquivado")
    }
    private fun event(record:JSONObject,action:String,uid:String,name:String,date:String):JSONObject {
        val history=record.optJSONArray("historicoExclusoes")?:JSONArray()
        history.put(JSONObject().put("acao",action).put("porUid",uid).put("porNome",name).put("em",date))
        record.put("historicoExclusoes",history)
        return record
    }
    fun archive(record:JSONObject,uid:String,name:String,date:String):JSONObject =
        event(withState(record,true).put("arquivadoPorUid",uid).put("arquivadoPorNome",name).put("arquivadoEm",date),"excluido",uid,name,date)
    fun restore(record:JSONObject,uid:String,name:String,date:String):JSONObject =
        event(withState(record,false).put("restauradoPorUid",uid).put("restauradoPorNome",name).put("restauradoEm",date),"restaurado",uid,name,date)
    fun active(all:JSONArray)=JSONArray().apply {
        for(i in 0 until all.length()) all.optJSONObject(i)?.takeUnless(::archived)?.let {put(JSONObject(it.toString()))}
    }
    fun merge(current:JSONArray,previous:JSONArray)=JSONArray().apply {
        val ids=mutableSetOf<String>()
        for(i in 0 until current.length()) current.optJSONObject(i)?.let {put(JSONObject(it.toString()));ids.add(it.optString("id"))}
        for(i in 0 until previous.length()) previous.optJSONObject(i)?.takeIf {archived(it)&&it.optString("id") !in ids}?.let {put(JSONObject(it.toString()))}
    }
    fun title(group:String,record:JSONObject):String = when {
        MaintenanceRecords.isEquipment(record)->"Equipamento ${record.optString("equipamento")}"
        PartsRecords.isPart(record)->"Peça ${record.optString("codigoPeca").ifBlank {record.optString("nomePeca")}}"
        RainRecords.isRain(record)->"Chuva • ${record.optString("dataCadastro")}"
        group=="historico_os"->"O.S. ${record.optString("numeroOs")} • ${record.optString("nomeMapa")}"
        group=="estoques_salvos"->"Lote ${record.optString("os")}"
        group=="caminhoes"->"Caminhão ${record.optString("numero")}"
        group=="pendencias"->"${record.optString("equipamento")} • ${record.optString("descricao")}"
        else->listOf(record.optString("tipo"),record.optString("turno"),record.optString("descricao")).filter {it.isNotBlank()}.joinToString(" • ").ifBlank {labels[group]?:"Registro"}
    }
    fun description(record:JSONObject)=listOf("Descrição" to "descricao","Localização" to "localizacao","Talhões" to "talhoes",
        "Área (ha)" to "areaRestante","Produtividade (t/ha)" to "produtividade","Observações" to "observacoes","Chuva (mm)" to "chuvaMm",
        "Observação" to "observacaoChuva","Quantidade" to "quantidadePeca","Referência" to "equipamentoReferencia",
        "Data do registro" to "dataCadastro","Situação da O.S." to "statusOs","Encerrada em" to "encerradaEm","Encerrada por" to "encerradaPorNome","Reaberta em" to "reabertaEm","Reaberta como" to "reabertaComo","Excluído em" to "arquivadoEm","Excluído por" to "arquivadoPorNome")
        .mapNotNull {(label,key)->record.optString(key).takeIf {it.isNotBlank()}?.let {"$label: $it"}}.joinToString("\n")
}
