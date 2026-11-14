package com.colheitagps.app

import android.app.AlertDialog
import android.app.Application
import android.os.Looper
import android.view.View
import android.view.ViewGroup
import android.widget.CheckBox
import android.widget.EditText
import android.widget.LinearLayout
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
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[28],manifest=Config.NONE,application=Application::class)
class ProductivityScreenTest {
    private fun views(v:View):List<View> = listOf(v)+(if(v is ViewGroup) (0 until v.childCount).flatMap {views(v.getChildAt(it))} else emptyList())
    private fun labels(v:View)=views(v).filterIsInstance<TextView>().map {it.text.toString()}
    private fun activity():MainActivity {
        val a=Robolectric.buildActivity(MainActivity::class.java).get();AppTheme.apply(a)
        ReflectionHelpers.setField(a,"teamAccess",TeamAccess("sup",mapOf("papel" to "supervisor","status" to "aprovado","frenteId" to "santo_inacio","turno" to "C")))
        ReflectionHelpers.setField(a,"teamStore",TeamStore(a.applicationContext,"santo_inacio_C"));ReflectionHelpers.setField(a,"papelAtual","supervisor");ReflectionHelpers.setField(a,"acessoConfirmado",true)
        return a
    }
    private fun call(a:MainActivity,name:String) {ReflectionHelpers.callInstanceMethod<Unit>(a,name);shadowOf(Looper.getMainLooper()).idle()}
    private fun input(a:MainActivity,label:String)=views(a.window.decorView).filterIsInstance<EditText>().single {it.contentDescription==label}
    @Test fun stockSearchFiltersWithoutChangingDataAndRefreshPreservesTheQueryAndFocus() {
        val a=activity();val all=JSONArray()
        for(lot in listOf("K/39","M/05")) all.put(JSONObject().put("id",lot).put("os",lot).put("talhoes","1 a 10").put("areaRestante","38").put("produtividade","80"))
        a.getSharedPreferences("registros_v3",0).edit().putString("estoques_salvos",all.toString()).commit()
        call(a,"estoque")
        val query=input(a,"Buscar lote ou talhão");query.requestFocus();query.setText("k39")
        val list=a.window.decorView.findViewWithTag<LinearLayout>("resultadosEstoque")
        assertTrue(labels(list).contains("K/39"));assertFalse(labels(list).contains("M/05"))
        ReflectionHelpers.getField<()->Unit>(a,"refreshList").invoke()
        assertSame(query,input(a,"Buscar lote ou talhão"));assertTrue(query.hasFocus());assertEquals("k39",query.text.toString())
        query.setText("");assertTrue(labels(list).contains("M/05"))
        assertEquals(all.toString(),a.getSharedPreferences("registros_v3",0).getString("estoques_salvos","[]"))
    }
    @Test fun urgencyIsVisibleFirstAndEquipmentSearchAcceptsTheDescription() {
        val a=activity();val all=JSONArray().put(JSONObject().put("id","n").put("equipamento","100").put("descricao","Revisão do óleo"))
            .put(JSONObject().put("id","u").put("equipamento","900").put("descricao","Mangueira rompida").put("urgente",true))
        a.getSharedPreferences("registros_v3",0).edit().putString("pendencias",all.toString()).commit();call(a,"pendencias")
        val list=a.window.decorView.findViewWithTag<LinearLayout>("resultadosEquipamentos")
        assertTrue(labels(list.getChildAt(0)).contains("Equipamento 900"));assertTrue(labels(list.getChildAt(0)).any {it.contains("URGENTE")})
        input(a,"Buscar equipamento, pendência ou O.S.").setText("revisao oleo")
        assertTrue(labels(list).contains("Equipamento 100"));assertFalse(labels(list).contains("Equipamento 900"))
    }
    @Test fun favoriteStarsReorderOnlyTheVisibleMapLibraryAndMapSearchPreservesTheCatalog() {
        val a=activity();val catalog=a.getSharedPreferences("mapas",0)
        for((id,name) in listOf("a" to "Fazenda Alfa.pdf","z" to "Usina Zulu.pdf")) {
            catalog.edit().putString(id,name).commit();File(a.filesDir,"mapas/$id.pdf").apply {parentFile!!.mkdirs();writeText("fixture")}
        }
        call(a,"biblioteca")
        val list=a.window.decorView.findViewWithTag<LinearLayout>("resultadosMapas")
        assertTrue(labels(list.getChildAt(0)).contains("Fazenda Alfa.pdf"))
        views(list).single {it.contentDescription=="Marcar Usina Zulu.pdf como favorito"}.performClick()
        assertTrue(labels(list.getChildAt(0)).contains("Usina Zulu.pdf"))
        assertTrue(MapFavorites(a,"santo_inacio_C","sup").ids().contains("z"))
        input(a,"Buscar mapa ou lote").setText("alfa")
        assertTrue(labels(list).contains("Fazenda Alfa.pdf"));assertFalse(labels(list).contains("Usina Zulu.pdf"))
        assertEquals(setOf("a","z"),catalog.all.keys)
    }
    @Test fun editingAnIssueCanSetUrgencyWithoutReplacingItsIdDateOrOrder() {
        val a=activity();val row=JSONObject().put("id","p1").put("equipamento","21569").put("descricao","Pneu furado").put("osPendencia","123").put("dataCadastro","14/11/2026")
        a.getSharedPreferences("registros_v3",0).edit().putString("pendencias",JSONArray().put(row).toString()).commit()
        a.javaClass.getDeclaredMethod("editarPendencia",Int::class.javaPrimitiveType,String::class.java).apply {isAccessible=true}.invoke(a,0,"")
        val dialog=ShadowAlertDialog.getLatestAlertDialog()
        views(dialog.window!!.decorView).filterIsInstance<CheckBox>().single {it.text=="Marcar como urgente"}.isChecked=true
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).performClick();shadowOf(Looper.getMainLooper()).idle()
        val saved=JSONArray(a.getSharedPreferences("registros_v3",0).getString("pendencias","[]"))
        val updated=(0 until saved.length()).map {saved.getJSONObject(it)}.single {it.optString("id")=="p1"}
        assertTrue(updated.getBoolean("urgente"));assertEquals("123",updated.getString("osPendencia"));assertEquals("14/11/2026",updated.getString("dataCadastro"))
    }
}
