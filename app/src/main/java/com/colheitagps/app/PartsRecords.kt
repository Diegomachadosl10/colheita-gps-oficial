package com.colheitagps.app

import org.json.JSONArray
import org.json.JSONObject

/** Parts requests share maintenance team/author permissions; photos remain local on Spark. */
internal object PartsRecords {
    const val GROUP="pendencias"
    val states=linkedMapOf("a_pedir" to "A pedir","pedido" to "Pedido","recebido" to "Recebido")
    fun isPart(item:JSONObject)=item.optString("categoriaRegistro")=="peca"
    fun rows(all:JSONArray)=JSONArray().apply { for(i in 0 until all.length()) all.optJSONObject(i)?.takeIf(::isPart)?.let { put(it) } }
    fun merge(parts:JSONArray,previous:JSONArray)=JSONArray().apply {
        for(i in 0 until previous.length()) previous.optJSONObject(i)?.takeUnless(::isPart)?.let { put(JSONObject(it.toString())) }
        for(i in 0 until parts.length()) parts.optJSONObject(i)?.takeIf(::isPart)?.let { put(JSONObject(it.toString())) }
    }
    fun build(existing:JSONObject?,code:String,description:String,quantity:String,reference:String,state:String,uid:String,name:String,date:String):JSONObject {
        val number=MaintenanceRecords.number(code)
        require(number.length<=80&&description.trim().length<=2000) { "Use até 80 caracteres no código e 2000 na descrição." }
        require(number.isNotBlank()||description.isNotBlank()) { "Informe o código ou o nome da peça." }
        val count=quantity.trim().toIntOrNull()
        require(count!=null&&count in 1..999999) { "Informe uma quantidade inteira de 1 a 999999." }
        require(state in states) { "Situação inválida" }
        return (existing?.let { JSONObject(it.toString()) } ?: JSONObject().put("id",java.util.UUID.randomUUID().toString())
            .put("autorPecaUid",uid).put("autorPecaNome",name).put("dataCadastro",date))
            .put("categoriaRegistro","peca").put("codigoPeca",number).put("nomePeca",description.trim())
            .put("descricao","Pedido de peça ${number.ifBlank { "sem código" }} · $count un. · ${description.trim()}".trim())
            .put("quantidadePeca",count).put("equipamentoReferencia",reference.trim().take(80)).put("situacaoPeca",state)
    }
    fun historical(item:JSONObject)=MaintenanceRecords.isArchived(item)||item.optString("situacaoPeca")=="recebido"
    fun sharingText(item:JSONObject,teamLabel:String):String {
        val state=if(MaintenanceRecords.isArchived(item)) "Excluída (histórico)" else states[item.optString("situacaoPeca")] ?: "A pedir"
        return "PEDIDO DE PEÇA · DM AGRO\n$teamLabel\nCódigo: ${item.optString("codigoPeca").ifBlank { "Não informado" }}\nDescrição: ${item.optString("nomePeca",item.optString("descricao"))}\nQuantidade: ${item.optInt("quantidadePeca")}\nEquipamento: ${item.optString("equipamentoReferencia").ifBlank { "Não informado" }}\nSituação: $state"
    }
}
