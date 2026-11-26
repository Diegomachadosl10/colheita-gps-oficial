package com.colheitagps.app

import org.json.JSONArray
import org.json.JSONObject

/** Typed item payloads are authoritative; opaque legacy JSON is only an internal local format. */
internal object BetaRecordCodec {
    fun sameContent(a: String?, b: String): Boolean {
        if (a == null) return false
        fun parsed(raw: String): Any = try { JSONObject(raw) } catch (_: Exception) {
            try { JSONArray(raw) } catch (_: Exception) { raw }
        }
        return canonical(parsed(a)) == canonical(parsed(b))
    }
    private fun canonical(value: Any?): String = when (value) {
        null, JSONObject.NULL -> "null"
        is JSONObject -> value.keys().asSequence().sorted().joinToString(",", "{", "}") { JSONObject.quote(it) + ":" + canonical(value.get(it)) }
        is JSONArray -> (0 until value.length()).joinToString(",", "[", "]") { canonical(value.get(it)) }
        is Number -> value.toString().toBigDecimal().stripTrailingZeros().toPlainString()
        is Boolean -> value.toString()
        else -> JSONObject.quote(value.toString())
    }
    fun toWire(internal: Map<String, Any>): Map<String, Any> {
        val result = internal.toMutableMap()
        result["formato"] = 2L
        if (result["tipo"] == "item") {
            val raw = result.remove("conteudo") as? String ?: error("Registro sem conteúdo")
            result["dados"] = objectMap(JSONObject(raw))
        }
        return result
    }
    fun fromWire(wire: Map<String, Any>): JSONObject {
        require((wire["formato"] as? Number)?.toInt() == 2) { "Formato do Beta não reconhecido; nenhum registro foi substituído." }
        val result = wire.toMutableMap()
        result.remove("formato")
        if (result["tipo"] == "item") {
            require(!result.containsKey("conteudo")) { "Conteúdo ambíguo no servidor" }
            val data = result.remove("dados") as? Map<*, *> ?: error("Registro sem dados estruturados")
            result["conteudo"] = JSONObject(data).toString()
        }
        return JSONObject(result)
    }
    private fun objectMap(obj: JSONObject): Map<String, Any?> = obj.keys().asSequence().associateWith { value(obj.get(it)) }
    private fun value(value: Any?): Any? = when(value) {
        null, JSONObject.NULL -> null
        is JSONObject -> objectMap(value)
        is JSONArray -> (0 until value.length()).map { value(value.get(it)) }
        is Double -> value.also { require(it.isFinite()) }
        is Float -> value.toDouble().also { require(it.isFinite()) }
        is String, is Number, is Boolean -> value
        else -> error("Tipo de dado não suportado")
    }
}
