package com.colheitagps.app

import android.app.Application
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import java.time.LocalDate

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[28],manifest=Config.NONE,application=Application::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class FieldReportTest {
    private val cache get()=TeamStore(RuntimeEnvironment.getApplication(),"santo_inacio_C").cache
    @Test fun allBusinessRecordsUseReadableFieldsAndNeverDumpInternalIdsPathsOrCredentials() {
        val examples=mapOf(
            "pendencias" to JSONObject().put("equipamento","21569").put("descricao","Pneu dianteiro").put("osPendencia","K/05"),
            "estoques_salvos" to JSONObject().put("os","K/05").put("talhoes","1 a 10").put("areaRestante","15").put("produtividade","80"),
            "historico_estoque" to JSONObject().put("os","K/10").put("talhoes","20 a 30").put("areaRestante","25"),
            "ocorrencias_campo" to JSONObject().put("tipo","Erosão").put("localizacao","Carreador 2").put("descricao","Valeta na curva"),
            "relatorios_turno" to JSONObject().put("turno","C").put("descricao","Serviço concluído").put("pendencias","Abastecer"),
            "movimentos" to JSONObject().put("tipo","Entrada").put("quantidade",45).put("data","11/11/2026 12:00"),
            "historico_os" to JSONObject().put("numeroOs","123").put("nomeMapa","Fazenda Santo Inácio").put("talhoes","1 a 4"),
            "caminhoes" to TruckRecords.build(null,"00123","10:00","11:00",LocalDate.of(2026,11,11),null,"santo_inacio_C")
        )
        for((group,record) in examples) {
            record.put("id","INTERNAL-ID").put("autorUid","SECRET-UID").put("foto","/private/photo.jpg").put("__syncBase",9)
            val output=FieldReports.group(group,group,listOf(record)).rows.flatten().joinToString(" ")
            assertTrue("Missing business information for $group",output.isNotBlank())
            for(private in listOf("INTERNAL-ID","SECRET-UID","/private/","__syncBase")) assertFalse(output.contains(private))
        }
        assertTrue(FieldReports.group("Estoque","historico_estoque",listOf(examples.getValue("historico_estoque"))).rows.single().last().contains("20 a 30"))
        val part=PartsRecords.build(null,"P001","Rolamento","2","21569","a_pedir","operador","Pessoa","11/11/2026")
        val text=FieldReports.group("Peças","pendencias",listOf(part)).rows.flatten().joinToString()
        assertTrue(text.contains("Rolamento"));assertTrue(text.contains("Quantidade: 2"));assertTrue(text.contains("21569"))
    }
    @Test fun organizedImagesAreReadableA4PagesAndLongRowsAndPhotosContinueAcrossPages() {
        val folder=File(cache,"report-image-test");folder.deleteRecursively()
        val photo=File(cache,"test-piece.png")
        Bitmap.createBitmap(40,40,Bitmap.Config.ARGB_8888).apply {eraseColor(android.graphics.Color.GREEN);photo.outputStream().use {compress(Bitmap.CompressFormat.PNG,100,it)};recycle()}
        val long=(1..180).joinToString("\n") {"Informação de campo $it"}
        val report=FieldReport("Pendências de manutenção","Santo Inácio • Turno C","11/11/2026",listOf(ReportSection("Equipamentos",listOf("Equipamento","Pendência"),listOf(listOf("21569",long),listOf("21570","Revisar mangueira")))),listOf(ReportPicture(photo,"Peça solicitada para 21569")))
        val before=report.toString();val files=FieldReportRenderer.render(report,folder,false)
        assertTrue(files.size>=5);assertEquals(before,report.toString())
        for(file in files) {
            val bitmap=BitmapFactory.decodeFile(file.path);assertEquals(1190,bitmap.width);assertEquals(1684,bitmap.height)
            assertNotEquals(android.graphics.Color.WHITE,bitmap.getPixel(20,20));bitmap.recycle()
            assertTrue(file.length()>3000)
        }
    }
    @Test @Config(shadows=[RecordingStockPdf::class]) fun pdfCompositionPaginatesTheSameLongTablesAsImages() {
        val rows=(1..90).map {listOf("Caminhão $it","11/11/2026 08:00","11/11/2026 10:00")}
        val report=FieldReport("Entrada e saída de caminhões","Santo Inácio • Turno C","11/11/2026 até 12:00",listOf(ReportSection("Caminhões",listOf("Caminhão","Entrada","Saída"),rows)))
        RecordingStockPdf.pages.clear()
        FieldReportRenderer.render(report,File(cache,"report-pdf-test"),true)
        val pdfPages=RecordingStockPdf.pages.size
        assertTrue(pdfPages>1);assertTrue(RecordingStockPdf.pages.all {it.pageWidth==595&&it.pageHeight==842})
        val images=FieldReportRenderer.render(report,File(cache,"report-same-image-test"),false)
        assertEquals(pdfPages,images.size)
        System.getenv("DM_AGRO_REPORT_PREVIEW")?.let {path->images.first().copyTo(File(path),overwrite=true)}
    }
}
