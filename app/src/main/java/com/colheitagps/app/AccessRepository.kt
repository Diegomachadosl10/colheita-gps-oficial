package com.colheitagps.app

import android.app.Activity
import android.content.Intent
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Source

internal object AccessRepository {
    fun load(activity: Activity, success: (Map<String, Any>?) -> Unit, failure: (Exception) -> Unit) {
        val user = FirebaseAuth.getInstance().currentUser
            ?: return failure(IllegalStateException("Sessão encerrada. Entre novamente."))
        FirebaseFirestore.getInstance().collection("usuarios").document(user.uid)
            .get(Source.SERVER)
            .addOnSuccessListener {
                if (!activity.isDestroyed && !activity.isFinishing && FirebaseAuth.getInstance().currentUser?.uid == user.uid) success(it.data)
            }.addOnFailureListener { if (!activity.isDestroyed && !activity.isFinishing) failure(it) }
    }

    fun logout(activity: Activity) {
        FirebaseAuth.getInstance().signOut()
        activity.startActivity(Intent(activity, LoginActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        })
        activity.finish()
    }
}
