package com.colheitagps.app

import android.content.Context
import android.content.SharedPreferences
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/** All operational preferences and files are isolated by a verified team. Legacy data is untouched. */
internal class TeamStore(context: Context, val team: String) {
    private val app = context.applicationContext
    val lock = Any()
    init { require(team in TeamPolicy.fronts.keys.flatMap { f -> TeamPolicy.shifts.map { "${f}_$it" } }) }
    val files = File(app.filesDir, "equipes/$team").apply { mkdirs() }
    val cache = File(app.cacheDir, "equipes/$team").apply { mkdirs() }
    fun prefs(name: String): SharedPreferences = app.getSharedPreferences("equipe_${team}_$name", Context.MODE_PRIVATE)
    fun encode(value: String): String = codec(value, true)
    fun decode(value: String): String = codec(value, false)
    fun decodeLegacy(value: String): String = codec(value, false, true)
    private fun codec(value: String, export: Boolean, legacy: Boolean = false): String {
        fun convert(input: Any?, field: String = ""): Any? = when (input) {
            is JSONObject -> input.also { obj -> obj.keys().asSequence().toList().forEach { if (export && it == "__syncBase") obj.remove(it) else obj.put(it, convert(obj.opt(it), it)) } }
            is JSONArray -> input.also { arr -> for (i in 0 until arr.length()) arr.put(i, convert(arr.opt(i), field)) }
            is String -> when {
                input.startsWith("@arquivo/") -> try {
                    val path = file(input.removePrefix("@arquivo/"))
                    if (export) input else path.absolutePath
                } catch (_: Exception) { "" }
                (input.startsWith(app.filesDir.absolutePath + "/") || input.startsWith("/data/") ||
                    (field in setOf("foto", "fotos", "fotoEstoque") && input.startsWith("/"))) -> {
                    val path = File(input).canonicalFile
                    if (legacy && path.path.startsWith(app.filesDir.canonicalPath + File.separator) &&
                        !path.path.startsWith(File(app.filesDir, "equipes").canonicalPath + File.separator))
                        file(path.relativeTo(app.filesDir.canonicalFile).invariantSeparatorsPath).absolutePath
                    else if (!path.path.startsWith(files.canonicalPath + File.separator)) ""
                    else if (export) "@arquivo/" + path.relativeTo(files).invariantSeparatorsPath else path.absolutePath
                }
                else -> input
            }
            else -> input
        }
        val parsed: Any = try { JSONObject(value) } catch (_: Exception) { try { JSONArray(value) } catch (_: Exception) { value } }
        return convert(parsed).toString()
    }
    fun file(relative: String): File {
        require(relative.isNotBlank() && !relative.startsWith('/') && relative.split('/').none { it == ".." || it.isBlank() })
        return File(files, relative).canonicalFile.also { require(it.path.startsWith(files.canonicalPath + File.separator)) }
    }
    fun hasLegacy(): Boolean = TeamPolicy.preferences.any { app.getSharedPreferences(it, 0).all.isNotEmpty() } ||
        app.filesDir.listFiles().orEmpty().any { it.isFile && it.extension.lowercase() in setOf("jpg", "jpeg", "png", "pdf") }
    fun importLegacy() {
        val tracking = app.getSharedPreferences("migracao_equipes", 0)
        check(tracking.getString("destino", null).let { it == null || it == team }) { "Os dados antigos já foram importados para outra equipe." }
        for (name in TeamPolicy.preferences) {
            val target = prefs(name)
            val edit = target.edit()
            for ((key, original) in app.getSharedPreferences(name, 0).all) {
                if (target.contains(key)) {
                    if (original is String && (name == "registros_v3" || (name == "controle_colheita" && key == "movimentos"))) {
                        val merged = try { JSONArray(target.getString(key, "[]")) } catch (_: Exception) { JSONArray() }
                        val incoming = try { JSONArray(codec(original, false, true)) } catch (_: Exception) { JSONArray() }
                        val ids = (0 until merged.length()).mapNotNull { merged.optJSONObject(it)?.optString("id") }.toMutableSet()
                        for (i in 0 until incoming.length()) {
                            val item = incoming.optJSONObject(i) ?: continue
                            if (item.optString("id").isBlank()) item.put("id", java.util.UUID.nameUUIDFromBytes("$name/$key/$i/$item".toByteArray()).toString())
                            if (ids.add(item.getString("id"))) merged.put(item)
                        }
                        edit.putString(key, merged.toString())
                    }
                    continue
                }
                when (original) {
                    is String -> edit.putString(key, codec(original, false, true))
                    is Float -> edit.putFloat(key, original)
                    is Long -> edit.putLong(key, original)
                    is Int -> edit.putInt(key, original)
                    is Boolean -> edit.putBoolean(key, original)
                }
            }
            check(edit.commit())
        }
        app.filesDir.walkTopDown().onEnter { it != File(app.filesDir, "equipes") }.filter { it.isFile }.forEach { source ->
            if (source.extension.lowercase() in setOf("jpg", "jpeg", "png", "pdf")) {
                val target = file(source.relativeTo(app.filesDir).invariantSeparatorsPath)
                target.parentFile?.mkdirs()
                if (!target.exists()) source.copyTo(target)
            }
        }
        tracking.edit().putString("destino", team).commit()
    }
}
