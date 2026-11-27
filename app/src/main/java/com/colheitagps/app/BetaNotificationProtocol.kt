package com.colheitagps.app

import androidx.work.Data
import org.json.JSONObject

/** Wire contract shared with the notification-only Beta Worker. Production has no such routes. */
internal object BetaNotificationProtocol {
    const val VERSION = 1
    const val PREFIX = "/beta/v1"
    private val paths = setOf("/devices/register", "/devices/unregister", "/devices/test", "/events/notify")
    fun supports(path: String) = path in paths
    fun endpoint(value: String): Boolean = AgroService.validEndpoint(value) &&
        value.matches(Regex("https://(?:[a-z0-9]+-)*beta(?:-[a-z0-9]+)*\\.[a-z0-9-]+\\.workers\\.dev/?"))
    fun request(body: JSONObject) = JSONObject(body.toString()).put("environment", BetaWorkspace.ID).put("protocol", VERSION)
    fun health(value: JSONObject, project: String): Boolean = envelope(value) && value.optString("workspace") == BetaWorkspace.ROOT &&
        value.optString("project") == project && value.opt("configured") == true && value.opt("notificationsOnly") == true &&
        value.optString("serviceVersion").startsWith("beta-")
    fun envelope(value: JSONObject) = value.optString("environment") == BetaWorkspace.ID &&
        value.opt("protocol") is Number && (value.opt("protocol") as Number).toDouble() == VERSION.toDouble()
    fun message(value: JSONObject): Boolean {
        if (value.opt("environment") != BetaWorkspace.ID || value.opt("workspace") != BetaWorkspace.ROOT || value.opt("protocol") != VERSION.toString()) return false
        val uid = value.opt("targetUid") as? String ?: return false
        val event = value.opt("eventId") as? String ?: return false
        val team = value.opt("team") as? String ?: return false
        return uid.matches(Regex("[A-Za-z0-9_-]{1,128}")) &&
            event.matches(Regex("[a-f0-9]{64}|user_[A-Za-z0-9_-]{1,128}|test_[a-f0-9]{8}(?:-[a-f0-9]{4}){3}-[a-f0-9]{12}")) &&
            team.substringBeforeLast('_') in TeamPolicy.fronts && team.substringAfterLast('_') in TeamPolicy.shifts
    }
    fun job(data: Data) = data.getString("environment") == BetaWorkspace.ID && data.getInt("protocol", 0) == VERSION &&
        data.getString("action") in setOf("event", "register", "display")
}
