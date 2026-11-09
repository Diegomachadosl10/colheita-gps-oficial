package com.colheitagps.app

import android.app.Activity
import android.app.AlertDialog
import android.os.Bundle
import android.widget.LinearLayout
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Source

class AdminActivity : Activity() {
    private val auth by lazy { FirebaseAuth.getInstance() }
    private val db by lazy { FirebaseFirestore.getInstance() }
    private lateinit var ui: AccessUi
    private var busy = false
    override fun onCreate(savedInstanceState: Bundle?) { super.onCreate(savedInstanceState); ui = AccessUi(this) }
    override fun onResume() { super.onResume(); if (!busy) load() }
    private fun notice(message: String) {
        if (!isDestroyed && !isFinishing) AlertDialog.Builder(this).setMessage(message).setPositiveButton("OK", null).show()
    }
    private fun load() {
        busy = true
        ui.page("Aprovações da equipe", "Verificando seu acesso…")
        AccessRepository.loadTeamAccess(this, { access ->
            if (!access.manages) { busy = false; denied("Seu perfil não permite aprovar usuários."); return@loadTeamAccess }
            val query = if (access.primary) db.collection("usuarios") else db.collection("usuarios")
                .whereEqualTo("frenteId", access.profile["frenteId"])
                .whereEqualTo("turno", access.profile["turno"])
                .whereIn("papel", TeamPolicy.leafRoles.toList())
                .whereIn("solicitacaoPerfil", listOf("operador", "tratorista"))
            query.get(Source.SERVER).addOnCompleteListener {
                if (isDestroyed) return@addOnCompleteListener
                busy = false
                val card = ui.page(if (access.primary) "Gerenciar todos os acessos" else "Aprovar minha equipe",
                    if (access.primary) "Somente você aprova supervisores e ajusta as equipes."
                    else "${TeamPolicy.label(access.team!!)}. Você pode aprovar somente operadores e tratoristas desta equipe.")
                ui.button(card, "Atualizar lista") { if (!busy) load() }
                ui.button(card, "Voltar", true) { finish() }
                if (!it.isSuccessful) {
                    ui.add(card, ui.text("Lista indisponível. Confira a conexão e se as regras e o índice de equipes já foram publicados."))
                    return@addOnCompleteListener
                }
                val users = it.result.documents.filter { d -> TeamPolicy.canApprove(access.uid, access.profile, d.id, d.data) }
                    .sortedWith(compareBy({ d -> if (d.getString("status") == "pendente") 0 else 1 }, { d -> d.get("nome")?.toString().orEmpty() }))
                ui.add(card, ui.text("${users.size} usuários", 14f, ui.green, true))
                for (user in users) {
                    val data = user.data.orEmpty()
                    val name = data["nome"]?.toString() ?: "Sem nome"
                    val role = TeamPolicy.requested(data)
                    val state = AccessPolicy.normalized(data["status"])
                    val item = ui.card().apply { background = ui.background(0xFFF1F6F1.toInt()); elevation = 0f }
                    ui.add(card, item, 18)
                    ui.add(item, ui.text(name, 19f, ui.dark, true), 0)
                    ui.add(item, ui.text("Matrícula: ${data["matricula"] ?: "Não informada"}\n${TeamPolicy.team(data)?.let(TeamPolicy::label) ?: "Equipe não definida"}\nSolicita: $role • Status: $state"))
                    if (!AccessPolicy.approved(data)) ui.button(item, if (state == "bloqueado") "Reativar $role" else "Aprovar $role") {
                        if (!busy) confirm(user.id, name, role, "aprovado")
                    }
                    if (state != "bloqueado") ui.button(item, "Bloquear acesso", true) {
                        if (!busy) confirm(user.id, name, AccessPolicy.role(data).ifEmpty { role }, "bloqueado")
                    }
                    if (access.primary) ui.button(item, "Ajustar frente e turno", true) {
                        if (!busy) adjustTeam(user.id, name)
                    }
                }
            }
        }, { busy = false; denied("Não foi possível verificar o acesso. Confira a conexão e a configuração das equipes no Firebase.") })
    }
    private fun denied(message: String) {
        val card = ui.page("Verificação de acesso", message)
        ui.button(card, "Tentar novamente") { if (!busy) load() }
        ui.button(card, "Voltar", true) { finish() }
    }
    private fun confirm(uid: String, name: String, role: String, status: String) {
        AlertDialog.Builder(this).setTitle(if (status == "aprovado") "Aprovar $role?" else "Bloquear acesso?")
            .setMessage("$name\nConfira a identidade, a frente e o turno antes de confirmar.")
            .setNegativeButton("Cancelar", null).setPositiveButton("Confirmar") { _, _ -> change(uid, role, status) }.show()
    }
    private fun change(uid: String, role: String, status: String) {
        if (busy) return
        val actor = auth.currentUser?.uid ?: return
        busy = true
        val actorRef = db.collection("usuarios").document(actor)
        val target = db.collection("usuarios").document(uid)
        db.runTransaction { tx ->
            val admin = tx.get(actorRef)
            val user = tx.get(target)
            check(auth.currentUser?.uid == actor && TeamPolicy.canApprove(actor, admin.data, uid, user.data))
            if (status == "aprovado") check(role == TeamPolicy.requested(user.data.orEmpty()))
            check(role in TeamPolicy.leafRoles + "supervisor")
            tx.update(target, mapOf("papel" to role, "perfil" to role, "status" to status))
            null
        }.addOnCompleteListener { busy = false; if (!isDestroyed) { if (it.isSuccessful) load() else notice("Alteração recusada. Atualize a lista e confira suas permissões.") } }
    }
    private fun adjustTeam(uid: String, name: String) {
        val body = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        val front = ui.spinner(body, "Frente", TeamPolicy.fronts.values.toTypedArray())
        val shift = ui.spinner(body, "Turno", TeamPolicy.shifts.toTypedArray())
        AlertDialog.Builder(this).setTitle("Equipe de $name").setView(body)
            .setMessage("O acesso passa a valer para a nova equipe. Registros anteriores permanecem na equipe onde foram criados.")
            .setNegativeButton("Cancelar", null).setPositiveButton("Salvar") { _, _ ->
                if (auth.currentUser?.uid != TeamPolicy.PRIMARY_ADMIN_UID || uid == TeamPolicy.PRIMARY_ADMIN_UID) return@setPositiveButton
                busy = true
                db.collection("usuarios").document(uid).update(mapOf("frenteId" to TeamPolicy.fronts.keys.elementAt(front.selectedItemPosition), "turno" to shift.selectedItem.toString()))
                    .addOnCompleteListener { busy = false; if (it.isSuccessful) load() else notice("Não foi possível ajustar a equipe.") }
            }.show()
    }
}
