package com.colheitagps.app

import android.app.Application
import android.app.AlertDialog
import android.content.Intent
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
import org.robolectric.*
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowAlertDialog
import org.robolectric.util.ReflectionHelpers
import java.time.*

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[28,35],manifest=Config.NONE,application=Application::class)
class FieldOperationsUpgradeTest {
    private fun views(view:View):List<View> = listOf(view)+(if(view is ViewGroup) (0 until view.childCount).flatMap {views(view.getChildAt(it))} else emptyList())
    private fun activity(role:String="supervisor"):MainActivity {
        val a=Robolectric.buildActivity(MainActivity::class.java).get();AppTheme.apply(a)
        ReflectionHelpers.setField(a,"teamStore",TeamStore(a.applicationContext,"santo_inacio_C"))
        ReflectionHelpers.setField(a,"teamAccess",TeamAccess("person",mapOf("nome" to "João","papel" to role,"status" to "aprovado","frenteId" to "santo_inacio","turno" to "C")))
        ReflectionHelpers.setField(a,"papelAtual",role);ReflectionHelpers.setField(a,"acessoConfirmado",true)
        ReflectionHelpers.setField(a,"relogioCaminhoes",Clock.fixed(Instant.parse("2026-11-16T02:00:00Z"),ZoneOffset.UTC))
        return a
    }
    private fun call(a:MainActivity,name:String,vararg values:Any?) {a.javaClass.declaredMethods.first {it.name==name}.apply {isAccessible=true}.invoke(a,*values);Shadows.shadowOf(Looper.getMainLooper()).idle()}
    private fun equipment()=JSONObject().put("id",MaintenanceRecords.equipmentId("00123")).put("categoriaRegistro","equipamento").put("equipamento","00123")
    @Test fun handoverUsesTheNightShiftAcrossMidnightAndExcludesOtherPeriodsArchivedAndCompletedRecords() {
        val now=LocalDateTime.of(2026,11,16,2,0);val period=ShiftHandover.latest("C",now)
        assertEquals(LocalDate.of(2026,11,15),period.date)
        val issues=JSONArray().put(equipment()).put(JSONObject().put("id","open").put("equipamento","00123").put("descricao","Mangueira aberta"))
            .put(JSONObject().put("id","done").put("descricao","NAO_INCLUIR_CONCLUIDA").put("concluida",true))
            .put(JSONObject().put("id","deleted").put("descricao","NAO_INCLUIR_EXCLUIDA").put("__arquivado",true))
            .put(JSONObject().put("id","part").put("categoriaRegistro","peca").put("codigoPeca","P-9").put("nomePeca","Pino").put("quantidadePeca",2).put("situacaoPeca","pedido"))
            .put(JSONObject().put("id","received").put("categoriaRegistro","peca").put("nomePeca","NAO_INCLUIR_RECEBIDA").put("situacaoPeca","recebido"))
        val trucks=JSONArray().put(TruckRecords.build(null,"INCLUIR_CAMINHAO","00:10","",now.toLocalDate(),null,"santo_inacio_C"))
            .put(TruckRecords.build(null,"NAO_INCLUIR_ONTEM","00:10","",now.toLocalDate().minusDays(1),null,"santo_inacio_C"))
            .put(TruckRecords.build(null,"NAO_INCLUIR_FUTURO","03:00","",now.toLocalDate(),null,"santo_inacio_C"))
        val lots=JSONArray().put(JSONObject().put("id","stock").put("os","K/39").put("talhoes","1 a 10").put("areaRestante","10,5").put("produtividade","80"))
        val before=listOf(issues.toString(),trucks.toString(),lots.toString())
        val result=ShiftHandover.build("santo_inacio_C",period,now,issues,lots,trucks,"Conferir rádio")
        assertTrue(result.text.contains("Mangueira aberta"));assertTrue(result.text.contains("P-9"));assertTrue(result.text.contains("K/39"));assertTrue(result.text.contains("INCLUIR_CAMINHAO"));assertTrue(result.text.contains("Conferir rádio"))
        assertFalse(result.text.contains("NAO_INCLUIR"));assertTrue(result.text.contains("Entradas: 1 • Saídas: 0 • Sem saída: 1"))
        assertEquals(before,listOf(issues.toString(),trucks.toString(),lots.toString()))
        assertTrue(result.report.sections.all {it.rows.isNotEmpty()})
    }
    @Test fun latestHandoverRemainsAvailableAtEachShiftBoundaryWithoutMixingTodaysAndYesterdaysTrucks() {
        val day=LocalDate.of(2026,11,16)
        assertEquals(day.minusDays(1),ShiftHandover.latest("A",day.atTime(6,59)).date)
        assertEquals(day,ShiftHandover.latest("A",day.atTime(7,0)).date)
        assertEquals(day.minusDays(1),ShiftHandover.latest("B",day.atTime(15,19)).date)
        assertEquals(day,ShiftHandover.latest("B",day.atTime(15,20)).date)
        assertEquals(day.minusDays(1),ShiftHandover.latest("C",day.atTime(23,19)).date)
        assertEquals(day,ShiftHandover.latest("C",day.atTime(23,20)).date)
    }
    @Test fun preventiveWarningsFollowBothDateAndHourThresholdsAndBrazilianDecimalReadings() {
        val today=LocalDate.of(2026,11,16)
        val record=PreventiveMaintenance.update(equipment(),"Óleo e filtros",today.plusDays(8).toString(),"1000,5","1051")
        assertEquals(PreventiveMaintenance.State.SCHEDULED,PreventiveMaintenance.status(record,today).state)
        record.put("horimetroAtual","1001")
        assertEquals(PreventiveMaintenance.State.SOON,PreventiveMaintenance.status(record,today).state)
        record.put("horimetroAtual","1051")
        assertEquals(PreventiveMaintenance.State.DUE,PreventiveMaintenance.status(record,today).state)
        record.put("horimetroAtual","900").put("preventivaData",today.toString())
        assertEquals(PreventiveMaintenance.State.DUE,PreventiveMaintenance.status(record,today).state)
        record.put("preventivaData",today.plusDays(7).toString())
        assertEquals(PreventiveMaintenance.State.SOON,PreventiveMaintenance.status(record,today).state)
        assertEquals(PreventiveMaintenance.State.NONE,PreventiveMaintenance.status(RecordArchive.withState(record,true),today).state)
    }
    @Test fun invalidServiceSchedulesNeverChangeTheOriginalAndRenamingOrRestoringEquipmentKeepsTheSchedule() {
        val original=equipment();val before=original.toString()
        for(values in listOf(listOf("","","",""),listOf("Óleo","2026-02-31","",""),listOf("Óleo","","","200"),listOf("Óleo","","-1","200"))) {
            try {PreventiveMaintenance.update(original,values[0],values[1],values[2],values[3]);fail("Invalid schedule") } catch(_:IllegalArgumentException) {}
        }
        assertEquals(before,original.toString())
        val scheduled=PreventiveMaintenance.update(original,"Óleo","2026-12-01","100,5","200")
        val renamed=MaintenanceRecords.correctNumber(JSONArray().put(scheduled),"00123","00456","manager","Gestor","16/11/2026")
        assertEquals("200",FieldReports.rows(renamed).single {it.optString("equipamento")=="00456"}.getString("proximaRevisaoHoras"))
        val archived=MaintenanceRecords.archiveEquipment(JSONArray().put(scheduled),"00123","manager","Gestor","16/11/2026")
        val restored=MaintenanceRecords.restoreEquipment(archived,"00123",false,"manager","Gestor","16/11/2026")
        assertEquals("Óleo",restored.getJSONObject(0).getString("preventivaServico"))
    }
    @Test fun aManagerCanSaveAServiceScheduleWithoutReplacingIssuesAndTheCorrectionIsAudited() {
        val a=activity();val records=JSONArray().put(equipment()).put(JSONObject().put("id","issue").put("equipamento","00123").put("descricao","Pneu"))
        a.getSharedPreferences("registros_v3",0).edit().putString("pendencias",records.toString()).commit()
        call(a,"programarPreventiva","00123");val form=ShadowAlertDialog.getLatestAlertDialog()
        val fields=views(form.window!!.decorView).filterIsInstance<EditText>()
        fields[0].setText("Óleo e filtros");fields[1].setText("1000,5");fields[2].setText("1050")
        form.getButton(AlertDialog.BUTTON_POSITIVE).performClick();Shadows.shadowOf(Looper.getMainLooper()).idle()
        val saved=JSONArray(a.getSharedPreferences("registros_v3",0).getString("pendencias","[]"))
        assertEquals("Pneu",FieldReports.rows(saved).single {it.optString("id")=="issue"}.getString("descricao"))
        val eq=FieldReports.rows(saved).single(MaintenanceRecords::isEquipment)
        assertEquals("1000,5",eq.getString("horimetroAtual"));assertEquals("Óleo e filtros",eq.getString("preventivaServico"))
        assertTrue(RecordEdits.text(eq).contains("Próxima revisão"))
        assertTrue(views(a.window.decorView).filterIsInstance<TextView>().any {it.text.toString().contains("Revisão próxima")})
    }
    @Test fun changingTeamsWhileAServiceFormIsOpenCannotWriteIntoEitherTeam() {
        val a=activity();val rows=JSONArray().put(equipment()).toString();a.getSharedPreferences("registros_v3",0).edit().putString("pendencias",rows).commit()
        call(a,"programarPreventiva","00123");val form=ShadowAlertDialog.getLatestAlertDialog();val fields=views(form.window!!.decorView).filterIsInstance<EditText>()
        fields[0].setText("Óleo");fields[1].setText("100");fields[2].setText("200")
        ReflectionHelpers.setField(a,"teamStore",TeamStore(a.applicationContext,"colorado_C"))
        form.getButton(AlertDialog.BUTTON_POSITIVE).performClick()
        assertEquals(rows,TeamStore(a.applicationContext,"santo_inacio_C").prefs("registros_v3").getString("pendencias","[]"))
        assertEquals("[]",TeamStore(a.applicationContext,"colorado_C").prefs("registros_v3").getString("pendencias","[]"))
    }
    @Test fun handoverOffersAReadOnlyPreviewAndDoesNotSendToWhatsAppUntilTheUserChooses() {
        val a=activity();call(a,"passagemTurno",null)
        views(a.window.decorView).filterIsInstance<EditText>().single().setText("Verificar mangueira")
        views(a.window.decorView).filterIsInstance<TextView>().single {it.text=="Compartilhar mensagem"}.performClick()
        assertNull(Shadows.shadowOf(a).nextStartedActivity)
        val preview=ShadowAlertDialog.getLatestAlertDialog()
        assertTrue(views(preview.window!!.decorView).filterIsInstance<TextView>().any {it.text.contains("Verificar mangueira")})
        preview.getButton(AlertDialog.BUTTON_POSITIVE).performClick()
        Shadows.shadowOf(Looper.getMainLooper()).idle()
        val chooser=Shadows.shadowOf(a).nextStartedActivity
        assertEquals(Intent.ACTION_CHOOSER,chooser.action)
        val outgoing=chooser.getParcelableExtra<Intent>(Intent.EXTRA_INTENT)!!
        assertEquals("text/plain",outgoing.type);assertTrue(outgoing.getStringExtra(Intent.EXTRA_TEXT)!!.contains("Verificar mangueira"))
        assertEquals("[]",a.getSharedPreferences("registros_v3",0).getString("caminhoes","[]"))
        assertTrue(FieldDrafts(a.applicationContext,"santo_inacio_C","person").read("passagem_2026-11-15/C")!!.getString("recado").contains("mangueira"))
    }
    @Test fun operatorsCannotOpenTheManagerHandoverAndEveryToolHasContextualHelp() {
        val a=activity("operador");call(a,"inicio")
        assertFalse(views(a.window.decorView).filterIsInstance<TextView>().any {it.text=="Passagem de turno"})
        call(a,"passagemTurno",null)
        assertFalse(views(a.window.decorView).filterIsInstance<TextView>().any {it.text=="Compartilhar mensagem"})
        call(a,"pendencias")
        views(a.window.decorView).single {it.contentDescription=="Ajuda desta tela"}.performClick()
        val text=views(ShadowAlertDialog.getLatestAlertDialog().window!!.decorView).filterIsInstance<TextView>().joinToString {it.text.toString()}
        assertTrue(text.contains("parada de chuva"));assertTrue(text.contains("Compartilhe"))
    }
}
