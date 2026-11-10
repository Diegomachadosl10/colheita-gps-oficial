package com.colheitagps.app

import android.app.Activity
import android.app.Application
import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.View
import android.widget.LinearLayout
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[28],manifest=Config.NONE,application=Application::class,qualifiers="w393dp-h650dp-xhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ProfileChoiceRenderTest {
    @Test fun roleCardsRenderWithReadableLabelsAndWholeCardTouchTargets() {
        val a=Robolectric.buildActivity(Activity::class.java).get(); a.setTheme(android.R.style.Theme_Material_Light_NoActionBar)
        val ui=AccessUi(a)
        val body=ui.page("Solicitar acesso","Escolha o perfil que deseja solicitar para trabalhar no campo.")
        val choice=ui.profileChoice(body)
        val root=a.window.decorView; val width=786; val height=1300
        root.measure(View.MeasureSpec.makeMeasureSpec(width,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(height,View.MeasureSpec.EXACTLY))
        root.layout(0,0,width,height)
        assertTrue(choice.getChildAt(0).height>=96*a.resources.displayMetrics.density)
        assertEquals(2,choice.childCount)
        val image=Bitmap.createBitmap(width,height,Bitmap.Config.ARGB_8888); root.draw(Canvas(image))
        System.getenv("DM_AGRO_PROFILE_SCREENSHOT")?.let { path->File(path).apply { parentFile?.mkdirs(); outputStream().use { image.compress(Bitmap.CompressFormat.PNG,100,it) } } }
        image.recycle()
    }
}
