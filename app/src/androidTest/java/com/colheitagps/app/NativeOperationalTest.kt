package com.colheitagps.app

import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import androidx.core.content.FileProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.UUID

/** Native Android PDF, provider and Keystore checks; no Firebase login or real operational data. */
@RunWith(AndroidJUnit4::class)
class NativeOperationalTest {
    private val context get()=InstrumentationRegistry.getInstrumentation().targetContext
    @Test fun generatedPdfCanBeOpenedRenderedAndReadThroughTheActualFileProvider() {
        val folder=File(context.cacheDir,"beta/equipes/santo_inacio_C/native-test-${UUID.randomUUID()}").apply {mkdirs()}
        try {
            val photo=File(folder,"foto-ficticia.jpg")
            Bitmap.createBitmap(80,80,Bitmap.Config.ARGB_8888).also {bitmap->
                bitmap.eraseColor(Color.BLUE);photo.outputStream().use {assertTrue(bitmap.compress(Bitmap.CompressFormat.JPEG,90,it))};bitmap.recycle()
            }
            val report=FieldReport("Relatório de turno • teste fictício","Equipe de teste • Turno C","26/11/2026 • Responsável de teste",
                listOf(ReportSection("Relato",listOf("Registro","Descrição"),(1..65).map {listOf("Equipamento $it","Observação fictícia $it • sem dados reais")})),
                listOf(ReportPicture(photo,"Foto fictícia de teste")))
            val file=FieldReportRenderer.render(report,folder,true).single()
            assertTrue(file.length()>1000)
            val uri=FileProvider.getUriForFile(context,"${context.packageName}.files",file)
            assertEquals("content",uri.scheme)
            context.contentResolver.openInputStream(uri)!!.use {input->
                assertEquals("%PDF-",String(ByteArray(5).also {assertEquals(5,input.read(it))},Charsets.US_ASCII))
            }
            val descriptor=context.contentResolver.openFileDescriptor(uri,"r")!!
            PdfRenderer(descriptor).use {renderer->
                assertTrue(renderer.pageCount>=2)
                renderer.openPage(0).use {page->
                    assertEquals(595,page.width);assertEquals(842,page.height)
                    val bitmap=Bitmap.createBitmap(page.width,page.height,Bitmap.Config.ARGB_8888)
                    page.render(bitmap,null,null,PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                    assertNotEquals(Color.WHITE,bitmap.getPixel(5,5));bitmap.recycle()
                }
            }
            val image=FieldReportRenderer.render(report.copy(sections=report.sections.map {it.copy(rows=it.rows.take(1))}),File(folder,"images"),false).first()
            val imageUri=FileProvider.getUriForFile(context,"${context.packageName}.files",image)
            context.contentResolver.openFileDescriptor(imageUri,"r")!!.use {assertTrue(it.statSize>0)}
        } finally {folder.deleteRecursively()}
    }

    @Test fun deviceKeystoreProtectsTheRestartableLeaseAndRejectsTamperedCiphertext() {
        val uid="native-test-${UUID.randomUUID()}"
        val first=OfflineEntryStore(context)
        try {
            first.remember(uid,mapOf("nome" to "Pessoa fictícia","status" to "aprovado","papel" to "operador","frenteId" to "santo_inacio","turno" to "C"))
            val reopened=OfflineEntryStore(context).read(uid)
            assertNotNull(reopened);assertEquals("santo_inacio_C",reopened!!.team)
            val hash=java.security.MessageDigest.getInstance("SHA-256").digest(uid.toByteArray()).joinToString("") {"%02x".format(it)}
            val file=File(context.noBackupFilesDir,"beta_access_lease/$hash.enc")
            val bytes=file.readBytes();assertFalse(String(bytes,Charsets.ISO_8859_1).contains("Pessoa fictícia"))
            bytes[bytes.lastIndex]=(bytes.last().toInt() xor 1).toByte();file.writeBytes(bytes)
            assertNull(OfflineEntryStore(context).read(uid))
        } finally {first.remove(uid)}
    }
}
