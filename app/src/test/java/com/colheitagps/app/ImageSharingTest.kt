package com.colheitagps.app

import android.app.Activity
import android.app.Application
import android.content.Intent
import android.net.Uri
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[26,35],manifest=Config.NONE,application=Application::class)
class ImageSharingTest {
    @Test fun oneOrManyPagesShareAsImagesWithGrantsForEveryPageOnOldAndCurrentAndroid() {
        val a=Robolectric.buildActivity(Activity::class.java).get()
        val report=FieldReport("Estoque","Santo Inácio • Turno C",sections=emptyList())
        val one=Uri.parse("content://com.colheitagps.app.files/relatorios/pagina1.png")
        val two=Uri.parse("content://com.colheitagps.app.files/relatorios/pagina2.png")
        for(uris in listOf(arrayListOf(one),arrayListOf(one,two))) {
            val share=FieldReportSharing.imageIntent(a,report,uris)
            assertEquals("image/png",share.type)
            assertEquals(if(uris.size==1) Intent.ACTION_SEND else Intent.ACTION_SEND_MULTIPLE,share.action)
            assertEquals(Intent.FLAG_GRANT_READ_URI_PERMISSION,share.flags and Intent.FLAG_GRANT_READ_URI_PERMISSION)
            assertEquals(uris.size,share.clipData!!.itemCount)
            for((index,uri) in uris.withIndex()) assertEquals(uri,share.clipData!!.getItemAt(index).uri)
            if(uris.size==1) assertEquals(one,share.getParcelableExtra<Uri>(Intent.EXTRA_STREAM))
            else assertEquals(uris,share.getParcelableArrayListExtra<Uri>(Intent.EXTRA_STREAM))
        }
    }
}
