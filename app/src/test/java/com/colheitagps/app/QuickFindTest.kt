package com.colheitagps.app

import org.junit.Assert.*
import org.junit.Test

class QuickFindTest {
    @Test fun searchAcceptsAccentsCaseAndLotSeparatorsWithoutChangingTheStoredNames() {
        assertTrue(QuickFind.matches("santo inacio","Fazenda Santo Inácio.pdf"))
        assertTrue(QuickFind.matches("k39","Lote K/39"))
        assertTrue(QuickFind.matches("2156","Equipamento 021569"))
        assertTrue(QuickFind.matches("óleo 21569","21569","Mangueira de óleo"))
        assertTrue(QuickFind.matches("  ","Qualquer item"))
        assertFalse(QuickFind.matches("k40","K/39"))
        assertFalse(QuickFind.matches("21569 motor","21569","Filtro hidráulico"))
    }
}
