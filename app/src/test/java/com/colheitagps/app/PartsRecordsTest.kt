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
@Config(sdk=[28],manifest=Config.NONE,application=Application::class)
class PartsRecordsTest {
    private fun part()=PartsRecords.build(null," 00081-ab ","Filtro","2","21569","a_pedir","opA","João","10/11/2026")
    @Test fun partCodeRetainsLeadingZeroesAndQuantityMustBeAValidPositiveInteger() {
        val part=part(); assertEquals("00081-AB",part.getString("codigoPeca")); assertEquals(2,part.getInt("quantidadePeca"))
        assertTrue(part.getString("descricao").contains("00081-AB")); assertTrue(part.getString("descricao").contains("2 un."))
        for(value in listOf("0","-1","1,5","abc","1000000")) try {
            PartsRecords.build(null,"1","Filtro",value,"","a_pedir","op","Nome","hoje"); fail(value)
        } catch(_:IllegalArgumentException) { }
    }
    @Test fun maintenanceAndPartsSavesPreserveEachOthersRecordsAndDoNotCreatePhantomEquipment() {
        val issue=JSONObject().put("id","p1").put("equipamento","21569").put("descricao","Pneu")
        val original=MaintenanceRecords.merge(JSONArray().put(issue),JSONArray())
        val withPart=PartsRecords.merge(JSONArray().put(part()),original)
        assertEquals(3,withPart.length()); assertEquals(1,MaintenanceRecords.issues(withPart).length())
        assertEquals(listOf("21569"),MaintenanceRecords.summaries(withPart).map { it.number })
        val savedIssue=MaintenanceRecords.merge(JSONArray().put(issue.put("descricao","Pneu novo")),withPart)
        assertEquals(1,PartsRecords.rows(savedIssue).length()); assertEquals("00081-AB",PartsRecords.rows(savedIssue).getJSONObject(0).getString("codigoPeca"))
        val editedPart=part().put("quantidadePeca",4)
        val savedPart=PartsRecords.merge(JSONArray().put(editedPart),savedIssue)
        assertEquals("Pneu novo",MaintenanceRecords.issues(savedPart).getJSONObject(0).getString("descricao"))
    }
    @Test fun localPhotosSurviveEditsAndAreRemovedOnlyFromTheCloudTextPayload() {
        val original=part().put("fotos",JSONArray().put("/foto-local.jpg"))
        val edited=PartsRecords.build(original,"00081-ab","Filtro novo","3","21569","pedido","outro","Outro","amanhã")
        assertEquals(original.getString("id"),edited.getString("id")); assertEquals("opA",edited.getString("autorPecaUid"))
        assertEquals("/foto-local.jpg",edited.getJSONArray("fotos").getString(0))
        val wire=TeamContent.textOnly(edited); assertFalse(wire.has("fotos")); assertTrue(wire.getBoolean("anexosManuais"))
        assertFalse(PartsRecords.historical(edited)); assertTrue(PartsRecords.historical(JSONObject(edited.toString()).put("situacaoPeca","recebido")))
        assertTrue(PartsRecords.historical(MaintenanceRecords.archive(edited,"sup","Supervisor","hoje")))
        val shared=PartsRecords.sharingText(MaintenanceRecords.archive(edited,"sup","Supervisor","hoje"),"Santo Inácio · Turno C")
        assertTrue(shared.contains("Código: 00081-AB")); assertTrue(shared.contains("Quantidade: 3")); assertTrue(shared.contains("Excluída (histórico)"))
    }
}
