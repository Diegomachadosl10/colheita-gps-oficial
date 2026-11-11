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

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[28],manifest=Config.NONE,application=Application::class)
class RestoreScreenTest {
    private fun views(v:View):List<View> = listOf(v)+(if(v is ViewGroup) (0 until v.childCount).flatMap {views(v.getChildAt(it))} else emptyList())
    private fun activity(role:String="supervisor"):MainActivity {
        val a=Robolectric.buildActivity(MainActivity::class.java).get();a.setTheme(android.R.style.Theme_Material_Light_NoActionBar)
        val uid=if(role=="administrador") TeamPolicy.PRIMARY_ADMIN_UID else "pessoa"
        ReflectionHelpers.setField(a,"teamAccess",TeamAccess(uid,mapOf("nome" to "Pessoa","papel" to role,"status" to "aprovado","frenteId" to "santo_inacio","turno" to "A")))
        ReflectionHelpers.setField(a,"teamStore",TeamStore(a.applicationContext,"santo_inacio_A"));ReflectionHelpers.setField(a,"papelAtual",role);ReflectionHelpers.setField(a,"acessoConfirmado",true)
        return a
    }
    private fun call(a:MainActivity,name:String,vararg args:Any) {a.javaClass.declaredMethods.first {it.name==name}.apply {isAccessible=true}.invoke(a,*args);shadowOf(Looper.getMainLooper()).idle()}
    private fun confirm() {ShadowAlertDialog.getLatestAlertDialog().getButton(AlertDialog.BUTTON_POSITIVE).performClick();shadowOf(Looper.getMainLooper()).idle()}
    private fun data(a:MainActivity,group:String)=JSONArray(a.getSharedPreferences("registros_v3",0).getString(group,"[]"))
    private fun select(label:String) {
        val list=ShadowAlertDialog.getLatestAlertDialog().listView
        val index=(0 until list.adapter.count).first {list.adapter.getItem(it).toString()==label}
        list.performItemClick(null,index,index.toLong());shadowOf(Looper.getMainLooper()).idle()
    }
    @Test fun bothSupervisorAndPrimaryAdminCanBringAnEquipmentAndItsPhotoBackFromDeletedEquipment() {
        for(role in listOf("supervisor","administrador")) {
            val a=activity(role)
            val all=MaintenanceRecords.merge(JSONArray().put(JSONObject().put("id","p1").put("equipamento","21569").put("descricao","Pneu").put("fotos",JSONArray().put("/foto.jpg"))),JSONArray())
            a.getSharedPreferences("registros_v3",0).edit().putString("pendencias",all.toString()).commit()
            call(a,"excluirEquipamento","21569");confirm();assertTrue(MaintenanceRecords.summaries(data(a,"pendencias")).isEmpty())
            call(a,"historicoEquipamentos")
            val title=views(a.window.decorView).filterIsInstance<TextView>().single {it.text.toString()=="Equipamento 21569"};(title.parent as View).performClick()
            select("Restaurar equipamento");confirm()
            val restored=data(a,"pendencias");assertEquals("21569",MaintenanceRecords.summaries(restored).single().number)
            val issue=MaintenanceRecords.issues(restored).getJSONObject(0);assertFalse(RecordArchive.archived(issue));assertEquals("p1",issue.getString("id"));assertEquals("/foto.jpg",issue.getJSONArray("fotos").getString(0))
            assertTrue(views(a.window.decorView).filterIsInstance<TextView>().any {it.text.toString()=="+ Adicionar pendência"})
        }
    }
    @Test fun anOperatorStillEditsOwnOccurrencesButCannotDeleteOrRestoreOperationalRecords() {
        val a=activity("operador");val item=JSONObject().put("id","occ").put("tipo","Buraco").put("descricao","Carreador")
        a.getSharedPreferences("registros_v3",0).edit().putString("ocorrencias_campo",JSONArray().put(item).toString()).commit()
        call(a,"opcoesOcorrencia",0)
        val list=ShadowAlertDialog.getLatestAlertDialog().listView
        val labels=(0 until list.adapter.count).map {list.adapter.getItem(it).toString()};assertTrue(labels.contains("Editar"));assertFalse(labels.contains("Excluir"))
        val returned:()->Unit={};call(a,"remover","ocorrencias_campo",0,returned)
        assertFalse(RecordArchive.archived(data(a,"ocorrencias_campo").getJSONObject(0)))
        val deleted=RecordArchive.archive(item,"sup","Gestor","ontem")
        a.getSharedPreferences("registros_v3",0).edit().putString("ocorrencias_campo",JSONArray().put(deleted).toString()).commit()
        call(a,"confirmarRestauracao","ocorrencias_campo","occ")
        assertTrue(RecordArchive.archived(data(a,"ocorrencias_campo").getJSONObject(0)))
    }
    @Test fun stockDeletionKeepsItsPhotoAndCanBeRestoredWithoutLosingAnotherStock() {
        val a=activity();val stocks=JSONArray().put(JSONObject().put("id","stock").put("os","K/05").put("talhoes","1 a 3").put("areaRestante","10").put("produtividade","80").put("fotoEstoque","/foto-stock.jpg"))
            .put(JSONObject().put("id","other").put("os","K/10"))
        a.getSharedPreferences("registros_v3",0).edit().putString("estoques_salvos",stocks.toString()).commit()
        call(a,"abrirEstoqueSalvo",stocks.getJSONObject(0));select("Excluir estoque");confirm()
        assertEquals(2,data(a,"estoques_salvos").length());assertEquals(1,RecordArchive.active(data(a,"estoques_salvos")).length())
        call(a,"confirmarRestauracao","estoques_salvos","stock");confirm()
        val restored=data(a,"estoques_salvos");assertEquals(2,RecordArchive.active(restored).length());assertEquals("/foto-stock.jpg",restored.getJSONObject(0).getString("fotoEstoque"));assertEquals("K/10",restored.getJSONObject(1).getString("os"))
    }
    @Test fun restoringAnOrderNeverOverwritesAnExistingNumberWithDifferentFields() {
        val a=activity();val map="mapa"
        a.getSharedPreferences("ordens_servico",0).edit().putString(map,"[\"123\"]").commit()
        a.getSharedPreferences("talhoes_por_os",0).edit().putString(map+"_123","1 a 3").commit()
        call(a,"excluirOrdemServico",map,"123");confirm()
        val archived=data(a,"historico_os").getJSONObject(0);assertTrue(RecordArchive.archived(archived));assertEquals("[]",a.getSharedPreferences("ordens_servico",0).getString(map,""))
        a.getSharedPreferences("ordens_servico",0).edit().putString(map,"[\"123\"]").commit()
        a.getSharedPreferences("talhoes_por_os",0).edit().putString(map+"_123","20 a 30").commit()
        call(a,"restaurarOrdemServico",archived.getString("id"));confirm()
        val dialog=ShadowAlertDialog.getLatestAlertDialog();assertTrue(dialog.isShowing);assertEquals("20 a 30",a.getSharedPreferences("talhoes_por_os",0).getString(map+"_123",""))
        views(dialog.window!!.decorView).filterIsInstance<EditText>().first().setText("124");confirm()
        assertEquals("1 a 3",a.getSharedPreferences("talhoes_por_os",0).getString(map+"_124",""));assertFalse(RecordArchive.archived(data(a,"historico_os").getJSONObject(0)))
    }
}
