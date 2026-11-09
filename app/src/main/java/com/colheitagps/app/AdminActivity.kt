package com.colheitagps.app

import android.app.Activity
import android.app.AlertDialog
import android.os.Bundle
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Source

class AdminActivity : Activity() {
    private val auth by lazy { FirebaseAuth.getInstance() }
    private val db by lazy { FirebaseFirestore.getInstance() }
    private lateinit var ui: AccessUi
    private var busy = false
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        ui = AccessUi(this)
    }
    override fun onResume() { super.onResume(); if (!busy) load() }
    private fun notice(message: String) {
        if (!isDestroyed && !isFinishing) AlertDialog.Builder(this).setMessage(message).setPositiveButton("OK", null).show()
    }
    private fun denied() {
        val card = ui.page("Acesso exclusivo", "Somente o administrador principal aprovado pode autorizar ou bloquear usuários. Supervisores não possuem essa permissão.")
        ui.button(card, "Voltar") { finish() }
    }
    private fun load() {
        if (auth.currentUser == null) { AccessRepository.logout(this); return }
        busy = true
        ui.page("Aprovações", "Verificando a permissão do administrador…")
        AccessRepository.load(this, { data ->
            if (!AccessPolicy.canApprove(data)) { busy = false; denied(); return@load }
            db.collection("usuarios").get(Source.SERVER).addOnCompleteListener {
                if (isDestroyed) return@addOnCompleteListener
                busy = false
                val card = ui.page("Gerenciar acessos", "Aprovação exclusiva do administrador principal. Confira nome, matrícula e frente antes de liberar uma conta.")
                ui.button(card, "Atualizar lista") { if (!busy) load() }
                ui.button(card, "Voltar ao aplicativo", true) { if (!busy) finish() }
                if (!it.isSuccessful) {
                    ui.add(card, ui.text("Não foi possível carregar a lista. Verifique sua conexão e as permissões já configuradas no Firestore."))
                    return@addOnCompleteListener
                }
                val users = it.result.documents.filter { d ->
                    d.id != auth.currentUser?.uid && AccessPolicy.role(d.data) != "administrador"
                }.sortedWith(compareBy({ d -> if (AccessPolicy.normalized(d.get("status")) == "pendente") 0 else 1 },
                    { d -> AccessPolicy.normalized(d.get("nome")) }))
                val pending = users.count { d -> AccessPolicy.normalized(d.get("status")) == "pendente" }
                ui.add(card, ui.text("${users.size} usuários  •  $pending aguardando aprovação", 14f, ui.green, true), 20)
                if (users.isEmpty()) ui.add(card, ui.text("Nenhuma solicitação no momento."))
                users.forEach { user ->
                    val dataUser = user.data.orEmpty()
                    val name = dataUser["nome"]?.toString() ?: "Sem nome"
                    val role = requestedRole(dataUser)
                    val state = AccessPolicy.normalized(dataUser["status"]).ifEmpty { "pendente" }
                    val item = ui.card().apply {
                        background = ui.background(0xFFF1F6F1.toInt(), 16)
                        elevation = 0f
                    }
                    ui.add(card, item, 18)
                    ui.add(item, ui.text(name, 19f, ui.dark, true), 0)
                    ui.add(item, ui.text("Matrícula: ${dataUser["matricula"] ?: "Não informada"}\nFrente: ${dataUser["frenteId"] ?: "Não informada"}\nTurno: ${dataUser["turno"] ?: "Não informado"}\nFunção: ${dataUser["funcao"] ?: "Não informada"}"), 8)
                    ui.add(item, ui.text("Solicita: $role  •  Status: $state", 14f, ui.green, true))
                    if (!AccessPolicy.approved(dataUser)) {
                        ui.button(item, if (state == "bloqueado") "Reativar como $role" else "Aprovar $role") {
                            if (!busy) confirm(user.id, name, role, "aprovado")
                        }
                    }
                    if (state != "bloqueado") ui.button(item, "Bloquear acesso", true) {
                        if (!busy) confirm(user.id, name, AccessPolicy.role(dataUser).ifEmpty { "operador" }, "bloqueado")
                    }
                }
            }
        }, {
            busy = false
            val card = ui.page("Verificação indisponível", "Não foi possível consultar as permissões no servidor. Verifique a conexão e tente novamente.")
            ui.button(card, "Tentar novamente") { if (!busy) load() }
            ui.button(card, "Voltar", true) { finish() }
        })
    }
    private fun requestedRole(data: Map<String, Any>): String =
        if (AccessPolicy.normalized(data["solicitacaoPerfil"]).ifEmpty { AccessPolicy.role(data) } == "supervisor") "supervisor" else "operador"

    private fun confirm(uid: String, name: String, role: String, status: String) {
        AlertDialog.Builder(this).setTitle(if (status == "aprovado") "Aprovar $role?" else "Bloquear acesso?")
            .setMessage("$name\nConfira a identidade e a matrícula com a relação oficial antes de confirmar.")
            .setNegativeButton("Cancelar", null)
            .setPositiveButton("Confirmar") { _, _ -> change(uid, role, status) }.show()
    }
    private fun change(uid: String, role: String, status: String) {
        if (busy) return
        val actor = auth.currentUser?.uid ?: return
        if (uid == actor || role !in setOf("operador", "funcionario", "supervisor")) return
        busy = true
        val actorRef = db.collection("usuarios").document(actor)
        val target = db.collection("usuarios").document(uid)
        // Recheck both records at the time of the write; the list is not an authorization cache.
        db.runTransaction { transaction ->
            val admin = transaction.get(actorRef)
            val user = transaction.get(target)
            check(auth.currentUser?.uid == actor && AccessPolicy.canApprove(admin.data)) { "Acesso exclusivo do administrador principal." }
            check(user.exists() && AccessPolicy.role(user.data) != "administrador") { "Conta protegida." }
            if (status == "aprovado") check(role == requestedRole(user.data.orEmpty())) { "Solicitação alterada. Atualize a lista." }
            transaction.update(target, mapOf("papel" to role, "perfil" to role, "status" to status))
            null
        }.addOnCompleteListener {
            if (isDestroyed) return@addOnCompleteListener
            busy = false
            if (it.isSuccessful) load() else notice("Não foi possível alterar o acesso. Verifique a conexão e suas permissões e atualize a lista.")
        }
    }
}
