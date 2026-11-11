package com.colheitagps.app

import android.app.Application
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.LocalDate
import java.time.LocalDateTime

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[28],manifest=Config.NONE,application=Application::class)
class TruckRecordsTest {
    private val day=LocalDate.of(2026,11,11)
    private fun row(number:String,entry:String,exit:String="",date:LocalDate=day)=TruckRecords.build(null,number,entry,exit,date,null,"santo_inacio_C")
    @Test fun anOvernightTripCountsItsEntryAndExitOnDifferentDaysAndCanBeEditedWithoutLosingItsHistory() {
        val first=row("00123","23:40","00:25")
        assertEquals(day.plusDays(1),TruckRecords.exitDay(first));assertEquals("00123",first.getString("numero"));assertEquals("C",first.getString("turno"))
        val all=JSONArray().put(first)
        assertEquals(TruckRecords.Summary(1,0,0),TruckRecords.summary(all,day));assertEquals(TruckRecords.Summary(0,1,0),TruckRecords.summary(all,day.plusDays(1)))
        assertEquals(1,TruckRecords.rows(all,day.plusDays(1)).size)
        first.put("restauradoEm","ontem").put("autorUid","motorista")
        val edited=TruckRecords.build(first,"00123","23:40","00:40",day,day.plusDays(1),"santo_inacio_C")
        assertEquals(first.getString("id"),edited.getString("id"));assertEquals("ontem",edited.getString("restauradoEm"));assertEquals("motorista",edited.getString("autorUid"))
        assertNotEquals(first.getString("id"),row("00123","23:40").getString("id"))
    }
    @Test fun invalidTimesAndExitDatesCannotBeSaved() {
        for((entry,exit) in listOf("24:00" to "","12:78" to "","" to "","12:00" to "25:10")) {
            try {row("1",entry,exit);fail("Invalid time was accepted")} catch(_:IllegalArgumentException) {}
        }
        try {TruckRecords.build(null,"1","12:00","11:00",day,day,"santo_inacio_A");fail()} catch(_:IllegalArgumentException) {}
        assertEquals("01:05",row("1","1h05").getString("chegada"))
    }
    @Test fun aNoonCutoffShowsTheSituationAtNoonWithoutRemovingTheLaterExitFromTheDatabase() {
        val previous=row("ANTERIOR","23:00","13:00",day.minusDays(1))
        val departed=row("SAIU","08:00","10:00")
        val later=row("DEPOIS","12:01","")
        val deleted=RecordArchive.archive(row("EXCLUIDO","09:00"),"sup","Supervisor","ontem")
        val all=JSONArray().put(previous).put(departed).put(later).put(deleted);val before=all.toString()
        val cut=TruckRecords.until(all,LocalDateTime.of(2026,11,11,12,0))
        assertEquals(TruckRecords.Summary(1,1,1),TruckRecords.summary(cut,day))
        assertEquals(setOf("ANTERIOR","SAIU"),TruckRecords.rows(cut,day,true).map {it.getString("numero")}.toSet())
        assertEquals("",cut.getJSONObject(0).getString("saida"));assertTrue(TruckRecords.details(cut.getJSONObject(0)).contains("11/11/2026 12:00"))
        assertEquals(before,all.toString());assertEquals("13:00",previous.getString("saida"))
        val report=FieldReports.trucks("Santo Inácio • Turno C","11/11/2026 até 12:00",TruckRecords.rows(cut,day,true),TruckRecords.summary(cut,day))
        assertEquals(listOf("1","1","1"),report.sections.first().rows.single());assertFalse(report.sections.last().rows.flatten().any {it.contains("13:00")})
    }
    @Test fun oldRowsRemainVisibleForCorrectionAndArchivedTrucksStayOutOfTotals() {
        val legacy=JSONObject().put("id","antigo").put("numero","9").put("chegada","10:00").put("saida","")
        val archived=RecordArchive.archive(row("1","08:00"),"sup","Supervisor","hoje")
        val all=JSONArray().put(legacy).put(archived)
        assertNull(TruckRecords.entryDay(legacy));assertEquals(1,TruckRecords.rows(all,null).size)
        assertEquals(TruckRecords.Summary(1,0,1),TruckRecords.summary(all,null))
        val fixed=TruckRecords.build(legacy,"9","10:00","",day,null,"santo_inacio_C")
        assertEquals("antigo",fixed.getString("id"));assertEquals(day,TruckRecords.entryDay(fixed))
    }
}
