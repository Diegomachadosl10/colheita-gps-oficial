package com.colheitagps.app

import android.app.AlertDialog
import android.app.Application
import android.os.Looper
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.TextView
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowAlertDialog
import org.robolectric.util.ReflectionHelpers
import java.time.LocalDate
import java.time.LocalDateTime
import java.math.BigDecimal

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[28,35], manifest=Config.NONE, application=Application::class)
class ProfessionalReportsTest {
    private val now=LocalDateTime.of(2026,11,27,3,10)
    private fun rejected(action:()->Unit) {try {action();fail("Should reject invalid report")} catch(_:IllegalArgumentException) {}}
    private fun views(v:View):List<View> = listOf(v)+(if(v is ViewGroup) (0 until v.childCount).flatMap {views(v.getChildAt(it))} else emptyList())
    private fun activity(manager:Boolean=true):MainActivity {
        val a=Robolectric.buildActivity(MainActivity::class.java).get();a.setTheme(android.R.style.Theme_Material_Light_NoActionBar)
        val access=TeamAccess("one",mapOf("nome" to "Responsável de teste","papel" to if(manager) "supervisor" else "operador","status" to "aprovado","frenteId" to "santo_inacio","turno" to "C"))
        ReflectionHelpers.setField(a,"teamStore",TeamStore(a.applicationContext,"santo_inacio_C"))
        ReflectionHelpers.setField(a,"teamAccess",access);ReflectionHelpers.setField(a,"papelAtual",access.role);ReflectionHelpers.setField(a,"acessoConfirmado",true)
        return a
    }
    @Test fun anOvernightReportPreservesItsOperationalDateAuthorPhotosAndIdentityWhenEdited() {
        val date=ShiftHandover.latest("C",now).date
        assertEquals(LocalDate.of(2026,11,26),date)
        val first=ShiftReports.save(null,ShiftReports.Draft(date,"Colheita no K/39","Trocar rolamento","K/39"),"santo_inacio_C","one","Autor",listOf("@arquivo/fotos/a.jpg"),now)
        val edited=ShiftReports.save(first,ShiftReports.Draft(date,"Parada às 02:30","","K/39"),"santo_inacio_C","supervisor","Gestor",listOf("@arquivo/fotos/a.jpg"),now.plusMinutes(2))
        assertEquals(first.getString("id"),edited.getString("id"));assertEquals("one",edited.getString("registradoPorUid"))
        assertEquals("Autor",edited.getString("registradoPorNome"));assertEquals(first.getString("criadoEm"),edited.getString("criadoEm"))
        assertEquals("26/11/2026",edited.getString("dataCadastro"));assertEquals("",edited.getString("pendencias"))
        assertEquals(first.getJSONArray("fotos").toString(),edited.getJSONArray("fotos").toString())
        assertEquals("Colheita no K/39",first.getString("descricao"))
        val text=ShiftReports.text("santo_inacio_C",edited)
        assertTrue(text.contains("Responsável: Autor"));assertTrue(text.contains("Data operacional: 26/11/2026"));assertTrue(text.contains("Parada às 02:30"))
        rejected {ShiftReports.save(RecordArchive.withState(edited,true),ShiftReports.Draft(date,"x"),"santo_inacio_C","one","",emptyList(),now)}
        rejected {ShiftReports.save(null,ShiftReports.Draft(now.toLocalDate().plusDays(1),"x"),"santo_inacio_C","one","",emptyList(),now)}
        rejected {ShiftReports.save(null,ShiftReports.Draft(date," "),"santo_inacio_C","one","",emptyList(),now)}
        rejected {ShiftReports.save(null,ShiftReports.Draft(date,"x"),"santo_inacio_C","one","",List(13) {"$it.jpg"},now)}
    }
    @Test fun indicatorsExcludeArchivedPartsRainAndNonNumericFreeTextWithoutInventingZeroes() {
        val maintenance=JSONArray()
        MaintenanceRecords.ensureEquipment(maintenance,"22071");MaintenanceRecords.ensureEquipment(maintenance,"22072")
        maintenance.put(JSONObject().put("id","p1").put("equipamento","22071").put("descricao","Vazamento").put("urgente",true))
        maintenance.put(JSONObject().put("id","p2").put("equipamento","999").put("concluida",true))
        maintenance.put(JSONObject().put("id","p3").put("equipamento","22071").put("__arquivado",true))
        val stock=JSONArray().put(JSONObject().put("areaRestante","40 ha").put("produtividade","80 t/ha"))
            .put(JSONObject().put("areaRestante","20,5").put("produtividade","100"))
            .put(JSONObject().put("areaRestante","restam 15 ou 20").put("produtividade","80 a 100"))
            .put(JSONObject().put("areaRestante","300").put("produtividade","80").put("__arquivado",true))
        val result=OperationalIndicators.build(maintenance,stock,JSONArray().put(JSONObject().put("descricao","Buraco")))
        assertEquals(2,result.equipment);assertEquals(1,result.pending);assertEquals(1,result.urgent)
        assertEquals(3,result.lots);assertEquals(2,result.numericLots);assertEquals(BigDecimal("60.5"),result.stockArea)
        assertEquals(BigDecimal("86.78"),result.weightedYield);assertEquals(1,result.occurrences)
        val empty=OperationalIndicators.build(JSONArray(),JSONArray(),JSONArray())
        assertNull(empty.stockArea);assertNull(empty.weightedYield)
        for(raw in listOf("20 a 30","1.234,5","-3","1e3","20 toneladas","40 + 20")) assertNull(OperationalIndicators.number(raw,"ha"))
    }
    @Test fun theReportFormActuallyPersistsEditsAndKeepsPhotosUntilExplicitlyRemoved() {
        val a=activity()
        val original=ShiftReports.save(null,ShiftReports.Draft(LocalDate.of(2026,11,26),"Relato anterior"),"santo_inacio_C","one","Autor",listOf("@arquivo/fotos/local.jpg"),now)
        a.getSharedPreferences("registros_v3",0).edit().putString(ShiftReports.GROUP,JSONArray().put(original).toString()).commit()
        a.javaClass.getDeclaredMethod("editarRelatorioTurno",Int::class.javaPrimitiveType).apply {isAccessible=true}.invoke(a,0)
        shadowOf(Looper.getMainLooper()).idle()
        val dialog=ShadowAlertDialog.getLatestAlertDialog();val fields=views(dialog.window!!.decorView).filterIsInstance<EditText>()
        fields[0].setText("Relato corrigido");fields[1].setText("Conferir talhão 11")
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).performClick()
        shadowOf(Looper.getMainLooper()).idle()
        val saved=JSONArray(a.getSharedPreferences("registros_v3",0).getString(ShiftReports.GROUP,"[]")).getJSONObject(0)
        assertEquals("Relato corrigido",saved.getString("descricao"));assertEquals("Conferir talhão 11",saved.getString("pendencias"))
        assertEquals(original.getString("id"),saved.getString("id"));assertEquals(original.getJSONArray("fotos").toString(),saved.getJSONArray("fotos").toString())
        ReflectionHelpers.callInstanceMethod<Unit>(a,"relatoriosTurno")
        assertTrue(views(a.window.decorView).filterIsInstance<TextView>().any {it.text.toString().contains("Relato corrigido")})
    }
    @Test fun operatorCannotOpenTheManagerReportForm() {
        val a=activity(false)
        a.javaClass.getDeclaredMethod("editarRelatorioTurno",Int::class.javaPrimitiveType).apply {isAccessible=true}.invoke(a,-1)
        assertNull(a.getSharedPreferences("registros_v3",0).getString(ShiftReports.GROUP,null))
        assertTrue(views(a.window.decorView).filterIsInstance<EditText>().isEmpty())
    }
}
