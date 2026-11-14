package com.colheitagps.app

import android.app.AlertDialog
import android.app.Application
import android.os.Looper
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowAlertDialog
import org.robolectric.util.ReflectionHelpers
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[28],manifest=Config.NONE,application=Application::class)
class ActiveMapCatalogTest {
    private val store get()=TeamStore(RuntimeEnvironment.getApplication(),"santo_inacio_C")
    private fun files(id:String,pdf:Boolean=true):File {
        if(pdf) File(store.files,"mapas/$id.pdf").apply {parentFile!!.mkdirs();writeText("fixture")}
        return File(store.files,"mapa_relatorio_$id.png").apply {writeText("fixture")}
    }
    private fun seed():File {
        val active=files("a".repeat(64))
        files("b".repeat(64)) // PDF and cached image without a library entry.
        files("c".repeat(64),false) // Library entry and cached image, but missing PDF.
        files("d".repeat(64)) // A map removed from the active library.
        store.prefs("mapas").edit().putString("a".repeat(64),"K-39 Parte 01.pdf")
            .putString("c".repeat(64),"Arquivo ausente.pdf").putString("d".repeat(64),"Excluído.pdf").commit()
        store.prefs("mapas").edit().remove("d".repeat(64)).commit()
        TeamStore(RuntimeEnvironment.getApplication(),"colorado_C").prefs("mapas").edit()
            .putString("b".repeat(64),"Mapa de outra frente.pdf").commit()
        return active
    }
    @Test fun onlyMapsWithAnActiveLibraryEntryAndAnExistingPdfAreOfferedWithoutDeletingAttachments() {
        val active=seed();val items=PaintedMapPicker.items(store)
        assertEquals(1,items.size);assertEquals(active,items.single().file);assertEquals("K-39 Parte 01",items.single().title)
        assertEquals(4,store.files.listFiles().orEmpty().count {it.name.startsWith("mapa_relatorio_")})
        store.prefs("mapas").edit().putString("a".repeat(64),"Nome novo.pdf").commit()
        assertEquals("Nome novo",PaintedMapPicker.items(store).single().title)
        store.prefs("mapas").edit().remove("a".repeat(64)).commit()
        assertTrue(PaintedMapPicker.items(store).isEmpty());assertTrue(active.isFile)
    }
    @Test fun theSingleStockPickerUsesTheSameActiveCatalogAsTheBatchPicker() {
        seed()
        val a=Robolectric.buildActivity(MainActivity::class.java).get();a.setTheme(android.R.style.Theme_Material_Light_NoActionBar)
        ReflectionHelpers.setField(a,"teamStore",store)
        ReflectionHelpers.setField(a,"teamAccess",TeamAccess("sup",mapOf("papel" to "supervisor","status" to "aprovado","frenteId" to "santo_inacio","turno" to "C")))
        ReflectionHelpers.setField(a,"papelAtual","supervisor");ReflectionHelpers.setField(a,"acessoConfirmado",true)
        store.prefs("controle_colheita").edit().putString("progresso","{\"os\":\"K/39\",\"talhoes\":\"1 a 10\",\"areaRestante\":\"38\",\"produtividade\":\"80\"}").commit()
        ReflectionHelpers.callInstanceMethod<Unit>(a,"escolherCompartilhamentoEstoque");shadowOf(Looper.getMainLooper()).idle()
        val items=ShadowAlertDialog.getLatestAlertDialog().listView.adapter
        assertEquals(listOf("K-39 Parte 01","Sem foto do mapa"),(0 until items.count).map {items.getItem(it).toString()})
        assertFalse(a.isFinishing)
    }
}
