package com.colheitagps.app

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.os.Bundle
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
    private var waiting = false
    private var listener: ListenerRegistration? = null
    private var attempt = 0
    private val frentes = arrayOf("Santo Inácio", "Santa Inês", "Itororó", "Cafeara", "Lupionópolis", "Colorado", "Sandovalina", "Jardim Olinda", "Itaguajé")
    private val ids = arrayOf("santo_inacio", "santa_ines", "itororo", "cafeara", "lupionopolis", "colorado", "sandovalina", "jardim_olinda", "itaguaje")
    private fun notice(message: String) {
        if (!isFinishing && !isDestroyed) AlertDialog.Builder(this).setMessage(message).setPositiveButton("OK", null).show()
    }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        ui = AccessUi(this)
        if (auth.currentUser == null) login() else verify()
    }
    override fun onStart() { super.onStart(); if (waiting) watchApproval() }
    override fun onStop() { listener?.remove(); listener = null; super.onStop() }
    override fun onDestroy() { attempt++; listener?.remove(); super.onDestroy() }

    private fun page(title: String, description: String): LinearLayout {
        waiting = false
        onLoginPage = false
        listener?.remove(); listener = null
        return ui.page(title, description)
    }
    private fun logout() {
        attempt++; busy = false
        auth.signOut(); login()
    }
    private fun errorMessage(error: Exception, registering: Boolean = false): String = when {
        error is FirebaseNetworkException -> "Sem conexão com o serviço de acesso. Verifique a internet e tente novamente."
        error is FirebaseTooManyRequestsException -> "Muitas tentativas. Aguarde alguns minutos e tente novamente."
        error is FirebaseAuthException -> when (error.errorCode) {
            "ERROR_EMAIL_ALREADY_IN_USE" -> "Esta matrícula já possui uma conta. Use Entrar com a senha cadastrada."
            "ERROR_USER_DISABLED" -> "Esta conta foi desativada. Procure o administrador principal."
            "ERROR_OPERATION_NOT_ALLOWED" -> "O acesso por senha está indisponível no Firebase. Informe o administrador."
            "ERROR_WEAK_PASSWORD" -> "Escolha uma senha com pelo menos 8 caracteres."
            else -> if (registering) "Não foi possível criar a conta. Confira os dados e tente novamente."
                else "Identificação ou senha incorreta. Para matrícula, use o mesmo número do cadastro, incluindo zeros à esquerda."
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
            busy = true; enter.isEnabled = false; enter.text = "Entrando…"
            auth.signInWithEmailAndPassword(email, secret).addOnCompleteListener { result ->
                if (isDestroyed) return@addOnCompleteListener
                busy = false; enter.isEnabled = true; enter.text = "Entrar"
                if (result.isSuccessful) verify() else notice(errorMessage(result.exception ?: Exception()))
            }
        }
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
        val role = ui.spinner(card, "Tipo de acesso solicitado", arrayOf("Operador", "Tratorista", "Supervisor"))
        val job = ui.field(card, "Função (ex.: operador de colhedora)")
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
                "turno" to shift.selectedItem.toString(), "funcao" to job.text.toString().trim(),
                "papel" to "operador", "perfil" to "operador",
                "solicitacaoPerfil" to when (role.selectedItemPosition) { 2 -> "supervisor"; 1 -> "tratorista"; else -> "operador" },
                "status" to "pendente", "criadoEm" to FieldValue.serverTimestamp()
            )
            busy = true; submit.isEnabled = false; submit.text = "Enviando…"
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
                    if (it.isSuccessful) verify() else {
                        register(true)
                        notice("Sua conta de acesso foi criada, mas a solicitação não foi salva. Confira a internet e toque em Enviar solicitação. Se sair, entre novamente com a mesma matrícula e senha para concluir.")
                    }
                }
            }
            if (existing) saveRequest() else auth.createUserWithEmailAndPassword(AccessPolicy.internalEmail(m), secret)
                .addOnCompleteListener {
                    if (isDestroyed) return@addOnCompleteListener
                    if (it.isSuccessful) saveRequest() else {
                        busy = false; submit.isEnabled = true; submit.text = "Enviar solicitação"
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
                    if (auth.currentUser?.email?.matches(Regex("matricula\\.[0-9]{1,20}@acesso\\.dm-agro\\.app")) == true) register(true)
                    else status("Cadastro não localizado", "O administrador precisa conferir o documento usuarios com o UID desta conta. A sessão sozinha não autoriza acesso.")
                }
                TeamPolicy.allowed(auth.currentUser?.uid.orEmpty(), data) -> if (TeamPolicy.primary(auth.currentUser?.uid.orEmpty())) adminPanel() else openApp()
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
    private fun adminPanel() {
        val card = page("Painel do administrador", "Controle de acesso exclusivo do administrador principal.")
        ui.button(card, "Abrir aplicativo") { openApp() }
        ui.button(card, "Gerenciar usuários", true) { startActivity(Intent(this, AdminActivity::class.java)) }
        ui.button(card, "Sair da conta", true) { logout() }
    }
    private fun status(title: String, message: String) {
        val card = page(title, message)
        ui.button(card, "Verificar novamente") { verify() }
        ui.button(card, "Sair da conta", true) { logout() }
    }
    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        if (busy) return
        when {
            onLoginPage -> finish()
            auth.currentUser == null -> login()
            else -> moveTaskToBack(true)
        }
    }
}
