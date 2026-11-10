package com.colheitagps.app

import android.app.Activity
import android.app.Application
import android.content.res.Configuration
import android.view.View
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[28],manifest=Config.NONE,application=Application::class,qualifiers="w320dp-h480dp-xhdpi")
class DashboardGridLayoutTest {
    private fun verify(count:Int,largeText:Boolean=false) {
        val a=Robolectric.buildActivity(Activity::class.java).get()
        if(largeText) a.resources.updateConfiguration(Configuration(a.resources.configuration).apply { fontScale=1.6f },a.resources.displayMetrics)
        val list=LinearLayout(a).apply { orientation=LinearLayout.VERTICAL }
        val scroll=ScrollView(a).apply { addView(list) }
        a.setContentView(scroll)
        var opened=-1
        DashboardUi(a).grid(list,(0 until count).map { index->DashboardUi.Tile("Ferramenta $index",FieldIcon.OCCURRENCE,{opened=index},if(index==0) 8 else 0) })
        val width=(a.resources.configuration.screenWidthDp*a.resources.displayMetrics.density).toInt()
        scroll.measure(View.MeasureSpec.makeMeasureSpec(width,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(400,View.MeasureSpec.EXACTLY));scroll.layout(0,0,width,400)
        val single=largeText||a.resources.configuration.screenWidthDp<320
        assertEquals(if(single) count else (count+1)/2,list.childCount)
        for(i in 0 until list.childCount) {
            val row=list.getChildAt(i) as LinearLayout
            assertTrue("A row must not collapse to the spacer height",row.height>=136*a.resources.displayMetrics.density)
            for(j in 0 until row.childCount) {
                val card=row.getChildAt(j) as? LinearLayout?:continue
                assertTrue(card.height>=136*a.resources.displayMetrics.density)
                val label=card.getChildAt(1) as TextView
                assertTrue("Every label must have visible height",label.height>0)
            }
        }
        val finalCard=(list.getChildAt(list.childCount-1) as LinearLayout).getChildAt(0)
        finalCard.performClick();assertEquals(if(single||count%2==1) count-1 else count-2,opened)
    }
    @Test fun anOddTwoColumnGridKeepsEveryFinalCardUsable() { for(count in listOf(1,3,5,9)) verify(count) }
    @Test @Config(qualifiers="w280dp-h480dp-xhdpi") fun narrowScreensKeepAllCardsInOneColumn() { verify(5) }
    @Test fun enlargedTextKeepsEveryCardUsableInOneColumn() { verify(5,true) }
}
