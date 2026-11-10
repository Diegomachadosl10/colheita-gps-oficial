package com.colheitagps.app

import org.junit.Assert.*
import org.junit.Test

class AdminUserListTest {
    private fun user(index:Int,state:String="aprovado")=AdminUser("u$index",mapOf("nome" to "Antônio $index","matricula" to "000$index","frenteId" to "santo_inacio","turno" to "C","papel" to "operador","status" to state))
    @Test fun searchIgnoresAccentsPreservesMatriculaZerosAndCombinesFilters() {
        val users=listOf(user(1),user(2,"pendente"))
        assertEquals("u1",AdminUserList.filter(users,"antonio 0001","aprovado","santo_inacio").single().uid)
        assertTrue(AdminUserList.filter(users,"antonio","","colorado").isEmpty())
        assertEquals("u2",AdminUserList.filter(users,"","","").first().uid)
    }
    @Test fun pagesHaveAtMostTenUsersAndFilteringCannotStrandTheLastPage() {
        val users=(1..23).map { user(it) }
        assertEquals(10,AdminUserList.page(users,0).size);assertEquals(3,AdminUserList.page(users,2).size)
        assertEquals(3,AdminUserList.page(users,99).size);assertTrue(AdminUserList.page(emptyList(),5).isEmpty())
        assertEquals(1,AdminUserList.page(users.take(1),2).size)
    }
}
