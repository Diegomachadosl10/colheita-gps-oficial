package com.colheitagps.app

import android.app.Activity
import android.app.Application
import android.content.Intent
import android.content.pm.ProviderInfo
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.PointF
import android.os.Bundle
import android.os.Looper
import android.net.Uri
import android.webkit.MimeTypeMap
import androidx.core.content.FileProvider
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.robolectric.shadows.ShadowContentResolver
import org.robolectric.shadows.ShadowAlertDialog
import org.robolectric.util.ReflectionHelpers
import java.io.File
import java.time.Duration

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[28,35],manifest=Config.NONE,application=Application::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class SharingProviderRegressionTest {
    private val app get()=RuntimeEnvironment.getApplication()
    private val store get()=TeamStore(app,"santo_inacio_C")
    private val report get()=FieldReport("Teste de compartilhamento","Santo Inácio • Turno C",sections=listOf(ReportSection("Lotes",listOf("Lote"),listOf(listOf("K/05")))))
    @Before fun useTheActualProviderAndItsProductionXmlWithAnEmptyCache() {
        shadowOf(MimeTypeMap.getSingleton()).addExtensionMimeTypeMapping("png","image/png")
        val authority="${app.packageName}.files"
        val info=ProviderInfo().apply {
            this.authority=authority;packageName=app.packageName;name=FileProvider::class.java.name
            exported=false;grantUriPermissions=true
            metaData=Bundle().apply {putInt("android.support.FILE_PROVIDER_PATHS",R.xml.shared_paths)}
        }
        shadowOf(app.packageManager).addOrUpdateProvider(info)
        ReflectionHelpers.getStaticField<MutableMap<String,Any>>(FileProvider::class.java,"sCache").clear()
        val provider=FileProvider();provider.attachInfo(app,info)
        ShadowContentResolver.registerProviderInternal(authority,provider)
    }
    private fun activity():MainActivity {
        val a=Robolectric.buildActivity(MainActivity::class.java).get();a.setTheme(android.R.style.Theme_Material_Light_NoActionBar)
        ReflectionHelpers.setField(a,"teamStore",store)
        ReflectionHelpers.setField(a,"teamAccess",TeamAccess("sup",mapOf("papel" to "supervisor","status" to "aprovado","frenteId" to "santo_inacio","turno" to "C")))
        ReflectionHelpers.setField(a,"papelAtual","supervisor");ReflectionHelpers.setField(a,"acessoConfirmado",true)
        return a
    }
    private fun image(file:File,color:Int=Color.WHITE):File {
        val bitmap=Bitmap.createBitmap(80,80,Bitmap.Config.ARGB_8888);bitmap.eraseColor(color)
        file.parentFile?.mkdirs();file.outputStream().use {check(bitmap.compress(Bitmap.CompressFormat.PNG,100,it))};bitmap.recycle()
        return file
    }
    private fun awaitShare(a:Activity,mime:String="image/png"):Intent {
        var chooser:Intent?=null
        val deadline=System.nanoTime()+Duration.ofSeconds(15).toNanos()
        while(chooser==null&&System.nanoTime()<deadline) {
            Thread.sleep(25);shadowOf(Looper.getMainLooper()).idle()
            ShadowAlertDialog.getLatestAlertDialog()?.takeIf {it.isShowing&&it.getButton(android.app.AlertDialog.BUTTON_POSITIVE)?.text=="Enviar"}?.getButton(android.app.AlertDialog.BUTTON_POSITIVE)?.performClick()
            chooser=shadowOf(a).nextStartedActivity
        }
        assertNotNull("O compartilhamento precisa abrir sem encerrar o aplicativo",chooser)
        assertEquals(Intent.ACTION_CHOOSER,chooser!!.action)
        return chooser.getParcelableExtra<Intent>(Intent.EXTRA_INTENT)!!.also {assertEquals(mime,it.type)}
    }
    private fun readable(uri:Uri):ByteArray {
        assertTrue(uri.path!!.startsWith("/compartilhamento_beta/"))
        assertEquals("image/png",app.contentResolver.getType(uri))
        return app.contentResolver.openInputStream(uri)!!.use {it.readBytes()}.also {
            assertTrue(it.size>100);assertEquals(0x89,it.first().toInt() and 255)
        }
    }
    @Test fun aReportFromTheTeamScopedActivityOpensTheChooserAndItsImagesAreReadableWithTheRealProvider() {
        val a=activity()
        FieldReportSharing.share(a,report,a.cacheDir)
        val share=awaitShare(a)
        assertEquals(Intent.ACTION_SEND,share.action)
        assertTrue(share.clipData!!.description.hasMimeType("image/png"))
        readable(share.getParcelableExtra<Uri>(Intent.EXTRA_STREAM)!!)
    }
    @Test fun sharingTheOpenPaintedMapRefreshesTheImageKeepsItsColorsAndDoesNotChangeCoordinates() {
        val a=activity();val id="a".repeat(64)
        a.getSharedPreferences("mapas",0).edit().putString(id,"Mapa de teste").commit()
        val view=MapaView(a)
        view.carregar(Bitmap.createBitmap(80,80,Bitmap.Config.ARGB_8888).apply {eraseColor(Color.WHITE)},id,null)
        ReflectionHelpers.getField<MutableList<MutableList<PointF>>>(view,"areas").add(mutableListOf(PointF(10f,10f),PointF(60f,10f),PointF(60f,60f),PointF(10f,60f)))
        ReflectionHelpers.getField<MutableList<Int>>(view,"coresAreas").add(Color.RED)
        assertTrue(view.salvar())
        val coordinates=a.getSharedPreferences("pinturas",0).getString(id,"")
        // An older preview must not win over the open map's current saved drawings.
        image(File(a.filesDir,"mapa_relatorio_$id.png"),Color.WHITE)
        ReflectionHelpers.setField(a,"mapa",view);ReflectionHelpers.setField(a,"chaveAtual",id)
        ReflectionHelpers.callInstanceMethod<Unit>(a,"compartilharImagemMapa",ReflectionHelpers.ClassParameter.from(String::class.java,id))
        val share=awaitShare(a);val bytes=readable(share.getParcelableExtra<Uri>(Intent.EXTRA_STREAM)!!)
        val output=BitmapFactory.decodeByteArray(bytes,0,bytes.size)
        assertEquals(Color.RED,output.getPixel(30,30));assertEquals(Color.WHITE,output.getPixel(75,75));output.recycle()
        assertEquals(coordinates,a.getSharedPreferences("pinturas",0).getString(id,""))
    }
    @Test fun sharingDuringAnAreaEditCannotOverwriteTheSavedPolygon() {
        val a=activity();val id="c".repeat(64)
        a.getSharedPreferences("mapas",0).edit().putString(id,"Mapa em edição").commit()
        val saved="{\"areas\":[[{\"x\":10,\"y\":10},{\"x\":60,\"y\":10},{\"x\":60,\"y\":60}]],\"coresAreas\":[-65536]}"
        a.getSharedPreferences("pinturas",0).edit().putString(id,saved).commit()
        val view=MapaView(a)
        view.carregar(Bitmap.createBitmap(80,80,Bitmap.Config.ARGB_8888),id,null)
        ReflectionHelpers.getField<MutableList<MutableList<PointF>>>(view,"areas").clear()
        ReflectionHelpers.setField(view,"areaEmEdicao",0)
        ReflectionHelpers.setField(a,"mapa",view);ReflectionHelpers.setField(a,"chaveAtual",id)
        ReflectionHelpers.callInstanceMethod<Unit>(a,"compartilharImagemMapa",ReflectionHelpers.ClassParameter.from(String::class.java,id))
        assertNull(shadowOf(a).nextStartedActivity)
        assertEquals(saved,a.getSharedPreferences("pinturas",0).getString(id,""))
    }
    @Test fun stocksShareTheSelectedPaintedMapWithAllLotsUsingReadableImageUris() {
        val painted=image(File(store.files,"mapa_relatorio_${"b".repeat(64)}.png"),Color.RED)
        val rows=JSONArray()
        for(id in listOf("A","B")) rows.put(JSONObject().put("id",id).put("os",id).put("talhoes","1 a 3").put("areaRestante","10").put("produtividade","80").apply {if(id=="B") put("fotoEstoque",painted.path)})
        store.prefs("registros_v3").edit().putString("estoques_salvos",rows.toString()).commit()
        val input=Intent().putExtra("team",store.team).putStringArrayListExtra("ids",arrayListOf("A","B"))
        val a=Robolectric.buildActivity(StockShareActivity::class.java,input).create().get()
        ReflectionHelpers.setField(a,"store",store)
        ReflectionHelpers.setField(a,"access",TeamAccess("sup",mapOf("papel" to "supervisor","status" to "aprovado","frenteId" to "santo_inacio","turno" to "C")))
        ReflectionHelpers.callInstanceMethod<Unit>(a,"sendStock")
        val share=awaitShare(a,"image/*")
        assertEquals(Intent.ACTION_SEND,share.action)
        assertEquals(1,share.clipData!!.itemCount)
        val message=share.getStringExtra(Intent.EXTRA_TEXT)!!
        assertFalse(message.contains("Fotos/mapas"));assertFalse(message.contains("anexados dos lotes"))
        assertTrue(message.contains("Lote: A"));assertTrue(message.contains("Lote: B"));assertTrue(message.contains("Talhões: 1 a 3"))
        assertArrayEquals(painted.readBytes(),readable(share.getParcelableExtra<Uri>(Intent.EXTRA_STREAM)!!))
        assertEquals(rows.toString(),store.prefs("registros_v3").getString("estoques_salvos","[]"))
    }
    @Test fun theSingleActiveStockSharesItsOriginalPaintedMapAndTheOriginalTextMessage() {
        val a=activity();val painted=image(File(store.files,"mapa_relatorio_${"d".repeat(64)}.png"),Color.RED)
        val row=JSONObject().put("id","one").put("os","K/05").put("talhoes","1 a 3").put("areaRestante","10").put("produtividade","80").put("fotoEstoque",painted.path)
        a.getSharedPreferences("controle_colheita",0).edit().putString("progresso",row.toString()).commit()
        ReflectionHelpers.callInstanceMethod<Unit>(a,"escolherCompartilhamentoEstoque")
        val share=awaitShare(a,"image/*")
        assertEquals(Intent.ACTION_SEND,share.action)
        assertTrue(share.getStringExtra(Intent.EXTRA_TEXT)!!.startsWith("ESTOQUE DE CANA - COLHEITA GPS"))
        assertFalse(share.getStringExtra(Intent.EXTRA_TEXT)!!.contains("Fotos/mapas"))
        assertTrue(share.getStringExtra(Intent.EXTRA_TEXT)!!.contains("Lote: K/05"))
        assertTrue(share.getStringExtra(Intent.EXTRA_TEXT)!!.contains("Talhões: 1 a 3"))
        assertArrayEquals(painted.readBytes(),readable(share.getParcelableExtra<Uri>(Intent.EXTRA_STREAM)!!))
        assertEquals(row.toString(),a.getSharedPreferences("controle_colheita",0).getString("progresso","{}"))
    }
    @Test fun severalLotsKeepBothOriginalMapsAndAllTheirDataInTheTextMessage() {
        val red=image(File(store.files,"mapa_relatorio_${"e".repeat(64)}.png"),Color.RED)
        val blue=image(File(store.files,"mapa_relatorio_${"f".repeat(64)}.png"),Color.BLUE)
        val rows=JSONArray()
        for(id in listOf("A","B","C")) rows.put(JSONObject().put("id",id).put("os",id).put("talhoes","1 a 3").put("areaRestante","10").put("produtividade","80").apply {
            when(id) {"A"->put("fotoEstoque",red.path);"B"->put("fotoEstoque",blue.path)}
        })
        store.prefs("registros_v3").edit().putString("estoques_salvos",rows.toString()).commit()
        val input=Intent().putExtra("team",store.team).putStringArrayListExtra("ids",arrayListOf("A","B","C"))
        val a=Robolectric.buildActivity(StockShareActivity::class.java,input).create().get()
        ReflectionHelpers.setField(a,"store",store)
        ReflectionHelpers.setField(a,"access",TeamAccess("sup",mapOf("papel" to "supervisor","status" to "aprovado","frenteId" to "santo_inacio","turno" to "C")))
        ReflectionHelpers.callInstanceMethod<Unit>(a,"sendStock")
        val share=awaitShare(a,"image/*");assertEquals(Intent.ACTION_SEND_MULTIPLE,share.action)
        val uris=share.getParcelableArrayListExtra<Uri>(Intent.EXTRA_STREAM)!!
        assertEquals(2,uris.size);assertEquals(2,share.clipData!!.itemCount)
        assertArrayEquals(red.readBytes(),readable(uris[0]));assertArrayEquals(blue.readBytes(),readable(uris[1]))
        val message=share.getStringExtra(Intent.EXTRA_TEXT)!!
        assertFalse(message.contains("Fotos/mapas"));assertFalse(message.contains("anexados dos lotes"))
        for(lot in listOf("A","B","C")) assertTrue(message.contains("Lote: $lot"))
        assertTrue(message.contains("Área: 10 ha"));assertTrue(message.contains("Produtividade: 80 t/ha"))
        assertTrue(share.flags and Intent.FLAG_GRANT_READ_URI_PERMISSION!=0)
    }
    @Test fun aStockWithoutALocalMapSendsItsMessageAsTextWithoutGeneratingAnImageReport() {
        val a=activity()
        val row=JSONObject().put("id","one").put("os","V/01").put("talhoes","4 a 8").put("areaRestante","15").put("produtividade","75")
        a.getSharedPreferences("controle_colheita",0).edit().putString("progresso",row.toString()).commit()
        ReflectionHelpers.callInstanceMethod<Unit>(a,"escolherCompartilhamentoEstoque")
        val share=awaitShare(a,"text/plain")
        assertEquals(Intent.ACTION_SEND,share.action);assertFalse(share.hasExtra(Intent.EXTRA_STREAM))
        assertTrue(share.getStringExtra(Intent.EXTRA_TEXT)!!.contains("Lote: V/01"))
        assertFalse(File(store.cache,"relatorios").exists())
    }
    @Test fun aChangedSessionCannotDispatchAStockMessageOrItsPhotos() {
        val a=activity()
        val message=Intent(Intent.ACTION_SEND).apply {type="text/plain";putExtra(Intent.EXTRA_TEXT,"Estoque")}
        assertFalse(StockSharing.deliver(a,message) {false})
        assertNull(shadowOf(a).nextStartedActivity)
    }

    @Test fun anUnshareableFileShowsAnErrorWithoutCrashingOrOpeningAnEmptyChooser() {
        val a=activity();val privateFile=image(File(app.filesDir,"fora-das-pastas-compartilhadas.png"))
        assertFalse(FieldReportSharing.deliverImages(a,report,listOf(privateFile)))
        assertFalse(a.isFinishing);assertNull(shadowOf(a).nextStartedActivity)
    }
    @Test fun aChangedSessionCannotDispatchPreviouslyPreparedImages() {
        val a=activity();val file=image(File(store.cache,"teste.png"))
        assertFalse(FieldReportSharing.deliverImages(a,report,listOf(file)) {false})
        assertNull(shadowOf(a).nextStartedActivity)
    }
    @Test fun savingAnUnchangedMapDoesNotChangeTheLastDrawingDateSignal() {
        val a=activity();val id="c".repeat(64);val view=MapaView(a)
        view.carregar(Bitmap.createBitmap(80,80,Bitmap.Config.ARGB_8888).apply {eraseColor(Color.WHITE)},id,null)
        var changes=0;view.aoSalvar={if(it) changes++}
        assertTrue(view.salvar());assertEquals(0,changes)
        ReflectionHelpers.getField<MutableList<MutableList<PointF>>>(view,"areas").add(mutableListOf(PointF(5f,5f),PointF(50f,5f),PointF(50f,50f)))
        ReflectionHelpers.getField<MutableList<Int>>(view,"coresAreas").add(Color.RED)
        assertTrue(view.salvar());assertEquals(1,changes)
        assertTrue(view.salvar());assertEquals(1,changes)
    }

}
