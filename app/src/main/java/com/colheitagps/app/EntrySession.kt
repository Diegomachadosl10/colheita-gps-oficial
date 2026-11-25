package com.colheitagps.app

import android.os.SystemClock
import com.google.android.gms.tasks.Task
import com.google.android.gms.tasks.TaskCompletionSource
import com.google.android.gms.tasks.Tasks
import java.util.concurrent.Executor

/** Short process-only reuse of a server-approved entry, never passwords, tokens or intent roles. */
internal class EntrySession(
    private val activeUid: () -> String?,
    private val now: () -> Long = { SystemClock.elapsedRealtime() }
) {
    private var uid: String? = null
    private var generation = 0
    private var approved: TeamAccess? = null
    private var checkedAt = 0L
    private var pending: Task<Map<String, Any>?>? = null

    @Synchronized fun authChanged(value: String?) {
        if (uid != value) { clear(); uid = value }
    }

    @Synchronized fun clear() {
        generation++; uid = null; approved = null; pending = null
    }

    @Synchronized fun reject(user: String) { if (uid == user) clear() }

    @Synchronized fun recent(user: String): TeamAccess? {
        authChanged(activeUid())
        if (uid != user) return null
        if (now() - checkedAt !in 0 until 300_000L) approved = null
        return approved
    }

    @Synchronized fun observe(user: String, data: Map<String, Any>?) {
        val previous = recent(user) ?: return
        if (!TeamPolicy.allowed(user, data) || TeamPolicy.team(data) != previous.team ||
            AccessPolicy.role(data) != AccessPolicy.role(previous.profile)) clear()
    }

    @Synchronized fun request(user: String, load: () -> Task<Map<String, Any>?>): Task<Map<String, Any>?> {
        authChanged(activeUid())
        if (uid != user) return Tasks.forException(AccessFailure("SESSION_CHANGED"))
        recent(user)?.let { return Tasks.forResult(it.profile) }
        pending?.takeUnless { it.isComplete }?.let { return it }
        val session = generation
        val result = TaskCompletionSource<Map<String, Any>?>()
        pending = result.task
        val source = try { load() } catch (error: Exception) { Tasks.forException(error) }
        source.addOnCompleteListener(Executor { it.run() }) { task ->
            synchronized(this) {
                if (session != generation || activeUid() != user) {
                    result.trySetException(AccessFailure("SESSION_CHANGED"))
                } else {
                    pending = null
                    if (task.isSuccessful) {
                        val data = task.result
                        approved = data?.takeIf { TeamPolicy.allowed(user, it) }?.let { TeamAccess(user, HashMap(it)) }
                        checkedAt = now()
                        result.trySetResult(data)
                    } else result.trySetException(task.exception ?: AccessFailure("CONNECTION_FAILED"))
                }
            }
        }
        return result.task
    }
}
