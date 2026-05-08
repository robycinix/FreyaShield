package com.callguardian.data

import android.content.Context
import org.json.JSONObject

data class ScreeningDiagnostics(
    val invocationCount: Int = 0,
    val lastTimestampMillis: Long = 0L,
    val lastMaskedNumber: String = "Nessuna"
)

object ScreeningDiagnosticsStore {
    private const val PREFS_NAME = "callguardian_screening_diagnostics"
    private const val KEY_STATE = "state"

    fun recordInvocation(context: Context, rawPhoneNumber: String) {
        val current = load(context)
        val updated = JSONObject()
            .put("invocationCount", current.invocationCount + 1)
            .put("lastTimestampMillis", System.currentTimeMillis())
            .put("lastMaskedNumber", maskPhoneNumber(rawPhoneNumber))

        prefs(context).edit().putString(KEY_STATE, updated.toString()).apply()
    }

    fun load(context: Context): ScreeningDiagnostics {
        val json = prefs(context).getString(KEY_STATE, null) ?: return ScreeningDiagnostics()
        return runCatching {
            val obj = JSONObject(json)
            ScreeningDiagnostics(
                invocationCount = obj.optInt("invocationCount"),
                lastTimestampMillis = obj.optLong("lastTimestampMillis"),
                lastMaskedNumber = obj.optString("lastMaskedNumber", "Nessuna")
            )
        }.getOrDefault(ScreeningDiagnostics())
    }

    fun clear(context: Context) {
        prefs(context).edit().clear().apply()
    }

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private fun maskPhoneNumber(number: String): String {
        val cleaned = number.trim()
        if (cleaned.length <= 6) {
            return "******"
        }
        return cleaned.take(6) + "******"
    }
}
