package com.colheitagps.app

import com.google.android.gms.tasks.Task

/** Retain only the identity and SDK task, never the supplied password. One sign-in at a time. */
internal class AccessAttempt<T>(val identity:String,val task:Task<T>,val started:Long) {
    fun remaining(now:Long)=(25_000L-(now-started)).coerceAtLeast(0L)
}
internal class AccessAttempts<T> {
    var current:AccessAttempt<T>?=null;private set
    @Synchronized fun start(identity:String,now:Long,request:()->Task<T>):AccessAttempt<T> {
        current?.takeIf {!it.task.isComplete}?.let {return it}
        return AccessAttempt(identity,request(),now).also {current=it}
    }
    @Synchronized fun consume(attempt:AccessAttempt<T>) {if(current===attempt&&attempt.task.isComplete) current=null}
}
internal object SignInAttempts {
    val requests=AccessAttempts<com.google.firebase.auth.AuthResult>()
}
