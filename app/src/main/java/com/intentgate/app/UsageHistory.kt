package com.intentgate.app

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/** Local-only usage history. One row represents one foreground visit to a protected app. */
object UsageHistory {
    private const val PREFS = "intentgate_history"
    private const val KEY = "visits"

    @Synchronized
    fun add(context: Context, packageName: String, appName: String, purpose: String, start: Long, end: Long) {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val rows = JSONArray(prefs.getString(KEY, "[]") ?: "[]")
        rows.put(JSONObject().apply {
            put("package", packageName)
            put("app", appName)
            put("purpose", purpose)
            put("start", start)
            put("end", end)
            put("duration", (end - start).coerceAtLeast(0L))
        })
        // Keep approximately 90 days, capped to prevent unbounded preference growth.
        val cutoff = System.currentTimeMillis() - 90L * 24 * 60 * 60 * 1000
        val trimmed = JSONArray()
        for (i in 0 until rows.length()) {
            val row = rows.optJSONObject(i) ?: continue
            if (row.optLong("start") >= cutoff && trimmed.length() < 5000) trimmed.put(row)
        }
        prefs.edit().putString(KEY, trimmed.toString()).apply()
    }

    fun read(context: Context): List<Visit> {
        val raw = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY, "[]") ?: "[]"
        val rows = JSONArray(raw)
        return (0 until rows.length()).mapNotNull { i ->
            val o = rows.optJSONObject(i) ?: return@mapNotNull null
            Visit(o.optString("package"), o.optString("app"), o.optString("purpose"), o.optLong("start"), o.optLong("end"), o.optLong("duration"))
        }.sortedByDescending { it.start }
    }

    data class Visit(val packageName: String, val appName: String, val purpose: String, val start: Long, val end: Long, val duration: Long)
}
