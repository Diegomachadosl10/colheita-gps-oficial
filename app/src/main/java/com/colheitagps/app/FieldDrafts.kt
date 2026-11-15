package com.colheitagps.app

import android.content.Context
import android.text.Editable
import android.text.TextWatcher
import android.widget.EditText
import org.json.JSONObject

/** Local drafts never become operational records until Save is confirmed. */
internal class FieldDrafts(context:Context,team:String,uid:String) {
    private val prefs=context.applicationContext.getSharedPreferences("rascunhos_campo_${team}_${uid}",Context.MODE_PRIVATE)
    fun read(key:String):JSONObject?=try {prefs.getString(key,null)?.let(::JSONObject)} catch(_:Exception) {null}
    fun write(key:String,row:JSONObject) {check(prefs.edit().putString(key,row.toString()).commit())}
    fun clear(key:String) {check(prefs.edit().remove(key).commit())}
    companion object {
        fun watch(fields:List<EditText>,save:()->Unit) {
            fields.forEach {it.addTextChangedListener(object:TextWatcher {
                override fun beforeTextChanged(s:CharSequence?,start:Int,count:Int,after:Int) {}
                override fun onTextChanged(s:CharSequence?,start:Int,before:Int,count:Int) {save()}
                override fun afterTextChanged(s:Editable?) {}
            })}
        }
    }
}
