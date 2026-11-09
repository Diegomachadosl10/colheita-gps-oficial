package com.colheitagps.app

import android.app.Application
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], manifest = Config.NONE, application = Application::class)
class RainRecordsTest {
    @Test fun decimalInputAcceptsCommaAndZeroButRejectsInvalidMeasurements() {
        assertEquals("12.5", RainRecords.amount("12,50")?.toPlainString())
        assertEquals("0", RainRecords.amount("0")?.toPlainString())
        assertEquals("123.45", RainRecords.amount("123.45")?.toPlainString())
        for (value in listOf("", "-1", "NaN", "Infinity", "1e3", "1,234", "1.000,5", "10000", "chuva")) {
            assertNull(value, RainRecords.amount(value))
        }
    }
    @Test fun dateIsStrictAndRecordIdentityIsStableForTheDayWithinItsTeam() {
        assertNull(RainRecords.date("31/02/2026"))
        assertNotNull(RainRecords.date("29/02/2028"))
        val first = RainRecords.create("09/11/2026", "0", "", "santo_inacio_A", "opA", "João")
        val corrected = RainRecords.create("09/11/2026", "12,5", " Chuva à tarde ", "santo_inacio_A", "opA", "João")
        assertEquals(first.getString("id"), corrected.getString("id"))
        assertEquals("0 mm", RainRecords.label(first))
        assertEquals("12,5 mm", RainRecords.label(corrected))
        assertEquals("Chuva à tarde", corrected.getString("observacaoChuva"))
        assertEquals("santo_inacio", corrected.getString("frenteId"))
        assertEquals("A", corrected.getString("turno"))
        assertTrue(RainRecords.GROUP in TeamPolicy.operatorGroups)
        assertTrue(RainRecords.GROUP in TeamPolicy.recordGroups)
    }
    @Test fun weatherHistoryUsesDateOrderAndExcludesOrdinaryFieldOccurrences() {
        val all = JSONArray().put(JSONObject().put("id", "buraco").put("tipo", "Buraco"))
            .put(RainRecords.create("31/10/2026", "5", "", "santo_inacio_A", "opA", "João"))
            .put(RainRecords.create("01/11/2026", "0", "", "santo_inacio_A", "opA", "João"))
        val history = RainRecords.readings(all)
        assertEquals(2, history.size)
        assertEquals("01/11/2026", history.first().getString("dataCadastro"))
        assertEquals(3, all.length())
        assertEquals("5 mm", RainRecords.label(TeamContent.textOnly(history.last())))
    }
}
