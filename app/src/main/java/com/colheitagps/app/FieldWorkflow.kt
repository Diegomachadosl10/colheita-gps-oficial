package com.colheitagps.app

import org.json.JSONArray
import org.json.JSONObject
import java.time.Duration
import java.time.LocalDateTime
import java.util.Locale

internal object FieldWorkflow {
    fun numeric(view:android.widget.EditText) {
        view.keyListener=android.text.method.DigitsKeyListener.getInstance("0123456789.,")
        view.setRawInputType(android.text.InputType.TYPE_CLASS_NUMBER or android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL)
    }
    fun lot(value:String)=java.text.Normalizer.normalize(value,java.text.Normalizer.Form.NFKC).uppercase(Locale.ROOT).replace(Regex("[\\s/_-]+"),"")
    fun decimal(value:String):Boolean=value.trim().matches(Regex("[0-9]{1,8}([.,][0-9]{1,4})?"))
    fun requireStock(record:JSONObject) {
        require(record.optString("os").isNotBlank()&&record.optString("talhoes").isNotBlank()) {"Informe o lote e os talhões."}
        require(decimal(record.optString("areaRestante"))) {"Informe a área em hectares, usando apenas número e vírgula ou ponto decimal."}
        require(decimal(record.optString("produtividade"))) {"Informe a produtividade em toneladas por hectare, usando um número válido."}
    }
    fun matches(all:JSONArray,row:JSONObject)=FieldReports.rows(all).filter {!RecordArchive.archived(it)&&it.optString("id")!=row.optString("id")&&lot(it.optString("os"))==lot(row.optString("os"))}
    fun parts(all:JSONArray,id:String)=FieldReports.rows(all).filter {PartsRecords.isPart(it)&&!RecordArchive.archived(it)&&id.isNotBlank()&&it.optString("pendenciaId")==id}
    fun partsLabel(all:JSONArray,id:String):String {
        val parts=parts(all,id);if(parts.isEmpty()) return ""
        return parts.groupingBy {it.optString("situacaoPeca","a_pedir")}.eachCount().entries.joinToString(" • ") {(state,count)->"$count ${if(count==1) "peça" else "peças"} ${when(state){"pedido"->if(count==1) "pedida" else "pedidas";"recebido"->if(count==1) "recebida" else "recebidas";else->"a pedir"}}"}
    }
    fun enrich(all:JSONArray,rows:List<JSONObject>)=rows.map {JSONObject(it.toString()).apply {
        partsLabel(all,optString("id")).takeIf {it.isNotBlank()}?.let {put("situacaoPecas",it)}
    }}
    fun waitMinutes(row:JSONObject,cutoff:LocalDateTime):Long? {
        if(row.optString("saida").isNotBlank()) return null
        val entry=TruckShift.entry(row)?:return null
        return Duration.between(entry,cutoff).toMinutes().takeIf {it>=0}
    }
    fun waitLabel(minutes:Long)="${minutes/60}h ${minutes%60}min"
}
