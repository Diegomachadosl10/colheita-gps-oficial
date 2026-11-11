package com.colheitagps.app

import android.app.Application
import org.json.JSONArray
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.*

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[28],manifest=Config.NONE,application=Application::class)
class TruckShiftTest {
    private val day=LocalDate.of(2026,11,12)
    @Test fun boundariesBelongOnlyToTheIncomingShiftAndMidnightStaysInTheSameC() {
        val cases=listOf("06:59:59" to "C","07:00" to "A","15:19:59" to "A","15:20" to "B","23:19:59" to "B","23:20" to "C")
        for((time,shift) in cases) assertEquals(shift,TruckShift.at(day.atTime(LocalTime.parse(time))).shift)
        val before=TruckShift.at(day.minusDays(1).atTime(23,59,59))
        assertEquals(before,TruckShift.at(day.atStartOfDay()));assertEquals(day.minusDays(1),before.date)
        for(minute in 0 until 24*60) {
            val now=day.atStartOfDay().plusMinutes(minute.toLong())
            val candidates=listOf(day.minusDays(1),day).flatMap {date->listOf("A","B","C").map {TruckShift.period(it,date)}}
            assertEquals(1,candidates.count {it.contains(now)})
        }
    }
    @Test fun anOldUnfinishedTripNeverCarriesIntoTheNextShiftOrTheNextDaysSameShift() {
        val first=TruckRecords.build(null,"OLD","23:50","",day.minusDays(1),null,"santo_inacio_C")
        val next=TruckRecords.build(null,"NEW","23:25","",day,null,"santo_inacio_C")
        val all=JSONArray().put(first).put(next);val original=all.toString()
        val atNight=TruckShift.snapshot(all,TruckShift.active("C",day.atTime(0,30)),day.atTime(0,30))
        assertEquals(listOf("OLD"),atNight.rows.map {it.getString("numero")})
        val atSeven=TruckShift.snapshot(all,TruckShift.active("C",day.atTime(7,0)))
        assertTrue(atSeven.rows.isEmpty());assertEquals(TruckRecords.Summary(0,0,0),atSeven.summary)
        val nextNight=TruckShift.snapshot(all,TruckShift.active("C",day.atTime(23,30)),day.atTime(23,30))
        assertEquals(listOf("NEW"),nextNight.rows.map {it.getString("numero")});assertEquals(1,nextNight.summary.pending)
        assertEquals(original,all.toString());assertEquals(1,TruckShift.snapshot(all,TruckShift.period("C",day.minusDays(1))).rows.size)
    }
    @Test fun historicalCUsesItsStartingDateAndCutoffAfterMidnightWithoutAlteringTheActualExit() {
        val period=TruckShift.period("C",day.minusDays(1))
        val record=TruckRecords.build(null,"1","00:30","03:00",day,null,"santo_inacio_C")
        val cut=TruckShift.cutoff(period,LocalTime.of(1,0))
        assertEquals(day.atTime(1,0),cut)
        val result=TruckShift.snapshot(JSONArray().put(record),period,cut)
        assertEquals(TruckRecords.Summary(1,0,1),result.summary);assertEquals("",result.rows.single().getString("saida"));assertEquals("03:00",record.getString("saida"))
        assertEquals(TruckRecords.Summary(1,1,0),TruckShift.snapshot(JSONArray().put(record),period).summary)
        assertEquals(day,TruckShift.entryDate(day.minusDays(1),"C",LocalTime.of(0,30)))
        for(shift in listOf("A","B","C")) {
            val p=TruckShift.period(shift,day)
            assertEquals(p.start,TruckShift.cutoff(p,p.start.toLocalTime()));assertEquals(p.end,TruckShift.cutoff(p,p.end.toLocalTime()))
        }
        try {TruckShift.cutoff(period,LocalTime.NOON);fail()} catch(_:IllegalArgumentException) {}
    }
    @Test fun oldOutOfShiftRecordsRemainInCompleteHistoryButDoNotPolluteAnActiveShift() {
        val old=TruckRecords.build(null,"LEGACY","10:00","",day,null,"santo_inacio_C")
        val correct=TruckRecords.build(null,"C","23:40","",day,null,"santo_inacio_C")
        val all=JSONArray().put(old).put(correct)
        assertEquals(listOf("C"),TruckShift.snapshot(all,TruckShift.period("C",day)).rows.map {it.getString("numero")})
        assertEquals(2,TruckRecords.rows(all,null).size)
        try {TruckShift.requireEntry("C",day.atTime(10,0));fail()} catch(error:IllegalArgumentException) {assertTrue(error.message!!.contains("turno A"))}
        TruckShift.requireEntry("C",day.atTime(0,30));TruckShift.requireEntry("C",day.atTime(23,20))
        TruckShift.requireEntry("A",day.atTime(7,0));TruckShift.requireEntry("B",day.atTime(15,20))
    }
}
