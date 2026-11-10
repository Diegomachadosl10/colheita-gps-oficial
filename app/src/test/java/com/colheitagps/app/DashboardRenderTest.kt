package com.colheitagps.app

import android.app.Application
import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.View
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.robolectric.util.ReflectionHelpers
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[28],manifest=Config.NONE,application=Application::class,qualifiers="w393dp-h820dp-xhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class DashboardRenderTest {
    @Test fun nativeDashboardRendersWithoutRequiringFirebaseOrAWeatherConnection() {
        val activity=Robolectric.buildActivity(MainActivity::class.java).get()
        activity.setTheme(android.R.style.Theme_Material_Light_NoActionBar)
        ReflectionHelpers.setField(activity,"teamAccess",TeamAccess(TeamPolicy.PRIMARY_ADMIN_UID,mapOf("nome" to "Diego")))
        ReflectionHelpers.setField(activity,"teamStore",TeamStore(activity.applicationContext,"santo_inacio_C"))
        ReflectionHelpers.setField(activity,"papelAtual","administrador")
        ReflectionHelpers.setField(activity,"acessoConfirmado",true)
        ReflectionHelpers.setField(activity,"syncMessage","Registros sincronizados • fotos e PDFs por compartilhamento")
        activity.javaClass.getDeclaredMethod("inicio").apply { isAccessible=true }.invoke(activity)
        val view=activity.window.decorView
        val width=786; val height=1640
        view.measure(View.MeasureSpec.makeMeasureSpec(width,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(height,View.MeasureSpec.EXACTLY))
        view.layout(0,0,width,height)
        val bitmap=Bitmap.createBitmap(width,height,Bitmap.Config.ARGB_8888)
        view.draw(Canvas(bitmap))
        assertTrue(view.measuredHeight==height&&view.measuredWidth==width)
        System.getenv("DM_AGRO_SCREENSHOT")?.let { path -> File(path).apply { parentFile?.mkdirs(); outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG,100,it) } } }
        bitmap.recycle()
    }
}
