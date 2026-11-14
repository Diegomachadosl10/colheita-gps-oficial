package com.colheitagps.app

import android.app.Application
import android.graphics.Bitmap
import android.graphics.Canvas
import android.os.Looper
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.TextView
import androidx.core.graphics.ColorUtils
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.robolectric.shadows.ShadowAlertDialog
import org.robolectric.util.ReflectionHelpers
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[28,35],manifest=Config.NONE,application=Application::class,qualifiers="w393dp-h820dp-xhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class AppThemeScreenTest {
    private fun views(v:View):List<View> = listOf(v)+(if(v is ViewGroup) (0 until v.childCount).flatMap {views(v.getChildAt(it))} else emptyList())
    private fun activity():MainActivity {
        val a=Robolectric.buildActivity(MainActivity::class.java).get();AppTheme.apply(a)
        ReflectionHelpers.setField(a,"teamAccess",TeamAccess(TeamPolicy.PRIMARY_ADMIN_UID,mapOf("nome" to "Diego")))
        ReflectionHelpers.setField(a,"teamStore",TeamStore(a.applicationContext,"santo_inacio_C"))
        ReflectionHelpers.setField(a,"papelAtual","administrador");ReflectionHelpers.setField(a,"acessoConfirmado",true)
        ReflectionHelpers.setField(a,"syncMessage","Registros sincronizados")
        return a
    }
    @Test fun lightIsDefaultAndBothThemesHaveReadableTextAndUrgencyOnTheirSurfaces() {
        val app=RuntimeEnvironment.getApplication();assertFalse(AppTheme.night(app))
        for(night in listOf(false,true)) {
            AppTheme.setNight(app,night);val c=AppTheme.colors(app)
            assertTrue(ColorUtils.calculateContrast(c.text,c.surface)>7)
            assertTrue(ColorUtils.calculateContrast(c.muted,c.surface)>4.5)
            assertTrue(ColorUtils.calculateContrast(c.danger,c.surface)>4.5)
            assertTrue(ColorUtils.calculateContrast(c.accent,c.surface)>4.5)
            assertEquals(night,AppTheme.night(app))
        }
    }
    @Test fun choosingNightFromTheStockPageKeepsTheAccountTeamAndSearchAndTheChoicePersists() {
        val a=activity();val team=ReflectionHelpers.getField<TeamStore>(a,"teamStore")
        val row=JSONObject().put("id","k").put("os","K/39").put("talhoes","1 a 10").put("areaRestante","38").put("produtividade","80")
        team.prefs("registros_v3").edit().putString("estoques_salvos",JSONArray().put(row).toString()).commit()
        ReflectionHelpers.callInstanceMethod<Unit>(a,"estoque")
        views(a.window.decorView).filterIsInstance<EditText>().single {it.contentDescription=="Buscar lote ou talhão"}.setText("K39")
        ReflectionHelpers.callInstanceMethod<Unit>(a,"escolherTema")
        val dialog=ShadowAlertDialog.getLatestAlertDialog();dialog.listView.performItemClick(null,1,1);shadowOf(Looper.getMainLooper()).idle()
        assertTrue(AppTheme.night(a))
        assertEquals(TeamPolicy.PRIMARY_ADMIN_UID,ReflectionHelpers.getField<TeamAccess>(a,"teamAccess").uid)
        assertEquals("santo_inacio_C",ReflectionHelpers.getField<TeamStore>(a,"teamStore").team)
        val query=views(a.window.decorView).filterIsInstance<EditText>().single {it.contentDescription=="Buscar lote ou talhão"}
        assertEquals("K39",query.text.toString());assertEquals(AppTheme.colors(a).text,query.currentTextColor)
        assertTrue(ReflectionHelpers.getField<Boolean>(a,"acessoConfirmado"));assertFalse(a.isFinishing)
        assertTrue(AppTheme.night(Robolectric.buildActivity(MainActivity::class.java).get()))
    }
    @Test fun bothSelectedStylesRenderEveryHomeToolAndTheDailyVerse() {
        val a=activity()
        for(night in listOf(false,true)) {
            AppTheme.setNight(a,night);AppTheme.apply(a);ReflectionHelpers.callInstanceMethod<Unit>(a,"inicio")
            val root=a.window.decorView;root.measure(View.MeasureSpec.makeMeasureSpec(786,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(1640,View.MeasureSpec.EXACTLY));root.layout(0,0,786,1640)
            val labels=views(root).filterIsInstance<TextView>().map {it.text.toString()}
            for(title in listOf("Mapas","Pendências","Estoque","Peças a pedir","Ocorrências","Chuva por turno","Caminhões","Calendário")) assertTrue(title,labels.contains(title))
            assertFalse(labels.contains("Relatórios"));assertTrue(labels.contains("Versículo do dia"))
            System.getenv("DM_AGRO_THEME_PREVIEW_DIR")?.let {folder->
                val bitmap=Bitmap.createBitmap(786,1640,Bitmap.Config.ARGB_8888);root.draw(Canvas(bitmap))
                File(folder).mkdirs();File(folder,if(night) "DM-AGRO-noturno.png" else "DM-AGRO-claro.png").outputStream().use {bitmap.compress(Bitmap.CompressFormat.PNG,100,it)};bitmap.recycle()
            }
        }
    }
}
