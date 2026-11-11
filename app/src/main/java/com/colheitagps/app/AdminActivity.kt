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
    private var removed = false
    private var search = ""
    private var statusFilter = ""
    private var frontFilter = ""
    private var pageIndex = 0
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
                val toolbar=LinearLayout(this).apply { orientation=LinearLayout.HORIZONTAL };ui.add(card,toolbar,0)
                for((label,action) in listOf("Atualizar" to { if(!busy) load() },"Voltar" to { finish() })) {
                    val holder=LinearLayout(this).apply { orientation=LinearLayout.VERTICAL }
                    toolbar.addView(holder,LinearLayout.LayoutParams(0,-2,1f).apply { marginEnd=ui.dp(5) });ui.button(holder,label,true,action)
                }
                if(access.primary) {
                    val more=ui.button(card,"Mais opções",true) {}
                    more.setOnClickListener { android.widget.PopupMenu(this,more).apply {
                        menu.add(if(removed) "Ver usuários ativos" else "Cadastros excluídos").setOnMenuItemClickListener { removed=!removed;statusFilter="";pageIndex=0;load();true }
                        menu.add("Notificações e serviço gratuito").setOnMenuItemClickListener { startActivity(android.content.Intent(this@AdminActivity,NotificationSettingsActivity::class.java));true }
                    }.show() }
                }
                if (!it.isSuccessful) {
                    ui.add(card, ui.text("Lista indisponível. Confira a conexão e se as regras e o índice de equipes já foram publicados."))
                    return@addOnCompleteListener
                }
                val users = it.result.documents.filter { d ->
                    if(access.primary && removed) d.id!=access.uid && !TeamPolicy.primary(d.id) && AccessPolicy.normalized(d.get("status"))=="excluido"
                    else TeamPolicy.canApprove(access.uid, access.profile, d.id, d.data)
                }
                    .map { d -> AdminUser(d.id,d.data.orEmpty()) }
                val field=ui.field(card,"Buscar por nome, matrícula ou equipe").apply { setText(search) }
                val statusValues=arrayOf("Todos","pendente","aprovado","bloqueado")
                val status=ui.spinner(card,"Situação",arrayOf("Todas as situações","Aguardando aprovação","Aprovados","Bloqueados"))
                status.setSelection(statusValues.indexOf(statusFilter).coerceAtLeast(0))
                val front=if(access.primary) ui.spinner(card,"Frente",arrayOf("Todas as frentes")+TeamPolicy.fronts.values.toTypedArray()).apply {
                    setSelection((TeamPolicy.fronts.keys.indexOf(frontFilter)+1).coerceAtLeast(0))
                } else null
                ui.button(card,"Compartilhar lista em PDF / imagem",true) {
                    val filtered=AdminUserList.filter(users,search,statusFilter,frontFilter)
                    AccessRepository.loadTeamAccess(this,{current->
                        if(current.uid!=access.uid||!current.manages||(!current.primary&&current.team!=access.team)) {notice("Não foi possível confirmar a permissão para compartilhar.");return@loadTeamAccess}
                        val report=FieldReport("Lista de usuários",if(current.primary) "Administração DM AGRO" else TeamPolicy.label(current.team!!),
                            "${filtered.size} usuário(s) • resultado dos filtros da tela",listOf(ReportSection("Cadastros",listOf("Nome / matrícula","Equipe","Perfil / situação"),filtered.map {user->
                                val d=user.data;listOf("${d["nome"]?:"Sem nome"}\nMatrícula: ${d["matricula"]?:"Não informada"}",TeamPolicy.team(d)?.let(TeamPolicy::label)?:"Não definida","${TeamPolicy.requested(d)}\n${AccessPolicy.normalized(d["status"])}")
                            })))
                        val cache=TeamStore(applicationContext,current.team?:"santo_inacio_A").cache
                        FieldReportSharing.choose(this,report,cache) {auth.currentUser?.uid==current.uid}
                    },{notice("Confira sua conexão antes de compartilhar a lista.")})
                }
                val rows=LinearLayout(this).apply { orientation=LinearLayout.VERTICAL }; ui.add(card,rows)
                fun refresh() {
                    val filtered=AdminUserList.filter(users,search,statusFilter,frontFilter)
                    pageIndex=pageIndex.coerceIn(0,AdminUserList.lastPage(filtered)); rows.removeAllViews()
                    ui.add(rows,ui.text("${filtered.size} de ${users.size} usuários • página ${pageIndex+1}/${AdminUserList.lastPage(filtered)+1}",14f,ui.green,true),0)
                    if(filtered.isEmpty()) ui.add(rows,ui.text("Nenhum usuário encontrado com esses filtros."))
                    for(user in AdminUserList.page(filtered,pageIndex)) {
                        val d=user.data
                        val item=ui.card().apply { background=ui.background(0xFFF1F6F1.toInt()); elevation=0f }
                        ui.add(rows,item,12)
                        ui.add(item,ui.text(d["nome"]?.toString()?:"Sem nome",17f,ui.dark,true),0)
                        ui.add(item,ui.text("Matrícula: ${d["matricula"]?:"Não informada"}\n${TeamPolicy.team(d)?.let(TeamPolicy::label)?:"Equipe não definida"}\n${TeamPolicy.requested(d).replaceFirstChar { c->c.uppercase() }} • ${AccessPolicy.normalized(d["status"])}",13f),4)
                        ui.button(item,"Gerenciar") { if(!busy) manageUser(access,user) }
                    }
                    val navigation=LinearLayout(this).apply { orientation=LinearLayout.HORIZONTAL }
                    ui.add(rows,navigation)
                    for((label,delta) in listOf("Anterior" to -1,"Próxima" to 1)) {
                        val holder=LinearLayout(this).apply { orientation=LinearLayout.VERTICAL }
                        navigation.addView(holder,LinearLayout.LayoutParams(0,-2,1f).apply { marginEnd=ui.dp(5) })
                        ui.button(holder,label,true) { pageIndex+=delta; refresh() }.isEnabled=if(delta<0) pageIndex>0 else pageIndex<AdminUserList.lastPage(filtered)
                    }
                }
                field.addTextChangedListener(object:android.text.TextWatcher {
                    override fun beforeTextChanged(s:CharSequence?,start:Int,count:Int,after:Int) {}
                    override fun onTextChanged(s:CharSequence?,start:Int,before:Int,count:Int) { search=s.toString(); pageIndex=0; refresh() }
                    override fun afterTextChanged(s:android.text.Editable?) {}
                })
                fun selection(action:()->Unit)=object:android.widget.AdapterView.OnItemSelectedListener {
                    override fun onNothingSelected(parent:android.widget.AdapterView<*>?) {}
                    override fun onItemSelected(parent:android.widget.AdapterView<*>?,view:android.view.View?,position:Int,id:Long) { action() }
                }
                status.onItemSelectedListener=selection { val value=statusValues[status.selectedItemPosition].takeUnless { p->p=="Todos" }.orEmpty(); if(value!=statusFilter) { statusFilter=value;pageIndex=0;refresh() } }
                front?.onItemSelectedListener=selection { val value=TeamPolicy.fronts.keys.elementAtOrNull(front.selectedItemPosition-1).orEmpty();if(value!=frontFilter) { frontFilter=value;pageIndex=0;refresh() } }
                refresh()
            }
        }, { busy = false; denied("Não foi possível verificar o acesso. Confira a conexão e a configuração das equipes no Firebase.") })
    }
    private fun manageUser(access:TeamAccess,user:AdminUser) {
                    val data = user.data
                    val name = data["nome"]?.toString() ?: "Sem nome"
                    val role = TeamPolicy.requested(data)
                    val state = AccessPolicy.normalized(data["status"])
                    val item=LinearLayout(this).apply { orientation=LinearLayout.VERTICAL;setPadding(ui.dp(18),0,ui.dp(18),ui.dp(12)) }
                    val dialog=AlertDialog.Builder(this).setTitle(name).setMessage("Matrícula: ${data["matricula"]}\n${TeamPolicy.team(data)?.let(TeamPolicy::label).orEmpty()}\nPerfil: $role • $state")
                        .setView(android.widget.ScrollView(this).apply { addView(item) }).setNegativeButton("Fechar",null).create()
                    if (state!="excluido" && !AccessPolicy.approved(data)) ui.button(item, if (state == "bloqueado") "Reativar $role" else "Aprovar $role") {
                        if (!busy) { dialog.dismiss();confirm(user.uid, name, role, "aprovado") }
                    }
                    if (state !in setOf("bloqueado","excluido")) ui.button(item, "Bloquear acesso", true) {
                        if (!busy) { dialog.dismiss();confirm(user.uid, name, AccessPolicy.role(data).ifEmpty { role }, "bloqueado") }
                    }
                    if (access.primary && state!="excluido") ui.button(item, "Desativar acesso", true) {
                        if (!busy) { dialog.dismiss();requestRemoval(user.uid, name) }
                    }
                    if (access.primary) ui.button(item, "Ajustar frente e turno", true) {
                        if (!busy) { dialog.dismiss();adjustTeam(user.uid, name) }
                    }
                    if(access.primary) {
                        ui.button(item,"Corrigir matrícula",true) { if(!busy) { dialog.dismiss();accountAction(user.uid,name,data["matricula"]?.toString().orEmpty(),false) } }
                        ui.button(item,"Excluir definitivamente e liberar matrícula",true) { if(!busy) { dialog.dismiss();accountAction(user.uid,name,data["matricula"]?.toString().orEmpty(),true) } }
                    }
                    dialog.show()
    }
    private fun accountAction(uid:String,name:String,matricula:String,delete:Boolean) {
        val user=auth.currentUser ?: return
        if(!TeamPolicy.primary(user.uid)||TeamPolicy.primary(uid)||uid==user.uid) return
        val body=LinearLayout(this).apply { orientation=LinearLayout.VERTICAL; setPadding(ui.dp(18),ui.dp(4),ui.dp(18),ui.dp(12)) }
        val corrected=if(!delete) ui.field(body,"Matrícula correta").apply { setText(matricula); inputType=android.text.InputType.TYPE_CLASS_NUMBER } else null
        val password=ui.field(body,"Confirme sua senha de administrador",true)
        val dialog=AlertDialog.Builder(this).setTitle(if(delete) "Excluir a conta de $name?" else "Corrigir matrícula de $name")
            .setMessage(if(delete) "A credencial e o cadastro serão removidos, liberando a matrícula. Registros de trabalho e histórico serão preservados."
                else "A correção atualizará também o Firebase Authentication. A senha e os registros da pessoa serão preservados.")
            .setView(body).setNegativeButton("Cancelar",null).setPositiveButton(if(delete) "Excluir definitivamente" else "Corrigir",null).create()
        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                if(busy) return@setOnClickListener
                val value=corrected?.text?.toString()?.trim().orEmpty()
                if(!delete&&!value.matches(Regex("[0-9]{1,20}"))) { corrected?.error="Informe a matrícula numérica"; return@setOnClickListener }
                if(password.text.isBlank()) { password.error="Confirme sua senha"; return@setOnClickListener }
                val email=user.email ?: return@setOnClickListener
                busy=true
                user.reauthenticate(com.google.firebase.auth.EmailAuthProvider.getCredential(email,password.text.toString())).addOnCompleteListener { result ->
                    if(!result.isSuccessful) { busy=false; notice("Não foi possível confirmar sua senha."); return@addOnCompleteListener }
                    val data=org.json.JSONObject().put("uid",uid)
                    if(!delete) data.put("matricula",value)
                    AgroService.async(this,if(delete) "/accounts/delete" else "/accounts/correct",data,{ response ->
                        busy=false; dialog.dismiss(); load(); notice(response.optString("message","Operação concluída"))
                    },{error->busy=false;notice(error)})
                }
            }
        }
        dialog.show()
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
    private fun requestRemoval(uid: String, name: String) {
        val actor = auth.currentUser?.uid ?: return
        if (!TeamPolicy.primary(actor) || uid == actor || TeamPolicy.primary(uid)) return
        busy = true
        db.document("configuracao/exclusaoUsuarios").get(Source.SERVER).addOnCompleteListener {
            busy = false
            if (isDestroyed || isFinishing) return@addOnCompleteListener
            if (!it.isSuccessful) {
                notice("Antes de excluir usuários, publique o ajuste de permissões desta versão. Ele impede que um supervisor reative um usuário excluído por você.")
                return@addOnCompleteListener
            }
            AlertDialog.Builder(this).setTitle("Excluir acesso de $name?")
                .setMessage("A pessoa sairá da lista ativa e perderá o acesso ao aplicativo. A matrícula continuará reservada. Registros, pendências e fotos serão preservados.")
                .setNegativeButton("Cancelar", null).setPositiveButton("Excluir acesso") { _, _ ->
                    if (busy) return@setPositiveButton
                    busy = true
                    val target = db.collection("usuarios").document(uid)
                    db.runTransaction { tx ->
                        check(auth.currentUser?.uid == actor && TeamPolicy.primary(actor) && !TeamPolicy.primary(uid))
                        val data = tx.get(target).data ?: error("Cadastro não encontrado")
                        val role = AccessPolicy.role(data).takeIf { it in TeamPolicy.leafRoles + "supervisor" } ?: "operador"
                        tx.update(target, mapOf("status" to "excluido", "papel" to role, "perfil" to role,
                            "excluidoPor" to actor, "excluidoEm" to com.google.firebase.firestore.FieldValue.serverTimestamp()))
                        null
                    }.addOnCompleteListener { result ->
                        busy = false
                        if (result.isSuccessful) load() else notice("Não foi possível excluir o acesso. Confira a conexão e tente novamente.")
                    }
                }.show()
        }
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
