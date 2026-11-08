
package com.colheitagps.app

import android.app.Activity
import android.app.AlertDialog
import android.os.Bundle
import android.view.Gravity
import android.widget.*
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore

class AdminActivity : Activity() {

    private val auth = FirebaseAuth.getInstance()
    private val db = FirebaseFirestore.getInstance()
    private lateinit var painel: LinearLayout

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        painel = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(28, 36, 28, 36)
        }

        setContentView(ScrollView(this).apply {
            addView(painel)
        })

        verificarAdministrador()
    }

    private fun aviso(texto: String) {
        AlertDialog.Builder(this)
            .setMessage(texto)
            .setPositiveButton("OK", null)
            .show()
    }

    private fun titulo(texto: String) {
        painel.addView(TextView(this).apply {
            text = texto
            textSize = 21f
            gravity = Gravity.CENTER
            setPadding(0, 14, 0, 18)
        })
    }

    private fun verificarAdministrador() {
        val uid = auth.currentUser?.uid ?: run {
            finish()
            return
        }

        db.collection("usuarios").document(uid).get()
            .addOnSuccessListener { doc ->
                if (doc.getString("papel") == "administrador" &&
                    doc.getString("status") == "aprovado") {
                    carregarUsuarios()
                } else {
                    aviso("Acesso exclusivo do administrador.")
                    finish()
                }
            }
            .addOnFailureListener {
                aviso("Não foi possível verificar sua autorização.")
                finish()
            }
    }

    private fun carregarUsuarios() {
        painel.removeAllViews()
        titulo("DM AGRO - Administração")

        painel.addView(Button(this).apply {
            text = "Atualizar usuários"
            setOnClickListener { carregarUsuarios() }
        })

        db.collection("usuarios").get()
            .addOnSuccessListener { resultado ->
                if (resultado.isEmpty) {
                    titulo("Nenhum usuário encontrado.")
                }

                for (doc in resultado.documents) {
                    val uid = doc.id
                    val nome = doc.getString("nome") ?: "Sem nome"
                    val email = doc.getString("email") ?: ""
                    val status = doc.getString("status") ?: "pendente"
                    val papel = doc.getString("papel") ?: "funcionario"

                    val texto = TextView(this).apply {
                        text = "$nome\n$email\nPerfil: $papel | Status: $status"
                        textSize = 16f
                        setPadding(8, 24, 8, 12)
                    }
                    painel.addView(texto)

                    if (uid == auth.currentUser?.uid) {
                        painel.addView(TextView(this).apply {
                            text = "Sua conta - Administrador principal"
                        })
                        continue
                    }

                    painel.addView(Button(this).apply {
                        text = "Aprovar como funcionário"
                        setOnClickListener {
                            alterar(uid, "funcionario", "aprovado")
                        }
                    })

                    painel.addView(Button(this).apply {
                        text = "Aprovar como supervisor"
                        setOnClickListener {
                            alterar(uid, "supervisor", "aprovado")
                        }
                    })

                    painel.addView(Button(this).apply {
                        text = "Bloquear acesso"
                        setOnClickListener {
                            AlertDialog.Builder(this@AdminActivity)
                                .setTitle("Bloquear usuário?")
                                .setMessage("O usuário perderá a autorização de acesso.")
                                .setNegativeButton("Cancelar", null)
                                .setPositiveButton("Bloquear") { _, _ ->
                                    alterar(uid, papel, "bloqueado")
                                }
                                .show()
                        }
                    })
                }
            }
            .addOnFailureListener {
                aviso("Erro ao carregar usuários. Confira as regras do Firebase.")
            }
    }

    private fun alterar(uid: String, papel: String, status: String) {
        db.collection("usuarios").document(uid)
            .update(
                mapOf(
                    "papel" to papel,
                    "status" to status
                )
            )
            .addOnSuccessListener {
                Toast.makeText(this, "Usuário atualizado!", Toast.LENGTH_SHORT).show()
                carregarUsuarios()
            }
            .addOnFailureListener {
                aviso("Não foi possível atualizar o usuário.")
            }
    }
}
