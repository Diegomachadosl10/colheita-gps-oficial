package com.colheitagps.app

import org.junit.Assert.*
import org.junit.Test

class AccessPolicyTest {
    @Test fun registrationAndLoginUseTheSameAddressAfterClearingAppData() {
        assertEquals("matricula.80911@acesso.dm-agro.app", AccessPolicy.internalEmail("80911"))
        assertEquals("matricula.0080911@acesso.dm-agro.app", AccessPolicy.internalEmail("0080911"))
    }
    @Test(expected = IllegalArgumentException::class) fun invalidMatriculaCannotBecomeAnAddress() {
        AccessPolicy.internalEmail("80911@other.com")
    }
    @Test fun approvedOperatorIsRecognizedWithLegacyProfile() {
        assertTrue(AccessPolicy.approved(mapOf("status" to " aprovado ", "perfil" to "Operador")))
        assertTrue(AccessPolicy.approved(mapOf("status" to "APROVADO", "papel" to "Funcionário")))
    }
    @Test fun blockedAndPendingProfilesNeverGetAccess() {
        for (state in listOf("bloqueado", "pendente", "", "rejeitado")) {
            val user = mapOf("status" to state, "papel" to "administrador")
            assertFalse(AccessPolicy.approved(user))
            assertFalse(AccessPolicy.canApprove(user))
        }
        assertFalse(AccessPolicy.approved(null))
        assertFalse(AccessPolicy.approved(mapOf("status" to "aprovado")))
        assertFalse(AccessPolicy.approved(mapOf("status" to "aprovado", "papel" to "desconhecido", "perfil" to "operador")))
    }
    @Test fun onlyCanonicalApprovedAdministratorMayApproveUsers() {
        for (role in listOf("supervisor", "operador", "funcionario")) {
            val user = mapOf("status" to "aprovado", "papel" to role, "perfil" to "administrador")
            assertTrue(AccessPolicy.approved(user))
            assertFalse(AccessPolicy.canApprove(user))
            assertEquals(role, AccessPolicy.role(user))
        }
        assertFalse(AccessPolicy.canApprove(mapOf("status" to "aprovado", "perfil" to "administrador")))
        assertTrue(AccessPolicy.canApprove(mapOf("status" to "aprovado", "papel" to "Administrador")))
    }
    @Test fun supervisorRequestIsNotAnApproval() {
        val request = mapOf("papel" to "operador", "perfil" to "operador", "solicitacaoPerfil" to "supervisor", "status" to "pendente")
        assertEquals("operador", AccessPolicy.role(request))
        assertFalse(AccessPolicy.approved(request))
        assertFalse(AccessPolicy.canApprove(request + ("status" to "aprovado")))
    }
}
