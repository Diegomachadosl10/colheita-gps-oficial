package com.colheitagps.app

import org.junit.Assert.*
import org.junit.Test

class EnrollmentPolicyTest {
    private val pending=mapOf<String,Any>("nome" to "Pessoa","matricula" to "080911","frenteId" to "santo_inacio","turno" to "C",
        "papel" to "operador","perfil" to "operador","status" to "pendente","solicitacaoPerfil" to "supervisor")
    @Test fun everyRequestedProfileWaitsForApprovalIncludingSupervisorAndHelper() {
        for (role in listOf("operador","tratorista","ajudante","supervisor")) {
            val profile = pending + EnrollmentPolicy.initial(role)
            assertEquals(role, profile["solicitacaoPerfil"])
            assertEquals("pendente",profile["status"])
            assertEquals("operador", profile["papel"])
            assertFalse(TeamPolicy.allowed("person",profile))
        }
        assertThrows(IllegalArgumentException::class.java) { EnrollmentPolicy.initial("administrador") }
    }
    @Test fun onlyDiegoApprovesSupervisorsAndSupervisorsApproveLeafProfilesAcrossTheirOwnFront() {
        val sup=pending + mapOf("papel" to "supervisor","perfil" to "supervisor","status" to "aprovado")
        assertTrue(TeamPolicy.canApprove(TeamPolicy.PRIMARY_ADMIN_UID,emptyMap(),"candidate",pending))
        assertFalse(TeamPolicy.canApprove("sup",sup,"candidate",pending))
        for(role in listOf("operador","tratorista","ajudante")) {
            val candidate=pending+EnrollmentPolicy.initial(role)+( "turno" to "A")
            assertTrue(TeamPolicy.canApprove("sup",sup,"candidate",candidate))
            assertFalse(TeamPolicy.canApprove("sup",sup,"candidate",candidate+("frenteId" to "colorado")))
            assertFalse(TeamPolicy.canApprove("candidate",candidate,"sup",sup))
        }
        assertTrue(TeamPolicy.canUseTeam("sup",sup,"santo_inacio_A"))
        assertFalse(TeamPolicy.canUseTeam("sup",sup,"colorado_A"))
        val op=pending+mapOf("status" to "aprovado","solicitacaoPerfil" to "operador")
        assertFalse(TeamPolicy.canUseTeam("op",op,"santo_inacio_A"))
    }
}
