package com.colheitagps.app

import android.app.Application
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[28],manifest=Config.NONE,application=Application::class)
class FirestoreAccessHttpTest {
    private val name="projects/dm-agro-c0e04/databases/(default)/documents/usuarios/person"
    private fun data(status:String="aprovado",role:String="supervisor")=JSONObject().put("name",name).put("fields",JSONObject()
        .put("nome",JSONObject().put("stringValue","Pessoa"))
        .put("status",JSONObject().put("stringValue",status)).put("papel",JSONObject().put("stringValue",role))
        .put("frenteId",JSONObject().put("stringValue","santo_inacio")).put("turno",JSONObject().put("stringValue","C")))
    @Test fun theRestProfileHasTheSameRoleAndTeamInterpretationAndNeverGrantsBlockedUsersAccess() {
        val parsed=FirestoreAccessHttp.parse(200,data().toString(),name)!!
        assertTrue(TeamPolicy.allowed("person",parsed));assertEquals("santo_inacio_C",TeamPolicy.team(parsed));assertTrue(TeamPolicy.manages("person",parsed))
        for(status in listOf("pendente","bloqueado","excluido")) assertFalse(TeamPolicy.allowed("person",FirestoreAccessHttp.parse(200,data(status).toString(),name)))
        assertFalse(TeamPolicy.allowed("person",FirestoreAccessHttp.parse(200,data(role="administrador").toString(),name)))
        assertTrue(TeamPolicy.allowed(TeamPolicy.PRIMARY_ADMIN_UID,parsed))
    }
    @Test fun authorizationDenialsAndUnexpectedResponsesCannotBeInterpretedAsApprovedOrMissingProfiles() {
        for((code,body,expected) in listOf(Triple(403,"{\"error\":{\"status\":\"PERMISSION_DENIED\"}}","PERMISSION_DENIED"),
            Triple(401,"{\"error\":{\"status\":\"UNAUTHENTICATED\"}}","UNAUTHENTICATED"),Triple(200,"<html>network login</html>","INVALID_RESPONSE"),
            Triple(404,"<html>not found</html>","INVALID_RESPONSE"),Triple(200,data().put("name",name+"-other").toString(),"INVALID_RESPONSE"))) {
            try {FirestoreAccessHttp.parse(code,body,name);fail("Rejected response was accepted")} catch(error:AccessFailure) {assertEquals(expected,error.code)}
        }
        assertNull(FirestoreAccessHttp.parse(404,"{\"error\":{\"status\":\"NOT_FOUND\"}}",name))
        assertEquals("PERMISSION_DENIED",AccessFailures.code(java.util.concurrent.ExecutionException(AccessFailure("PERMISSION_DENIED"))))
        assertEquals("TIMEOUT",AccessFailures.code(java.util.concurrent.TimeoutException("private details")))
        assertFalse(AccessFailures.description(AccessFailure("PERMISSION_DENIED")).contains("private details"))
        assertTrue(AccessFailures.description(AccessFailure("PROFILE_INVALID")).contains("cadastro"))
    }
    @Test fun urlsStayOnTheFirestoreProjectAndTypedValuesAreDecodedWithoutUsingCachedProfiles() {
        val url=FirestoreAccessHttp.path("dm-agro-c0e04","person+one",false)
        assertEquals("https://firestore.googleapis.com/v1/projects/dm-agro-c0e04/databases/(default)/documents/usuarios/person%2Bone",url)
        assertTrue(FirestoreAccessHttp.path("dm-agro-c0e04","person",true).endsWith("/configuracao/equipes"))
        for((project,uid) in listOf("https://other.example" to "person","dm-agro-c0e04" to "../other")) {
            try {FirestoreAccessHttp.path(project,uid,false);fail()} catch(_:IllegalArgumentException) {}
        }
        val fixture=data();val fields=fixture.getJSONObject("fields")
        fields.put("ativo",JSONObject().put("booleanValue",true)).put("numero",JSONObject().put("integerValue","123"))
            .put("criadoEm",JSONObject().put("timestampValue","2026-11-11T12:00:00.123456789Z"))
        val parsed=FirestoreAccessHttp.parse(200,fixture.toString(),name)!!
        assertEquals(123L,parsed["numero"]);assertEquals(true,parsed["ativo"]);assertEquals(123456789,(parsed["criadoEm"] as com.google.firebase.Timestamp).nanoseconds)
    }
}
