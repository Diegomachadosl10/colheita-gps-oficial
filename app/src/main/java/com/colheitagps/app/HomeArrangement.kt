package com.colheitagps.app

import android.content.Context
import org.json.JSONArray

internal class HomeArrangement(context:Context,team:String,uid:String) {
    private val prefs=context.applicationContext.getSharedPreferences("inicio_${team}_${uid}",Context.MODE_PRIVATE)
    fun order(available:List<String>):List<String> {
        val saved=try {JSONArray(prefs.getString("ordem","[]"))} catch(_:Exception) {JSONArray()}
        val favorites=prefs.getStringSet("favoritas",emptySet()).orEmpty()
        val ordered=((0 until saved.length()).map {saved.optString(it)}+available).filter {it in available}.distinct()
        return ordered.filter {it in favorites}+ordered.filter {it !in favorites}
    }
    fun favorite(title:String)=title in prefs.getStringSet("favoritas",emptySet()).orEmpty()
    fun save(order:List<String>) {check(prefs.edit().putString("ordem",JSONArray(order).toString()).commit())}
    fun toggle(title:String) {val stars=prefs.getStringSet("favoritas",emptySet()).orEmpty().toMutableSet();if(!stars.add(title)) stars.remove(title);check(prefs.edit().putStringSet("favoritas",stars).commit())}
}
