package com.colheitagps.app

import android.app.Activity
import android.app.AlertDialog
import android.app.Application
import android.content.Intent
import android.net.Uri
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
import java.io.ByteArrayInputStream
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[28],manifest=Config.NONE,application=Application::class)
class PartsScreenTest {
    private fun views(view:View):List<View> = listOf(view)+(if(view is ViewGroup) (0 until view.childCount).flatMap { views(view.getChildAt(it)) } else emptyList())
    private fun activity():MainActivity {
        val a=Robolectric.buildActivity(MainActivity::class.java).get()
        ReflectionHelpers.setField(a,"teamAccess",TeamAccess("opA",mapOf("nome" to "João","papel" to "operador","status" to "aprovado","frenteId" to "santo_inacio","turno" to "C")))
        ReflectionHelpers.setField(a,"teamStore",TeamStore(a.applicationContext,"santo_inacio_C"))
        ReflectionHelpers.setField(a,"papelAtual","operador"); ReflectionHelpers.setField(a,"acessoConfirmado",true)
        val issue=JSONObject().put("id","p1").put("equipamento","21569").put("descricao","Pneu")
        a.getSharedPreferences("registros_v3",0).edit().putString("pendencias",MaintenanceRecords.merge(JSONArray().put(issue),JSONArray()).toString()).commit()
        return a
    }
    private fun form(a:MainActivity):AlertDialog {
        a.javaClass.getDeclaredMethod("editarPeca",String::class.java,Boolean::class.javaPrimitiveType).apply { isAccessible=true }.invoke(a,null,false)
        shadowOf(Looper.getMainLooper()).idle(); return ShadowAlertDialog.getLatestAlertDialog()
    }
    private fun all(a:MainActivity)=JSONArray(a.getSharedPreferences("registros_v3",0).getString("pendencias","[]"))
    @Test fun operatorSavesPartWithoutReplacingMaintenanceAndCannotDeleteIt() {
        val a=activity(); val dialog=form(a); val fields=views(dialog.window!!.decorView).filterIsInstance<EditText>()
        fields[0].setText("00012"); fields[1].setText("Filtro hidráulico"); fields[2].setText("2"); fields[3].setText("21569")
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).performClick(); shadowOf(Looper.getMainLooper()).idle()
        val stored=all(a); val part=PartsRecords.rows(stored).getJSONObject(0)
        assertEquals(3,stored.length()); assertEquals("00012",part.getString("codigoPeca"))
        assertEquals(1,MaintenanceRecords.issues(stored).length()); assertEquals(1,MaintenanceRecords.summaries(stored).size)
        a.javaClass.getDeclaredMethod("opcoesPeca",JSONObject::class.java,Boolean::class.javaPrimitiveType).apply { isAccessible=true }.invoke(a,part,false)
        val menu=ShadowAlertDialog.getLatestAlertDialog().listView.adapter
        assertFalse((0 until menu.count).map { menu.getItem(it).toString() }.any { it.contains("Excluir") })
    }
    @Test fun invalidQuantityKeepsTheFormOpenWithoutSavingAnIncompleteRequest() {
        val a=activity(); val dialog=form(a); val fields=views(dialog.window!!.decorView).filterIsInstance<EditText>()
        fields[0].setText("123"); fields[2].setText("0")
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).performClick(); shadowOf(Looper.getMainLooper()).idle()
        assertTrue(dialog.isShowing); assertEquals(0,PartsRecords.rows(all(a)).length())
    }
    @Test fun galleryAttachmentIsCopiedToTheTeamFolderAndPreservedWithTheRequest() {
        val a=activity(); val dialog=form(a); val fields=views(dialog.window!!.decorView).filterIsInstance<EditText>()
        fields[0].setText("001"); fields[1].setText("Rolamento")
        views(dialog.window!!.decorView).filterIsInstance<TextView>().single { it.text.toString()=="Escolher fotos no celular" }.performClick()
        val uri=Uri.parse("content://fotos/peca.jpg"); val bytes=byteArrayOf(1,2,3,4)
        shadowOf(a.contentResolver).registerInputStream(uri,ByteArrayInputStream(bytes))
        a.javaClass.getDeclaredMethod("resultadoFotoPeca",Int::class.javaPrimitiveType,Int::class.javaPrimitiveType,Intent::class.java).apply { isAccessible=true }
            .invoke(a,8801,Activity.RESULT_OK,Intent().setData(uri))
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).performClick(); shadowOf(Looper.getMainLooper()).idle()
        val part=PartsRecords.rows(all(a)).getJSONObject(0); val photo=File(part.getJSONArray("fotos").getString(0))
        assertTrue(photo.canonicalPath.startsWith(a.filesDir.canonicalPath+File.separator)); assertArrayEquals(bytes,photo.readBytes())
        assertEquals("001",part.getString("codigoPeca"))
    }
}
