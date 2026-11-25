package com.colheitagps.app

import android.app.Activity
import android.app.Application
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import com.google.android.gms.tasks.TaskCompletionSource
import com.google.android.gms.tasks.Tasks
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.util.ReflectionHelpers

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28, 35], manifest = Config.NONE, application = Application::class)
class EntrySessionTest {
    private val profile = mapOf<String, Any>("nome" to "Pessoa de teste", "status" to "aprovado",
        "papel" to "operador", "frenteId" to "santo_inacio", "turno" to "C")

    @Test fun loginAndMainShareOneUnfinishedRequestAndReuseOnlyTheApprovedResult() {
        val entry = EntrySession({ "one" }, { 1_000L })
        val source = TaskCompletionSource<Map<String, Any>?>(); var reads = 0
        val first = entry.request("one") { reads++; source.task }
        val second = entry.request("one") { reads++; source.task }
        assertSame(first, second); assertEquals(1, reads); assertNull(entry.recent("one"))
        source.setResult(profile)
        assertTrue(first.isSuccessful); assertEquals("operador", entry.recent("one")!!.role)
        assertEquals(profile, entry.request("one") { reads++; Tasks.forResult(profile) }.result)
        assertEquals(1, reads)
    }

    @Test fun anExpiredOrFailedSessionCannotGrantEntryFromAnOldProfile() {
        var now = 1_000L; var reads = 0
        val entry = EntrySession({ "one" }, { now })
        entry.request("one") { reads++; Tasks.forResult(profile) }
        now += 299_999; assertNotNull(entry.recent("one"))
        now++; assertNull(entry.recent("one"))
        val failed = entry.request("one") { reads++; Tasks.forException(AccessFailure("TIMEOUT")) }
        assertFalse(failed.isSuccessful); assertNull(entry.recent("one")); assertEquals(2, reads)
        assertTrue(entry.request("one") { reads++; Tasks.forResult(profile) }.isSuccessful)
        assertEquals(3, reads)
    }

    @Test fun pendingBlockedMissingAndInvalidProfilesAreNeverRememberedAsApproved() {
        val values = listOf(null, profile + ("status" to "pendente"), profile + ("status" to "bloqueado"),
            profile + ("status" to "excluido"), profile + ("papel" to "administrador"), profile + ("turno" to "X"))
        for (data in values) {
            val entry = EntrySession({ "one" }); var reads = 0
            repeat(2) { entry.request("one") { reads++; Tasks.forResult(data) } }
            assertNull(entry.recent("one")); assertEquals(2, reads)
        }
    }

    @Test fun logoutAndChangingAccountsDiscardLateServerResponses() {
        var uid: String? = "one"
        val entry = EntrySession({ uid })
        val source = TaskCompletionSource<Map<String, Any>?>()
        val old = entry.request("one") { source.task }
        entry.clear(); uid = null
        source.setResult(profile)
        assertFalse(old.isSuccessful); assertNull(entry.recent("one"))
        uid = "two"; entry.authChanged(uid)
        assertFalse(entry.request("one") { fail("Must not request the other account"); Tasks.forResult(profile) }.isSuccessful)
        assertTrue(entry.request("two") { Tasks.forResult(profile + ("papel" to "supervisor")) }.isSuccessful)
        assertNull(entry.recent("one")); assertEquals("supervisor", entry.recent("two")!!.role)
        entry.reject("one"); assertNotNull(entry.recent("two"))
        entry.reject("two"); assertNull(entry.recent("two"))
    }

    @Test fun aPreviousAccountCannotOverwriteTheNewAccountsPendingRequest() {
        var uid: String? = "one"
        val entry = EntrySession({ uid })
        val first = TaskCompletionSource<Map<String, Any>?>(); val second = TaskCompletionSource<Map<String, Any>?>()
        val old = entry.request("one") { first.task }
        uid = "two"; entry.authChanged(uid)
        val current = entry.request("two") { second.task }
        first.setResult(profile)
        assertFalse(old.isSuccessful); assertFalse(current.isComplete)
        assertSame(current, entry.request("two") { fail("Must reuse the pending read"); second.task })
        second.setResult(profile); assertTrue(current.isSuccessful); assertEquals("two", entry.recent("two")!!.uid)
    }

    @Test fun serverBlocksAndRoleOrTeamChangesInvalidateReuseImmediately() {
        val changes = listOf(null, profile + ("status" to "bloqueado"), profile + ("papel" to "supervisor"),
            profile + ("frenteId" to "colorado"), profile + ("turno" to "A"))
        for (data in changes) {
            val entry = EntrySession({ "one" })
            entry.request("one") { Tasks.forResult(profile) }
            entry.observe("one", data); assertNull(entry.recent("one"))
        }
        val entry = EntrySession({ "one" }); entry.request("one") { Tasks.forResult(profile) }
        entry.observe("one", profile + ("nome" to "Nome corrigido")); assertNotNull(entry.recent("one"))
    }

    private fun views(view: View): List<View> = listOf(view) +
        (if (view is ViewGroup) (0 until view.childCount).flatMap { views(view.getChildAt(it)) } else emptyList())

    @Test fun startupShowsOnlyTheBrandAndReturnsToRealLoginControlsAfterAnError() {
        val activity = Robolectric.buildActivity(LoginActivity::class.java).get()
        ReflectionHelpers.setField(activity, "ui", AccessUi(activity))
        EntryLoading.show(activity)
        val labels = views(activity.window.decorView).filterIsInstance<TextView>().map { it.text.toString() }
        assertTrue(labels.contains("DM AGRO"))
        assertFalse(labels.any { it.contains("Verificando") || it.contains("Verificar novamente") })
        assertTrue(views(activity.window.decorView).filterIsInstance<Button>().isEmpty())
        ReflectionHelpers.setField(activity, "opening", true)
        ReflectionHelpers.callInstanceMethod<Unit>(activity, "status",
            ReflectionHelpers.ClassParameter.from(String::class.java, "Não foi possível entrar"),
            ReflectionHelpers.ClassParameter.from(String::class.java, "Sem conexão"))
        assertTrue(views(activity.window.decorView).filterIsInstance<Button>().any { it.text.toString() == "Verificar novamente" })
    }
}
