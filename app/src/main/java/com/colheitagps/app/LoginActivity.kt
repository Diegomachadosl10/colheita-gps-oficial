package com.colheitagps.app

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.view.View
import com.google.firebase.auth.AuthResult
import android.text.InputType
import android.widget.LinearLayout
import com.google.firebase.FirebaseNetworkException
import com.google.firebase.FirebaseTooManyRequestsException
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthException
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.MetadataChanges

class LoginActivity : Activity() {
    private val auth by lazy { FirebaseAuth.getInstance() }
    private val db by lazy { FirebaseFirestore.getInstance() }
    private lateinit var ui: AccessUi
    private var busy = false
    private var onLoginPage = false
    private var onWelcomePage = false
    private var waiting = false
    private var listener: ListenerRegistration? = null
    private var attempt = 0
    private val waitHandler=Handler(Looper.getMainLooper())
    private var enterButton:Button?=null
    private var loginIdentity:EditText?=null
    private var loginPassword:EditText?=null
    private var connectionHelp:View?=null
    private var connectionMessage:TextView?=null
    private var observedSignIn:AccessAttempt<AuthResult>?=null
    private var resumed=false
    private var needsVerification=false
    private var deferredSignInError:String?=null
    private val slowSignIn=Runnable {
        val pending=SignInAttempts.requests.current
        if(onLoginPage&&pending!=null&&!pending.task.isComplete&&!isFinishing&&!isDestroyed) {
            enterButton?.text="Aguardando resposta do acesso"
            connectionHelp?.visibility=View.VISIBLE
            connectionMessage?.text="O acesso está demorando a responder nesta rede. Use Testar conexão ou alterne a rede. A tentativa continua em andamento; sua autorização ainda não foi confirmada. Você pode sair desta tela com a tecla Voltar."
        }
    }
    private val frentes = arrayOf("Santo Inácio", "Santa Inês", "Itororó", "Cafeara", "Lupionópolis", "Colorado", "Sandovalina", "Jardim Olinda", "Itaguajé")
    private val ids = arrayOf("santo_inacio", "santa_ines", "itororo", "cafeara", "lupionopolis", "colorado", "sandovalina", "jardim_olinda", "itaguaje")
    private fun notice(message: String) {
        if (!isFinishing && !isDestroyed) AlertDialog.Builder(this).setMessage(message).setPositiveButton("OK", null).show()
    }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        ui = AccessUi(this)
        val pending=SignInAttempts.requests.current
        if(pending!=null&&!pending.task.isComplete) login()
        else {if(pending!=null) SignInAttempts.requests.consume(pending);if(auth.currentUser==null) welcome() else verify()}
    }
    override fun onResume() {
        super.onResume();resumed=true
        if(onWelcomePage) welcome()
        StartupUpdates.resume(this)
        if(needsVerification) {needsVerification=false;verify()}
        deferredSignInError?.let {deferredSignInError=null;notice(it)}
    }
    override fun onPause() {resumed=false;super.onPause()}
    override fun onWindowFocusChanged(hasFocus:Boolean) { super.onWindowFocusChanged(hasFocus);if(hasFocus) StartupUpdates.focused(this) }
    private fun welcome() {
        waiting=false;onLoginPage=false;onWelcomePage=true;listener?.remove();listener=null
        WelcomeUi.render(this,{login()},{register()})
    }
    override fun onStart() {super.onStart();if(waiting) watchApproval();if(onLoginPage) observeSignIn()}
    override fun onStop() {listener?.remove();listener=null;observedSignIn=null;waitHandler.removeCallbacks(slowSignIn);super.onStop()}
    override fun onDestroy() {attempt++;listener?.remove();waitHandler.removeCallbacksAndMessages(null);super.onDestroy()}

    private fun page(title: String, description: String): LinearLayout {
        if(onWelcomePage) ui=AccessUi(this)
        waitHandler.removeCallbacks(slowSignIn)
        observedSignIn=null
        waiting = false
        onLoginPage = false
        onWelcomePage = false
        listener?.remove(); listener = null
        return ui.page(title, description)
    }
    private fun logout() {
        attempt++; busy = false
        AgroNotifications.logout(this)
        SignInAttempts.requests.current?.let {SignInAttempts.requests.consume(it)}
        auth.signOut(); login()
    }
    private fun errorMessage(error: Exception, registering: Boolean = false): String = when {
        error is FirebaseNetworkException -> "Sem conexão com o serviço de acesso. Verifique a internet e tente novamente."
        error is FirebaseTooManyRequestsException -> "Muitas tentativas. Aguarde alguns minutos e tente novamente."
        error is FirebaseAuthException -> when (error.errorCode) {
            "ERROR_EMAIL_ALREADY_IN_USE" -> "Esta matrícula ainda possui uma credencial no Firebase Authentication. Se o número foi digitado errado ou o acesso foi desativado, peça ao administrador para corrigir a matrícula ou excluir definitivamente a conta. Não basta remover o cadastro do Firestore."
            "ERROR_USER_DISABLED" -> "Esta conta foi desativada. Procure o administrador principal."
            "ERROR_OPERATION_NOT_ALLOWED" -> "O acesso por senha está indisponível no Firebase. Informe o administrador."
            "ERROR_WEAK_PASSWORD" -> "Escolha uma senha com pelo menos 8 caracteres."
            "ERROR_NETWORK_REQUEST_FAILED" -> "O aplicativo não conseguiu conectar ao serviço de acesso nesta rede. Use Testar conexão."
            "ERROR_APP_NOT_AUTHORIZED", "ERROR_INVALID_API_KEY" -> "O serviço recusou a configuração do aplicativo. Informe esse problema ao administrador."
            "ERROR_INVALID_CREDENTIAL", "ERROR_INVALID_LOGIN_CREDENTIALS", "ERROR_WRONG_PASSWORD", "ERROR_USER_NOT_FOUND" -> "Identificação ou senha incorreta. Para matrícula, use o mesmo número do cadastro, incluindo zeros à esquerda."
            else -> if(registering) "Não foi possível criar a conta. Confira os dados e tente novamente."
                else "Não foi possível concluir o acesso. Use Testar conexão e tente novamente."
        }
        else -> "Não foi possível concluir. Verifique a conexão e tente novamente."
    }
    private fun login() {
        val card = page("Bem-vindo de volta", "Entre com sua matrícula e senha. O administrador também pode usar seu e-mail cadastrado.")
        onLoginPage = true
        val id = ui.field(card, "Matrícula ou e-mail do administrador")
        val password = ui.field(card, "Senha", true)
        val enter = ui.button(card, "Entrar") {}
        enter.setOnClickListener {
            if (busy) return@setOnClickListener
            val value = id.text.toString().trim()
            val secret = password.text.toString()
            val email = when {
                value.matches(Regex("[0-9]{1,20}")) -> AccessPolicy.internalEmail(value)
                android.util.Patterns.EMAIL_ADDRESS.matcher(value).matches() -> value
                else -> { notice("Informe a matrícula numérica ou o e-mail cadastrado do administrador."); return@setOnClickListener }
            }
            if (secret.isBlank()) { notice("Informe sua senha."); return@setOnClickListener }
            try {
                SignInAttempts.requests.start(value,SystemClock.elapsedRealtime()) {auth.signInWithEmailAndPassword(email,secret)}
                observeSignIn()
            } catch(error:Exception) {busy=false;enter.isEnabled=true;notice(errorMessage(error))}
        }
        enterButton=enter;loginIdentity=id;loginPassword=password
        val help=LinearLayout(this).apply {orientation=LinearLayout.VERTICAL;visibility=View.GONE}
        connectionMessage=ui.text("");ui.add(help,connectionMessage!!,8)
        ui.button(help,"Testar conexão",true) {testConnection()}
        ui.button(help,"Abrir configurações de Wi-Fi",true) {
            try {startActivity(Intent(android.provider.Settings.ACTION_WIFI_SETTINGS))} catch(_:Exception) {notice("Abra as configurações de Wi-Fi do Android.")}
        }
        connectionHelp=help;ui.add(card,help,0)
        ui.button(card, "Verificar atualização", true) { startActivity(Intent(this, UpdateActivity::class.java)) }
        ui.button(card, "Solicitar acesso", true) { if (!busy) register() }
        ui.button(card, "Recuperar senha", true) {
            if (!busy) {
                val email = id.text.toString().trim()
                if (!email.contains("@") || email.endsWith("@acesso.dm-agro.app", true)) {
                    notice("Contas por matrícula não usam e-mail pessoal. Para recuperar uma senha esquecida, procure o administrador principal. Não crie outra conta para a mesma matrícula.")
                } else {
                    busy = true
                    auth.sendPasswordResetEmail(email).addOnCompleteListener {
                        busy = false
                        notice(if (it.isSuccessful) "Se este e-mail estiver cadastrado, você receberá as instruções de recuperação." else errorMessage(it.exception ?: Exception()))
                    }
                }
            }
        }
        observeSignIn()
    }
    private fun observeSignIn() {
        val pending=SignInAttempts.requests.current?:return
        if(!onLoginPage||observedSignIn===pending||isDestroyed) return
        observedSignIn=pending;busy=true
        enterButton?.isEnabled=false;enterButton?.text="Entrando…"
        loginIdentity?.setText(pending.identity);loginIdentity?.isEnabled=false;loginPassword?.isEnabled=false
        waitHandler.removeCallbacks(slowSignIn)
        waitHandler.postDelayed(slowSignIn,pending.remaining(SystemClock.elapsedRealtime()))
        pending.task.addOnCompleteListener(this) {result->
            if(isDestroyed||!onLoginPage||observedSignIn!==pending||SignInAttempts.requests.current!==pending) return@addOnCompleteListener
            SignInAttempts.requests.consume(pending);observedSignIn=null;waitHandler.removeCallbacks(slowSignIn)
            busy=false;enterButton?.isEnabled=true;enterButton?.text="Entrar";loginIdentity?.isEnabled=true;loginPassword?.isEnabled=true
            if(result.isSuccessful&&auth.currentUser?.uid==result.result?.user?.uid) {
                if(resumed) verify() else needsVerification=true
            } else {
                val error=result.exception?:IllegalStateException("A sessão mudou. Tente entrar novamente.")
                val message=errorMessage(error)
                connectionHelp?.visibility=View.VISIBLE;connectionMessage?.text=message
                if(resumed) notice(message) else deferredSignInError=message
            }
        }
    }
    private fun testConnection() {
        val dialog=AlertDialog.Builder(this).setTitle("Teste da conexão do aplicativo")
            .setMessage("Verificando o endereço do serviço nesta rede, sem enviar matrícula, e-mail ou senha…")
            .setNegativeButton("Fechar",null).setPositiveButton("Copiar resultado",null).create()
        dialog.show();dialog.getButton(AlertDialog.BUTTON_POSITIVE).isEnabled=false
        Thread {
            val report=try {AccessConnection.check(applicationContext)} catch(_:Exception) {"Não foi possível concluir o diagnóstico nesta rede."}
            runOnUiThread {
                if(isDestroyed||isFinishing||!dialog.isShowing) return@runOnUiThread
                dialog.setMessage(report)
                dialog.getButton(AlertDialog.BUTTON_POSITIVE).apply {
                    isEnabled=true;setOnClickListener {
                        (getSystemService(CLIPBOARD_SERVICE) as android.content.ClipboardManager).setPrimaryClip(android.content.ClipData.newPlainText("Diagnóstico de conexão",report))
                        android.widget.Toast.makeText(this@LoginActivity,"Resultado copiado",android.widget.Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }.start()
    }
    private fun register(existing: Boolean = false) {
        val card = page(if (existing) "Concluir solicitação" else "Solicitar acesso",
            "Escolha sua frente e turno. Seu supervisor aprova os operadores e tratoristas da equipe. Supervisores são aprovados pelo administrador principal.")
        val matricula = ui.field(card, "Número de matrícula").apply { inputType = InputType.TYPE_CLASS_NUMBER }
        if (existing) {
            matricula.setText(auth.currentUser?.email?.substringBefore("@")?.removePrefix("matricula."))
            matricula.isEnabled = false
        }
        val name = ui.field(card, "Nome completo")
        val front = ui.spinner(card, "Frente", frentes)
        val shift = ui.spinner(card, "Turno", arrayOf("A", "B", "C")).apply { setSelection(2) }
        val role = ui.profileChoice(card)
        val password = if (!existing) ui.field(card, "Criar senha (mínimo 8 caracteres)", true) else null
        val confirmation = if (!existing) ui.field(card, "Confirmar senha", true) else null
        val submit = ui.button(card, "Enviar solicitação") {}
        submit.setOnClickListener {
            if (busy) return@setOnClickListener
            val m = matricula.text.toString().trim()
            val n = name.text.toString().trim()
            val secret = password?.text?.toString().orEmpty()
            if (!m.matches(Regex("[0-9]{1,20}")) || n.length < 3 ||
                (!existing && (secret.length < 8 || secret != confirmation?.text?.toString()))) {
                notice("Confira a matrícula, o nome e as senhas iguais com pelo menos 8 caracteres.")
                return@setOnClickListener
            }
            val data = hashMapOf<String, Any>(
                "nome" to n, "matricula" to m, "frenteId" to ids[front.selectedItemPosition],
                "turno" to shift.selectedItem.toString(), "funcao" to role.selectedProfile.label,
                "papel" to "operador", "perfil" to "operador",
                "solicitacaoPerfil" to role.selectedProfile.requested,
                "status" to "pendente", "criadoEm" to FieldValue.serverTimestamp()
            )
            busy = true; submit.isEnabled = false; submit.text = "Enviando…"; role.setSelectable(false)
            fun saveRequest() {
                val user = auth.currentUser ?: run { busy = false; login(); return }
                val doc = db.collection("usuarios").document(user.uid)
                // A retry may create the missing request, but can never replace an approved profile.
                db.runTransaction { transaction ->
                    if (!transaction.get(doc).exists()) transaction.set(doc, data)
                    null
                }.addOnCompleteListener {
                    if (isDestroyed) return@addOnCompleteListener
                    busy = false
                    if (it.isSuccessful) { AgroNotifications.event(applicationContext,"user"); verify() } else {
                        register(true)
                        notice("Sua conta de acesso foi criada, mas a solicitação não foi salva. Confira a internet e toque em Enviar solicitação. Se sair, entre novamente com a mesma matrícula e senha para concluir.")
                    }
                }
            }
            if (existing) saveRequest() else auth.createUserWithEmailAndPassword(AccessPolicy.internalEmail(m), secret)
                .addOnCompleteListener {
                    if (isDestroyed) return@addOnCompleteListener
                    if (it.isSuccessful) saveRequest() else {
                        busy = false; submit.isEnabled = true; submit.text = "Enviar solicitação"; role.setSelectable(true)
                        notice(errorMessage(it.exception ?: Exception(), true))
                    }
                }
        }
        ui.button(card, if (existing) "Sair da conta" else "Voltar ao login", true) { if (!busy) logout() }
    }
    private fun verify() {
        if (auth.currentUser == null) { login(); return }
        val token = ++attempt
        page("Verificando acesso", "Consultando sua autorização no servidor…")
        AccessRepository.load(this, { data ->
            if (token != attempt || isDestroyed) return@load
            when {
                data == null -> {
                    if (auth.currentUser?.email?.matches(Regex("matricula\\.[0-9]{1,20}@acesso\\.dm-agro\\.app")) == true) {
                        auth.currentUser?.reload()?.addOnCompleteListener { result ->
                            if(token!=attempt||isDestroyed) return@addOnCompleteListener
                            if(result.isSuccessful) register(true)
                            else if((result.exception as? FirebaseAuthException)?.errorCode in setOf("ERROR_USER_NOT_FOUND","ERROR_USER_DISABLED","ERROR_USER_TOKEN_EXPIRED")) {
                                logout(); notice("A conta anterior foi encerrada. Solicite um novo acesso para cadastrar novamente.")
                            } else status("Não foi possível conferir a conta","Confira a conexão e tente novamente.")
                        }
                    }
                    else status("Cadastro não localizado", "O administrador precisa conferir o documento usuarios com o UID desta conta. A sessão sozinha não autoriza acesso.")
                }
                TeamPolicy.allowed(auth.currentUser?.uid.orEmpty(), data) -> if (TeamPolicy.primary(auth.currentUser?.uid.orEmpty())) adminPanel(data) else openApp()
                AccessPolicy.normalized(data["status"]) == "excluido" -> status("Acesso encerrado", "Seu acesso foi excluído pelo administrador principal. Procure o administrador para esclarecer seu cadastro.")
                AccessPolicy.normalized(data["status"]) == "bloqueado" -> status("Acesso bloqueado", "Procure o administrador principal para revisar seu acesso.")
                AccessPolicy.normalized(data["status"]) == "aprovado" -> status("Perfil precisa de revisão", "Sua conta foi aprovada, mas o perfil está ausente ou inválido. Peça ao administrador para conferir o cadastro.")
                else -> {
                    status("Aguardando aprovação", "Sua solicitação foi enviada. O supervisor da sua frente e turno pode aprovar operadores e tratoristas. Supervisores dependem do administrador principal. Esta tela acompanha a aprovação quando há conexão.")
                    waiting = true; watchApproval()
                }
            }
        }, {
            if (token == attempt && !isDestroyed) status("Não foi possível verificar", "Conecte-se à internet e tente novamente. Sua aprovação será consultada no servidor.")
        })
    }
    private fun watchApproval() {
        if (listener != null || !waiting) return
        val uid = auth.currentUser?.uid ?: return
        listener = db.collection("usuarios").document(uid).addSnapshotListener(MetadataChanges.INCLUDE) { doc, _ ->
            if (!waiting || isDestroyed || auth.currentUser?.uid != uid || doc == null || doc.metadata.isFromCache) return@addSnapshotListener
            if (AccessPolicy.normalized(doc.data?.get("status")) in setOf("aprovado", "bloqueado")) verify()
        }
    }
    private fun openApp() {
        startActivity(Intent(this, MainActivity::class.java)); finish()
    }
    private fun adminPanel(data: Map<String, Any>) {
        val name = data["nome"]?.toString()?.trim().orEmpty().ifBlank { "Administrador" }
        val card = page("Painel do administrador", "Olá, $name. Gerencie os acessos ou abra o aplicativo.")
        ui.button(card, "Abrir aplicativo") { openApp() }
        ui.button(card, "Gerenciar usuários", true) { startActivity(Intent(this, AdminActivity::class.java)) }
        ui.button(card, "Sair da conta", true) { logout() }
    }
    private fun status(title: String, message: String) {
        val card = page(title, message)
        if(title.startsWith("Não foi possível")) ui.button(card,"Testar conexão",true) {testConnection()}
        ui.button(card, "Verificar novamente") { verify() }
        ui.button(card, "Sair da conta", true) { logout() }
    }
    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        if (busy) {moveTaskToBack(true);return}
        when {
            onWelcomePage -> moveTaskToBack(true)
            onLoginPage -> welcome()
            auth.currentUser == null -> login()
            else -> moveTaskToBack(true)
        }
    }
}
