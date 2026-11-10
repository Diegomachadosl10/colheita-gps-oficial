package com.colheitagps.app

import org.json.JSONArray
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[28],manifest=Config.NONE,application=android.app.Application::class)
class StockBatchTest {
    @Test fun savingSeveralLotsAssignsIndependentIdsAndSharingDoesNotIncludeUnselectedLots() {
        val records=StockBatch.records(listOf(StockBatch.Draft("001/K","1, 2","10,5","90"),StockBatch.Draft("B/20","8 a 12","8","70")),"10/11/2026")
        assertNotEquals(records[0].getString("id"),records[1].getString("id"))
        val array=JSONArray();records.forEach { array.put(it) }
        val selected=StockBatch.selected(array,setOf(records[0].getString("id")))
        val text=StockBatch.text(selected,"Santo Inácio • Turno C","10/11/2026")
        assertTrue(text.contains("001/K"));assertTrue(text.contains("10,5"));assertFalse(text.contains("B/20"))
        assertEquals(2,array.length());assertFalse(records[0].has("fotoEstoque"))
    }
    @Test fun oneIncompleteLotRejectsTheWholeBatchBeforeAnyPartialSave() {
        try { StockBatch.records(listOf(StockBatch.Draft("A","1","10","80"),StockBatch.Draft("B","","10","80")),"hoje");fail() }
        catch(_:IllegalArgumentException) {}
        try { StockBatch.records(emptyList(),"hoje");fail() } catch(_:IllegalArgumentException) {}
    }
}
