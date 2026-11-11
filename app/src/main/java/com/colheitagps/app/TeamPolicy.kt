package com.colheitagps.app

internal object TeamPolicy {
    const val AUTOMATIC_ATTACHMENTS = false // Spark: never contact paid Storage for uploads or downloads.
    const val PRIMARY_ADMIN_UID = "Z8zjZP3iWjRSD6imwiDO4VLboOY2"
    val fronts = linkedMapOf(
        "santo_inacio" to "Santo Inácio", "santa_ines" to "Santa Inês", "itororo" to "Itororó",
        "cafeara" to "Cafeara", "lupionopolis" to "Lupionópolis", "colorado" to "Colorado",
        "sandovalina" to "Sandovalina", "jardim_olinda" to "Jardim Olinda", "itaguaje" to "Itaguajé"
    )
    val shifts = listOf("A", "B", "C")
    val leafRoles = setOf("operador", "funcionario", "tratorista")
    val operatorGroups = setOf("pendencias", "ocorrencias_campo")
    val preferences = setOf("registros_v3", "controle_colheita", "mapas", "pinturas", "ordens_servico", "talhoes_por_os")
    val recordGroups = setOf("pendencias", "ocorrencias_campo", "estoques_salvos", "historico_estoque", "relatorios_turno", "caminhoes", "movimentos", "historico_os")
    fun team(data: Map<String, Any>?): String? {
        val front = data?.get("frenteId") as? String ?: return null
        val shift = data["turno"] as? String ?: return null
        return if (front in fronts && shift in shifts) "${front}_$shift" else null
    }
    fun label(team: String): String = "${fronts[team.substringBeforeLast('_')] ?: "Equipe"} • Turno ${team.substringAfterLast('_')}"
    fun primary(uid: String) = uid == PRIMARY_ADMIN_UID
    fun allowed(uid: String, data: Map<String, Any>?) = primary(uid) ||
        (AccessPolicy.approved(data) && AccessPolicy.role(data) in leafRoles + "supervisor" && team(data) != null)
    fun manages(uid: String, data: Map<String, Any>?) = primary(uid) ||
        (allowed(uid, data) && AccessPolicy.role(data) == "supervisor")
    fun requested(data: Map<String, Any>): String = AccessPolicy.normalized(data["solicitacaoPerfil"])
        .ifEmpty { AccessPolicy.role(data) }.let { if (it in setOf("supervisor", "tratorista")) it else "operador" }
    fun canApprove(actorUid: String, actor: Map<String, Any>?, targetUid: String, target: Map<String, Any>?): Boolean {
        if (target == null || AccessPolicy.normalized(target["status"]) == "excluido" || targetUid == actorUid || primary(targetUid)) return false
        if (primary(actorUid)) return true
        return manages(actorUid, actor) && team(actor) == team(target) && team(actor) != null &&
            AccessPolicy.role(target) in leafRoles && AccessPolicy.normalized(target["solicitacaoPerfil"]) in setOf("operador", "tratorista")
    }
}

internal data class TeamAccess(val uid: String, val profile: Map<String, Any>) {
    val primary get() = TeamPolicy.primary(uid)
    val role get() = if (primary) "administrador" else AccessPolicy.role(profile)
    val team get() = TeamPolicy.team(profile)
    val manages get() = TeamPolicy.manages(uid, profile)
}
