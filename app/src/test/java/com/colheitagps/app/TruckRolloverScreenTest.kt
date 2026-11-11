package com.colheitagps.app

import android.app.Application
import android.app.AlertDialog
import android.os.Looper
import android.view.*
import android.widget.*
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
@Config(sdk=[28],manifest=Config.NONE,application=Application::class)
class TruckRolloverScreenTest {
    private fun views(v:View):List<View> = listOf(v)+(if(v is ViewGroup) (0 until v.childCount).flatMap {views(v.getChildAt(it))} else emptyList())
    private fun clock(a:MainActivity,time:LocalDateTime) {val zone=ZoneId.of("America/Sao_Paulo");ReflectionHelpers.setField(a,"relogioCaminhoes",Clock.fixed(time.atZone(zone).toInstant(),zone))}
    private fun activity(shift:String):MainActivity {
        val a=Robolectric.buildActivity(MainActivity::class.java).get();a.setTheme(android.R.style.Theme_Material_Light_NoActionBar)
        ReflectionHelpers.setField(a,"teamAccess",TeamAccess("sup",mapOf("papel" to "supervisor","status" to "aprovado","frenteId" to "santo_inacio","turno" to shift)))
        ReflectionHelpers.setField(a,"teamStore",TeamStore(a.applicationContext,"santo_inacio_$shift"));ReflectionHelpers.setField(a,"papelAtual","supervisor");ReflectionHelpers.setField(a,"acessoConfirmado",true)
        return a
    }
    private fun call(a:MainActivity,name:String,vararg args:Any) {a.javaClass.declaredMethods.first {it.name==name}.apply {isAccessible=true}.invoke(a,*args);Shadows.shadowOf(Looper.getMainLooper()).idle()}
    private fun report(a:MainActivity)=ReflectionHelpers.getField<()->FieldReport?>(a,"relatorioAtual").invoke()!!
    @Test fun anOpenTruckScreenResetsExactlyAtEachBoundaryAndTheOldTripIsStillAvailableInHistory() {
        val day=LocalDate.of(2026,11,12)
        for((shift,boundary,entryDate,entryTime) in listOf(
            listOf("A","15:20",day.toString(),"08:00"),listOf("B","23:20",day.toString(),"16:00"),listOf("C","07:00",day.toString(),"01:00"))) {
            val a=activity(shift);val end=day.atTime(LocalTime.parse(boundary));clock(a,end.minusSeconds(1))
            val record=TruckRecords.build(null,"TESTE-$shift",entryTime,"",LocalDate.parse(entryDate),null,"santo_inacio_$shift")
            val original=JSONArray().put(record).toString();a.getSharedPreferences("registros_v3",0).edit().putString("caminhoes",original).commit()
            call(a,"caminhoes");assertEquals(1,report(a).sections.last().rows.size)
            clock(a,end);Shadows.shadowOf(Looper.getMainLooper()).idleFor(java.time.Duration.ofSeconds(1))
            assertTrue(report(a).sections.last().rows.isEmpty());assertEquals(listOf("0","0","0"),report(a).sections.first().rows.single())
            assertEquals(original,a.getSharedPreferences("registros_v3",0).getString("caminhoes","[]"))
            ReflectionHelpers.setField(a,"diaCaminhoes","todos");call(a,"caminhoes");assertEquals(1,report(a).sections.last().rows.size)
        }
    }
    @Test fun anInvalidNewEntryStaysInTheFormAndAnAfterMidnightHistoricalEntryGetsTheCorrectCalendarDate() {
        val a=activity("C");val day=LocalDate.of(2026,11,11);clock(a,day.atTime(23,50))
        ReflectionHelpers.setField(a,"diaCaminhoes","11/11/2026");call(a,"editarCaminhao",-1)
        val dialog=ShadowAlertDialog.getLatestAlertDialog();val fields=views(dialog.window!!.decorView).filterIsInstance<EditText>()
        fields[0].setText("123");fields[1].setText("10:00");dialog.getButton(AlertDialog.BUTTON_POSITIVE).performClick()
        assertTrue(dialog.isShowing);assertNotNull(fields[1].error);assertEquals("[]",a.getSharedPreferences("registros_v3",0).getString("caminhoes","[]"))
        fields[1].setText("00:30");dialog.getButton(AlertDialog.BUTTON_POSITIVE).performClick();Shadows.shadowOf(Looper.getMainLooper()).idle()
        val record=JSONArray(a.getSharedPreferences("registros_v3",0).getString("caminhoes","[]")).getJSONObject(0)
        assertEquals("12/11/2026",record.getString("dataEntrada"));assertEquals(1,report(a).sections.last().rows.size)
    }
    @Test fun removingTheQuickAlertKeepsOldOccurrencesAndTheirUnknownTypeEditable() {
        val a=activity("C");call(a,"inicio")
        assertFalse(views(a.window.decorView).filterIsInstance<TextView>().any {it.text.toString().contains("encalhado",true)})
        val file=java.io.File(a.filesDir,"foto-legada.jpg").apply {parentFile?.mkdirs();writeText("foto local preservada")}
        val legacy=JSONObject().put("id","antigo").put("tipo","Equipamento encalhado").put("categoriaRegistro","encalhado").put("equipamento","21569")
            .put("descricao","Pedido antigo").put("gps","-22.5, -51.4").put("fotos",JSONArray().put(file.path))
        a.getSharedPreferences("registros_v3",0).edit().putString("ocorrencias_campo",JSONArray().put(legacy).toString()).commit()
        call(a,"editarOcorrencia",0);val dialog=ShadowAlertDialog.getLatestAlertDialog()
        views(dialog.window!!.decorView).filterIsInstance<EditText>()[1].setText("Descrição corrigida")
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).performClick();Shadows.shadowOf(Looper.getMainLooper()).idle()
        val record=JSONArray(a.getSharedPreferences("registros_v3",0).getString("ocorrencias_campo","[]")).getJSONObject(0)
        assertEquals("antigo",record.getString("id"));assertEquals("Equipamento encalhado",record.getString("tipo"));assertEquals("21569",record.getString("equipamento"))
        assertEquals("Descrição corrigida",record.getString("descricao"));assertEquals(file.path,record.getJSONArray("fotos").getString(0));assertEquals("-22.5, -51.4",record.getString("gps"))
    }
}
