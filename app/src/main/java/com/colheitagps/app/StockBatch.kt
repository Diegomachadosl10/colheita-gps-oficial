package com.colheitagps.app

import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

internal object StockBatch {
    data class Draft(val lot:String,val fields:String,val area:String,val yield:String,val observations:String="")
    fun records(drafts:List<Draft>,date:String):List<JSONObject> {
        require(drafts.isNotEmpty()&&drafts.size<=20) { "Adicione de 1 a 20 lotes por cadastro." }
        require(drafts.all { listOf(it.lot,it.fields,it.area,it.yield).all { value->value.isNotBlank() } }) { "Preencha lote, talhões, área e produtividade em todos os cartões." }
        require(drafts.map {FieldWorkflow.lot(it.lot)}.distinct().size==drafts.size) {"Há um lote repetido neste cadastro. Mantenha um cartão por lote."}
        val records=drafts.map { d->JSONObject().put("os",d.lot.trim())
            .put("talhoes",d.fields.trim()).put("areaRestante",d.area.trim()).put("produtividade",d.yield.trim())
            .put("observacoes",d.observations.trim()).put("dataCadastro",date) }
        records.forEach(FieldWorkflow::requireStock)
        return records.map {it.put("id",UUID.randomUUID().toString())}
    }
    fun selected(array:JSONArray,ids:Set<String>):List<JSONObject> = (0 until array.length()).mapNotNull { array.optJSONObject(it) }
        .filter { it.optString("id") in ids&&!RecordArchive.archived(it) }.map { JSONObject(it.toString()) }
    fun complete(record:JSONObject)=listOf("os","talhoes","areaRestante","produtividade").all { record.optString(it).isNotBlank() }
    // Only a plain numeric value receives an implicit unit. Free text is never parsed or rounded.
    fun quantity(value:String,unit:String)=value.trim().let {if(FieldWorkflow.decimal(it)) "$it $unit" else it}
    fun details(record:JSONObject):String = "Talhões: ${record.optString("talhoes")}\nÁrea: ${quantity(record.optString("areaRestante"),"ha")}\nProdutividade: ${quantity(record.optString("produtividade"),"t/ha")}"+
        record.optString("observacoes").takeIf {it.isNotBlank()}?.let {"\nObservações: $it"}.orEmpty()
    fun singleText(record:JSONObject,team:String,date:String) =
        "DM AGRO — ESTOQUE DE CANA\n$team\nData: $date\nLote: ${record.optString("os")}\n${details(record)}"
    fun text(records:List<JSONObject>,team:String,date:String):String {
        require(records.isNotEmpty())
        return "DM AGRO — ESTOQUE DE CANA\n$team\nData: $date\n${records.size} lote(s) selecionado(s)\n\n"+
            records.mapIndexed { index,p->"${index+1}. Lote: ${p.optString("os")}\n${details(p)}" }.joinToString("\n\n")
    }
}
