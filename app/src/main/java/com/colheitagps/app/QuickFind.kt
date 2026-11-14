package com.colheitagps.app

import java.text.Normalizer
import java.util.Locale

/** Filters only the already authorized local list; no broader query is made to Firebase. */
internal object QuickFind {
    private val marks=Regex("\\p{M}+")
    private val separators=Regex("[^\\p{L}\\p{N}]+")
    fun key(value:String):String=Normalizer.normalize(value,Normalizer.Form.NFD)
        .replace(marks,"").lowercase(Locale.ROOT).replace(separators," ").trim()
    fun matches(query:String,vararg values:String):Boolean {
        val tokens=key(query).split(' ').filter {it.isNotEmpty()}
        if(tokens.isEmpty()) return true
        val content=key(values.joinToString(" "));val compact=content.replace(" ","")
        return tokens.all {content.contains(it)||compact.contains(it)}
    }
}
