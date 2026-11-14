package com.colheitagps.app

import android.app.Activity
import android.app.AlertDialog
import android.content.Context
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.view.View
import android.widget.EditText

internal object AppTheme {
    data class Colors(val background:Int,val surface:Int,val alternate:Int,val text:Int,val muted:Int,val accent:Int,val border:Int,val danger:Int,val favorite:Int) {
        val primary=0xFF187447.toInt()
        val header=0xFF103F28.toInt()
    }
    private val light=Colors(0xFFF7F8F4.toInt(),Color.WHITE,0xFFEDF3ED.toInt(),0xFF163C28.toInt(),0xFF52665B.toInt(),0xFF187447.toInt(),0xFFD5E1D7.toInt(),0xFFB42318.toInt(),0xFF946000.toInt())
    private val dark=Colors(0xFF111714.toInt(),0xFF1D2822.toInt(),0xFF27362D.toInt(),0xFFF1F6F1.toInt(),0xFFB8C8BD.toInt(),0xFFA7DF99.toInt(),0xFF42594A.toInt(),0xFFFF8C85.toInt(),0xFFFFD77A.toInt())
    private fun prefs(context:Context)=context.applicationContext.getSharedPreferences("dm_agro_aparencia",Context.MODE_PRIVATE)
    fun night(context:Context)=prefs(context).getBoolean("noturno",false)
    fun colors(context:Context)=if(night(context)) dark else light
    fun setNight(context:Context,value:Boolean) {check(prefs(context).edit().putBoolean("noturno",value).commit())}
    fun apply(activity:Activity) {
        activity.setTheme(if(night(activity)) android.R.style.Theme_Material_NoActionBar else android.R.style.Theme_Material_Light_NoActionBar)
        activity.window.statusBarColor=colors(activity).background
        activity.window.navigationBarColor=colors(activity).background
        @Suppress("DEPRECATION")
        activity.window.decorView.systemUiVisibility=if(night(activity)) 0 else View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR or View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR
    }
    fun choose(activity:Activity,changed:()->Unit) {
        AlertDialog.Builder(activity).setTitle("Tema do aplicativo")
            .setSingleChoiceItems(arrayOf("Claro e limpo","Noturno"),if(night(activity)) 1 else 0) {dialog,index->
                val next=index==1
                if(next!=night(activity)) {setNight(activity,next);apply(activity);dialog.dismiss();changed()} else dialog.dismiss()
            }.setNegativeButton("Cancelar",null).show()
    }
    fun field(view:EditText) {
        val p=colors(view.context);val density=view.resources.displayMetrics.density
        view.setTextColor(p.text);view.setHintTextColor(p.muted);view.textSize=16f;view.minHeight=(52*density).toInt()
        view.setPadding((14*density).toInt(),(12*density).toInt(),(14*density).toInt(),(12*density).toInt())
        view.backgroundTintList=null
        view.background=GradientDrawable().apply {setColor(p.alternate);cornerRadius=12*density;setStroke(maxOf(1,density.toInt()),p.border)}
    }
    fun surface(context:Context,color:Int):Int = if(night(context)&&color in setOf(Color.WHITE,0xFFF1F6F1.toInt(),0xFFEEF5EF.toInt(),0xFFE2F4D9.toInt(),0xFFE8F6EA.toInt())) colors(context).alternate else color
}
