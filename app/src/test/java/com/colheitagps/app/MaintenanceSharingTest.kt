package com.colheitagps.app

import android.app.Application
import android.content.Intent
import android.content.pm.ProviderInfo
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.os.Looper
import android.view.View
import android.view.ViewGroup
import android.webkit.MimeTypeMap
import android.widget.CheckBox
import android.widget.RadioGroup
import android.widget.TextView
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
import org.robolectric.shadows.ShadowAlertDialog
import org.robolectric.shadows.ShadowContentResolver
import org.robolectric.util.ReflectionHelpers
import java.io.File
import java.time.Duration

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[28,35],manifest=Config.NONE,application=Application::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class MaintenanceSharingTest {
    private val app get()=RuntimeEnvironment.getApplication()
    private val store get()=TeamStore(app,"santo_inacio_C")
    private fun issue(id:String,equipment:String="21571",description:String="Braço do trator quebrado")=JSONObject()
        .put("id",id).put("equipamento",equipment).put("descricao",description).put("osPendencia","12345").put("dataCadastro","14/11/2026")
    private fun views(v:View):List<View> = listOf(v)+(if(v is ViewGroup) (0 until v.childCount).flatMap {views(v.getChildAt(it))} else emptyList())
    @Before fun realProviderUsesOnlyTheExistingProductionRoots() {
        shadowOf(MimeTypeMap.getSingleton()).addExtensionMimeTypeMapping("png","image/png")
        val authority="${app.packageName}.files"
        val info=ProviderInfo().apply {this.authority=authority;packageName=app.packageName;name=FileProvider::class.java.name;exported=false;grantUriPermissions=true
            metaData=Bundle().apply {putInt("android.support.FILE_PROVIDER_PATHS",R.xml.shared_paths)}}
        shadowOf(app.packageManager).addOrUpdateProvider(info)
        ReflectionHelpers.getStaticField<MutableMap<String,Any>>(FileProvider::class.java,"sCache").clear()
        FileProvider().also {it.attachInfo(app,info);ShadowContentResolver.registerProviderInternal(authority,it)}
    }
    private fun activity():MainActivity {
        val a=Robolectric.buildActivity(MainActivity::class.java).get();AppTheme.apply(a)
        ReflectionHelpers.setField(a,"teamStore",store)
        ReflectionHelpers.setField(a,"teamAccess",TeamAccess("sup",mapOf("papel" to "supervisor","status" to "aprovado","frenteId" to "santo_inacio","turno" to "C")))
        ReflectionHelpers.setField(a,"papelAtual","supervisor");ReflectionHelpers.setField(a,"acessoConfirmado",true)
        return a
    }
    private fun image(path:String,color:Int):File=File(store.files,path).apply {
        parentFile!!.mkdirs();val bitmap=Bitmap.createBitmap(160,100,Bitmap.Config.ARGB_8888)
        bitmap.eraseColor(color);outputStream().use {bitmap.compress(Bitmap.CompressFormat.PNG,100,it)};bitmap.recycle()
    }
    private fun awaitShare(a:MainActivity):Intent {
        val end=System.nanoTime()+Duration.ofSeconds(15).toNanos()
        while(System.nanoTime()<end) {
            shadowOf(Looper.getMainLooper()).idle()
            shadowOf(a).nextStartedActivity?.let {chooser->
                assertEquals(Intent.ACTION_CHOOSER,chooser.action)
                return chooser.getParcelableExtra(Intent.EXTRA_INTENT)!!
            }
            Thread.sleep(20)
        }
        error("O seletor de compartilhamento não abriu")
    }
    private fun bytes(uri:Uri)=app.contentResolver.openInputStream(uri)!!.use {it.readBytes()}
    @Test fun allOpenIssuesExcludeEquipmentPartsCompletedDeletedAndDeletedEquipmentAndTextKeepsTheBusinessFields() {
        val rows=JSONArray().put(issue("normal")).put(issue("urgent","22071","Mangueira rompida").put("urgente",true))
            .put(issue("done").put("concluida",true)).put(issue("deleted").put("__arquivado",true))
            .put(JSONObject().put("id","eq").put("categoriaRegistro","equipamento").put("equipamento","21571"))
            .put(JSONObject().put("id","part").put("categoriaRegistro","peca").put("codigoPeca","P001"))
            .put(JSONObject().put("id","removed-eq").put("categoriaRegistro","equipamento").put("equipamento","999").put("__arquivado",true))
            .put(issue("orphan","999"))
        val before=rows.toString();val open=MaintenanceShareData.open(rows)
        assertEquals(setOf("normal","urgent"),open.map {it.getString("id")}.toSet())
        assertEquals(listOf("urgent"),MaintenanceShareData.open(rows,"22071").map {it.getString("id")})
        open.first().put("autorUid","PRIVATE-UID").put("foto","/private/secret.jpg").put("__syncBase",42)
        val text=MaintenanceShareData.message(open,"Santo Inácio • Turno C","14/11/2026 14:00")
        for(s in listOf("PENDÊNCIAS EM ABERTO","21571","22071","Braço do trator quebrado","Mangueira rompida","Prioridade: Urgente","O.S.: 12345","Data: 14/11/2026")) assertTrue(s,text.contains(s))
        for(s in listOf("PRIVATE-UID","/private","__syncBase","999","P001")) assertFalse(s,text.contains(s))
        assertEquals(before,rows.toString())
    }
    @Test fun aTextOnlyShipmentContainsEveryOpenIssueWithoutPhotoStreamsOrFilePermissionsAndKeepsStoredAttachments() {
        val photo=image("photo.png",Color.RED);val row=issue("one").put("fotos",JSONArray().put(photo.path))
        val before=row.toString()
        val intent=MaintenanceSharing.prepare(app,store,listOf(row,issue("two","22071","Revisar freio")),false,false,"14/11/2026")
        assertEquals("text/plain",intent.type);assertEquals(Intent.ACTION_SEND,intent.action)
        assertTrue(intent.getStringExtra(Intent.EXTRA_TEXT)!!.contains("Revisar freio"))
        assertFalse(intent.hasExtra(Intent.EXTRA_STREAM));assertNull(intent.clipData)
        assertEquals(0,intent.flags and Intent.FLAG_GRANT_READ_URI_PERMISSION)
        assertEquals(before,row.toString());assertTrue(photo.isFile)
    }
    @Test fun oneIssueWithTwoPhotosRendersOneImageContainingTextAndBothPhotosInsteadOfThreePages() {
        val red=image("red.png",Color.RED);val blue=image("blue.png",Color.BLUE)
        val row=issue("one").put("fotos",JSONArray().put(red.path).put(blue.path));val before=row.toString()
        val folder=File(store.cache,"same-page")
        val files=MaintenanceShareRenderer.render(folder,store.files,listOf(row),"Santo Inácio • Turno C","14/11/2026",true)
        assertEquals(1,files.size)
        val bitmap=BitmapFactory.decodeFile(files.single().path)
        assertEquals(1000,bitmap.width);assertTrue(bitmap.height<2500)
        // The upper body contains the text; both original colored photos appear lower on that same bitmap.
        var ink=0;var redPixels=0;var bluePixels=0
        for(y in 120 until bitmap.height-70 step 3) for(x in 40 until bitmap.width-40 step 3) {
            val pixel=bitmap.getPixel(x,y)
            if(y<520&&pixel!=Color.WHITE) ink++
            if(pixel==Color.RED) redPixels++
            if(pixel==Color.BLUE) bluePixels++
        }
        assertTrue(ink>100);assertTrue(redPixels>500);assertTrue(bluePixels>500)
        bitmap.recycle();assertEquals(before,row.toString())
    }
    @Test fun photoOmissionAlsoAppliesToImageModeAndPhotosFromAnotherTeamAreNeverAttached() {
        val red=image("local.png",Color.RED)
        val other=File(TeamStore(app,"sandovalina_C").files,"private.png").apply {parentFile!!.mkdirs();red.copyTo(this,true)}
        val row=issue("one").put("fotos",JSONArray().put(red.path).put(other.path)).put("foto",red.path)
        assertEquals(listOf(red.canonicalFile),MaintenanceShareData.photos(store.files,row))
        val with=MaintenanceSharing.prepare(app,store,listOf(row),true,true,"14/11/2026")
        val without=MaintenanceSharing.prepare(app,store,listOf(row),true,false,"14/11/2026")
        for((intent,hasRed) in listOf(with to true,without to false)) {
            assertEquals(Intent.ACTION_SEND,intent.action);assertEquals("image/png",intent.type)
            assertEquals(1,intent.clipData!!.itemCount)
            assertEquals(Intent.FLAG_GRANT_READ_URI_PERMISSION,intent.flags and Intent.FLAG_GRANT_READ_URI_PERMISSION)
            val data=bytes(intent.getParcelableExtra(Intent.EXTRA_STREAM)!!);val bitmap=BitmapFactory.decodeByteArray(data,0,data.size)
            var count=0
            for(y in 120 until bitmap.height step 5) for(x in 0 until bitmap.width step 5) if(bitmap.getPixel(x,y)==Color.RED) count++
            assertEquals(hasRed,count>0);bitmap.recycle()
        }
    }
    @Test fun aBatchSharesAllImageCardsAtOnceAndTextWithPhotosStillIncludesIssuesWithoutLocalPhotos() {
        val photo=image("batch.png",Color.RED)
        val rows=listOf(issue("one").put("fotos",JSONArray().put(photo.path)),issue("two","22071","Sem foto mas precisa fazer"))
        val images=MaintenanceSharing.prepare(app,store,rows,true,true,"14/11/2026")
        assertEquals(Intent.ACTION_SEND_MULTIPLE,images.action)
        val uris=images.getParcelableArrayListExtra<Uri>(Intent.EXTRA_STREAM)!!;assertEquals(2,uris.size)
        assertEquals(2,images.clipData!!.itemCount);uris.forEach {assertTrue(bytes(it).size>1000)}
        val message=MaintenanceSharing.prepare(app,store,rows,false,true,"14/11/2026")
        assertEquals(Intent.ACTION_SEND,message.action);assertEquals("image/png",message.type)
        assertTrue(message.getStringExtra(Intent.EXTRA_TEXT)!!.contains("Sem foto mas precisa fazer"))
        assertTrue(message.getStringExtra(Intent.EXTRA_TEXT)!!.contains("Braço do trator quebrado"))
    }
    @Test fun shareAllIgnoresTheSearchRefreshesOpenStatusAtConfirmationAndOffersNoPhotoText() {
        val a=activity();val first=issue("first");val second=issue("second","22071","Manutenção do freio")
        a.getSharedPreferences("registros_v3",0).edit().putString("pendencias",JSONArray().put(first).put(second).toString()).commit()
        ReflectionHelpers.setField(a,"buscaEquipamento","21571")
        ReflectionHelpers.callInstanceMethod<Unit>(a,"pendencias")
        views(a.window.decorView).filterIsInstance<TextView>().single {it.text=="Compartilhar todas as abertas"}.performClick()
        val dialog=ShadowAlertDialog.getLatestAlertDialog()
        assertTrue(views(dialog.window!!.decorView).filterIsInstance<TextView>().any {it.text.toString().contains("2 pendências")})
        val formats=dialog.window!!.decorView.findViewWithTag<RadioGroup>("formatoEnvioPendencias");assertEquals(5101,formats.checkedRadioButtonId)
        val photos=dialog.window!!.decorView.findViewWithTag<CheckBox>("incluirFotosPendencias");assertFalse(photos.isChecked)
        // A record completed on another screen/device while the dialog is open must no longer be sent.
        first.put("concluida",true)
        a.getSharedPreferences("registros_v3",0).edit().putString("pendencias",JSONArray().put(first).put(second).toString()).commit()
        dialog.getButton(android.app.AlertDialog.BUTTON_POSITIVE).performClick()
        val share=awaitShare(a);assertEquals("text/plain",share.type)
        val text=share.getStringExtra(Intent.EXTRA_TEXT)!!;assertTrue(text.contains("22071"));assertFalse(text.contains("21571"))
        assertFalse(a.isFinishing)
    }
    @Test fun shareIsCancelledIfTheAccountOrTeamChangesBeforeDelivery() {
        val a=activity();val intent=Intent(Intent.ACTION_SEND).apply {type="text/plain";putExtra(Intent.EXTRA_TEXT,"texto")}
        assertFalse(MaintenanceSharing.deliver(a,intent) {false});assertNull(shadowOf(a).nextStartedActivity)
        a.finish();assertFalse(MaintenanceSharing.deliver(a,intent) {true})
    }
}
