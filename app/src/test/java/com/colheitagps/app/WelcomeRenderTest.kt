package com.colheitagps.app

import android.app.Activity
import android.app.Application
import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[28],manifest=Config.NONE,application=Application::class,qualifiers="w393dp-h820dp-xhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class WelcomeRenderTest {
    private fun views(view:View):List<View> = listOf(view)+(if(view is ViewGroup) (0 until view.childCount).flatMap { views(view.getChildAt(it)) } else emptyList())
    @Test fun welcomeOffersBothAccessPathsAndTodaysVerseWithoutStartingAuthentication() {
        val a=Robolectric.buildActivity(Activity::class.java).get();a.setTheme(android.R.style.Theme_Material_Light_NoActionBar)
        var login=false;var register=false
        WelcomeUi.render(a,{login=true},{register=true})
        val root=a.window.decorView
        val texts=views(root).filterIsInstance<TextView>()
        texts.single { it.text.toString()=="Entrar" }.performClick()
        texts.single { it.text.toString()=="Solicitar acesso" }.performClick()
        assertTrue(login&&register);assertTrue(texts.any { it.text.toString()==DailyVerse.forDate().text })
        root.measure(View.MeasureSpec.makeMeasureSpec(786,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(1640,View.MeasureSpec.EXACTLY));root.layout(0,0,786,1640)
        val bitmap=Bitmap.createBitmap(786,1640,Bitmap.Config.ARGB_8888);root.draw(Canvas(bitmap))
        System.getenv("DM_AGRO_WELCOME_SCREENSHOT")?.let { path->File(path).outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG,100,it) } }
        bitmap.recycle()
    }
}
