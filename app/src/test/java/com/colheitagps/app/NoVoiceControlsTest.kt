package com.colheitagps.app

import android.app.Application
import android.graphics.Bitmap
import android.graphics.PointF
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.util.ReflectionHelpers

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[28,35],manifest=Config.NONE,application=Application::class)
class NoVoiceControlsTest {
    private fun views(view:View):List<View> = listOf(view)+(if(view is ViewGroup) (0 until view.childCount).flatMap {views(view.getChildAt(it))} else emptyList())
    @Test fun homeAndSettingsHaveNoVoiceControlsEvenIfTheOldPreferenceWasEnabled() {
        val main=Robolectric.buildActivity(MainActivity::class.java).get()
        val access=TeamAccess("person",mapOf("nome" to "João","papel" to "supervisor","status" to "aprovado","frenteId" to "santo_inacio","turno" to "C"))
        ReflectionHelpers.setField(main,"teamAccess",access);ReflectionHelpers.setField(main,"teamStore",TeamStore(main.applicationContext,"santo_inacio_C"))
        ReflectionHelpers.setField(main,"papelAtual","supervisor");ReflectionHelpers.setField(main,"acessoConfirmado",true)
        val oldScope=java.security.MessageDigest.getInstance("SHA-256").digest("person|santo_inacio_C".toByteArray()).joinToString("") {"%02x".format(it)}
        main.applicationContext.getSharedPreferences("voz_$oldScope",0).edit().putBoolean("inicio",true).commit()
        for(screen in listOf("inicio","configuracoes")) {
            main.javaClass.getDeclaredMethod(screen).apply {isAccessible=true}.invoke(main)
            val controls=views(main.window.decorView).flatMap {view->listOfNotNull(view.contentDescription?.toString(),(view as? TextView)?.takeIf {it.isClickable}?.text?.toString())}
            assertFalse("Não deve haver controles de voz em $screen",controls.any {Regex("voz|ditar|microfone|🎙|🎤",RegexOption.IGNORE_CASE).containsMatchIn(it)})
            if(screen=="inicio") assertTrue(views(main.window.decorView).filterIsInstance<TextView>().any {it.text.toString()=="Mapas"})
        }
    }
    @Test fun existingPaintedAreasRemainReadableWithoutTheVoiceFeature() {
        val app=RuntimeEnvironment.getApplication()
        fun area(x:Int)=JSONArray().put(JSONObject().put("x",x).put("y",10)).put(JSONObject().put("x",x+10).put("y",10))
            .put(JSONObject().put("x",x+10).put("y",30)).put(JSONObject().put("x",x).put("y",30))
        val saved=JSONObject().put("areas",JSONArray().put(area(10)).put(area(40))).put("linhas",JSONArray())
            .put("coresAreas",JSONArray().put(0x6600AA00).put(0x6600AA00)).put("coresLinhas",JSONArray()).toString()
        app.getSharedPreferences("pinturas",0).edit().putString("existing-map",saved).commit()
        val map=MapaView(app);map.carregar(Bitmap.createBitmap(100,100,Bitmap.Config.ARGB_8888),"existing-map",null)
        val areas=ReflectionHelpers.getField<List<List<PointF>>>(map,"areas")
        assertEquals(2,areas.size);assertEquals(10f,areas[0][0].x,0f);assertEquals(40f,areas[1][0].x,0f)
        assertEquals(saved,app.getSharedPreferences("pinturas",0).getString("existing-map",""))
    }
}
