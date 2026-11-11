package com.colheitagps.app

import android.app.AlertDialog
import android.app.Application
import android.os.Looper
import android.view.*
import android.widget.*
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
class ClosedOrderTest {
    private fun activity(role:String="supervisor"):MainActivity {
        val a=Robolectric.buildActivity(MainActivity::class.java).get();a.setTheme(android.R.style.Theme_Material_Light_NoActionBar)
        ReflectionHelpers.setField(a,"teamAccess",TeamAccess("sup",mapOf("nome" to "Pessoa","papel" to role,"status" to "aprovado","frenteId" to "santo_inacio","turno" to "C")))
        ReflectionHelpers.setField(a,"teamStore",TeamStore(a.applicationContext,"santo_inacio_C"));ReflectionHelpers.setField(a,"papelAtual",role);ReflectionHelpers.setField(a,"acessoConfirmado",true)
        a.getSharedPreferences("mapas",0).edit().putString("mapa","Fazenda Santo Inácio").commit()
        a.getSharedPreferences("ordens_servico",0).edit().putString("mapa","[\"K/05\",\"K/10\"]").commit()
        a.getSharedPreferences("talhoes_por_os",0).edit().putString("mapa_K/05","17 ao 21, 22 ao 68").putString("mapa_K/10","1 ao 5").commit()
        return a
    }
    private fun call(a:MainActivity,name:String,vararg args:Any?) {a.javaClass.declaredMethods.first {it.name==name}.apply {isAccessible=true}.invoke(a,*args);shadowOf(Looper.getMainLooper()).idle()}
    private fun confirm() {ShadowAlertDialog.getLatestAlertDialog().getButton(AlertDialog.BUTTON_POSITIVE).performClick();shadowOf(Looper.getMainLooper()).idle()}
    private fun history(a:MainActivity)=JSONArray(a.getSharedPreferences("registros_v3",0).getString(OrderRecords.GROUP,"[]"))
    private fun views(v:View):List<View> = listOf(v)+(if(v is ViewGroup) (0 until v.childCount).flatMap {views(v.getChildAt(it))} else emptyList())
    @Test fun closingAnOrderKeepsItsCompleteSnapshotAndAnotherActiveOrderEvenWhenOffline() {
        val a=activity();call(a,"encerrarOrdemServico","mapa","K/05");confirm()
        val active=JSONArray(a.getSharedPreferences("ordens_servico",0).getString("mapa","[]"));assertEquals(1,active.length());assertEquals("K/10",active.getString(0))
        assertEquals("1 ao 5",a.getSharedPreferences("talhoes_por_os",0).getString("mapa_K/10",""))
        val all=history(a);assertEquals(1,all.length());val closed=all.getJSONObject(0)
        assertTrue(OrderRecords.closed(closed));assertFalse(RecordArchive.archived(closed));assertEquals("17 ao 21, 22 ao 68",closed.getString("talhoes"))
        assertEquals("K/05",closed.getString("numeroOs"));assertEquals("mapa",closed.getString("mapaId"));assertEquals("sup",closed.getString("encerradaPorUid"));assertTrue(closed.getString("encerradaEm").isNotBlank())
        call(a,"encerrarOrdemServico","mapa","K/05");confirm();assertEquals(1,history(a).length())
        val report=FieldReports.group("Histórico",OrderRecords.GROUP,listOf(closed)).rows.flatten().joinToString()
        assertTrue(report.contains("17 ao 21, 22 ao 68"));assertTrue(report.contains("Encerrada em"))
    }
    @Test fun reopeningPreservesTheClosedSnapshotAndNeverOverwritesAnExistingOrder() {
        val a=activity();call(a,"encerrarOrdemServico","mapa","K/05");confirm();val closed=history(a).getJSONObject(0)
        a.getSharedPreferences("ordens_servico",0).edit().putString("mapa","[\"K/05\",\"K/10\"]").commit()
        a.getSharedPreferences("talhoes_por_os",0).edit().putString("mapa_K/05","90 ao 100").commit()
        call(a,"restaurarOrdemServico",closed.getString("id"));confirm()
        val dialog=ShadowAlertDialog.getLatestAlertDialog();assertTrue(dialog.isShowing);assertTrue(OrderRecords.closed(history(a).getJSONObject(0)))
        assertEquals("90 ao 100",a.getSharedPreferences("talhoes_por_os",0).getString("mapa_K/05",""))
        views(dialog.window!!.decorView).filterIsInstance<EditText>()[0].setText("K/06");confirm()
        val reopened=history(a).getJSONObject(0);assertEquals(closed.getString("id"),reopened.getString("id"));assertEquals("reaberta",reopened.getString("statusOs"))
        assertEquals("K/05",reopened.getString("numeroOs"));assertEquals("K/06",reopened.getString("reabertaComo"));assertEquals("17 ao 21, 22 ao 68",reopened.getString("talhoes"))
        assertEquals("17 ao 21, 22 ao 68",a.getSharedPreferences("talhoes_por_os",0).getString("mapa_K/06",""))
    }
    @Test fun closingFromTheEditorRequiresSavingChangedFieldsInsteadOfSilentlyIgnoringThem() {
        val a=activity();call(a,"ordens","mapa")
        val menu=ShadowAlertDialog.getLatestAlertDialog().listView
        menu.performItemClick(null,0,0);shadowOf(Looper.getMainLooper()).idle()
        val editor=ShadowAlertDialog.getLatestAlertDialog()
        views(editor.window!!.decorView).filterIsInstance<EditText>()[1].setText("Talhões corrigidos")
        views(editor.window!!.decorView).filterIsInstance<TextView>().single {it.text.toString()=="Encerrar O.S."}.performClick()
        assertTrue(editor.isShowing);assertEquals(0,history(a).length())
        assertEquals("17 ao 21, 22 ao 68",a.getSharedPreferences("talhoes_por_os",0).getString("mapa_K/05",""))
    }
    @Test fun anOperatorCannotCloseOrReopenAnOrderOrReadTheManagerHistory() {
        val a=activity("operador");call(a,"encerrarOrdemServico","mapa","K/05")
        assertNull(ShadowAlertDialog.getLatestAlertDialog());assertEquals(0,history(a).length());assertTrue(a.getSharedPreferences("ordens_servico",0).getString("mapa","")!!.contains("K/05"))
        val record=OrderRecords.close("mapa","Mapa","K/05","1 ao 3","sup","Supervisor","hoje",1)
        a.getSharedPreferences("registros_v3",0).edit().putString(OrderRecords.GROUP,JSONArray().put(record).toString()).commit()
        call(a,"restaurarOrdemServico",record.getString("id"));assertEquals("encerrada",history(a).getJSONObject(0).getString("statusOs"))
    }
}
