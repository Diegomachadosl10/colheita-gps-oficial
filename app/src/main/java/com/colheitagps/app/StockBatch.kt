package com.colheitagps.app

import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

internal object StockBatch {
    data class Draft(val lot:String,val fields:String,val area:String,val yield:String)
    fun records(drafts:List<Draft>,date:String):List<JSONObject> {
        require(drafts.isNotEmpty()&&drafts.size<=20) { "Adicione de 1 a 20 lotes por cadastro." }
        require(drafts.all { listOf(it.lot,it.fields,it.area,it.yield).all { value->value.isNotBlank() } }) { "Preencha lote, talhões, área e produtividade em todos os cartões." }
        require(drafts.map {FieldWorkflow.lot(it.lot)}.distinct().size==drafts.size) {"Há um lote repetido neste cadastro. Mantenha um cartão por lote."}
        require(drafts.all {FieldWorkflow.decimal(it.area)&&FieldWorkflow.decimal(it.yield)}) {"Área (ha) e produtividade (t/ha) precisam ser números válidos."}
        return drafts.map { d->JSONObject().put("id",UUID.randomUUID().toString()).put("os",d.lot.trim())
            .put("talhoes",d.fields.trim()).put("areaRestante",d.area.trim()).put("produtividade",d.yield.trim()).put("dataCadastro",date) }
    }
    fun selected(array:JSONArray,ids:Set<String>):List<JSONObject> = (0 until array.length()).mapNotNull { array.optJSONObject(it) }
        .filter { it.optString("id") in ids&&!RecordArchive.archived(it) }.map { JSONObject(it.toString()) }
    fun complete(record:JSONObject)=listOf("os","talhoes","areaRestante","produtividade").all { record.optString(it).isNotBlank() }
    fun text(records:List<JSONObject>,team:String,date:String):String {
        require(records.isNotEmpty())
        return "DM AGRO — ESTOQUE DE CANA\n$team\nData: $date\n${records.size} lote(s) selecionado(s)\n\n"+
            records.mapIndexed { index,p->"${index+1}. Lote: ${p.optString("os")}\nTalhões: ${p.optString("talhoes")}\nÁrea: ${p.optString("areaRestante")} ha\nProdutividade: ${p.optString("produtividade")} t/ha" }.joinToString("\n\n")
    }
}
