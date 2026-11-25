package com.colheitagps.app

import android.app.Activity
import android.content.Intent
import com.google.firebase.FirebaseApp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.android.gms.tasks.Task
import com.google.android.gms.tasks.Tasks
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

internal object AccessRepository {
    private val entry by lazy {
        val auth = FirebaseAuth.getInstance()
        EntrySession({ auth.currentUser?.uid }).also { session ->
            auth.addAuthStateListener { session.authChanged(it.currentUser?.uid) }
        }
    }
    fun forgetEntry() = entry.clear()
    fun observeProfile(uid: String, data: Map<String, Any>?) = entry.observe(uid, data)
    fun recentEntry(): TeamAccess? = FirebaseAuth.getInstance().currentUser?.uid?.let(entry::recent)
    private fun rejected(uid: String, error: Exception) {
        if (AccessFailures.code(error) in setOf("PERMISSION_DENIED", "UNAUTHENTICATED", "SESSION_CHANGED", "PROFILE_INVALID")) entry.reject(uid)
    }
    private val reads=Executors.newCachedThreadPool {work->Thread(work,"dm-agro-access").apply {isDaemon=true}}
    private fun document(user:FirebaseUser,readiness:Boolean=false):Task<Map<String,Any>?> {
        val request=Tasks.call(reads,java.util.concurrent.Callable<Map<String,Any>?> {
            val project=FirebaseApp.getInstance().options.projectId?:throw AccessFailure("INVALID_RESPONSE")
            fun read(forceRefresh:Boolean):Map<String,Any>? {
                if(FirebaseAuth.getInstance().currentUser?.uid!=user.uid) throw AccessFailure("SESSION_CHANGED")
                val token=Tasks.await(user.getIdToken(forceRefresh),6,TimeUnit.SECONDS).token?:throw AccessFailure("UNAUTHENTICATED")
                if(FirebaseAuth.getInstance().currentUser?.uid!=user.uid) throw AccessFailure("SESSION_CHANGED")
                val data=FirestoreAccessHttp.get(project,user.uid,token,readiness)
                if(!readiness&&EnrollmentPolicy.pendingSupervisor(user.uid,user.email,data)) {
                    if(FirebaseAuth.getInstance().currentUser?.uid!=user.uid) throw AccessFailure("SESSION_CHANGED")
                    return try {FirestoreAccessHttp.activateSupervisor(project,user.uid,token)} catch(error:AccessFailure) {
                        if(error.code=="PERMISSION_DENIED") throw AccessFailure("SUPERVISOR_ENROLLMENT") else throw error
                    }
                }
                return data
            }
            try {read(false)} catch(error:AccessFailure) {if(error.code=="UNAUTHENTICATED") read(true) else throw error}
        })
        // This deadline also bounds DNS, which can outlast a socket connect timeout.
        return Tasks.withTimeout(request,20,TimeUnit.SECONDS)
    }
    fun load(activity: Activity, success: (Map<String, Any>?) -> Unit, failure: (Exception) -> Unit) {
        val user=FirebaseAuth.getInstance().currentUser?:return failure(AccessFailure("UNAUTHENTICATED"))
        document(user).addOnSuccessListener {data->
            entry.observe(user.uid, data)
            if(!activity.isDestroyed&&!activity.isFinishing&&FirebaseAuth.getInstance().currentUser?.uid==user.uid) success(data)
        }.addOnFailureListener {error->
            rejected(user.uid, error)
            if(!activity.isDestroyed&&!activity.isFinishing&&FirebaseAuth.getInstance().currentUser?.uid==user.uid) failure(error)
        }
    }
    fun loadEntry(activity: Activity, success: (Map<String, Any>?) -> Unit, failure: (Exception) -> Unit) {
        val user = FirebaseAuth.getInstance().currentUser ?: return failure(AccessFailure("UNAUTHENTICATED"))
        entry.request(user.uid) {
            val request = document(user).continueWithTask { read ->
                val data = read.result
                if (!TeamPolicy.allowed(user.uid, data)) Tasks.forResult(data)
                else document(user, true).continueWith { ready -> ready.result; data }
            }
            Tasks.withTimeout(request, 20, TimeUnit.SECONDS)
        }.addOnSuccessListener { data ->
            if (!activity.isDestroyed && !activity.isFinishing && FirebaseAuth.getInstance().currentUser?.uid == user.uid) success(data)
        }.addOnFailureListener { error ->
            if (!activity.isDestroyed && !activity.isFinishing && FirebaseAuth.getInstance().currentUser?.uid == user.uid) failure(error)
        }
    }
    fun loadEntryTeamAccess(activity: Activity, success: (TeamAccess) -> Unit, failure: (Exception) -> Unit) {
        val user = FirebaseAuth.getInstance().currentUser ?: return failure(AccessFailure("UNAUTHENTICATED"))
        loadEntry(activity, { data ->
            if (TeamPolicy.allowed(user.uid, data)) success(TeamAccess(user.uid, data.orEmpty()))
            else failure(AccessFailure("PROFILE_INVALID"))
        }, failure)
    }
    fun loadTeamAccess(activity: Activity, success: (TeamAccess) -> Unit, failure: (Exception) -> Unit) {
        val user=FirebaseAuth.getInstance().currentUser?:return failure(AccessFailure("UNAUTHENTICATED"))
        load(activity,{data->
            if(!TeamPolicy.allowed(user.uid,data)) failure(AccessFailure("PROFILE_INVALID"))
            else document(user,true).addOnSuccessListener {
                if(!activity.isDestroyed&&!activity.isFinishing&&FirebaseAuth.getInstance().currentUser?.uid==user.uid) success(TeamAccess(user.uid,data.orEmpty()))
            }.addOnFailureListener {error->
                rejected(user.uid, error)
                if(!activity.isDestroyed&&!activity.isFinishing&&FirebaseAuth.getInstance().currentUser?.uid==user.uid) failure(error)
            }
        },failure)
    }
    fun logout(activity: Activity) {
        forgetEntry()
        AgroNotifications.logout(activity)
        FirebaseAuth.getInstance().signOut()
        activity.startActivity(Intent(activity, LoginActivity::class.java).apply {flags=Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK})
        activity.finish()
    }
}
