package com.colheitagps.app

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.os.SystemClock
import java.lang.ref.WeakReference

/** Quiet background lookup, shared across entry screens. No session or operational data is changed. */
internal object StartupUpdates {
    private var controller:UpdateController?=null
    private var foreground=WeakReference<Activity>(null)
    private var lastCheck=-300_000L
    private var promptedVersion=0L
    fun resume(activity:Activity) {
        foreground=WeakReference(activity)
        if(controller==null) controller=UpdateController(activity.applicationContext).also { c-> c.listener={ state->if(state.stage=="available") offer() } }
        val c=controller!!
        if(c.state.stage=="available") offer()
        if(SystemClock.elapsedRealtime()-lastCheck>=300_000 && c.state.stage !in setOf("checking","downloading")) {
            lastCheck=SystemClock.elapsedRealtime();c.check()
        }
    }
    fun focused(activity:Activity) { foreground=WeakReference(activity);if(controller?.state?.stage=="available") offer() }
    private fun offer() {
        val activity=foreground.get()?:return
        activity.window.decorView.postDelayed({
            if(foreground.get()!==activity||activity.isFinishing||activity.isDestroyed||!activity.hasWindowFocus()) return@postDelayed
            val info=controller?.state?.takeIf { it.stage=="available" }?.info?:return@postDelayed
            if(info.versionCode<=promptedVersion) return@postDelayed
            promptedVersion=info.versionCode
            AlertDialog.Builder(activity).setTitle("Atualização disponível")
                .setMessage("A versão ${info.versionName} do DM AGRO já está disponível. Deseja atualizar agora? Seus dados serão mantidos.")
                .setNegativeButton("Agora não",null).setPositiveButton("Atualizar") { _,_->activity.startActivity(Intent(activity,UpdateActivity::class.java)) }.show()
        },350)
    }
}
