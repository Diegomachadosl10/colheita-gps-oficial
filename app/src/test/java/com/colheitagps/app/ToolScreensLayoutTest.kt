package com.colheitagps.app

import android.app.Application
import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RuntimeEnvironment
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.robolectric.shadows.ShadowPopupMenu
import org.robolectric.shadows.ShadowAlertDialog
import org.robolectric.util.ReflectionHelpers
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[28,35],manifest=Config.NONE,application=Application::class,qualifiers="w393dp-h820dp-xhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ToolScreensLayoutTest {
    private fun views(v:View):List<View> = listOf(v)+(if(v is ViewGroup) (0 until v.childCount).flatMap {views(v.getChildAt(it))} else emptyList())
    private fun activity():MainActivity {
        val a=Robolectric.buildActivity(MainActivity::class.java).get();AppTheme.apply(a)
        ReflectionHelpers.setField(a,"teamAccess",TeamAccess(TeamPolicy.PRIMARY_ADMIN_UID,mapOf("nome" to "Diego")))
        ReflectionHelpers.setField(a,"teamStore",TeamStore(a.applicationContext,"santo_inacio_C"))
        ReflectionHelpers.setField(a,"papelAtual","administrador");ReflectionHelpers.setField(a,"acessoConfirmado",true)
        val all=JSONArray().put(JSONObject().put("id","1").put("equipamento","21571").put("descricao","Pneu"))
            .put(JSONObject().put("id","2").put("equipamento","21571").put("descricao","Óleo").put("concluida",true))
            .put(JSONObject().put("id","3").put("equipamento","22071").put("descricao","Mangueira").put("urgente",true))
        a.getSharedPreferences("registros_v3",0).edit().putString("pendencias",MaintenanceRecords.merge(MaintenanceRecords.issues(all),all).toString()).putString("estoques_salvos",JSONArray()
            .put(JSONObject().put("id","k").put("os","K/39").put("talhoes","1 a 10").put("areaRestante","38").put("produtividade","80"))
            .put(JSONObject().put("id","m").put("os","K/05").put("talhoes","17 a 21").put("areaRestante","54").put("produtividade","70")).toString()).commit()
        return a
    }
    private fun call(a:MainActivity,name:String) {ReflectionHelpers.callInstanceMethod<Unit>(a,name)}
    private fun measure(a:MainActivity,widthDp:Int=393) {
        val root=a.window.decorView;val d=a.resources.displayMetrics.density;val w=(widthDp*d).toInt();val h=(820*d).toInt()
        root.measure(View.MeasureSpec.makeMeasureSpec(w,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(h,View.MeasureSpec.EXACTLY));root.layout(0,0,w,h)
    }
    private fun screenshot(a:MainActivity,name:String,night:Boolean) {
        System.getenv("DM_AGRO_THEME_PREVIEW_DIR")?.let {folder->
            val root=a.window.decorView;val image=Bitmap.createBitmap(root.width,root.height,Bitmap.Config.ARGB_8888);root.draw(Canvas(image))
            File(folder).mkdirs();File(folder,"$name-${if(night) "noturno" else "claro"}.png").outputStream().use {image.compress(Bitmap.CompressFormat.PNG,100,it)};image.recycle()
        }
    }
    @Test fun toolPagesUseSpacedActionsInBothThemesAndOpeningPagesDoesNotModifyRecords() {
        val a=activity()
        // Catalog migration can reorder the array; compare identities and contents, not array order.
        fun snapshot()=a.getSharedPreferences("registros_v3",0).all.mapValues {(_,value)->
            if(value is String&&value.startsWith("[")) {
                val rows=JSONArray(value)
                (0 until rows.length()).map {rows.getJSONObject(it)}.associate {it.getString("id") to it.toString()}
            } else value
        }
        val records=snapshot()
        for(night in listOf(false,true)) {
            AppTheme.setNight(a,night);AppTheme.apply(a)
            for(page in listOf("estoque","pendencias","pecas","chuvas","ocorrenciasCampo","caminhoes","paginaOrdens","calendario","biblioteca","configuracoes")) {
                when(page) {
                    "pecas","ocorrenciasCampo" -> a.javaClass.declaredMethods.first {it.name==page}.apply {isAccessible=true}.invoke(a,false)
                    "calendario" -> a.javaClass.declaredMethods.first {it.name==page}.apply {isAccessible=true}.invoke(a,"14/11/2026",{})
                    else -> call(a,page)
                }
                measure(a)
                val nodes=views(a.window.decorView)
                for(button in nodes.filterIsInstance<Button>()) {
                    assertTrue("$page: ${button.text}",button.height>=48*a.resources.displayMetrics.density)
                    assertTrue("$page: ${button.text}",button.width>0)
                }
                for(parent in nodes.filterIsInstance<LinearLayout>().filter {it.orientation==LinearLayout.VERTICAL}) {
                    val children=(0 until parent.childCount).map {parent.getChildAt(it)}.filter {it.visibility==View.VISIBLE}
                    for((first,second) in children.zipWithNext()) if(first is Button&&second is Button) {
                        assertTrue("Touching buttons: $page",second.top>first.bottom)
                    }
                }
                screenshot(a,page,night)
            }
        }
        assertEquals(records,snapshot())
    }
    @Test fun overflowHasOnlySettingsThemeAndLogoutAndTheToolShareActionIsContextual() {
        val a=activity();call(a,"inicio")
        fun openMenu():List<String> {
            views(a.window.decorView).single {it.contentDescription=="Mais opções"}.performClick()
            val menu=ShadowPopupMenu.getLatestPopupMenu().menu
            return (0 until menu.size()).map {menu.getItem(it).title.toString()}
        }
        assertEquals(listOf("Tema do aplicativo","Configurações","Sair da conta"),openMenu())
        val menu=ShadowPopupMenu.getLatestPopupMenu().menu
        assertTrue(menu.performIdentifierAction(2,0))
        val labels=views(a.window.decorView).filterIsInstance<TextView>().map {it.text.toString()}
        assertTrue(labels.containsAll(listOf("Verificar atualização","Notificações","Registros excluídos","Sincronização")))
        call(a,"estoque");assertEquals(listOf("Tema do aplicativo","Configurações","Sair da conta"),openMenu())
        call(a,"pendencias");assertEquals(listOf("Compartilhar imagem","Enviar em PDF","Tema do aplicativo","Configurações","Sair da conta"),openMenu())
    }
    @Test fun tileActionsStackOnNarrowScreensAndLargeFontsKeepTheirLabelsAndReachTheRightForm() {
        for(fontScale in listOf(1f,1.6f)) {
            RuntimeEnvironment.setQualifiers("w320dp-h640dp-xhdpi")
            val a=activity();val config=a.resources.configuration;config.fontScale=fontScale
            @Suppress("DEPRECATION") a.resources.updateConfiguration(config,a.resources.displayMetrics)
            call(a,"estoque");measure(a,320)
            val rows=views(a.window.decorView).filterIsInstance<LinearLayout>().filter {it.tag=="atalhosFerramenta"}
            assertEquals(4,rows.size)
            val buttons=rows.flatMap {views(it).filterIsInstance<Button>()}
            assertEquals(listOf("Novo estoque","Vários lotes","Compartilhar","Calendário"),buttons.map {it.text.toString()})
            for(button in buttons) {
                assertTrue(button.height>=88*a.resources.displayMetrics.density)
                assertNotNull(button.layout);assertEquals(0,button.layout.getEllipsisCount(0))
            }
            buttons.single {it.text=="Vários lotes"}.performClick()
            assertTrue(ShadowAlertDialog.getLatestAlertDialog().isShowing)
            assertEquals(4,views(ShadowAlertDialog.getLatestAlertDialog().window!!.decorView).filterIsInstance<EditText>().size)
            ShadowAlertDialog.getLatestAlertDialog().dismiss()
        }
    }
}
