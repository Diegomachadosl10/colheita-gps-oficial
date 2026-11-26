package com.colheitagps.app

/** Enrollment only; blocked/deleted accounts never gain access through this policy. */
internal object EnrollmentPolicy {
    fun initial(requested:String):Map<String,String> {
        require(requested in setOf("operador","tratorista","ajudante","supervisor"))
        return mapOf("papel" to "operador", "perfil" to "operador", "solicitacaoPerfil" to requested, "status" to "pendente")
    }
}
