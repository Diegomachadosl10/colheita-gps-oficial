package com.colheitagps.app

import android.content.Context
import java.security.MessageDigest

/** Personal device preference, separated by signed-in UID and team; never changes the map catalog. */
internal class MapFavorites(context:Context,team:String,uid:String) {
    private val preferences:android.content.SharedPreferences
    init {
        require(uid.isNotBlank())
        val owner=MessageDigest.getInstance("SHA-256").digest(uid.toByteArray()).joinToString("") {"%02x".format(it)}
        preferences=context.applicationContext.getSharedPreferences(BetaWorkspace.prefs("favoritos_mapas_${team}_$owner"),Context.MODE_PRIVATE)
    }
    fun ids():Set<String> = preferences.getStringSet("ids",emptySet()).orEmpty().toSet()
    fun toggle(id:String) {
        require(id.isNotBlank()&&id.length<=250&&!id.contains('/'))
        val next=ids().toMutableSet();if(!next.add(id)) next.remove(id)
        check(preferences.edit().putStringSet("ids",next).commit())
    }
}
