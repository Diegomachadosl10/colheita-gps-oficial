package com.colheitagps.app

import org.junit.Assert.*
import org.junit.Test

class TeamPolicyTest {
    private fun profile(role: String, shift: String = "A", front: String = "santo_inacio", status: String = "aprovado") =
        mapOf<String, Any>("papel" to role, "status" to status, "frenteId" to front, "turno" to shift, "solicitacaoPerfil" to "operador")
    @Test fun sameFrontDifferentShiftIsAnotherTeam() {
        assertNotEquals(TeamPolicy.team(profile("supervisor")), TeamPolicy.team(profile("supervisor", "B")))
        assertNull(TeamPolicy.team(profile("operador", "D")))
        assertNull(TeamPolicy.team(profile("operador", front="../outra")))
    }
    @Test fun supervisorMayApproveOnlyOwnLeafUsers() {
        val supervisor = profile("supervisor")
        assertTrue(TeamPolicy.canApprove("sup", supervisor, "op", profile("operador", status="pendente")))
        assertTrue(TeamPolicy.canApprove("sup", supervisor, "tractor", profile("tratorista") + ("solicitacaoPerfil" to "tratorista")))
        assertFalse(TeamPolicy.canApprove("sup", supervisor, "op", profile("operador", "B")))
        assertFalse(TeamPolicy.canApprove("sup", supervisor, "op", profile("operador", front="colorado")))
        assertFalse(TeamPolicy.canApprove("sup", supervisor, "supervisor", profile("operador") + ("solicitacaoPerfil" to "supervisor")))
        assertFalse(TeamPolicy.canApprove("sup", supervisor, "sup", supervisor))
        assertFalse(TeamPolicy.canApprove("sup", supervisor, TeamPolicy.PRIMARY_ADMIN_UID, profile("administrador")))
    }
    @Test fun onlyFixedAdministratorGetsGlobalPrivileges() {
        assertTrue(TeamPolicy.primary(TeamPolicy.PRIMARY_ADMIN_UID))
        assertFalse(TeamPolicy.primary("outro"))
        assertFalse(TeamPolicy.allowed("outro", profile("administrador")))
        assertFalse(TeamPolicy.manages("op", profile("operador")))
        assertFalse(TeamPolicy.manages("sup", profile("supervisor", status="bloqueado")))
        assertTrue(TeamPolicy.canApprove(TeamPolicy.PRIMARY_ADMIN_UID, profile("administrador"), "sup", profile("supervisor", "B")))
    }
}
