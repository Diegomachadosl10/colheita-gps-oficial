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
    @Test fun freeTextAndOptionalNotesSurviveTheIndividualAndBatchMessagesWithoutExtractingNumbers() {
        val notes="Esperar liberação do carreador\nUsar entrada sul"
        val rows=StockBatch.records(listOf(StockBatch.Draft("K/39","1 a 10","40 ha, aproximadamente","Entre 70 e 80 t/ha",notes),
            StockBatch.Draft("K/40","11","A conferir","Não medida")),"26/11/2026")
        val before=rows.map {it.toString()}
        for(text in listOf(StockBatch.singleText(rows[0],"Santo Inácio • Turno C","hoje"),StockBatch.text(rows,"Santo Inácio • Turno C","hoje"))) {
            assertTrue(text.contains("Área: 40 ha, aproximadamente\n"));assertFalse(text.contains("aproximadamente ha"))
            assertTrue(text.contains("Produtividade: Entre 70 e 80 t/ha"));assertTrue(text.contains(notes))
        }
        assertEquals(before,rows.map {it.toString()})
        val plain=StockBatch.singleText(rows[1],"Equipe","hoje")
        assertTrue(plain.contains("Área: A conferir\n"));assertFalse(plain.contains("Observações:"))
    }
    @Test fun numericMessagesKeepTheEnteredDecimalSeparatorAndNeverDuplicateAlreadyTypedUnits() {
        assertEquals("10,50 ha",StockBatch.quantity("10,50","ha"))
        assertEquals("80.5 t/ha",StockBatch.quantity("80.5","t/ha"))
        assertEquals("40 hectares",StockBatch.quantity("40 hectares","ha"))
        assertEquals("1.000,5",StockBatch.quantity("1.000,5","ha"))
        val data=StockBatch.records(listOf(StockBatch.Draft("A","1","10,50","80.5")),"hoje").single()
        assertTrue(StockBatch.text(listOf(data),"Equipe","hoje").contains("Área: 10,50 ha\nProdutividade: 80.5 t/ha"))
    }
}
