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
class StockBatchScreenTest {
    private fun views(v:View):List<View> = listOf(v)+(if(v is ViewGroup) (0 until v.childCount).flatMap { views(v.getChildAt(it)) } else emptyList())
    private fun activity(role:String):MainActivity {
        val a=Robolectric.buildActivity(MainActivity::class.java).get()
        ReflectionHelpers.setField(a,"teamAccess",TeamAccess("pessoa",mapOf("nome" to "Teste","papel" to role,"status" to "aprovado","frenteId" to "santo_inacio","turno" to "C")))
        ReflectionHelpers.setField(a,"teamStore",TeamStore(a.applicationContext,"santo_inacio_C"))
        ReflectionHelpers.setField(a,"papelAtual",role);ReflectionHelpers.setField(a,"acessoConfirmado",true)
        a.getSharedPreferences("registros_v3",0).edit().putString("estoques_salvos",JSONArray().put(JSONObject().put("id","original").put("os","ANTIGO")).toString()).commit()
        return a
    }
    @Test fun supervisorSavesTwoLotsTogetherWithoutReplacingExistingStocksOrActiveSelection() {
        val a=activity("supervisor")
        a.getSharedPreferences("controle_colheita",0).edit().putString("progresso","{\"id\":\"original\"}").commit()
        a.javaClass.getDeclaredMethod("cadastrarLotes").apply { isAccessible=true }.invoke(a)
        shadowOf(Looper.getMainLooper()).idle()
        val dialog=ShadowAlertDialog.getLatestAlertDialog()
        views(dialog.window!!.decorView).filterIsInstance<TextView>().single { it.text.toString()=="+ Adicionar outro lote" }.performClick()
        val fields=views(dialog.window!!.decorView).filterIsInstance<EditText>()
        listOf("K/01","1, 2","10,5","80","Carreador sul","K/02","3 a 5","Área a medir","Produtividade a confirmar","").forEachIndexed { i,text->fields[i].setText(text) }
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).performClick();shadowOf(Looper.getMainLooper()).idle()
        val saved=JSONArray(a.getSharedPreferences("registros_v3",0).getString("estoques_salvos","[]"))
        assertEquals(3,saved.length());assertEquals("ANTIGO",saved.getJSONObject(0).getString("os"))
        assertEquals("K/02",saved.getJSONObject(2).getString("os"))
        assertEquals("Carreador sul",saved.getJSONObject(1).getString("observacoes"))
        assertEquals("Área a medir",saved.getJSONObject(2).getString("areaRestante"))
        assertEquals("Produtividade a confirmar",saved.getJSONObject(2).getString("produtividade"))
        assertEquals("original",JSONObject(a.getSharedPreferences("controle_colheita",0).getString("progresso","{}")).getString("id"))
    }
    @Test fun anOperatorCannotOpenTheStockBatchForm() {
        val a=activity("operador")
        a.javaClass.getDeclaredMethod("cadastrarLotes").apply { isAccessible=true }.invoke(a)
        assertNull(ShadowAlertDialog.getLatestAlertDialog())
        assertEquals(1,JSONArray(a.getSharedPreferences("registros_v3",0).getString("estoques_salvos","[]")).length())
    }
    @Test fun anIncompleteSecondLotDoesNotSaveTheFirstLot() {
        val a=activity("supervisor")
        a.javaClass.getDeclaredMethod("cadastrarLotes").apply {isAccessible=true}.invoke(a)
        shadowOf(Looper.getMainLooper()).idle()
        val dialog=ShadowAlertDialog.getLatestAlertDialog()
        views(dialog.window!!.decorView).filterIsInstance<TextView>().single {it.text=="+ Adicionar outro lote"}.performClick()
        val fields=views(dialog.window!!.decorView).filterIsInstance<EditText>()
        listOf("K/01","1","A conferir","80","Sem foto","K/02","","10","70","").forEachIndexed {i,value->fields[i].setText(value)}
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).performClick();shadowOf(Looper.getMainLooper()).idle()
        assertTrue(dialog.isShowing)
        assertEquals(1,JSONArray(a.getSharedPreferences("registros_v3",0).getString("estoques_salvos","[]")).length())
    }
    @Test fun aBatchFromTheOldTeamCannotSaveIntoTheNewTeam() {
        val a=activity("supervisor")
        a.javaClass.getDeclaredMethod("cadastrarLotes").apply {isAccessible=true}.invoke(a)
        shadowOf(Looper.getMainLooper()).idle()
        val dialog=ShadowAlertDialog.getLatestAlertDialog()
        val fields=views(dialog.window!!.decorView).filterIsInstance<EditText>()
        listOf("K/01","1","10","80","").forEachIndexed {i,value->fields[i].setText(value)}
        ReflectionHelpers.setField(a,"teamStore",TeamStore(a.applicationContext,"colorado_C"))
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).performClick();shadowOf(Looper.getMainLooper()).idle()
        assertEquals("[]",TeamStore(a.applicationContext,"colorado_C").prefs("registros_v3").getString("estoques_salvos","[]"))
        assertEquals(1,JSONArray(TeamStore(a.applicationContext,"santo_inacio_C").prefs("registros_v3").getString("estoques_salvos","[]")).length())
    }
}
