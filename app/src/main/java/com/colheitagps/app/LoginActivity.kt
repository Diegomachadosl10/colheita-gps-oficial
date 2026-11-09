package com.colheitagps.app

import android.app.*
import android.content.*
import android.graphics.Color
import android.os.Bundle
import android.text.InputType
import android.view.*
import android.widget.*
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore

class LoginActivity : Activity() {
    private val auth by lazy { FirebaseAuth.getInstance() }
    private val db by lazy { FirebaseFirestore.getInstance() }
    private lateinit var root: LinearLayout
    private val frentes = arrayOf("Santo Inácio","Santa Inês","Itororó","Cafeara","Lupionópolis","Colorado","Sandovalina","Jardim Olinda","Itaguajé")
    private val ids = arrayOf("santo_inacio","santa_ines","itororo","cafeara","lupionopolis","colorado","sandovalina","jardim_olinda","itaguaje")
    private fun interno(m: String) = "matricula.${m}@acesso.dm-agro.app"
    private fun aviso(s: String) = AlertDialog.Builder(this).setMessage(s).setPositiveButton("OK",null).show()
    private fun campo(h: String, password: Boolean = false) = EditText(this).apply {
        hint=h; setSingleLine(true); textSize=16f
        inputType=if(password) InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD else InputType.TYPE_CLASS_TEXT
    }
    private fun titulo(s: String) { root.addView(TextView(this).apply { text=s; textSize=23f; setTextColor(Color.rgb(16,70,48)); setPadding(0,12,0,20) }) }
    private fun botao(s:String, click:()->Unit) { root.addView(Button(this).apply { text=s; isAllCaps=false; setOnClickListener { click() } }) }
    private fun add(v:View) { root.addView(v,LinearLayout.LayoutParams(-1,-2)) }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        root=LinearLayout(this).apply { orientation=LinearLayout.VERTICAL; setPadding(28,32,28,28) }
        setContentView(ScrollView(this).apply { addView(root) })
        if(auth.currentUser!=null) verificarAcesso() else entrar()
    }
    private fun entrar() {
        root.removeAllViews(); titulo("DM AGRO — Entrar")
        val matricula=campo("Matrícula ou e-mail do administrador")
        val senha=campo("Senha",true)
        add(matricula); add(senha)
        botao("Entrar") {
            val id=matricula.text.toString().trim()
            val pass=senha.text.toString()
            if(id.isBlank()||pass.isBlank()) { aviso("Informe matrícula e senha."); return@botao }
            val email=if(id.contains("@")) id else if(id.matches(Regex("[0-9]{1,20}"))) interno(id) else {
                aviso("Informe uma matrícula numérica ou o e-mail do administrador."); return@botao
            }
            auth.signInWithEmailAndPassword(email,pass).addOnSuccessListener { verificarAcesso() }
                .addOnFailureListener { aviso("Não foi possível entrar. Confira matrícula e senha.") }
        }
        botao("Criar conta") { cadastro() }
        botao("Esqueci minha senha (administrador)") {
            val e=matricula.text.toString().trim()
            if(!e.contains("@")) aviso("Para contas por matrícula, solicite ao administrador a recuperação de acesso.")
            else auth.sendPasswordResetEmail(e).addOnSuccessListener { aviso("Confira seu e-mail.") }
                .addOnFailureListener { aviso("Não foi possível enviar a recuperação.") }
        }
    }
    private fun cadastro() {
        root.removeAllViews(); titulo("Solicitar acesso — DM AGRO")
        val matricula=campo("Número de matrícula")
        matricula.inputType=InputType.TYPE_CLASS_NUMBER
        val nome=campo("Nome completo")
        val frente=Spinner(this).apply { adapter=ArrayAdapter(this@LoginActivity,android.R.layout.simple_spinner_dropdown_item,frentes) }
        val turno=Spinner(this).apply { adapter=ArrayAdapter(this@LoginActivity,android.R.layout.simple_spinner_dropdown_item,arrayOf("A","B","C")); setSelection(2) }
        val perfil=Spinner(this).apply { adapter=ArrayAdapter(this@LoginActivity,android.R.layout.simple_spinner_dropdown_item,arrayOf("Operador","Supervisor")) }
        val funcao=campo("Função (ex.: operador de colhedora)")
        val senha=campo("Criar senha (mínimo 8 caracteres)",true)
        val confirmar=campo("Confirmar senha",true)
        add(matricula);add(nome)
        add(TextView(this).apply { text="Frente" });add(frente)
        add(TextView(this).apply { text="Turno" });add(turno)
        add(TextView(this).apply { text="Tipo de acesso solicitado" });add(perfil)
        add(funcao);add(senha);add(confirmar)
        botao("Solicitar cadastro") {
            val m=matricula.text.toString().trim()
            val n=nome.text.toString().trim()
            val s=senha.text.toString()
            if(!m.matches(Regex("[0-9]{1,20}"))||n.length<3||s.length<8||s!=confirmar.text.toString()) {
                aviso("Confira matrícula numérica, nome e senhas iguais com pelo menos 8 caracteres.");return@botao
            }
            val solicitado=if(perfil.selectedItemPosition==1) "supervisor" else "operador"
            // Authentication uses an internal address; no personal email is collected.
            auth.createUserWithEmailAndPassword(interno(m),s).addOnSuccessListener {
                val uid=auth.currentUser?.uid ?: return@addOnSuccessListener
                val dados=hashMapOf<String,Any>(
                    "nome" to n, "matricula" to m, "frenteId" to ids[frente.selectedItemPosition],
                    "turno" to turno.selectedItem.toString(), "funcao" to funcao.text.toString().trim(),
                    "papel" to "operador", "perfil" to "operador", "solicitacaoPerfil" to solicitado,
                    "status" to "pendente", "criadoEm" to FieldValue.serverTimestamp()
                )
                db.collection("usuarios").document(uid).set(dados).addOnSuccessListener { verificarAcesso() }
                    .addOnFailureListener {
                        auth.signOut(); entrar()
                        aviso("Conta criada, mas não foi possível salvar a solicitação. Procure o administrador.")
                    }
            }.addOnFailureListener { aviso("Não foi possível cadastrar. Matrícula já utilizada ou falha de conexão.") }
        }
        botao("Voltar para entrar") { entrar() }
    }
    private fun verificarAcesso() {
        val user=auth.currentUser ?: run { entrar();return }
        root.removeAllViews();titulo("Verificando autorização...")
        db.collection("usuarios").document(user.uid).get().addOnSuccessListener { d ->
            when(d.getString("status")) {
                "aprovado" -> if(d.getString("papel")=="administrador") painelAdmin() else {
                    startActivity(Intent(this,MainActivity::class.java));finish()
                }
                "bloqueado" -> pendente("Seu acesso foi bloqueado.")
                else -> pendente("Aguardando aprovação. Operadores: supervisor da frente. Supervisores: administrador.")
            }
        }.addOnFailureListener { pendente("Não foi possível consultar sua autorização. Verifique a conexão.") }
    }
    private fun painelAdmin() {
        root.removeAllViews();titulo("DM AGRO — Administrador")
        botao("Abrir aplicativo") { startActivity(Intent(this,MainActivity::class.java));finish() }
        botao("Gerenciar usuários") { startActivity(Intent(this,AdminActivity::class.java)) }
        botao("Sair") { auth.signOut();entrar() }
    }
    private fun pendente(s:String) {
        root.removeAllViews();titulo("Status da conta")
        add(TextView(this).apply { text=s })
        botao("Verificar novamente") { verificarAcesso() }
        botao("Sair") { auth.signOut();entrar() }
    }
}
