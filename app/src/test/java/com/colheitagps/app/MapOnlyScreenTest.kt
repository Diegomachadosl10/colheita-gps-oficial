package com.colheitagps.app

import android.app.Application
import android.os.Looper
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
class MapOnlyScreenTest {
    private fun activity():MainActivity {
        val a=Robolectric.buildActivity(MainActivity::class.java).get();a.setTheme(android.R.style.Theme_Material_Light_NoActionBar)
        ReflectionHelpers.setField(a,"teamAccess",TeamAccess("sup",mapOf("papel" to "supervisor","status" to "aprovado","frenteId" to "santo_inacio","turno" to "C")))
        ReflectionHelpers.setField(a,"teamStore",TeamStore(a.applicationContext,"santo_inacio_C"));ReflectionHelpers.setField(a,"papelAtual","supervisor");ReflectionHelpers.setField(a,"acessoConfirmado",true)
        a.getSharedPreferences("mapas",0).edit().putString("mapa","Fazenda de teste").commit()
        return a
    }
    private fun choices():List<String> {
        shadowOf(Looper.getMainLooper()).idle()
        val items=ShadowAlertDialog.getLatestAlertDialog().listView.adapter
        return (0 until items.count).map {items.getItem(it).toString()}
    }
    @Test fun libraryActionsAreOnlyMapActionsAndImagesWhileOrdersRemainInTheirOwnTool() {
        val a=activity()
        ReflectionHelpers.callInstanceMethod<Unit>(a,"opcoesMapa",ReflectionHelpers.ClassParameter.from(String::class.java,"mapa"))
        assertEquals(listOf("Abrir","Renomear","Excluir","Compartilhar imagem do mapa"),choices())
    }
    @Test fun paintingAndNormalMenusKeepGpsRotationAndSavingWithoutOrderShortcuts() {
        val a=activity();val map=MapaView(a);ReflectionHelpers.setField(a,"mapa",map)
        for(mode in listOf(0,7)) {
            ReflectionHelpers.setField(map,"modo",mode)
            ReflectionHelpers.callInstanceMethod<Unit>(a,"menu")
            val options=choices()
            assertFalse(options.any {it.contains("O.S.")||it.contains("PDF")})
            assertTrue(options.contains("GPS"));assertTrue(options.contains("Salvar mapa pintado"));assertTrue(options.contains("Como girar o mapa"))
            assertTrue(options.contains("Pintar talhão"));assertTrue(options.contains("Medir área"))
        }
    }
}
