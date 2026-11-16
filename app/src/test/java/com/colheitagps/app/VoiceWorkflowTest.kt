package com.colheitagps.app

import android.app.Activity
import android.app.Application
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.*
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowAlertDialog
import org.robolectric.util.ReflectionHelpers
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[28,35],manifest=Config.NONE,application=Application::class)
class VoiceWorkflowTest {
    private class Workspace(role:String="supervisor"):VoiceOperations.Host {
        override val activity=Robolectric.buildActivity(Activity::class.java).setup().get().apply {AppTheme.apply(this)}
        override var access:TeamAccess?=TeamAccess("person",mapOf("papel" to role,"nome" to "João","status" to "aprovado","frenteId" to "santo_inacio","turno" to "C"))
        override var store:TeamStore?=TeamStore(activity.applicationContext,"santo_inacio_C")
        var session="person|santo_inacio_C";var saves=0;var valid=true;var destination="";var shared:JSONObject?=null
        val records=mutableMapOf<String,JSONArray>();var images:List<PaintedMapPicker.Item> = emptyList()
        override fun scope()=session
        override fun active()=valid
        override fun read(group:String)=JSONArray((records[group]?:JSONArray()).toString())
        override fun save(group:String,records:JSONArray) {saves++;this.records[group]=JSONArray(records.toString())}
        override fun canEdit(group:String,record:JSONObject)=access!!.manages||record.optString("author")==access!!.uid
        override fun revision(group:String,id:String)=7L
        override fun navigate(tool:String) {destination=tool}
        override fun share(tool:String,image:Boolean,photos:Boolean,filters:JSONObject) {shared=filters}
        override fun maps()=emptyList<VoiceOperations.VoiceMap>()
        override fun paintedMaps()=images
        override fun currentMap()=""
        override fun curve(draft:VoiceCommands.Draft,ui:FieldVoice) {}
        override fun closeOrder(map:String,number:String) {}
    }
    private fun conversation(host:Workspace):FieldVoice {
        val operations=VoiceOperations(host)
        return FieldVoice(host.activity,{host.scope()},{host.active()},{false},{d,ui->operations.prepare(d,ui)},{ }).also {it.open()}
    }
    private fun rows(h:Workspace,group:String)=FieldReports.rows(h.records[group]?:JSONArray())
    @Test fun homeListeningIsOptInScopedToTheAccountAndStopsWhenTheAppPauses() {
        val h=Workspace();val voice=conversation(h)
        assertFalse(voice.enabled)
        Shadows.shadowOf(RuntimeEnvironment.getApplication()).grantPermissions(android.Manifest.permission.RECORD_AUDIO)
        voice.enable();assertTrue(voice.enabled)
        ReflectionHelpers.setField(voice,"listening",true);voice.pause()
        assertFalse(ReflectionHelpers.getField<Boolean>(voice,"listening"));assertTrue(voice.enabled)
        h.session="another|colorado_A";assertFalse(voice.enabled)
        h.session="person|santo_inacio_C";assertTrue(voice.enabled);voice.destroy()
    }
    @Test fun hearingAnIssueDoesNotWriteAndConfirmationSavesTheCorrectedNumberAndPriority() {
        val h=Workspace();val voice=conversation(h)
        voice.receive("DM AGRO colhedora 22282 deu problema no rolamento")
        assertEquals(0,h.saves)
        voice.receive("não, é 22283");voice.receive("coloca como urgente");assertEquals(0,h.saves)
        voice.receive("confirmar")
        val row=rows(h,"pendencias").single();assertEquals("22283",row.getString("equipamento"));assertTrue(row.getBoolean("urgente"));assertTrue(row.getString("descricao").contains("rolamento"));assertEquals(1,h.saves)
        voice.destroy()
    }
    @Test fun routineCommandsNeverCreateMaintenanceIssues() {
        val h=Workspace();val voice=conversation(h)
        voice.receive("DM AGRO colhedora 22282 parou para abastecer");voice.receive("confirmar")
        assertTrue(rows(h,"pendencias").isEmpty());assertEquals("rotina",rows(h,"ocorrencias_campo").single().getString("categoriaRegistro"));voice.destroy()
    }
    @Test fun updatingStockKeepsItsIdentityAndAddsTheSavedPaintedMapOnlyAfterConfirmation() {
        val h=Workspace();h.records["estoques_salvos"]=JSONArray().put(JSONObject().put("id","saved").put("os","K/39").put("talhoes","1 a 10").put("areaRestante","20").put("produtividade","70"))
        val image=File(h.store!!.files,"mapa-teste.png").apply {writeText("local picture")};h.images=listOf(PaintedMapPicker.Item(image,"K-39 Parte 01 - 26","16/11/2026"))
        val voice=conversation(h);voice.receive("DM AGRO estoque do K/39 área 40 hectares produtividade 80 coloque o mapa pintado")
        assertEquals(0,h.saves);voice.receive("confirmar")
        val saved=rows(h,"estoques_salvos").single();assertEquals("saved",saved.getString("id"));assertEquals("40",saved.getString("areaRestante"));assertEquals("1 a 10",saved.getString("talhoes"));assertEquals(image.path,saved.getString("fotoEstoque"));assertEquals(7L,saved.getLong("__syncBase"));voice.destroy()
    }
    @Test fun anotherTeamOrAChangedRecordCannotReceiveThePreparedWrite() {
        val h=Workspace();h.records["estoques_salvos"]=JSONArray().put(JSONObject().put("id","saved").put("os","K/39").put("talhoes","9").put("areaRestante","20").put("produtividade","70"))
        var voice=conversation(h);voice.receive("estoque do K/39 área 40 produtividade 80")
        h.records["estoques_salvos"]!!.getJSONObject(0).put("areaRestante","90");voice.receive("confirmar")
        assertEquals(0,h.saves);assertEquals("90",rows(h,"estoques_salvos").single().getString("areaRestante"));voice.destroy()
        voice=conversation(h);voice.receive("colhedora 22282 precisa trocar o pneu");h.session="person|colorado_C";voice.receive("confirmar")
        assertEquals(0,h.saves);voice.destroy()
    }
    @Test fun operatorsCannotPrepareManagerWritesOrOpenManagerToolsByVoice() {
        val h=Workspace("operador");val voice=conversation(h)
        for(command in listOf("estoque do K/39 talhão 9 área 40 produtividade 80","caminhão 10880 chegou agora","abra usuários","abra caminhões")) {voice.receive(command);voice.receive("confirmar")}
        assertEquals(0,h.saves);assertEquals("",h.destination);voice.destroy()
    }
    @Test fun resumingWorkRequiresChoosingOneIssueAndLeavesTheOtherIssuesOpen() {
        val h=Workspace();h.records["pendencias"]=JSONArray().put(JSONObject().put("id","bearing").put("equipamento","22282").put("descricao","Trocar rolamento"))
            .put(JSONObject().put("id","light").put("equipamento","22282").put("descricao","Trocar lâmpada"))
        val voice=conversation(h);voice.receive("DM AGRO colhedora 22282 voltou a trabalhar");assertEquals(0,h.saves)
        voice.receive("o rolamento");voice.receive("confirmar")
        assertTrue(rows(h,"pendencias").single {it.optString("id")=="bearing"}.getBoolean("concluida"));assertFalse(rows(h,"pendencias").single {it.optString("id")=="light"}.optBoolean("concluida"));voice.destroy()
    }
    @Test fun aRainReadingRequiresChoosingAccumulatedOrAdditionalInsteadOfGuessing() {
        val h=Workspace();val day=java.time.LocalDate.now()
        h.records["ocorrencias_campo"]=JSONArray().put(RainRecords.create(TruckRecords.label(day),"10","Observação preservada","santo_inacio_C","original-author","Maria"))
        val voice=conversation(h);voice.receive("DM AGRO choveu vinte milímetros");assertEquals(0,h.saves)
        voice.receive("acumulado");voice.receive("confirmar")
        val row=rows(h,"ocorrencias_campo").single();assertEquals("20",row.getString("chuvaMm"));assertEquals("original-author",row.getString("registradoPorUid"));assertEquals("Observação preservada",row.getString("observacaoChuva"));voice.destroy()
    }
    @Test fun aShareCommandCarriesEquipmentUrgencyAndNoPhotoFiltersToTheExistingSharingFlow() {
        val h=Workspace();val voice=conversation(h)
        voice.receive("DM AGRO mande as pendências urgentes do equipamento 22282 sem foto");assertNull(h.shared);voice.receive("confirmar")
        assertEquals("22282",h.shared!!.getString("equipment"));assertTrue(h.shared!!.getBoolean("onlyUrgent"));assertFalse(h.shared!!.getBoolean("photos"));assertEquals(0,h.saves);voice.destroy()
    }
    @Test fun duplicateOpenTruckEntriesRequireChoosingTheExactTripBeforeExit() {
        val h=Workspace();val day=java.time.LocalDate.now().minusDays(1)
        val a=TruckRecords.build(null,"10880","23:40","",day,null,"santo_inacio_C");val b=TruckRecords.build(null,"10880","23:50","",day,null,"santo_inacio_C")
        h.records["caminhoes"]=JSONArray().put(a).put(b)
        val voice=conversation(h);voice.receive("DM AGRO caminhão 10880 saindo agora");assertEquals(0,h.saves)
        voice.receive("segunda");voice.receive("confirmar")
        assertEquals("",rows(h,"caminhoes").single {it.optString("id")==a.getString("id")}.optString("saida"));assertTrue(rows(h,"caminhoes").single {it.optString("id")==b.getString("id")}.optString("saida").isNotBlank());voice.destroy()
    }
    @Test fun theActualMainActivityUsesTheSamePermissionsAndKeepsVoiceOffUntilOptIn() {
        val main=Robolectric.buildActivity(MainActivity::class.java).get();AppTheme.apply(main)
        ReflectionHelpers.setField(main,"teamAccess",TeamAccess("op",mapOf("nome" to "João","papel" to "operador","status" to "aprovado","frenteId" to "santo_inacio","turno" to "C")))
        ReflectionHelpers.setField(main,"teamStore",TeamStore(main.applicationContext,"santo_inacio_C"));ReflectionHelpers.setField(main,"papelAtual","operador");ReflectionHelpers.setField(main,"acessoConfirmado",true)
        val voice=main.javaClass.getDeclaredMethod("voz").apply {isAccessible=true}.invoke(main) as FieldVoice
        assertFalse(voice.enabled);voice.open();voice.receive("DM AGRO colhedora 22282 deu problema no rolamento");assertEquals("[]",main.getSharedPreferences("registros_v3",0).getString("pendencias","[]"));voice.receive("confirmar")
        val row=MaintenanceRecords.issues(JSONArray(main.getSharedPreferences("registros_v3",0).getString("pendencias","[]"))).getJSONObject(0)
        assertEquals("22282",row.getString("equipamento"));assertEquals("op",row.getString("atualizadoPorUid"));voice.destroy()
    }
}
