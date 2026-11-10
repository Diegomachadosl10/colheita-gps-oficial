package com.colheitagps.app

import android.app.Application
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.util.ReflectionHelpers

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[28],manifest=Config.NONE,application=Application::class)
class DashboardTest {
    private fun texts(view:View):List<String> = (if(view is TextView) listOf(view.text.toString()) else emptyList())+
        (if(view is ViewGroup) (0 until view.childCount).flatMap { texts(view.getChildAt(it)) } else emptyList())
    private fun home(role:String):MainActivity {
        val a=Robolectric.buildActivity(MainActivity::class.java).get()
        val uid=if(role=="administrador") TeamPolicy.PRIMARY_ADMIN_UID else "pessoa"
        ReflectionHelpers.setField(a,"teamAccess",TeamAccess(uid,mapOf("nome" to "Pessoa", "papel" to role,"status" to "aprovado","frenteId" to "santo_inacio","turno" to "C")))
        ReflectionHelpers.setField(a,"teamStore",TeamStore(a.applicationContext,"santo_inacio_C"))
        ReflectionHelpers.setField(a,"papelAtual",role)
        ReflectionHelpers.setField(a,"acessoConfirmado",true)
        a.javaClass.getDeclaredMethod("inicio").apply { isAccessible=true }.invoke(a)
        return a
    }
    @Test fun operatorDashboardKeepsMapsWeatherAndFieldToolsWithoutManagerTools() {
        val a=home("operador"); val labels=texts(a.window.decorView)
        for(title in listOf("DM AGRO","Olá, Pessoa","Mapas","Pendências","Equipamentos","Chuva por turno","Ocorrências","Previsão do tempo")) assertTrue(title,labels.contains(title))
        for(title in listOf("Estoque","Ordens de serviço","Relatórios","Caminhões","Calendário","Gerenciar usuários","Sair da conta")) assertFalse(title,labels.contains(title))
    }
    @Test fun supervisorDashboardPreservesEveryManagerToolWithoutTheOwnerTeamSwitcher() {
        val labels=texts(home("supervisor").window.decorView)
        for(title in listOf("Estoque","Ordens de serviço","Relatórios","Caminhões","Calendário","Aprovar minha equipe")) assertTrue(title,labels.contains(title))
        assertFalse(labels.contains("Trocar"))
    }
    @Test fun ownerDashboardHasTeamSwitcherAndTheNameNeverDefaultsToUsuario() {
        val labels=texts(home("administrador").window.decorView)
        assertTrue(labels.contains("Trocar")); assertTrue(labels.contains("Gerenciar usuários"))
        assertEquals("Administrador",DashboardUi.name(TeamAccess(TeamPolicy.PRIMARY_ADMIN_UID,emptyMap())))
        assertEquals("Diego",DashboardUi.name(TeamAccess(TeamPolicy.PRIMARY_ADMIN_UID,emptyMap()),"Diego"))
    }
}
