package com.colheitagps.app

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.RippleDrawable
import android.view.Gravity
import android.view.View
import android.view.accessibility.AccessibilityNodeInfo
import android.widget.LinearLayout
import android.widget.RadioButton
import android.widget.TextView

/** Single profile choice; access is granted only after Firestore accepts the enrollment. */
internal class ProfileChoice(context:Context):LinearLayout(context) {
    enum class Profile(val label:String,val requested:String,val description:String,val icon:FieldIcon) {
        OPERATOR("Operador","operador","Registrar atividades no campo",FieldIcon.EQUIPMENT),
        SUPERVISOR("Supervisor","supervisor","Acompanhar a própria equipe",FieldIcon.PEOPLE)
    }
    private val green=0xFF187447.toInt()
    private val dark=0xFF153F28.toInt()
    private val cards=mutableListOf<LinearLayout>()
    private val radios=mutableListOf<RadioButton>()
    var selectedProfile=Profile.OPERATOR
        private set
    private fun dp(value:Int)=(value*resources.displayMetrics.density).toInt()
    init {
        orientation=VERTICAL
        for((index,profile) in Profile.entries.withIndex()) {
            val row=object:LinearLayout(context) {
                override fun onInitializeAccessibilityNodeInfo(info:AccessibilityNodeInfo) {
                    super.onInitializeAccessibilityNodeInfo(info)
                    info.className=RadioButton::class.java.name; info.isCheckable=true; info.isChecked=isSelected
                    info.collectionItemInfo=AccessibilityNodeInfo.CollectionItemInfo.obtain(index,1,0,1,false,isSelected)
                }
            }.apply {
                orientation=HORIZONTAL; gravity=Gravity.CENTER_VERTICAL
                minimumHeight=dp(98); setPadding(dp(14),dp(14),dp(10),dp(14))
                contentDescription="${profile.label}. ${profile.description}"
                isFocusable=true; importantForAccessibility=View.IMPORTANT_FOR_ACCESSIBILITY_YES
                setOnClickListener { select(profile) }
            }
            row.addView(FieldIconView(context,profile.icon,dark).apply { importantForAccessibility=View.IMPORTANT_FOR_ACCESSIBILITY_NO },LayoutParams(dp(40),dp(40)))
            row.addView(LinearLayout(context).apply {
                orientation=VERTICAL; setPadding(dp(12),0,dp(5),0)
                importantForAccessibility=View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS
                addView(TextView(context).apply { text=profile.label; textSize=17f; setTextColor(dark); setTypeface(null,Typeface.BOLD) })
                addView(TextView(context).apply { text=profile.description; textSize=13f; setTextColor(0xFF52665B.toInt()); setPadding(0,dp(4),0,0) })
            },LayoutParams(0,-2,1f))
            val radio=RadioButton(context).apply {
                isClickable=false; isFocusable=false; importantForAccessibility=View.IMPORTANT_FOR_ACCESSIBILITY_NO
                buttonTintList=ColorStateList(arrayOf(intArrayOf(android.R.attr.state_checked),intArrayOf()),intArrayOf(green,0xFF6D8073.toInt()))
            }
            row.addView(radio,LayoutParams(dp(40),dp(48)))
            cards.add(row); radios.add(radio)
            addView(row,LayoutParams(-1,-2).apply { if(index>0) topMargin=dp(10) })
        }
        select(Profile.OPERATOR)
    }
    private fun select(profile:Profile) {
        if(!isEnabled) return
        selectedProfile=profile
        for((index,card) in cards.withIndex()) {
            val selected=Profile.entries[index]==profile
            card.isSelected=selected; radios[index].isChecked=selected
            val shape=GradientDrawable().apply {
                setColor(if(selected) 0xFFE8F6EA.toInt() else Color.WHITE); cornerRadius=dp(16).toFloat()
                setStroke(dp(if(selected) 2 else 1),if(selected) green else 0xFFCEDCD1.toInt())
            }
            card.background=RippleDrawable(ColorStateList.valueOf(0x22187447),shape,null)
            card.invalidate()
        }
    }
    fun setSelectable(value:Boolean) {
        isEnabled=value; alpha=if(value) 1f else 0.65f
        cards.forEach { it.isEnabled=value; it.isClickable=value }
        radios.forEach { it.isEnabled=value }
    }
    override fun onInitializeAccessibilityNodeInfo(info:AccessibilityNodeInfo) {
        super.onInitializeAccessibilityNodeInfo(info)
        info.collectionInfo=AccessibilityNodeInfo.CollectionInfo.obtain(2,1,false,AccessibilityNodeInfo.CollectionInfo.SELECTION_MODE_SINGLE)
    }
}
