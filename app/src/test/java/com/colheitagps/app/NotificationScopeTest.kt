package com.colheitagps.app

import org.junit.Assert.*
import org.junit.Test

class NotificationScopeTest {
    @Test fun ownerSeesOnlySelectedFrontAndSupervisorOnlyTheirFrontAcrossShiftsEvenForQueuedMessages() {
        assertTrue(AgroNotifications.mayDisplay(TeamPolicy.PRIMARY_ADMIN_UID,emptyMap(),"santo_inacio_A","santo_inacio"))
        assertFalse(AgroNotifications.mayDisplay(TeamPolicy.PRIMARY_ADMIN_UID,emptyMap(),"colorado_C","santo_inacio"))
        assertFalse(AgroNotifications.mayDisplay(TeamPolicy.PRIMARY_ADMIN_UID,emptyMap(),"santo_inacio_C","colorado"))
        val supervisor=mapOf<String,Any>("papel" to "supervisor","status" to "aprovado","frenteId" to "santo_inacio","turno" to "C")
        assertTrue(AgroNotifications.mayDisplay("sup",supervisor,"santo_inacio_C","qualquer"))
        assertTrue(AgroNotifications.mayDisplay("sup",supervisor,"santo_inacio_A","santo_inacio"))
        assertFalse(AgroNotifications.mayDisplay("sup",supervisor,"colorado_A","santo_inacio"))
        assertFalse(AgroNotifications.mayDisplay("sup",supervisor+("status" to "bloqueado"),"santo_inacio_C","santo_inacio"))
    }
}
