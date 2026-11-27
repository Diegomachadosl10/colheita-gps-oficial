package com.colheitagps.app

import android.app.Application
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.io.File
import javax.crypto.KeyGenerator
import java.security.MessageDigest

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[28,35],manifest=Config.NONE,application=Application::class)
class OfflineEntryTest {
    private val app get()=RuntimeEnvironment.getApplication()
    private val key=KeyGenerator.getInstance("AES").apply {init(256)}.generateKey()
    private var now=OfflineEntryPolicy.Clock(1_800_000_000_000L,900_000L,12)
    private val profile=mapOf<String,Any>("nome" to "Pessoa de teste","status" to "aprovado","papel" to "operador","frenteId" to "santo_inacio","turno" to "C","senha" to "FORBIDDEN_FAKE_SECRET","email" to "not-stored@example.invalid")
    private fun store()=OfflineEntryStore(app,{key},{now})
    @Before fun clean() {File(app.noBackupFilesDir,"beta_access_lease").deleteRecursively()}
    private fun file(uid:String):File {
        val name=MessageDigest.getInstance("SHA-256").digest(uid.toByteArray()).joinToString("") {"%02x".format(it)}
        return File(app.noBackupFilesDir,"beta_access_lease/$name.enc")
    }
    @Test fun anApprovedEntrySurvivesProcessRecreationWithoutPasswordsTokensOrPlaintextProfile() {
        store().remember("one",profile)
        val restored=store().read("one")!!
        assertEquals("operador",restored.role);assertEquals("santo_inacio_C",restored.team)
        assertFalse(restored.profile.containsKey("senha"));assertFalse(restored.profile.containsKey("email"))
        val bytes=file("one").readBytes().toString(Charsets.ISO_8859_1)
        for(secret in listOf("Pessoa de teste","FORBIDDEN_FAKE_SECRET","aprovado","one")) assertFalse(bytes.contains(secret))
        assertNull(store().read("two"))
    }
    @Test fun expiredLeasesAreNotExtendedByReadsOrApprovedSnapshots() {
        store().remember("one",profile)
        now=now.copy(wall=now.wall+OfflineEntryPolicy.WINDOW_MS-1,elapsed=now.elapsed+OfflineEntryPolicy.WINDOW_MS-1)
        assertNotNull(store().read("one"));store().observe("one",profile)
        now=now.copy(wall=now.wall+1,elapsed=now.elapsed+1)
        assertNull(store().read("one"));assertFalse(file("one").exists())
    }
    @Test fun rebootKeepsTheOriginalDeadlineAndClockRollbackRevokesTheLease() {
        store().remember("one",profile)
        now=now.copy(wall=now.wall+3_600_000,elapsed=1000,boot=13)
        assertNotNull(store().read("one"))
        now=now.copy(wall=now.wall-300_000,elapsed=2000)
        assertNull(store().read("one"))
        store().remember("one",profile)
        now=now.copy(elapsed=1000)
        assertNull(store().read("one"))
    }
    @Test fun clockManipulationCannotKeepTheLeaseAlivePastItsMonotonicWindow() {
        store().remember("one",profile)
        now=now.copy(wall=now.wall+1000,elapsed=now.elapsed+OfflineEntryPolicy.WINDOW_MS)
        assertNull(store().read("one"))
    }
    @Test fun tamperingCopyingBetweenAccountsOrLosingTheInstallationKeyFailsClosed() {
        store().remember("one",profile)
        file("one").copyTo(file("two"),overwrite=true)
        assertNull(store().read("two"));assertNotNull(store().read("one"))
        val bytes=file("one").readBytes();bytes[bytes.lastIndex]=(bytes.last().toInt() xor 1).toByte();file("one").writeBytes(bytes)
        assertNull(store().read("one"))
        store().remember("one",profile)
        val anotherKey=KeyGenerator.getInstance("AES").apply {init(256)}.generateKey()
        assertNull(OfflineEntryStore(app,{anotherKey},{now}).read("one"))
    }
    @Test fun revokedMissingOrChangedTeamAndRoleInvalidateStoredApproval() {
        for(data in listOf(null,profile+("status" to "bloqueado"),profile+("status" to "pendente"),profile+("papel" to "supervisor"),profile+("frenteId" to "colorado"))) {
            store().remember("one",profile);store().observe("one",data);assertNull(store().read("one"))
        }
        store().remember("one",profile);store().clear();assertNull(store().read("one"))
        store().remember("one",profile+("status" to "pendente"));assertNull(store().read("one"))
    }
    @Test fun onlyConnectivityFailuresMayUseAnAlreadyApprovedLease() {
        for(code in listOf("DNS","TIMEOUT","UNAVAILABLE","HTTP_502","HTTP_503")) assertTrue(OfflineEntryPolicy.networkFailure(AccessFailure(code)))
        for(code in listOf("UNAUTHENTICATED","PERMISSION_DENIED","SESSION_CHANGED","PROFILE_INVALID","TLS","INVALID_RESPONSE","CONNECTION_FAILED","HTTP_400")) assertFalse(OfflineEntryPolicy.networkFailure(AccessFailure(code)))
    }
}
