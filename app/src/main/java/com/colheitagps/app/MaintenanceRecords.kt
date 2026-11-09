package com.colheitagps.app

import org.json.JSONArray
import org.json.JSONObject
import java.security.MessageDigest
import java.text.Normalizer
import java.util.Locale

/** Equipment cards share the team's maintenance permissions and survive completion/deletion of issues. */
internal object MaintenanceRecords {
    private const val CATEGORY = "equipamento"
    fun isEquipment(record: JSONObject) = record.optString("categoriaRegistro") == CATEGORY
    fun number(value: String): String = Normalizer.normalize(value, Normalizer.Form.NFKC)
        .trim().replace(Regex("\\s+"), " ").uppercase(Locale.ROOT)
    fun equipmentId(value: String): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(number(value).toByteArray(Charsets.UTF_8))
        return "equipamento_" + digest.joinToString("") { "%02x".format(it) }
    }
    fun issues(all: JSONArray): JSONArray = JSONArray().apply {
        for (i in 0 until all.length()) all.optJSONObject(i)?.takeUnless(::isEquipment)?.let { put(it) }
    }
    fun ensureEquipment(all: JSONArray, equipment: String): Boolean {
        val normalized = number(equipment)
        if (normalized.isBlank()) return false
        for (i in 0 until all.length()) {
            val record = all.optJSONObject(i) ?: continue
            if (isEquipment(record) && number(record.optString("equipamento")) == normalized) return false
        }
        all.put(JSONObject().put("id", equipmentId(normalized)).put("categoriaRegistro", CATEGORY).put("equipamento", normalized))
        return true
    }
    fun merge(issues: JSONArray, previous: JSONArray): JSONArray = JSONArray().apply {
        for (i in 0 until previous.length()) previous.optJSONObject(i)?.takeIf(::isEquipment)?.let { put(JSONObject(it.toString())) }
        for (i in 0 until issues.length()) issues.optJSONObject(i)?.takeUnless(::isEquipment)?.let { put(JSONObject(it.toString())) }
        val equipment = (0 until length()).mapNotNull { optJSONObject(it) }
            .filterNot(::isEquipment).map { it.optString("equipamento") }.filter { it.isNotBlank() }
        equipment.forEach { ensureEquipment(this, it) }
    }
    data class Summary(val number: String, val open: Int, val completed: Int)
    fun summaries(all: JSONArray): List<Summary> {
        val counts = linkedMapOf<String, Pair<Int, Int>>()
        for (i in 0 until all.length()) {
            val record = all.optJSONObject(i) ?: continue
            val number = number(record.optString("equipamento"))
            if (number.isBlank()) continue
            val current = counts[number] ?: (0 to 0)
            counts[number] = when {
                isEquipment(record) -> current
                record.optBoolean("concluida") -> current.first to current.second + 1
                else -> current.first + 1 to current.second
            }
        }
        return counts.map { Summary(it.key, it.value.first, it.value.second) }
            .sortedWith(compareBy<Summary> { it.open == 0 }.thenBy(String.CASE_INSENSITIVE_ORDER) { it.number })
    }
}
