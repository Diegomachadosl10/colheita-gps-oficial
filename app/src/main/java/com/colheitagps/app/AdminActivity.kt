package com.colheitagps.app

import android.app.*
import android.os.Bundle
import android.view.*
import android.widget.*
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore

class AdminActivity : Activity() {
    private val auth by lazy { FirebaseAuth.getInstance() }
    private val db by lazy { FirebaseFirestore.getInstance() }
    private lateinit var root: LinearLayout
    private var meuPapel=""
    private var minhaFrente=""
    private fun aviso(s:String)=AlertDialog.Builder(this).setMessage(s).setPositiveButton("OK",null).show()
    override fun onCreate(savedInstanceState:Bundle?) {
        super.onCreate(savedInstanceState)
        root=LinearLayout(this).apply { orientation=LinearLayout.VERTICAL;setPadding(24,24,24,24) }
        setContentView(ScrollView(this).apply { addView(root) })
        val uid=auth.currentUser?.uid ?: run { finish();return }
        db.collection("usuarios").document(uid).get().addOnSuccessListener { d ->
            meuPapel=d.getString("papel") ?: ""
            minhaFrente=d.getString("frenteId") ?: ""
            if(d.getString("status")!="aprovado" || meuPapel !in listOf("administrador","supervisor")) {
                aviso("Acesso não autorizado.");finish()
            } else carregar()
        }.addOnFailureListener { aviso("Falha ao verificar permissões.");finish() }
    }
    private fun carregar() {
        root.removeAllViews()
        root.addView(TextView(this).apply { text="DM AGRO — Aprovações";textSize=23f })
        root.addView(Button(this).apply { text="Atualizar";setOnClickListener { carregar() } })
        // Supervisor may query only pending operators from own front.
        val consulta=if(meuPapel=="administrador") db.collection("usuarios")
          else db.collection("usuarios").whereEqualTo("frenteId",minhaFrente)
              .whereEqualTo("papel","operador")
              .whereEqualTo("solicitacaoPerfil","operador")
        consulta.get().addOnSuccessListener { docs ->
            for(d in docs.documents) {
                val uid=d.id
                if(uid==auth.currentUser?.uid)continue
                val papel=d.getString("papel") ?: "operador"
                val solicitado=d.getString("solicitacaoPerfil") ?: papel
                val status=d.getString("status") ?: "pendente"
                if(meuPapel=="supervisor" && (solicitado!="operador" || papel=="supervisor" || papel=="administrador"))continue
                val nome=d.getString("nome") ?: "Sem nome"
                val matricula=d.getString("matricula") ?: "Conta antiga"
                val frente=d.getString("frenteId") ?: "Sem frente"
                val turno=d.getString("turno") ?: "Não informado"
                root.addView(TextView(this).apply {
                    text="$nome\nMatrícula: $matricula\nFrente: $frente | Turno: $turno\nSolicita: $solicitado | Status: $status"
                    textSize=16f;setPadding(0,18,0,8)
                })
                if(status=="pendente") {
                    root.addView(Button(this).apply {
                        text="Aprovar ${if(solicitado=="supervisor") "supervisor" else "operador"}"
                        setOnClickListener {
                            if(solicitado=="supervisor" && meuPapel!="administrador")return@setOnClickListener
                            AlertDialog.Builder(this@AdminActivity).setTitle("Confirmar identidade")
                                .setMessage("Confira matrícula e nome com a relação oficial antes de aprovar.")
                                .setNegativeButton("Cancelar",null)
                                .setPositiveButton("Aprovar") { _,_ ->
                                    alterar(uid,if(solicitado=="supervisor") "supervisor" else "operador","aprovado")
                                }.show()
                        }
                    })
                }
                root.addView(Button(this).apply {
                    text="Bloquear acesso"
                    setOnClickListener {
                        AlertDialog.Builder(this@AdminActivity).setTitle("Bloquear usuário?")
                            .setNegativeButton("Cancelar",null)
                            .setPositiveButton("Bloquear") { _,_->alterar(uid,papel,"bloqueado") }.show()
                    }
                })
            }
        }.addOnFailureListener { aviso("Não foi possível carregar os usuários. Atualize as regras do Firestore.") }
    }
    private fun alterar(uid:String,papel:String,status:String) {
        db.collection("usuarios").document(uid).update(mapOf("papel" to papel,"perfil" to papel,"status" to status))
            .addOnSuccessListener { carregar() }
            .addOnFailureListener { aviso("Operação recusada. Confira permissões no Firestore.") }
    }
}
