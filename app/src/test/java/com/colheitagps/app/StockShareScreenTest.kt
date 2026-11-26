package com.colheitagps.app

import android.app.Application
import android.app.AlertDialog
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.os.Bundle
import android.os.Looper
import android.view.View
import android.view.ViewGroup
import android.widget.CheckBox
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.robolectric.util.ReflectionHelpers
import org.robolectric.util.ReflectionHelpers.ClassParameter
import org.robolectric.shadows.ShadowAlertDialog
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[28],manifest=Config.NONE,application=Application::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class StockShareScreenTest {
    private fun views(v:View):List<View> = listOf(v)+(if(v is ViewGroup) (0 until v.childCount).flatMap {views(v.getChildAt(it))} else emptyList())
    private fun intent()=Intent().putExtra("team","santo_inacio_C").putStringArrayListExtra("ids",arrayListOf("A","B"))
    private fun prepare():StockShareActivity {
        val a=Robolectric.buildActivity(StockShareActivity::class.java,intent()).create().get()
        a.setTheme(android.R.style.Theme_Material_Light_NoActionBar)
        val store=TeamStore(a.applicationContext,"santo_inacio_C")
        ReflectionHelpers.setField(a,"store",store)
        ReflectionHelpers.setField(a,"access",TeamAccess("supervisor",mapOf("papel" to "supervisor","status" to "aprovado","frenteId" to "santo_inacio","turno" to "C")))
        val records=JSONArray()
        for(id in listOf("A","B","C")) records.put(JSONObject().put("id",id).put("os",id).put("talhoes","1 a 3").put("areaRestante","15").put("produtividade","80").put("__syncBase",42))
        store.prefs("registros_v3").edit().putString("estoques_salvos",records.toString()).commit()
        return a
    }
    private fun store(a:StockShareActivity)=TeamStore(a.applicationContext,"santo_inacio_C")
    private fun photo(a:StockShareActivity):File=File(store(a).files,"local.png").apply {
        val bitmap=Bitmap.createBitmap(240,160,Bitmap.Config.ARGB_8888);bitmap.eraseColor(0xFF356C3D.toInt())
        Canvas(bitmap).drawText("FOTO DE TESTE",18f,85f,android.graphics.Paint(3).apply {color=android.graphics.Color.WHITE;textSize=26f})
        outputStream().use {bitmap.compress(Bitmap.CompressFormat.PNG,100,it)};bitmap.recycle()
    }
    private fun attach(a:StockShareActivity,id:String,file:File) {ReflectionHelpers.callInstanceMethod<Unit>(a,"attachPhoto",ClassParameter.from(String::class.java,id),ClassParameter.from(File::class.java,file))}
    @Test fun addingAPhotoChangesOnlyTheChosenLotsLocalAttachmentAndPreservesItsCurrentData() {
        val a=prepare();val s=store(a)
        s.prefs("controle_colheita").edit().putString("progresso","{\"id\":\"C\",\"os\":\"C\"}").commit()
        attach(a,"B",photo(a))
        val rows=JSONArray(s.prefs("registros_v3").getString("estoques_salvos","[]"))
        assertEquals(3,rows.length());assertFalse(rows.getJSONObject(0).has("fotoEstoque"));assertTrue(rows.getJSONObject(1).has("fotoEstoque"));assertFalse(rows.getJSONObject(2).has("fotoEstoque"))
        assertEquals("15",rows.getJSONObject(1).getString("areaRestante"));assertEquals(42,rows.getJSONObject(1).getInt("__syncBase"))
        assertEquals("C",JSONObject(s.prefs("controle_colheita").getString("progresso","{}")).getString("id"))
    }
    @Test fun thePersonCanOmitOneLotsPhotoWithoutRemovingItAndTheChoiceSurvivesRecreation() {
        val a=prepare();attach(a,"A",photo(a));attach(a,"B",photo(a))
        ReflectionHelpers.callInstanceMethod<Unit>(a,"render")
        views(a.window.decorView).filterIsInstance<CheckBox>().forEach {it.jumpDrawablesToCurrentState()}
        System.getenv("DM_AGRO_STOCK_SCREENSHOT")?.let { path->
            val root=a.window.decorView;root.measure(View.MeasureSpec.makeMeasureSpec(720,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(1280,View.MeasureSpec.EXACTLY));root.layout(0,0,720,1280)
            val image=Bitmap.createBitmap(720,1280,Bitmap.Config.ARGB_8888);root.draw(Canvas(image));File(path).outputStream().use {image.compress(Bitmap.CompressFormat.PNG,100,it)};image.recycle()
        }
        val boxes=views(a.window.decorView).filterIsInstance<CheckBox>();assertEquals(2,boxes.size)
        boxes[1].isChecked=false
        val saved=Bundle();a.javaClass.getDeclaredMethod("onSaveInstanceState",Bundle::class.java).apply {isAccessible=true}.invoke(a,saved)
        assertEquals(listOf("B"),saved.getStringArrayList("omitted"))
        val restored=Robolectric.buildActivity(StockShareActivity::class.java,intent()).create(saved).get()
        assertTrue(ReflectionHelpers.getField<Set<String>>(restored,"omitted").contains("B"))
        assertTrue(JSONArray(store(a).prefs("registros_v3").getString("estoques_salvos","[]")).getJSONObject(1).has("fotoEstoque"))
    }
    @Test fun theGalleryIsOpenedForTheSpecificLotRatherThanThePreviouslyActiveStock() {
        val a=prepare()
        ReflectionHelpers.callInstanceMethod<Unit>(a,"choosePhoto",ClassParameter.from(String::class.java,"B"))
        assertEquals("B",ReflectionHelpers.getField<String>(a,"pendingLot"))
        val result=shadowOf(a).nextStartedActivityForResult
        assertEquals(Intent.ACTION_OPEN_DOCUMENT,result.intent.action);assertEquals("image/*",result.intent.type);assertEquals(8810,result.requestCode)
    }
    @Test fun theMapIsChosenByItsCatalogNameAndAttachedOnlyAfterConfirmingThePreview() {
        val a=prepare();val s=store(a);val id="a".repeat(64)
        val map=photo(a).copyTo(File(s.files,"mapa_relatorio_$id.png"))
        s.prefs("mapas").edit().putString(id,"Fazenda Santo Inácio - K05.pdf").commit()
        File(s.files,"mapas/$id.pdf").apply {parentFile!!.mkdirs();writeText("fixture") }
        ReflectionHelpers.callInstanceMethod<Unit>(a,"choosePhoto",ClassParameter.from(String::class.java,"B"))
        val source=ShadowAlertDialog.getLatestAlertDialog()
        source.listView.performItemClick(source.listView.getChildAt(1),1,1);shadowOf(Looper.getMainLooper()).idle()
        val picker=ShadowAlertDialog.getLatestAlertDialog()
        val label=picker.listView.adapter.getItem(0).toString()
        assertTrue(label.contains("Fazenda Santo Inácio - K05"));assertFalse(label.contains(id));assertFalse(label.contains("mapa_relatorio_"))
        picker.listView.performItemClick(picker.listView.getChildAt(0),0,0);shadowOf(Looper.getMainLooper()).idle()
        val preview=ShadowAlertDialog.getLatestAlertDialog()
        assertTrue(preview.getButton(AlertDialog.BUTTON_POSITIVE).isEnabled)
        assertFalse(JSONArray(s.prefs("registros_v3").getString("estoques_salvos","[]")).getJSONObject(1).has("fotoEstoque"))
        System.getenv("DM_AGRO_MAP_PICKER_SCREENSHOT")?.let { path->
            val root=preview.window!!.decorView;root.measure(View.MeasureSpec.makeMeasureSpec(720,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(1080,View.MeasureSpec.EXACTLY));root.layout(0,0,720,1080)
            val image=Bitmap.createBitmap(720,1080,Bitmap.Config.ARGB_8888);root.draw(Canvas(image));File(path).outputStream().use {image.compress(Bitmap.CompressFormat.PNG,100,it)};image.recycle()
        }
        preview.getButton(AlertDialog.BUTTON_POSITIVE).performClick();shadowOf(Looper.getMainLooper()).idle()
        val rows=JSONArray(s.prefs("registros_v3").getString("estoques_salvos","[]"))
        assertEquals(map.path,rows.getJSONObject(1).getString("fotoEstoque"));assertFalse(rows.getJSONObject(0).has("fotoEstoque"))
    }
    @Test fun orphanPaintedImagesNeverAppearAsUnnamedMapsFromOtherTeams() {
        val a=prepare();val s=store(a)
        for(letter in listOf("a","b")) photo(a).copyTo(File(s.files,"mapa_relatorio_${letter.repeat(64)}.png"))
        TeamStore(a.applicationContext,"colorado_C").prefs("mapas").edit().putString("a".repeat(64),"Nome de outra frente.pdf").commit()
        val maps=PaintedMapPicker.items(s)
        assertTrue(maps.isEmpty())
        assertEquals(2,s.files.listFiles().orEmpty().count {it.name.startsWith("mapa_relatorio_")})
    }
    @Test fun mixedLotsKeepTheirTextAndOnlyTheChosenPhotosWithoutChangingStoredAttachments() {
        val a=prepare();attach(a,"A",photo(a));attach(a,"B",photo(a))
        ReflectionHelpers.getField<MutableSet<String>>(a,"omitted").add("B")
        val selected=ReflectionHelpers.callInstanceMethod<List<JSONObject>>(a,"selectedForSharing")
        assertEquals(2,selected.size)
        assertNotNull(StockShareFiles.photo(store(a).files,selected[0]))
        assertNull(StockShareFiles.photo(store(a).files,selected[1]))
        val text=StockBatch.text(selected,TeamPolicy.label(store(a).team),"hoje")
        assertTrue(text.contains("Lote: A"));assertTrue(text.contains("Lote: B"))
        val stored=JSONArray(store(a).prefs("registros_v3").getString("estoques_salvos","[]"))
        assertTrue(stored.getJSONObject(0).has("fotoEstoque"));assertTrue(stored.getJSONObject(1).has("fotoEstoque"))
        ReflectionHelpers.callInstanceMethod<Unit>(a,"render")
        val labels=views(a.window.decorView).filterIsInstance<android.widget.TextView>().map {it.text.toString()}
        assertTrue(labels.contains("Enviar mensagem e fotos"));assertTrue(labels.contains("Enviar em PDF"))
        val report=StockSharing.pdfReport(store(a),selected,"hoje","Responsável fictício")
        assertEquals(1,report.pictures.size)
    }

}
