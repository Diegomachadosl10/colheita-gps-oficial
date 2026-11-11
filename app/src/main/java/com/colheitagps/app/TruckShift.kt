package com.colheitagps.app

import org.json.JSONArray
import org.json.JSONObject
import java.time.*
import java.time.format.DateTimeFormatter

/** Local working intervals are half-open: a boundary belongs only to the shift starting there. */
internal object TruckShift {
    private val a=LocalTime.of(7,0)
    private val b=LocalTime.of(15,20)
    private val c=LocalTime.of(23,20)
    data class Period(val shift:String,val date:LocalDate,val start:LocalDateTime,val end:LocalDateTime) {
        val key get()="$date/$shift"
        fun contains(time:LocalDateTime)=!time.isBefore(start)&&time.isBefore(end)
        fun label():String {
            val format=DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm")
            return "Turno $shift • ${start.format(format)} às ${end.format(format)}"
        }
    }
    fun hours(shift:String)=when(shift) {"A"->"07:00 às 15:20";"B"->"15:20 às 23:20";"C"->"23:20 às 07:00";else->error("Turno inválido")}
    fun period(shift:String,date:LocalDate):Period=when(shift) {
        "A"->Period(shift,date,date.atTime(a),date.atTime(b))
        "B"->Period(shift,date,date.atTime(b),date.atTime(c))
        "C"->Period(shift,date,date.atTime(c),date.plusDays(1).atTime(a))
        else->error("Turno inválido")
    }
    fun at(time:LocalDateTime):Period=when {
        time.toLocalTime()<a->period("C",time.toLocalDate().minusDays(1))
        time.toLocalTime()<b->period("A",time.toLocalDate())
        time.toLocalTime()<c->period("B",time.toLocalDate())
        else->period("C",time.toLocalDate())
    }
    fun active(shift:String,now:LocalDateTime)=at(now).takeIf {it.shift==shift}
    fun entry(record:JSONObject):LocalDateTime? {
        val day=TruckRecords.entryDay(record)?:return null
        return TruckRecords.time(record.optString("chegada"))?.let {day.atTime(it)}
    }
    fun requireEntry(shift:String,time:LocalDateTime) {
        val actual=at(time).shift
        require(actual==shift) {"A entrada informada pertence ao turno $actual (${hours(actual)}). Esta equipe é do turno $shift (${hours(shift)}). Confira a data e o horário."}
    }
    fun entryDate(startDate:LocalDate,shift:String,time:LocalTime)=if(shift=="C"&&time<a) startDate.plusDays(1) else startDate
    fun cutoff(period:Period,hour:LocalTime):LocalDateTime {
        val date=entryDate(period.date,period.shift,hour)
        // 07:00 closes C; it is not 07:00 on the starting day.
        val value=if(period.shift=="C"&&hour==a) period.end else date.atTime(hour)
        require(value>=period.start&&value<=period.end) {"Escolha um horário entre ${hours(period.shift)}."}
        return value
    }
    data class Snapshot(val rows:List<JSONObject>,val summary:TruckRecords.Summary)
    fun snapshot(all:JSONArray,period:Period?,cutoff:LocalDateTime?=null):Snapshot {
        if(period==null) return Snapshot(emptyList(),TruckRecords.Summary(0,0,0))
        val end=minOf(cutoff?:period.end,period.end)
        val selected=JSONArray(TruckRecords.active(all).filter {record->entry(record)?.let(period::contains)==true&&
            (record.optString("turno").isBlank()||record.optString("turno")==period.shift)})
        val actualEnd=if(end==period.end) end.minusNanos(1) else end
        val rows=TruckRecords.rows(TruckRecords.until(selected,actualEnd),null)
        if(end==period.end) rows.forEach {if(it.optString("saida").isBlank()) it.put("saidaAteCorte",end.format(DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm")))}
        return Snapshot(rows,TruckRecords.Summary(rows.size,rows.count {it.optString("saida").isNotBlank()},rows.count {it.optString("saida").isBlank()}))
    }
}
