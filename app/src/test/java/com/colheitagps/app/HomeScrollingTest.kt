package com.colheitagps.app

import android.app.Application
import android.graphics.Rect
import android.graphics.Bitmap
import android.graphics.Canvas
import android.os.Looper
import android.view.View
import android.view.ViewGroup
import android.widget.ScrollView
import android.widget.TextView
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.robolectric.util.ReflectionHelpers
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[28],manifest=Config.NONE,application=Application::class,qualifiers="w360dp-h640dp-xhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class HomeScrollingTest {
    private fun views(v:View):List<View> = listOf(v)+(if(v is ViewGroup) (0 until v.childCount).flatMap { views(v.getChildAt(it)) } else emptyList())
    @Test fun anOperatorCanScrollPastTheFirstFourTilesAndOpenFieldOccurrences() {
        val a=Robolectric.buildActivity(MainActivity::class.java).get()
        a.setTheme(android.R.style.Theme_Material_Light_NoActionBar)
        ReflectionHelpers.setField(a,"teamAccess",TeamAccess("operador-teste",mapOf("nome" to "João","papel" to "operador","status" to "aprovado","frenteId" to "santo_inacio","turno" to "C")))
        ReflectionHelpers.setField(a,"teamStore",TeamStore(a.applicationContext,"santo_inacio_C"))
        ReflectionHelpers.setField(a,"papelAtual","operador");ReflectionHelpers.setField(a,"acessoConfirmado",true)
        a.javaClass.getDeclaredMethod("inicio").apply { isAccessible=true }.invoke(a)
        val root=a.window.decorView
        root.measure(View.MeasureSpec.makeMeasureSpec(720,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(1280,View.MeasureSpec.EXACTLY));root.layout(0,0,720,1280)
        val scroll=views(root).filterIsInstance<ScrollView>().single()
        val occurrence=views(root).filterIsInstance<TextView>().single { it.text.toString()=="Ocorrências" }
        assertTrue("The last card must retain its minimum height",(occurrence.parent.parent as View).height>=a.resources.displayMetrics.density*88)
        assertTrue("The last card label cannot be collapsed",occurrence.height>0)
        assertTrue("The tools grid must be measured",scroll.getChildAt(0).height>0)
        scroll.scrollTo(0,scroll.getChildAt(0).height)
        assertFalse("The bottom of the tools grid must be reachable",scroll.canScrollVertically(1))
        val bounds=Rect(0,0,occurrence.width,occurrence.height);scroll.offsetDescendantRectToMyCoords(occurrence,bounds)
        assertTrue("Ocorrências must be visible: bounds=$bounds, scrollY=${scroll.scrollY}, viewport=${scroll.height}, content=${scroll.getChildAt(0).height}",bounds.top-scroll.scrollY>=0&&bounds.bottom-scroll.scrollY<=scroll.height)
        System.getenv("DM_AGRO_SCROLL_SCREENSHOT")?.let { path->
            val bitmap=Bitmap.createBitmap(720,1280,Bitmap.Config.ARGB_8888);root.draw(Canvas(bitmap))
            File(path).outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG,100,it) };bitmap.recycle()
        }
        (occurrence.parent.parent as View).performClick()
        assertTrue(views(a.window.decorView).filterIsInstance<TextView>().any { it.text.toString()=="+ Nova ocorrência" })
    }
}
