package com.callguardian.data

import android.content.Context
import com.callguardian.engine.PlatformRiskAssessment
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit

data class CallEvent(
    val maskedNumber: String,
    val action: Int,
    val reason: String,
    val score: Float,
    val timestampMillis: Long
)

object CallEventStore {
    private const val PREFS_NAME = "callguardian_masked_events"
    private const val KEY_EVENTS = "events"
    private const val MAX_EVENTS = 5
    private const val RETENTION_DAYS = 30L

    fun record(context: Context, rawPhoneNumber: String, assessment: PlatformRiskAssessment) {
        val event = JSONObject()
            .put("maskedNumber", maskPhoneNumber(rawPhoneNumber))
            .put("action", assessment.action)
            .put("reason", assessment.primaryReason.ifBlank { "NONE" })
            .put("score", assessment.score.toDouble())
            .put("timestampMillis", System.currentTimeMillis())

        val events = prune(JSONArray(prefs(context).getString(KEY_EVENTS, "[]") ?: "[]"))
        val updated = JSONArray()
        updated.put(event)

        val keep = minOf(events.length(), MAX_EVENTS - 1)
        for (index in 0 until keep) {
            updated.put(events.getJSONObject(index))
        }

        prefs(context).edit().putString(KEY_EVENTS, updated.toString()).apply()
    }

    fun loadRecent(context: Context, limit: Int = MAX_EVENTS): List<CallEvent> {
        val events = prune(JSONArray(prefs(context).getString(KEY_EVENTS, "[]") ?: "[]"))
        prefs(context).edit().putString(KEY_EVENTS, events.toString()).apply()
        val out = mutableListOf<CallEvent>()
        val count = minOf(events.length(), limit, MAX_EVENTS)
        for (index in 0 until count) {
            val obj = events.optJSONObject(index) ?: continue
            out.add(
                CallEvent(
                    maskedNumber = obj.optString("maskedNumber", "******"),
                    action = obj.optInt("action", 0),
                    reason = obj.optString("reason", "NONE"),
                    score = obj.optDouble("score", 0.0).toFloat(),
                    timestampMillis = obj.optLong("timestampMillis", 0L)
                )
            )
        }
        return out
    }

    fun clear(context: Context) {
        prefs(context).edit().clear().apply()
    }

    fun formatTime(timestampMillis: Long): String {
        if (timestampMillis <= 0L) {
            return "-"
        }
        return SimpleDateFormat("dd/MM HH:mm", Locale.getDefault()).format(Date(timestampMillis))
    }

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private fun prune(events: JSONArray): JSONArray {
        val cutoff = System.currentTimeMillis() - TimeUnit.DAYS.toMillis(RETENTION_DAYS)
        val kept = JSONArray()
        for (index in 0 until events.length()) {
            val event = events.optJSONObject(index) ?: continue
            if (event.optLong("timestampMillis", 0L) >= cutoff) {
                kept.put(event)
            }
        }
        return kept
    }

    private fun maskPhoneNumber(number: String): String {
        val cleaned = number.trim()
        if (cleaned.length <= 6) {
            return "******"
        }
        return cleaned.take(6) + "******"
    }
}
