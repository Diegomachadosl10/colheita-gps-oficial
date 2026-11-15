package com.colheitagps.app

import android.app.Application
import android.app.AlertDialog
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Robolectric
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowAlertDialog
import org.robolectric.util.ReflectionHelpers

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[28],manifest=Config.NONE,application=Application::class)
class NotificationRoutingTest {
    private val supervisor=TeamAccess("sup",mapOf("papel" to "supervisor","status" to "aprovado","frenteId" to "santo_inacio","turno" to "C"))
    @Test fun tappingAnAlertCarriesItsEventAndRecipientAndNeverUsesTheDescriptionAsARecordId() {
        val data=JSONObject().put("team","santo_inacio_C").put("eventId","a".repeat(64)).put("body","outra equipe/id")
        val intent=AgroNotifications.tapIntent(RuntimeEnvironment.getApplication(),"sup",data)
        assertEquals(MainActivity::class.java.name,intent.component!!.className)
        assertEquals("sup",intent.getStringExtra("usuarioAlerta"));assertEquals("a".repeat(64),intent.getStringExtra("eventoAlerta"))
        assertEquals("santo_inacio_C",intent.getStringExtra("equipeAlerta"));assertFalse(intent.hasExtra("body"))
    }
    @Test fun openingANotificationRechecksAccountRoleAndTeam() {
        assertTrue(NotificationRoutes.authorized(supervisor,"sup","santo_inacio_C"))
        assertFalse(NotificationRoutes.authorized(supervisor,"outro","santo_inacio_C"))
        assertFalse(NotificationRoutes.authorized(supervisor,"sup","santo_inacio_A"))
        assertFalse(NotificationRoutes.authorized(supervisor.copy(profile=supervisor.profile+("status" to "bloqueado")),"sup","santo_inacio_C"))
        assertFalse(NotificationRoutes.authorized(supervisor.copy(profile=supervisor.profile+("papel" to "operador")),"sup","santo_inacio_C"))
        assertFalse(NotificationRoutes.authorized(TeamAccess(TeamPolicy.PRIMARY_ADMIN_UID,emptyMap()),TeamPolicy.PRIMARY_ADMIN_UID,"inventada_C"))
    }
    @Test fun theExistingFreeWorkerEventOpensTheCurrentRecordEvenAfterMoreEditsAndNeverAnotherTeamsRecord() {
        val team="santo_inacio_C";val key="a".repeat(64)
        val metadata=mapOf("b_$key" to JSONObject().put("equipeId",team).put("tipo","item").put("grupo","pendencias").put("chave","issue-id").put("versao",5).toString())
        assertEquals(NotificationRoutes.Record("pendencias","issue-id"),NotificationRoutes.resolve(NotificationRoutes.event(team,key,1),team,metadata))
        assertEquals(NotificationRoutes.Record("pendencias","issue-id"),NotificationRoutes.resolve(NotificationRoutes.event(team,key,3),team,metadata))
        assertNull(NotificationRoutes.resolve(NotificationRoutes.event(team,key,1),"colorado_C",metadata))
        assertNull(NotificationRoutes.resolve("b".repeat(64),team,metadata))
        assertNull(NotificationRoutes.resolve("test_id",team,metadata))
    }
    @Test fun aResolvedAlertActuallyOpensTheIssueOptionsAndAnOldAccountCannotOpenThem() {
        fun views(v:View):List<View> = listOf(v)+(if(v is ViewGroup) (0 until v.childCount).flatMap {views(v.getChildAt(it))} else emptyList())
        val a=Robolectric.buildActivity(MainActivity::class.java).get();AppTheme.apply(a)
        ReflectionHelpers.setField(a,"teamStore",TeamStore(a.applicationContext,"santo_inacio_C"));ReflectionHelpers.setField(a,"teamAccess",supervisor)
        ReflectionHelpers.setField(a,"papelAtual","supervisor");ReflectionHelpers.setField(a,"acessoConfirmado",true)
        ReflectionHelpers.setField(a,"notificationTeam","santo_inacio_C");ReflectionHelpers.setField(a,"notificationUid","sup")
        a.getSharedPreferences("registros_v3",0).edit().putString("pendencias",JSONArray().put(JSONObject().put("id","alert-issue").put("equipamento","21571").put("descricao","Trocar mangueira")).toString()).commit()
        val show=a.javaClass.getDeclaredMethod("mostrarRegistroDoAviso",NotificationRoutes.Record::class.java).apply {isAccessible=true}
        assertEquals(true,show.invoke(a,NotificationRoutes.Record("pendencias","alert-issue")))
        val options=ShadowAlertDialog.getLatestAlertDialog()
        assertTrue(options.isShowing);assertTrue((0 until options.listView.adapter.count).any {options.listView.adapter.getItem(it)=="Editar pendência"})
        options.dismiss();ReflectionHelpers.setField(a,"notificationUid","another-account")
        assertEquals(false,show.invoke(a,NotificationRoutes.Record("pendencias","alert-issue")));assertFalse(options.isShowing)
    }
}
