package com.colheitagps.app

import org.junit.Assert.*
import org.junit.Test

class EnrollmentPolicyTest {
    private val pending=mapOf<String,Any>("nome" to "Pessoa","matricula" to "080911","frenteId" to "santo_inacio","turno" to "C",
        "papel" to "operador","perfil" to "operador","status" to "pendente","solicitacaoPerfil" to "supervisor")
    @Test fun supervisorsStartApprovedWhileOperatorsAndTractorDriversWaitForTheirTeam() {
        val supervisor=EnrollmentPolicy.initial("supervisor")
        assertTrue(TeamPolicy.allowed("sup",pending+supervisor));assertTrue(TeamPolicy.manages("sup",pending+supervisor))
        for(role in listOf("operador","tratorista")) {
            val profile=pending+EnrollmentPolicy.initial(role)
            assertEquals("pendente",profile["status"]);assertFalse(TeamPolicy.allowed("op",profile))
        }
        assertThrows(IllegalArgumentException::class.java) {EnrollmentPolicy.initial("administrador")}
    }
    @Test fun onlyTheExactPendingSupervisorRequestCanBeActivatedWithoutChangingItsTeam() {
        assertTrue(EnrollmentPolicy.pendingSupervisor("sup","matricula.080911@acesso.dm-agro.app",pending))
        assertFalse(EnrollmentPolicy.pendingSupervisor("sup","matricula.80911@acesso.dm-agro.app",pending))
        assertFalse(EnrollmentPolicy.pendingSupervisor(TeamPolicy.PRIMARY_ADMIN_UID,"matricula.080911@acesso.dm-agro.app",pending))
        for(change in listOf(mapOf("status" to "bloqueado"),mapOf("status" to "excluido"),mapOf("status" to "aprovado"),
            mapOf("solicitacaoPerfil" to "operador"),mapOf("perfil" to "supervisor"),mapOf("turno" to "D"),mapOf("frenteId" to "outra"))) {
            assertFalse(change.toString(),EnrollmentPolicy.pendingSupervisor("sup","matricula.080911@acesso.dm-agro.app",pending+change))
        }
        val active=pending+EnrollmentPolicy.supervisorActivation
        assertEquals("santo_inacio_C",TeamPolicy.team(active))
        assertTrue(TeamPolicy.canApprove("sup",active,"op",pending+EnrollmentPolicy.initial("operador")))
        assertFalse(TeamPolicy.canApprove("sup",active,"op",pending+EnrollmentPolicy.initial("operador")+("turno" to "A")))
        assertFalse(TeamPolicy.canApprove("sup",active,"sup2",active))
    }
}
