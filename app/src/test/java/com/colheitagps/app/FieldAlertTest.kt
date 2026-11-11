package com.colheitagps.app

import android.app.AlertDialog
import android.app.Application
import android.location.Location
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
class FieldAlertTest {
    private val now=1_794_395_000_000L
    private val elapsed=400_000_000_000L
    private fun fix(age:Long=0)=Location("gps").apply {latitude=-22.5;longitude=-51.4;accuracy=7f;elapsedRealtimeNanos=elapsed-age;time=now-age/1_000_000}
    @Test fun anAlertUsesTheExistingOccurrenceGroupAndCanBeSavedWithoutGpsOrInternet() {
        val record=FieldAlertRecords.build(null," 021569 ","Talhão 10","Solo molhado","op","João","santo_inacio_C",null,now,elapsed)
        assertTrue(FieldAlertRecords.isAlert(record));assertEquals("021569",record.getString("equipamento"));assertEquals("",record.getString("gps"));assertFalse(record.getBoolean("concluida"))
        assertTrue(record.getString("descricao").startsWith("EQUIPAMENTO ENCALHADO"));assertTrue(record.getString("descricao").contains("Solo molhado"))
        assertEquals("santo_inacio",record.getString("frenteId"));assertEquals("C",record.getString("turno"));assertTrue(FieldAlertRecords.GROUP in TeamPolicy.operatorGroups)
        val report=FieldReports.group("Avisos",FieldAlertRecords.GROUP,listOf(record)).rows.flatten().joinToString()
        assertTrue(report.contains("021569"));assertTrue(report.contains("Talhão 10"))
    }
    @Test fun oldFutureAndInvalidLocationFixesAreExcludedInsteadOfPresentingThemAsCurrent() {
        for(location in listOf(fix(121_000_000_000),fix(-1),fix().apply {latitude=91.0},fix().apply {removeAccuracy()})) assertNull(FieldAlertRecords.currentLocation(location,elapsed))
        val record=FieldAlertRecords.build(null,"123","Carreador 5","","op","João","santo_inacio_C",fix(3_000_000_000),now,elapsed)
        assertEquals("-22.500000, -51.400000",record.getString("gps"));assertEquals(7.0,record.getDouble("precisaoGpsM"),0.0)
        val old=FieldAlertRecords.build(null,"123","","","op","João","santo_inacio_C",fix(121_000_000_000),now,elapsed)
        assertEquals("",old.getString("gps"));assertFalse(old.has("latitude"))
    }
    @Test fun correctingAnAlertKeepsItsIdAuthorCoordinatesPhotosAndRestorationHistory() {
        val original=FieldAlertRecords.build(null,"123","Talhão 1","","op","João","santo_inacio_C",fix(),now,elapsed)
            .put("fotos",JSONArray().put("/foto.jpg")).put("restauradoEm","ontem").put("__syncBase",3)
        val changed=FieldAlertRecords.build(original,"124","Talhão 2","Corrigido","sup","Supervisor","santo_inacio_C",null,now+60000,elapsed+60_000_000_000)
        assertEquals(original.getString("id"),changed.getString("id"));assertEquals("op",changed.getString("registradoPorUid"));assertEquals("-22.500000, -51.400000",changed.getString("gps"))
        assertEquals("/foto.jpg",changed.getJSONArray("fotos").getString(0));assertEquals("ontem",changed.getString("restauradoEm"));assertEquals(3,changed.getInt("__syncBase"))
        assertEquals("124",changed.getString("equipamento"));assertEquals("123",original.getString("equipamento"))
        try {FieldAlertRecords.build(null,"","","","op","João","santo_inacio_C",null,now,elapsed);fail()} catch(_:IllegalArgumentException) {}
    }
    private fun views(v:View):List<View> = listOf(v)+(if(v is ViewGroup) (0 until v.childCount).flatMap {views(v.getChildAt(it))} else emptyList())
    private fun activity(role:String="operador"):MainActivity {
        val a=Robolectric.buildActivity(MainActivity::class.java).get();a.setTheme(android.R.style.Theme_Material_Light_NoActionBar)
        ReflectionHelpers.setField(a,"teamAccess",TeamAccess("op",mapOf("nome" to "João","papel" to role,"status" to "aprovado","frenteId" to "santo_inacio","turno" to "C")))
        ReflectionHelpers.setField(a,"teamStore",TeamStore(a.applicationContext,"santo_inacio_C"));ReflectionHelpers.setField(a,"papelAtual",role);ReflectionHelpers.setField(a,"acessoConfirmado",true)
        return a
    }
    private fun open(a:MainActivity,existing:JSONObject?=null) {
        a.javaClass.getDeclaredMethod("editarAvisoEncalhado",JSONObject::class.java).apply {isAccessible=true}.invoke(a,existing);shadowOf(Looper.getMainLooper()).idle()
    }
    @Test fun theOperatorCanCreateAnAlertOfflineAndTheFormDoesNotSendAnEmptyOrDuplicateEquipment() {
        val a=activity();open(a)
        val dialog=ShadowAlertDialog.getLatestAlertDialog();dialog.getButton(AlertDialog.BUTTON_POSITIVE).performClick();assertTrue(dialog.isShowing)
        assertEquals("[]",a.getSharedPreferences("registros_v3",0).getString(FieldAlertRecords.GROUP,"[]"))
        val fields=views(dialog.window!!.decorView).filterIsInstance<EditText>();fields[0].setText("21569");fields[1].setText("Talhão 15");fields[2].setText("Preciso de apoio")
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).performClick();shadowOf(Looper.getMainLooper()).idle()
        val all=JSONArray(a.getSharedPreferences("registros_v3",0).getString(FieldAlertRecords.GROUP,"[]"));assertEquals(1,all.length());assertTrue(FieldAlertRecords.isAlert(all.getJSONObject(0)))
        assertEquals("21569",all.getJSONObject(0).getString("equipamento"));assertFalse(dialog.isShowing)
        assertTrue(views(a.window.decorView).filterIsInstance<TextView>().any {it.text.toString().contains("não confirma que ele já leu")})
        a.javaClass.getDeclaredMethod("opcoesOcorrencia",Int::class.javaPrimitiveType).apply {isAccessible=true}.invoke(a,0)
        val list=ShadowAlertDialog.getLatestAlertDialog().listView
        assertFalse((0 until list.adapter.count).any {list.adapter.getItem(it).toString()=="Excluir"})
    }
    @Test fun aCorrectionKeepsOneRecordAndDeletedAlertsRequireManagerRestorationFirst() {
        val a=activity();val first=FieldAlertRecords.build(null,"21569","Talhão 15","","op","João","santo_inacio_C",null,now,elapsed)
        a.getSharedPreferences("registros_v3",0).edit().putString(FieldAlertRecords.GROUP,JSONArray().put(first).toString()).commit();open(a,first)
        val dialog=ShadowAlertDialog.getLatestAlertDialog();views(dialog.window!!.decorView).filterIsInstance<EditText>()[0].setText("21570")
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).performClick();shadowOf(Looper.getMainLooper()).idle()
        val all=JSONArray(a.getSharedPreferences("registros_v3",0).getString(FieldAlertRecords.GROUP,"[]"));assertEquals(1,all.length());assertEquals(first.getString("id"),all.getJSONObject(0).getString("id"));assertEquals("21570",all.getJSONObject(0).getString("equipamento"))
        val deleted=RecordArchive.archive(all.getJSONObject(0),"sup","Supervisor","hoje")
        open(a,deleted);assertFalse(ShadowAlertDialog.getLatestAlertDialog().isShowing)
    }
}
