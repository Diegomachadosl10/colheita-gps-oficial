package com.colheitagps.app

import android.app.Application
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.Canvas
import android.graphics.pdf.PdfDocument
import android.net.Uri
import androidx.core.content.FileProvider
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.robolectric.annotation.Implements
import org.robolectric.annotation.Implementation
import org.robolectric.util.ReflectionHelpers
import org.robolectric.util.ReflectionHelpers.ClassParameter
import java.io.ByteArrayInputStream
import java.io.File
import java.io.OutputStream
import javax.xml.parsers.DocumentBuilderFactory

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[28],manifest=Config.NONE,application=Application::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class StockShareFilesTest {
    private val app get()=RuntimeEnvironment.getApplication()
    private val store get()=TeamStore(app,"santo_inacio_C")
    /** Use the actual provider and its production roots; Config.NONE does not load manifest metadata. */
    @Before fun configureProviderFromTheProductionPathsXml() {
        val xml=listOf(File("src/main/res/xml/shared_paths.xml"),File("app/src/main/res/xml/shared_paths.xml")).first {it.isFile}
        val strategy=ReflectionHelpers.callConstructor<Any>(Class.forName("androidx.core.content.FileProvider\$SimplePathStrategy"),ClassParameter.from(String::class.java,"${app.packageName}.files"))
        val nodes=DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(xml).documentElement.childNodes
        for(i in 0 until nodes.length) {
            val element=nodes.item(i) as? org.w3c.dom.Element?:continue
            val base=when(element.tagName) {"cache-path"->app.cacheDir;"files-path"->app.filesDir;else->continue}
            ReflectionHelpers.callInstanceMethod<Unit>(strategy,"addRoot",ClassParameter.from(String::class.java,element.getAttribute("name")),ClassParameter.from(File::class.java,File(base,element.getAttribute("path"))))
        }
        ReflectionHelpers.getStaticField<MutableMap<String,Any>>(FileProvider::class.java,"sCache")["${app.packageName}.files"]=strategy
    }
    private fun image(name:String)=File(store.files,name).apply {
        val bitmap=Bitmap.createBitmap(40,30,Bitmap.Config.ARGB_8888);bitmap.eraseColor(Color.GREEN)
        outputStream().use {bitmap.compress(Bitmap.CompressFormat.PNG,100,it)};bitmap.recycle()
    }
    private fun row(id:String,file:File?=null)=JSONObject().put("id",id).put("os",id).put("talhoes","1 a 3").put("areaRestante","10").put("produtividade","80").apply {file?.let {put("fotoEstoque",it.path)}}
    @Test fun onePhotoAndSeveralTextOnlyLotsShareTogetherUsingTheConfiguredCacheProvider() {
        val a=image("a.png");val records=listOf(row("LOTE-A",a),row("LOTE-B"))
        val text=StockBatch.text(records,"Santo Inácio • Turno C","hoje")
        val intent=StockShareFiles.message(app,store.cache,store.files,records,text)
        assertEquals(Intent.ACTION_SEND,intent.action);assertEquals("image/*",intent.type)
        assertTrue(intent.getStringExtra(Intent.EXTRA_TEXT)!!.contains("LOTE-A"));assertTrue(intent.getStringExtra(Intent.EXTRA_TEXT)!!.contains("LOTE-B"))
        val uri=intent.getParcelableExtra<Uri>(Intent.EXTRA_STREAM)!!
        assertEquals("${app.packageName}.files",uri.authority);assertTrue(uri.path!!.startsWith("/compartilhamento_equipes/"))
        assertEquals(1,intent.clipData!!.itemCount);assertTrue(intent.flags and Intent.FLAG_GRANT_READ_URI_PERMISSION!=0)
        assertTrue(a.isFile)
    }
    @Test fun twoPhotosAreBothGrantedAndCopiedWithoutChangingTheirOriginalLots() {
        val records=listOf(row("A",image("a.png")),row("B",image("b.png")),row("C"))
        val before=records.map {it.toString()}
        val intent=StockShareFiles.message(app,store.cache,store.files,records,StockBatch.text(records,"equipe","hoje"))
        assertEquals(Intent.ACTION_SEND_MULTIPLE,intent.action);assertEquals(2,intent.getParcelableArrayListExtra<Uri>(Intent.EXTRA_STREAM)!!.size)
        assertEquals(2,intent.clipData!!.itemCount);assertEquals(before,records.map {it.toString()})
    }
    @Test fun lotsWithoutPhotosRemainAValidTextShipmentAndFilesOutsideTheTeamAreExcluded() {
        val foreign=File(app.filesDir,"outra-equipe.png").apply {writeText("privado")}
        val intent=StockShareFiles.message(app,store.cache,store.files,listOf(row("A"),row("B",foreign)),"Dois lotes sem foto")
        assertEquals("text/plain",intent.type);assertEquals(Intent.ACTION_SEND,intent.action)
        assertFalse(intent.hasExtra(Intent.EXTRA_STREAM));assertNull(intent.clipData)
    }
    @Test fun galleryPhotoIsCopiedAndInvalidFilesLeaveNoPartialAttachment() {
        val valid=Uri.parse("content://test-photo/valid");val original=image("original.png")
        shadowOf(app.contentResolver).registerInputStream(valid,ByteArrayInputStream(original.readBytes()))
        val copy=StockShareFiles.copyImage(app,store.files,valid)
        assertTrue(copy.canonicalPath.startsWith(store.files.canonicalPath+File.separator));assertArrayEquals(original.readBytes(),copy.readBytes());assertEquals("png",copy.extension)
        val invalid=Uri.parse("content://test-photo/invalid");shadowOf(app.contentResolver).registerInputStream(invalid,ByteArrayInputStream("not an image".toByteArray()))
        try {StockShareFiles.copyImage(app,store.files,invalid);fail()} catch(_:IllegalArgumentException) {}
        assertTrue(File(store.files,"fotos_estoque").listFiles().orEmpty().none {it.extension=="tmp"})
    }
    @Test @Config(shadows=[RecordingStockPdf::class]) fun aCombinedPdfComposesTheSummaryAndOnlyTheTwoSelectedPhotos() {
        val records=listOf(row("A",image("a.png")),row("B"),row("C",image("c.png")))
        RecordingStockPdf.pages.clear()
        val file=StockShareFiles.pdf(store.cache,store.files,records,StockBatch.text(records,"Santo Inácio • Turno C","hoje"))
        assertEquals(listOf(1,2,3),RecordingStockPdf.pages.map {it.pageNumber})
        assertTrue(RecordingStockPdf.pages.all {it.pageWidth==595&&it.pageHeight==842});assertTrue(file.isFile)
    }
}

/** Robolectric 4.14 does not implement Android's native PDF writer. Capture the real
 * composition calls while Canvas/Bitmap rendering remains native; this is not a PDF file test. */
@Implements(PdfDocument::class)
class RecordingStockPdf {
    @Implementation fun __constructor__() {}
    @Implementation fun startPage(info:PdfDocument.PageInfo):PdfDocument.Page = ReflectionHelpers.callConstructor(PdfDocument.Page::class.java,
        ClassParameter.from(Canvas::class.java,Canvas(Bitmap.createBitmap(info.pageWidth,info.pageHeight,Bitmap.Config.ARGB_8888))),ClassParameter.from(PdfDocument.PageInfo::class.java,info))
    @Implementation fun finishPage(page:PdfDocument.Page) {pages.add(page.info)}
    @Implementation fun writeTo(output:OutputStream) {output.write(byteArrayOf())}
    @Implementation fun close() {}
    companion object {val pages=mutableListOf<PdfDocument.PageInfo>()}
}
