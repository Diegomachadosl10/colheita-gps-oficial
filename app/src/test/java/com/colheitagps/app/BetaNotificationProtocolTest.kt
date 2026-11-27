package com.colheitagps.app

import android.app.Application
import androidx.work.Data
import com.google.firebase.messaging.RemoteMessage
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[28,35],manifest=Config.NONE,application=Application::class)
class BetaNotificationProtocolTest {
    private fun fixture()=javaClass.getResourceAsStream("/beta-notification.json")!!.bufferedReader().use {JSONObject(it.readText())}
    @Test fun theWorkerFixtureIsAcceptedAndEveryForeignMarkerOrInvalidScopeIsRejected() {
        val message=fixture();assertTrue(BetaNotificationProtocol.message(message))
        for((key,value) in listOf("environment" to "production","workspace" to "equipes","protocol" to "2",
            "targetUid" to "../other","eventId" to "invalid","team" to "unknown_C","team" to "santo_inacio_D")) {
            assertFalse("$key=$value",BetaNotificationProtocol.message(JSONObject(message.toString()).put(key,value)))
        }
        assertFalse(BetaNotificationProtocol.message(JSONObject(message.toString()).put("protocol",1)))
        assertFalse(BetaNotificationProtocol.message(JSONObject(message.toString()).put("targetUid",123)))
    }
    @Test fun aLegacyPushIsDiscardedBeforeAccessingFirebaseAndBetaTapsCarryAnExplicitWorkspace() {
        val message=fixture();message.remove("environment")
        val data=message.keys().asSequence().associateWith {message.getString(it)}
        AgroMessagingService().onMessageReceived(RemoteMessage.Builder("offline").setData(data).build())
        val app=RuntimeEnvironment.getApplication()
        val tap=AgroNotifications.tapIntent(app,"supA",fixture())
        assertEquals("beta",tap.getStringExtra("ambienteAlerta"))
        assertEquals("com.colheitagps.app.BETA_NOTICE",tap.action)
        assertEquals("supA",tap.getStringExtra("usuarioAlerta"))
    }
    @Test fun theServiceMustConfirmTheWorkspaceProjectAndProtocolBeforeReceivingAUserToken() {
        val health=JSONObject().put("environment","beta").put("protocol",1).put("workspace","ambientes/beta")
            .put("project","demo-dm-agro").put("configured",true).put("notificationsOnly",true).put("serviceVersion","beta-1.0.0")
        assertTrue(BetaNotificationProtocol.health(health,"demo-dm-agro"))
        assertFalse(BetaNotificationProtocol.health(health,"demo-other-project"))
        for((key,value) in listOf("environment" to "production","workspace" to "equipes","serviceVersion" to "1.4.0","configured" to "true","protocol" to "1"))
            assertFalse(BetaNotificationProtocol.health(JSONObject(health.toString()).put(key,value),"demo-dm-agro"))
        assertFalse(BetaNotificationProtocol.health(JSONObject(health.toString()).put("configured",false),"demo-dm-agro"))
    }
    @Test fun onlyExplicitBetaEndpointsAndNotificationRoutesAreAllowed() {
        assertTrue(BetaNotificationProtocol.endpoint("https://dm-agro-beta-servico.exemplo.workers.dev"))
        for(url in listOf("", "https://dm-agro-servico.exemplo.workers.dev","https://notbeta.exemplo.workers.dev",
            "http://dm-agro-beta-servico.exemplo.workers.dev","https://beta.exemplo.workers.dev/path",
            "https://token@beta.exemplo.workers.dev","https://beta.exemplo.workers.dev:443","https://beta.exemplo.workers.dev?secret=invalid"))
            assertFalse(url,BetaNotificationProtocol.endpoint(url))
        assertTrue(BetaNotificationProtocol.supports("/events/notify"));assertTrue(BetaNotificationProtocol.supports("/devices/unregister"))
        assertFalse(BetaNotificationProtocol.supports("/accounts/delete"));assertFalse(BetaNotificationProtocol.supports("/accounts/correct"))
        val original=JSONObject().put("kind","user").put("environment","production").put("protocol",99)
        val wire=BetaNotificationProtocol.request(original)
        assertEquals("beta",wire.getString("environment"));assertEquals(1,wire.getInt("protocol"));assertEquals("production",original.getString("environment"))
        assertEquals("/beta/v1",BetaNotificationProtocol.PREFIX)
    }
    @Test fun oldOrUnknownWorkCannotBeInterpretedAsANewBetaNotification() {
        assertFalse(BetaNotificationProtocol.job(Data.Builder().putString("action","event").build()))
        for(action in listOf("event","register","display"))assertTrue(BetaNotificationProtocol.job(Data.Builder()
            .putString("environment","beta").putInt("protocol",1).putString("action",action).build()))
        assertFalse(BetaNotificationProtocol.job(Data.Builder().putString("environment","production").putInt("protocol",1).putString("action","event").build()))
        assertFalse(BetaNotificationProtocol.job(Data.Builder().putString("environment","beta").putInt("protocol",2).putString("action","event").build()))
    }
    @Test fun theAdministratorUsesOnlyBetaPreferencesAndASupervisorDoesNotChooseAnotherFront() {
        val app=RuntimeEnvironment.getApplication();val uid=TeamPolicy.PRIMARY_ADMIN_UID
        app.getSharedPreferences("avisos_$uid",0).edit().putString("frente","colorado").commit()
        app.getSharedPreferences(BetaWorkspace.prefs("avisos_$uid"),0).edit().putString("frente","santo_inacio").commit()
        assertEquals("santo_inacio",AgroNotifications.registration(app,uid,"fixture-token").getString("front"))
        assertFalse(AgroNotifications.registration(app,"supA","fixture-token").has("front"))
        assertEquals("colorado",app.getSharedPreferences("avisos_$uid",0).getString("frente",null))
        assertFalse(BetaWorkspace.notificationsReady)
    }
    @Test fun persistedBetaWorkUsesItsOwnClassAndCannotBeCancelledAsAnOldDeviceJob() {
        val event=AgroNotifications.work("event",JSONObject().put("kind","user").put("uid","supA"),"supA")
        assertEquals(BetaNotificationWorker::class.java.name,event.workSpec.workerClassName)
        assertNotEquals(AgroNotificationWorker::class.java.name,event.workSpec.workerClassName)
        assertTrue(BetaNotificationProtocol.job(event.workSpec.input));assertTrue(event.tags.contains("beta-avisos-supA"))
        assertFalse(event.tags.contains("avisos-supA"));assertFalse(event.tags.contains("beta-aparelho-supA"))
        val registration=AgroNotifications.work("register",JSONObject().put("token","fixture-token"),"supA")
        assertTrue(registration.tags.contains("beta-aparelho-supA"))
    }
}
