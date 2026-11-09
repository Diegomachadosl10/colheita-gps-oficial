package com.colheitagps.app

import java.text.Normalizer
import java.util.Locale

/** One interpretation of legacy and current profiles for all entry points. */
internal object AccessPolicy {
    fun normalized(value: Any?): String = Normalizer.normalize(value?.toString().orEmpty().trim(), Normalizer.Form.NFD)
        .replace(Regex("\\p{M}+"), "").lowercase(Locale.ROOT)

    fun internalEmail(matricula: String): String {
        require(matricula.matches(Regex("[0-9]{1,20}")))
        // Preserve leading zeroes: a matrícula is an identifier, never a number.
        return "matricula.$matricula@acesso.dm-agro.app"
    }

    fun role(data: Map<String, Any>?): String {
        val primary = normalized(data?.get("papel"))
        return primary.ifEmpty { normalized(data?.get("perfil")) }
    }

    fun approved(data: Map<String, Any>?): Boolean =
        normalized(data?.get("status")) == "aprovado" &&
            role(data) in setOf("administrador", "supervisor", "funcionario", "operador")

    // A legacy display field alone must never grant user-administration privileges.
    fun canApprove(data: Map<String, Any>?): Boolean = approved(data) &&
        normalized(data?.get("papel")) == "administrador"
}
