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
import java.time.LocalTime

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[28],manifest=Config.NONE,application=Application::class,qualifiers="w360dp-h640dp-xhdpi")
@org.robolectric.annotation.GraphicsMode(org.robolectric.annotation.GraphicsMode.Mode.NATIVE)
class TruckScreenTest {
    private fun views(v:View):List<View> = listOf(v)+(if(v is ViewGroup) (0 until v.childCount).flatMap {views(v.getChildAt(it))} else emptyList())
    private fun activity(role:String="supervisor"):MainActivity {
        val a=Robolectric.buildActivity(MainActivity::class.java).get();a.setTheme(android.R.style.Theme_Material_Light_NoActionBar)
        ReflectionHelpers.setField(a,"teamAccess",TeamAccess("pessoa",mapOf("nome" to "Pessoa","papel" to role,"status" to "aprovado","frenteId" to "santo_inacio","turno" to "C")))
        ReflectionHelpers.setField(a,"teamStore",TeamStore(a.applicationContext,"santo_inacio_C"));ReflectionHelpers.setField(a,"papelAtual",role);ReflectionHelpers.setField(a,"acessoConfirmado",true)
        return a
    }
    private fun call(a:MainActivity,name:String,vararg args:Any) {a.javaClass.declaredMethods.first {it.name==name}.apply {isAccessible=true}.invoke(a,*args);shadowOf(Looper.getMainLooper()).idle()}
    private fun report(a:MainActivity)=ReflectionHelpers.getField<()->FieldReport?>(a,"relatorioAtual").invoke()!!
    @Test fun editingAnExitUpdatesTheExistingTripAndInvalidInputLeavesTheDialogOpen() {
        val a=activity();val record=TruckRecords.build(null,"00123","08:00","",LocalDate.now(),null,"santo_inacio_C").put("restauradoEm","ontem")
        a.getSharedPreferences("registros_v3",0).edit().putString("caminhoes",JSONArray().put(record).toString()).commit()
        call(a,"editarCaminhao",0)
        val dialog=ShadowAlertDialog.getLatestAlertDialog();val fields=views(dialog.window!!.decorView).filterIsInstance<EditText>()
        fields[2].setText("26:00");dialog.getButton(AlertDialog.BUTTON_POSITIVE).performClick()
        assertTrue(dialog.isShowing);assertEquals("",JSONArray(a.getSharedPreferences("registros_v3",0).getString("caminhoes","[]")).getJSONObject(0).getString("saida"))
        fields[2].setText("11:00");dialog.getButton(AlertDialog.BUTTON_POSITIVE).performClick();shadowOf(Looper.getMainLooper()).idle()
        val all=JSONArray(a.getSharedPreferences("registros_v3",0).getString("caminhoes","[]"));assertEquals(1,all.length())
        assertEquals(record.getString("id"),all.getJSONObject(0).getString("id"));assertEquals("11:00",all.getJSONObject(0).getString("saida"));assertEquals("ontem",all.getJSONObject(0).getString("restauradoEm"))
    }
    @Test fun theTruckScreenAndItsExportUseTheSameDayAndCutoffAndDoNotMixTeams() {
        val a=activity();val day=LocalDate.of(2026,11,11)
        val trip=TruckRecords.build(null,"123","00:30","02:00",day.plusDays(1),null,"santo_inacio_C")
        a.getSharedPreferences("registros_v3",0).edit().putString("caminhoes",JSONArray().put(trip).toString()).commit()
        TeamStore(a.applicationContext,"sandovalina_C").prefs("registros_v3").edit().putString("caminhoes",JSONArray().put(TruckRecords.build(null,"OUTRO","10:00","",day,null,"sandovalina_C")).toString()).commit()
        ReflectionHelpers.setField(a,"diaCaminhoes","11/11/2026");ReflectionHelpers.setField(a,"corteCaminhoes",LocalTime.of(1,0));call(a,"caminhoes")
        val exported=report(a);assertTrue(exported.team.contains("Santo Inácio"));assertTrue(exported.period.contains("12/11/2026 01:00"))
        assertEquals(listOf("1","0","1"),exported.sections.first().rows.single());assertEquals(1,exported.sections.last().rows.size)
        assertFalse(exported.toString().contains("OUTRO"));assertFalse(exported.toString().contains("02:00"))
        views(a.window.decorView).filterIsInstance<TextView>().single {it.text.toString()=="Compartilhar PDF / imagem deste período"}.performClick()
        val choices=ShadowAlertDialog.getLatestAlertDialog().listView.adapter
        assertEquals("PDF organizado",choices.getItem(0));assertEquals("Imagem organizada (PNG)",choices.getItem(1))
    }
    @Test fun openEquipmentIssuesHaveARedAccessibleIconAndCompletedOnesDoNot() {
        val a=activity()
        val catalog=MaintenanceRecords.merge(JSONArray().put(JSONObject().put("id","1").put("equipamento","21569").put("descricao","Pneu")).put(JSONObject().put("id","2").put("equipamento","21570").put("descricao","Filtro").put("concluida",true)),JSONArray())
        a.getSharedPreferences("registros_v3",0).edit().putString("pendencias",catalog.toString()).commit();call(a,"pendencias")
        val labels=views(a.window.decorView).filterIsInstance<TextView>()
        val alert=labels.single {it.contentDescription?.toString()?.startsWith("Alerta: equipamento 21569")==true}
        assertEquals(0xFFFF7777.toInt(),alert.currentTextColor);assertTrue(alert.text.startsWith("●"));assertTrue(alert.text.contains("1"))
        assertTrue(labels.any {it.contentDescription?.toString()=="Equipamento 21570 sem pendências abertas"&&it.text.startsWith("✓")})
        System.getenv("DM_AGRO_PENDING_PREVIEW")?.let {path->
            val root=a.window.decorView
            root.measure(View.MeasureSpec.makeMeasureSpec(720,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(1280,View.MeasureSpec.EXACTLY));root.layout(0,0,720,1280)
            val bitmap=android.graphics.Bitmap.createBitmap(720,1280,android.graphics.Bitmap.Config.ARGB_8888);root.draw(android.graphics.Canvas(bitmap))
            java.io.File(path).outputStream().use {bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG,100,it)};bitmap.recycle()
        }
    }
    @Test fun operatorsCannotOpenTheManagerTruckEditor() {
        val a=activity("operador");call(a,"editarCaminhao",-1)
        assertNull(ShadowAlertDialog.getLatestAlertDialog());assertEquals("[]",a.getSharedPreferences("registros_v3",0).getString("caminhoes","[]"))
    }
}
