package com.colheitagps.app

import android.app.Application
import android.os.Looper
import android.view.*
import android.widget.*
import com.google.android.gms.tasks.TaskCompletionSource
import com.google.firebase.FirebaseNetworkException
import com.google.firebase.auth.AuthResult
import org.junit.After
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.util.ReflectionHelpers
import java.time.Duration

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[28],manifest=Config.NONE,application=Application::class)
class AccessWaitTest {
    @After fun reset() {SignInAttempts.requests.current?.let {pending->if(!pending.task.isComplete) ReflectionHelpers.setField(SignInAttempts.requests,"current",null) else SignInAttempts.requests.consume(pending)}}
    private fun views(v:View):List<View> = listOf(v)+(if(v is ViewGroup) (0 until v.childCount).flatMap {views(v.getChildAt(it))} else emptyList())
    @Test fun aSecondTapOrActivityCannotStartASecondSdkRequestWhileTheFirstOneIsUnfinished() {
        val attempts=AccessAttempts<String>();val source=TaskCompletionSource<String>();var calls=0
        val first=attempts.start("123",1000) {calls++;source.task}
        val again=attempts.start("456",2000) {calls++;TaskCompletionSource<String>().task}
        assertSame(first,again);assertEquals("123",again.identity);assertEquals(1,calls)
        assertEquals(25000,first.remaining(1000));assertEquals(0,first.remaining(26000));assertFalse(first.task.isComplete)
        source.setException(FirebaseNetworkException("simulated"))
        val next=attempts.start("456",27000) {calls++;TaskCompletionSource<String>().task}
        attempts.consume(first);assertSame(next,attempts.current);assertEquals(2,calls)
    }
    @Test fun afterTwentyFiveSecondsTheLoginShowsNetworkHelpAndARealNetworkFailureReleasesTheButton() {
        val source=TaskCompletionSource<AuthResult>()
        SignInAttempts.requests.start("123",android.os.SystemClock.elapsedRealtime()) {source.task}
        val a=Robolectric.buildActivity(LoginActivity::class.java).get();a.setTheme(android.R.style.Theme_Material_Light_NoActionBar)
        ReflectionHelpers.setField(a,"ui",AccessUi(a))
        a.javaClass.getDeclaredMethod("login").apply {isAccessible=true}.invoke(a)
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(25))
        val labels=views(a.window.decorView).filterIsInstance<TextView>()
        assertTrue(labels.any {it.text.toString()=="Aguardando resposta do acesso"})
        assertTrue(labels.any {it.text.toString()=="Testar conexão"&&it.visibility==View.VISIBLE})
        assertEquals(View.VISIBLE,ReflectionHelpers.getField<View>(a,"connectionHelp").visibility)
        assertFalse(source.task.isComplete);assertFalse(a.isFinishing)
        source.setException(FirebaseNetworkException("simulated"));shadowOf(Looper.getMainLooper()).idle()
        val button=views(a.window.decorView).filterIsInstance<Button>().single {it.text.toString()=="Entrar"}
        assertTrue(button.isEnabled);assertNull(SignInAttempts.requests.current)
        assertTrue(views(a.window.decorView).filterIsInstance<TextView>().any {it.text.toString().startsWith("Sem conexão com o serviço")})
    }
    @Test fun theDiagnosticClassifiesDnsTlsAndUnexpectedPagesWithoutExposingRawErrors() {
        assertTrue(AccessConnection.failure(java.net.UnknownHostException("SECRET-URL")).contains("DNS"))
        assertTrue(AccessConnection.failure(javax.net.ssl.SSLException("SECRET-URL")).contains("TLS"))
        assertFalse(AccessConnection.failure(java.net.SocketTimeoutException("SECRET-URL")).contains("SECRET-URL"))
        assertTrue(AccessConnection.response(200,"{\"projectId\":\"123\"}","123").contains("HTTP 200"))
        assertTrue(AccessConnection.response(200,"<html>router login</html>","123").contains("não retornou"))
        assertTrue(AccessConnection.response(200,"{\"projectId\":\"other\"}","123").contains("não retornou"))
        assertTrue(AccessConnection.response(403,"SECRET-URL","123").contains("HTTP 403"))
        assertFalse(AccessConnection.response(403,"SECRET-URL","123").contains("SECRET-URL"))
    }
}
