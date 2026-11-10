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
    fun isArchived(record: JSONObject) = record.optBoolean("__arquivado")
    fun withArchiveState(record: JSONObject, archived: Boolean): JSONObject = JSONObject(record.toString()).apply {
        if (archived) put("__arquivado", true) else remove("__arquivado")
    }
    fun archive(record: JSONObject, uid: String, name: String, date: String): JSONObject =
        withArchiveState(record, true).apply {
            put("arquivadoPorUid", uid); put("arquivadoPorNome", name); put("arquivadoEm", date)
        }
    fun archiveEquipment(all: JSONArray, equipment: String, uid: String, name: String, date: String): JSONArray = JSONArray().apply {
        for (i in 0 until all.length()) all.optJSONObject(i)?.let { item ->
            put(if (number(item.optString("equipamento")) == number(equipment) && !isArchived(item))
                archive(item, uid, name, date) else JSONObject(item.toString()))
        }
    }
    fun archivedEquipment(all: JSONArray): List<String> = (0 until all.length()).mapNotNull { all.optJSONObject(it) }
        .filter { isEquipment(it) && isArchived(it) }.map { number(it.optString("equipamento")) }.filter { it.isNotBlank() }.distinct().sorted()
    fun correctNumber(all: JSONArray, previous: String, replacement: String, uid: String, name: String, date: String): JSONArray {
        val old = number(previous); val next = number(replacement)
        require(next.isNotBlank() && next.length <= 80 && next != old) { "Informe um número diferente, com até 80 caracteres." }
        require((0 until all.length()).mapNotNull { all.optJSONObject(it) }.none { number(it.optString("equipamento")) == next }) {
            "Esse equipamento já possui registros. A correção não pode misturar os históricos."
        }
        val result = JSONArray()
        for (i in 0 until all.length()) all.optJSONObject(i)?.let { item ->
            result.put(when {
                number(item.optString("equipamento")) != old -> JSONObject(item.toString())
                isEquipment(item) -> archive(item, uid, name, date).put("numeroCorrigidoPara", next)
                else -> JSONObject(item.toString()).put("equipamento", next).put("numeroEquipamentoAnterior", old)
            })
        }
        ensureEquipment(result, next)
        return result
    }
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
            .filterNot { isEquipment(it) || isArchived(it) }.map { it.optString("equipamento") }.filter { it.isNotBlank() }
        equipment.forEach { ensureEquipment(this, it) }
    }
    data class Summary(val number: String, val open: Int, val completed: Int)
    fun summaries(all: JSONArray): List<Summary> {
        val counts = linkedMapOf<String, Pair<Int, Int>>()
        for (i in 0 until all.length()) {
            val record = all.optJSONObject(i) ?: continue
            if (isArchived(record)) continue
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
