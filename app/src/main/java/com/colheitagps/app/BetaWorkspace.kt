package com.colheitagps.app

/** One test organisation. Never select a company or fall back to production data. */
internal object BetaWorkspace {
    const val ID = "beta"
    const val TITLE = "Nossa equipe • Beta gratuita"
    const val SUBTITLE = "Gestão Operacional Agrícola"
    const val ROOT = "ambientes/beta"
    const val LOCAL = "beta"
    val notificationsReady = false
    val credentialChangesReady = false
    fun path(relative: String): String {
        require(relative.isNotBlank() && !relative.startsWith('/') && relative.split('/').none { it in setOf("", ".", "..") })
        return "$ROOT/$relative"
    }
    fun prefs(name: String) = "beta_$name"
}
