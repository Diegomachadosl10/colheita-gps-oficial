package com.colheitagps.app

import org.json.JSONArray
import org.json.JSONObject
import java.time.*
import java.time.format.DateTimeFormatter
import java.util.UUID

internal object TruckRecords {
    private val dates=DateTimeFormatter.ofPattern("dd/MM/uuuu")
    private val times=DateTimeFormatter.ofPattern("HH:mm")
    fun label(date:LocalDate)=date.format(dates)
    fun time(value:String):LocalTime? {
        val parts=Regex("^([0-9]{1,2})[:h]([0-9]{2})$",RegexOption.IGNORE_CASE).matchEntire(value.trim())?:return null
        return try {LocalTime.of(parts.groupValues[1].toInt(),parts.groupValues[2].toInt())} catch(_:Exception) {null}
    }
    fun entryDay(record:JSONObject):LocalDate? {
        for(key in listOf("dataEntrada","dataOperacao","dataCadastro","data")) RainRecords.date(record.optString(key).take(10))?.let {return it}
        val stamp=record.optString("id").toLongOrNull()?:return null
        return if(stamp in 946684800001L..4102444799999L) Instant.ofEpochMilli(stamp).atZone(ZoneId.systemDefault()).toLocalDate() else null
    }
    fun exitDay(record:JSONObject):LocalDate? {
        if(record.optString("saida").isBlank()) return null
        RainRecords.date(record.optString("dataSaida"))?.let {return it}
        val day=entryDay(record)?:return null
        val entry=time(record.optString("chegada"));val exit=time(record.optString("saida"))
        return if(entry!=null&&exit!=null&&exit<entry) day.plusDays(1) else day
    }
    fun build(existing:JSONObject?,number:String,arrival:String,departure:String,day:LocalDate,explicitExit:LocalDate?,team:String):JSONObject {
        require(number.isNotBlank()&&number.trim().length<=80) {"Informe o caminhão, com até 80 caracteres."}
        val entry=requireNotNull(time(arrival)) {"Informe a entrada no formato HH:mm, de 00:00 a 23:59."}
        val exit=if(departure.isBlank()) null else requireNotNull(time(departure)) {"Informe a saída no formato HH:mm, de 00:00 a 23:59, ou deixe vazia."}
        require(team.substringBeforeLast('_') in TeamPolicy.fronts&&team.substringAfterLast('_') in TeamPolicy.shifts)
        val exitDate=if(exit==null) null else explicitExit?:if(exit<entry) day.plusDays(1) else day
        require(exitDate==null||!LocalDateTime.of(exitDate,exit!!).isBefore(LocalDateTime.of(day,entry))) {"A saída não pode ser anterior à entrada. Confira a data da saída."}
        return (existing?.let {JSONObject(it.toString())}?:JSONObject().put("id",UUID.randomUUID().toString()))
            .put("numero",number.trim()).put("chegada",entry.format(times)).put("saida",exit?.format(times).orEmpty())
            .put("dataEntrada",label(day)).put("dataOperacao",label(day)).put("turno",team.substringAfterLast('_'))
            .apply {if(optString("dataCadastro").isBlank()) put("dataCadastro",label(day));if(exitDate==null) remove("dataSaida") else put("dataSaida",label(exitDate))}
    }
    fun active(all:JSONArray)= (0 until all.length()).mapNotNull {all.optJSONObject(it)}.filterNot(RecordArchive::archived)
    fun until(all:JSONArray,cutoff:LocalDateTime):JSONArray=JSONArray().apply {
        for(record in active(all)) {
            val day=entryDay(record)?:continue;val hour=time(record.optString("chegada"))?:continue
            if(LocalDateTime.of(day,hour).isAfter(cutoff)) continue
            val copy=JSONObject(record.toString())
            val endDay=exitDay(record);val end=time(record.optString("saida"))
            if(record.optString("saida").isNotBlank()&&(endDay==null||end==null||LocalDateTime.of(endDay,end).isAfter(cutoff)))
                copy.put("saida","").remove("dataSaida")
            if(copy.optString("saida").isBlank()) copy.put("saidaAteCorte",cutoff.format(DateTimeFormatter.ofPattern("dd/MM/uuuu HH:mm")))
            put(copy)
        }
    }
    fun matchesDay(record:JSONObject,date:LocalDate)=entryDay(record)==date||exitDay(record)==date
    fun rows(all:JSONArray,day:LocalDate?,includeOldPending:Boolean=false)=active(all).filter {
        day==null||matchesDay(it,day)||(includeOldPending&&it.optString("saida").isBlank()&&entryDay(it)?.isAfter(day)!=true)
    }.sortedWith(compareByDescending<JSONObject> {entryDay(it)}.thenByDescending {time(it.optString("chegada"))}.thenBy {it.optString("numero")})
    data class Summary(val entries:Int,val exits:Int,val pending:Int)
    fun summary(all:JSONArray,day:LocalDate?):Summary {
        val records=active(all)
        return Summary(records.count {it.optString("chegada").isNotBlank()&&(day==null||entryDay(it)==day)},
            records.count {it.optString("saida").isNotBlank()&&(day==null||exitDay(it)==day)},records.count {it.optString("saida").isBlank()})
    }
    fun details(record:JSONObject):String = "Entrada: ${entryDay(record)?.let(::label)?:"Sem data"} ${record.optString("chegada").ifBlank {"Sem horário"}}\n"+
        "Saída: "+if(record.optString("saida").isBlank()) record.optString("saidaAteCorte").takeIf {it.isNotBlank()}?.let {"Sem saída até $it"}?:"Aguardando saída" else "${exitDay(record)?.let(::label)?:"Sem data"} ${record.optString("saida")}"
    fun report(records:List<JSONObject>,team:String,period:String,summary:Summary)=
        "DM AGRO — CAMINHÕES\n$team\n$period\nEntradas: ${summary.entries}\nSaídas: ${summary.exits}\nAguardando saída na equipe: ${summary.pending}\n\n"+
            records.joinToString("\n\n") {"Caminhão ${it.optString("numero")} • Turno ${it.optString("turno")}\n${details(it)}"}
}
