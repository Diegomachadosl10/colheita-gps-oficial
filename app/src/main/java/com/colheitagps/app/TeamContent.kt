package com.colheitagps.app

import org.json.JSONArray
import org.json.JSONObject

/** On Spark, media stays on the device; remote text updates must preserve those local attachments. */
internal object TeamContent {
    val mediaFields = listOf("foto", "fotos", "fotoEstoque")
    fun textOnly(source: JSONObject): JSONObject = JSONObject(source.toString()).apply {
        val hasMedia = source.optBoolean("anexosManuais") || source.optJSONArray("fotos")?.length()?.let { it > 0 } == true ||
            source.optString("foto").isNotBlank() || source.optString("fotoEstoque").isNotBlank()
        mediaFields.forEach { remove(it) }
        if (hasMedia) put("anexosManuais", true)
    }
    fun preserveLocalMedia(incoming: JSONObject, local: JSONObject?): JSONObject = JSONObject(incoming.toString()).apply {
        mediaFields.forEach { field ->
            remove(field)
            if (local?.has(field) == true) put(field, local.get(field))
        }
    }
}
