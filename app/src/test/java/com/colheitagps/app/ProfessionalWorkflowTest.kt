package com.colheitagps.app

import android.app.Application
import android.content.Intent
import android.os.Looper
import android.view.View
import android.view.ViewGroup
import android.widget.CheckBox
import android.widget.EditText
import android.widget.TextView
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowAlertDialog
import org.robolectric.util.ReflectionHelpers
import java.time.LocalDateTime

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[28,35],manifest=Config.NONE,application=Application::class)
class ProfessionalWorkflowTest {
    private val app get()=RuntimeEnvironment.getApplication()
    private fun views(v:View):List<View> = listOf(v)+(if(v is ViewGroup) (0 until v.childCount).flatMap {views(v.getChildAt(it))} else emptyList())
    private fun activity():MainActivity {
        val a=Robolectric.buildActivity(MainActivity::class.java).get();AppTheme.apply(a)
        ReflectionHelpers.setField(a,"teamStore",TeamStore(app,"santo_inacio_C"))
        ReflectionHelpers.setField(a,"teamAccess",TeamAccess("sup",mapOf("nome" to "João","papel" to "supervisor","status" to "aprovado","frenteId" to "santo_inacio","turno" to "C")))
        ReflectionHelpers.setField(a,"papelAtual","supervisor");ReflectionHelpers.setField(a,"acessoConfirmado",true)
        return a
    }
    @Test fun draftAndHomePreferencesAreIsolatedByAccountAndTeamAndOrderingNeverGrantsATool() {
        val draft=FieldDrafts(app,"santo_inacio_C","one");draft.write("novo",JSONObject().put("descricao","Revisar braço"))
        assertEquals("Revisar braço",FieldDrafts(app,"santo_inacio_C","one").read("novo")!!.getString("descricao"))
        assertNull(FieldDrafts(app,"santo_inacio_C","two").read("novo"));assertNull(FieldDrafts(app,"colorado_C","one").read("novo"))
        val home=HomeArrangement(app,"santo_inacio_C","one");home.save(listOf("Estoque","Pendências","Mapas"));home.toggle("Mapas")
        assertEquals(listOf("Mapas","Pendências"),home.order(listOf("Mapas","Pendências")))
        assertEquals(listOf("Pendências","Mapas"),HomeArrangement(app,"santo_inacio_C","two").order(listOf("Pendências","Mapas")))
        draft.clear("novo");assertNull(draft.read("novo"))
    }
    @Test fun correctionsPreserveMetadataAndOnlyRecordRealBusinessChangesWithoutMediaPaths() {
        val old=JSONObject().put("id","1").put("descricao","Antes").put("foto","/private/old.jpg")
        val created=RecordEdits.stamp(JSONArray(),JSONArray().put(old),"uid","João","15/11/2026").getJSONObject(0)
        val edited=JSONObject(created.toString()).put("descricao","Depois").put("foto","/private/new.jpg")
        val result=RecordEdits.stamp(JSONArray().put(created),JSONArray().put(edited),"uid","Maria","16/11/2026").getJSONObject(0)
        val text=RecordEdits.text(result);assertTrue(text.contains("Maria"));assertTrue(text.contains("Antes → Depois"));assertFalse(text.contains("/private/"))
        assertEquals(1,result.getJSONArray("historicoAlteracoes").length())
        val unchanged=RecordEdits.stamp(JSONArray().put(result),JSONArray().put(JSONObject(result.toString()).put("__syncBase",4)),"uid","Outra pessoa","17/11/2026").getJSONObject(0)
        assertEquals("Maria",unchanged.getString("atualizadoPorNome"));assertEquals(result.getJSONArray("historicoAlteracoes").toString(),unchanged.getJSONArray("historicoAlteracoes").toString())
        assertFalse(TeamContent.textOnly(result).getJSONArray("historicoAlteracoes").toString().contains("/private/"))
    }
    @Test fun stockValidationAcceptsBrazilianNumbersAndFindsExistingLotsWithoutModifyingThem() {
        val row=JSONObject().put("id","new").put("os","k39").put("talhoes","1 a 5").put("areaRestante","10,5").put("produtividade","80")
        FieldWorkflow.requireStock(row)
        val saved=JSONObject(row.toString()).put("id","old").put("os","K/39");val all=JSONArray().put(saved)
        assertEquals("old",FieldWorkflow.matches(all,row).single().getString("id"))
        for(bad in listOf("abc","NaN","-1","1,2,3","1.000,5")) {try {FieldWorkflow.requireStock(JSONObject(row.toString()).put("areaRestante",bad));fail(bad)} catch(_:IllegalArgumentException) {}}
        assertEquals("K/39",all.getJSONObject(0).getString("os"))
    }
    @Test fun pieceStatesRemainLinkedToTheRightIssueAndWaitTimesRespectTheCutoffAndMidnight() {
        val all=JSONArray().put(JSONObject().put("id","part").put("categoriaRegistro","peca").put("pendenciaId","issue").put("situacaoPeca","pedido"))
        assertTrue(FieldWorkflow.partsLabel(all,"issue").contains("pedida"));assertEquals("",FieldWorkflow.partsLabel(all,"other"))
        all.getJSONObject(0).put("situacaoPeca","recebido");assertTrue(FieldWorkflow.partsLabel(all,"issue").contains("recebida"))
        all.getJSONObject(0).put("__arquivado",true);assertEquals("",FieldWorkflow.partsLabel(all,"issue"))
        val truck=JSONObject().put("dataEntrada","14/11/2026").put("chegada","23:40").put("saida","")
        assertEquals(40L,FieldWorkflow.waitMinutes(truck,LocalDateTime.of(2026,11,15,0,20)))
        assertNull(FieldWorkflow.waitMinutes(truck,LocalDateTime.of(2026,11,14,23,0)))
        truck.put("saida","00:30");assertNull(FieldWorkflow.waitMinutes(truck,LocalDateTime.of(2026,11,15,1,0)))
    }
    @Test fun aPendingDraftSurvivesClosingTheFormAndOnlyTheExplicitSaveCreatesTheRainJob() {
        val a=activity()
        fun form() {a.javaClass.getDeclaredMethod("editarPendencia",Int::class.javaPrimitiveType,String::class.java).apply {isAccessible=true}.invoke(a,-1,"")}
        form();var dialog=ShadowAlertDialog.getLatestAlertDialog()
        var fields=views(dialog.window!!.decorView).filterIsInstance<EditText>()
        fields[0].setText("21571");fields[2].setText("Revisar braço na chuva")
        views(dialog.window!!.decorView).filterIsInstance<CheckBox>().single {it.text=="Pode ser feito na parada de chuva"}.isChecked=true
        dialog.getButton(android.app.AlertDialog.BUTTON_NEGATIVE).performClick()
        assertEquals("[]",a.getSharedPreferences("registros_v3",0).getString("pendencias","[]"))
        form();dialog=ShadowAlertDialog.getLatestAlertDialog();fields=views(dialog.window!!.decorView).filterIsInstance<EditText>()
        assertEquals("21571",fields[0].text.toString());assertEquals("Revisar braço na chuva",fields[2].text.toString())
        dialog.getButton(android.app.AlertDialog.BUTTON_POSITIVE).performClick()
        val records=MaintenanceRecords.issues(JSONArray(a.getSharedPreferences("registros_v3",0).getString("pendencias","[]")))
        assertEquals(1,records.length());assertTrue(records.getJSONObject(0).getBoolean("paradaChuva"))
        assertNull(FieldDrafts(app,"santo_inacio_C","sup").read("pendencia_nova_"))
    }
    @Test fun previewRequiresConfirmationAndRechecksTheSessionBeforeOpeningAnotherApp() {
        val a=activity();val intent=Intent(Intent.ACTION_SEND).apply {type="text/plain";putExtra(Intent.EXTRA_TEXT,"Equipamento 21571\nRevisar braço")}
        var allowed=true;var sent=false
        SharePreview.review(a,intent,{allowed}) {sent=true}
        assertFalse(sent);val preview=ShadowAlertDialog.getLatestAlertDialog()
        assertTrue(views(preview.window!!.decorView).filterIsInstance<TextView>().any {it.text.contains("Revisar braço")})
        allowed=false;preview.getButton(android.app.AlertDialog.BUTTON_POSITIVE).performClick();shadowOf(Looper.getMainLooper()).idle();assertFalse(sent)
        allowed=true;SharePreview.review(a,intent,{allowed}) {sent=true}
        ShadowAlertDialog.getLatestAlertDialog().getButton(android.app.AlertDialog.BUTTON_POSITIVE).performClick();shadowOf(Looper.getMainLooper()).idle();assertTrue(sent)
    }
    @Test fun stockDraftIsNotPublishedEarlyAndUpdatingAnExistingLotPreservesItsIdentity() {
        val a=activity()
        fun form() {a.javaClass.getDeclaredMethod("editarProgressoColheita",Boolean::class.javaPrimitiveType).apply {isAccessible=true}.invoke(a,true);shadowOf(Looper.getMainLooper()).idle()}
        form();var dialog=ShadowAlertDialog.getLatestAlertDialog();var fields=views(dialog.window!!.decorView).filterIsInstance<EditText>()
        listOf("K/39","1 a 10","10,5","80").forEachIndexed {i,value->fields[i].setText(value)}
        assertEquals("[]",a.getSharedPreferences("registros_v3",0).getString("estoques_salvos","[]"))
        dialog.getButton(android.app.AlertDialog.BUTTON_NEGATIVE).performClick();shadowOf(Looper.getMainLooper()).idle()
        val existing=JSONObject().put("id","original").put("os","K/39").put("talhoes","1").put("areaRestante","20").put("produtividade","70")
        a.getSharedPreferences("registros_v3",0).edit().putString("estoques_salvos",JSONArray().put(existing).toString()).commit()
        form();dialog=ShadowAlertDialog.getLatestAlertDialog();fields=views(dialog.window!!.decorView).filterIsInstance<EditText>()
        assertEquals("10,5",fields[2].text.toString())
        dialog.getButton(android.app.AlertDialog.BUTTON_POSITIVE).performClick();shadowOf(Looper.getMainLooper()).idle()
        val choice=ShadowAlertDialog.getLatestAlertDialog();choice.listView.performItemClick(null,0,0);shadowOf(Looper.getMainLooper()).idle()
        val rows=JSONArray(a.getSharedPreferences("registros_v3",0).getString("estoques_salvos","[]"))
        assertEquals(1,rows.length());assertEquals("original",rows.getJSONObject(0).getString("id"));assertEquals("10,5",rows.getJSONObject(0).getString("areaRestante"))
        assertTrue(rows.getJSONObject(0).getJSONArray("historicoAlteracoes").length()>0)
    }
    @Test fun aTruckDraftSurvivesTheBackButtonWithoutCreatingAnOperationalEntry() {
        val a=activity()
        fun form() {a.javaClass.getDeclaredMethod("editarCaminhao",Int::class.javaPrimitiveType).apply {isAccessible=true}.invoke(a,-1);shadowOf(Looper.getMainLooper()).idle()}
        form();var dialog=ShadowAlertDialog.getLatestAlertDialog();val fields=views(dialog.window!!.decorView).filterIsInstance<EditText>()
        fields[0].setText("00123");fields[1].setText("01:00");dialog.cancel();shadowOf(Looper.getMainLooper()).idle()
        assertEquals("[]",a.getSharedPreferences("registros_v3",0).getString("caminhoes","[]"))
        form();dialog=ShadowAlertDialog.getLatestAlertDialog()
        assertEquals("00123",views(dialog.window!!.decorView).filterIsInstance<EditText>()[0].text.toString())
        dialog.getButton(android.app.AlertDialog.BUTTON_POSITIVE).performClick();shadowOf(Looper.getMainLooper()).idle()
        assertEquals(1,JSONArray(a.getSharedPreferences("registros_v3",0).getString("caminhoes","[]")).length())
        assertNull(FieldDrafts(app,"santo_inacio_C","sup").read("caminhao_novo"))
    }
    @Test fun aPartCanBeLinkedThroughTheFormAndItsStatusAppearsOnOnlyThatIssue() {
        val a=activity();val pending=JSONObject().put("id","p1").put("equipamento","21571").put("descricao","Braço quebrado")
        a.getSharedPreferences("registros_v3",0).edit().putString("pendencias",JSONArray().put(pending).toString()).commit()
        a.javaClass.getDeclaredMethod("editarPeca",String::class.java,Boolean::class.javaPrimitiveType).apply {isAccessible=true}.invoke(a,null,false);shadowOf(Looper.getMainLooper()).idle()
        val form=ShadowAlertDialog.getLatestAlertDialog();val fields=views(form.window!!.decorView).filterIsInstance<EditText>()
        fields[0].setText("P001");fields[1].setText("Pino do braço")
        views(form.window!!.decorView).filterIsInstance<TextView>().single {it.text=="Vincular a uma pendência"}.performClick()
        ShadowAlertDialog.getLatestAlertDialog().listView.performItemClick(null,0,0);shadowOf(Looper.getMainLooper()).idle()
        assertEquals("21571",fields[3].text.toString())
        form.getButton(android.app.AlertDialog.BUTTON_POSITIVE).performClick();shadowOf(Looper.getMainLooper()).idle()
        val all=JSONArray(a.getSharedPreferences("registros_v3",0).getString("pendencias","[]"))
        assertEquals("p1",PartsRecords.rows(all).getJSONObject(0).getString("pendenciaId"));assertTrue(FieldWorkflow.partsLabel(all,"p1").contains("a pedir"))
        assertEquals("",FieldWorkflow.partsLabel(all,"outro"))
    }

    @Test fun theRainFilterAlsoLimitsTheEquipmentDetailAndNeverHidesUnselectedRecordsFromStorage() {
        val a=activity()
        val rows=JSONArray().put(JSONObject().put("id","rain").put("equipamento","21571").put("descricao","SERVICO_CHUVA").put("paradaChuva",true))
            .put(JSONObject().put("id","other").put("equipamento","21571").put("descricao","SERVICO_NORMAL"))
        a.getSharedPreferences("registros_v3",0).edit().putString("pendencias",rows.toString()).commit()
        ReflectionHelpers.setField(a,"filtroParadaChuva",true)
        a.javaClass.getDeclaredMethod("pendenciasEquipamento",String::class.java,Boolean::class.javaPrimitiveType).apply {isAccessible=true}.invoke(a,"21571",false)
        val text=views(a.window.decorView).filterIsInstance<TextView>().joinToString {it.text.toString()}
        assertTrue(text.contains("SERVICO_CHUVA"));assertFalse(text.contains("SERVICO_NORMAL"))
        assertEquals(2,MaintenanceRecords.issues(JSONArray(a.getSharedPreferences("registros_v3",0).getString("pendencias","[]"))).length())
    }

    @Test fun aFormFromAnotherTeamCannotSaveIntoTheNewTeamAfterASessionChange() {
        val a=activity()
        a.javaClass.getDeclaredMethod("editarPendencia",Int::class.javaPrimitiveType,String::class.java).apply {isAccessible=true}.invoke(a,-1,"")
        val dialog=ShadowAlertDialog.getLatestAlertDialog();val fields=views(dialog.window!!.decorView).filterIsInstance<EditText>()
        fields[0].setText("21571");fields[2].setText("Rascunho da equipe original")
        ReflectionHelpers.setField(a,"teamStore",TeamStore(app,"colorado_C"))
        dialog.getButton(android.app.AlertDialog.BUTTON_POSITIVE).performClick();shadowOf(Looper.getMainLooper()).idle()
        assertEquals("[]",TeamStore(app,"colorado_C").prefs("registros_v3").getString("pendencias","[]"))
        assertEquals("[]",TeamStore(app,"santo_inacio_C").prefs("registros_v3").getString("pendencias","[]"))
    }

}
