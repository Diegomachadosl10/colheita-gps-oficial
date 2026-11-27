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
import org.robolectric.shadows.ShadowPopupMenu
import org.robolectric.util.ReflectionHelpers
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[28],manifest=Config.NONE,application=Application::class)
class ReportRemovalScreenTest {
    private fun views(v:View):List<View> = listOf(v)+(if(v is ViewGroup) (0 until v.childCount).flatMap {views(v.getChildAt(it))} else emptyList())
    private fun activity():MainActivity {
        val a=Robolectric.buildActivity(MainActivity::class.java).get();a.setTheme(android.R.style.Theme_Material_Light_NoActionBar)
        ReflectionHelpers.setField(a,"teamStore",TeamStore(a.applicationContext,"santo_inacio_C"))
        ReflectionHelpers.setField(a,"teamAccess",TeamAccess("sup",mapOf("papel" to "supervisor","status" to "aprovado","frenteId" to "santo_inacio","turno" to "C")))
        ReflectionHelpers.setField(a,"papelAtual","supervisor");ReflectionHelpers.setField(a,"acessoConfirmado",true)
        return a
    }
    @Test fun reportsHaveTheirOwnModuleAndDoNotPolluteTheCalendarOrHomeOverflowMenu() {
        val a=activity()
        val legacy="[{\"id\":\"old\",\"descricao\":\"RELATO_ANTIGO\",\"dataCadastro\":\"14/11/2026\",\"turno\":\"C\"}]"
        a.getSharedPreferences("registros_v3",0).edit().putString("relatorios_turno",legacy).commit()
        ReflectionHelpers.callInstanceMethod<Unit>(a,"inicio")
        views(a.window.decorView).first {it.contentDescription?.toString()=="Mais opções"}.performClick()
        val menu=ShadowPopupMenu.getLatestPopupMenu().menu
        assertFalse((0 until menu.size()).any {menu.getItem(it).title.toString().contains("Relatórios")})
        val parent:()->Unit={}
        a.javaClass.declaredMethods.first {it.name=="calendario"}.apply {isAccessible=true}.invoke(a,"14/11/2026",parent)
        shadowOf(Looper.getMainLooper()).idle()
        val text=views(a.window.decorView).filterIsInstance<TextView>().joinToString {it.text.toString()}
        assertFalse(text.contains("RELATÓRIOS DE TURNO"));assertFalse(text.contains("RELATO_ANTIGO"))
        val output=ReflectionHelpers.getField<()->FieldReport?>(a,"relatorioAtual").invoke()!!.toString()
        assertFalse(output.contains("RELATO_ANTIGO"));assertFalse(output.contains("relatorios_turno"))
        assertTrue(RecordArchive.labels.containsKey("relatorios_turno"))
        assertEquals(legacy,a.getSharedPreferences("registros_v3",0).getString("relatorios_turno","[]"))
    }
    @Test fun editingMaintenanceStillPreservesItsPhotosOrderDateAndRecordId() {
        val a=activity();val photo=File(a.filesDir,"pendencia.jpg").apply {writeText("foto")}
        val row=JSONObject().put("id","p1").put("equipamento","21569").put("descricao","Defeito original").put("osPendencia","12345")
            .put("dataCadastro","12/11/2026").put("fotos",JSONArray().put(photo.path)).put("foto",photo.path)
        a.getSharedPreferences("registros_v3",0).edit().putString("pendencias",JSONArray().put(row).toString()).commit()
        a.javaClass.getDeclaredMethod("editarPendencia",Int::class.javaPrimitiveType,String::class.java).apply {isAccessible=true}.invoke(a,0,"")
        val dialog=ShadowAlertDialog.getLatestAlertDialog();val fields=views(dialog.window!!.decorView).filterIsInstance<EditText>()
        assertEquals(3,fields.size);fields[2].setText("Defeito corrigido")
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).performClick();shadowOf(Looper.getMainLooper()).idle()
        val rows=JSONArray(a.getSharedPreferences("registros_v3",0).getString("pendencias","[]"))
        val saved=(0 until rows.length()).map {rows.getJSONObject(it)}.single {it.optString("id")=="p1"}
        assertEquals("Defeito corrigido",saved.getString("descricao"));assertEquals("12345",saved.getString("osPendencia"))
        assertEquals("12/11/2026",saved.getString("dataCadastro"));assertEquals(photo.path,saved.getJSONArray("fotos").getString(0))
    }
}
