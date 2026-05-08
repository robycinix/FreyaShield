package com.callguardian.data

import android.content.Context
import com.callguardian.engine.PlatformRiskAssessment
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit

data class CallStatsSnapshot(
    val blockedCount: Int = 0,
    val silencedCount: Int = 0,
    val topReason: String = "Nessun evento"
)

object CallStatsStore {
    private const val PREFS_NAME = "callguardian_aggregate_stats"
    private const val KEY_DAYS = "days"
    private const val ACTION_SILENCE = 2
    private const val ACTION_BLOCK = 3
    private val dateFormat = SimpleDateFormat("yyyyMMdd", Locale.US)

    fun recordAssessment(context: Context, assessment: PlatformRiskAssessment) {
        if (assessment.action != ACTION_SILENCE && assessment.action != ACTION_BLOCK) {
            return
        }

        val prefs = prefs(context)
        val root = JSONObject(prefs.getString(KEY_DAYS, "{}") ?: "{}")
        prune(root)

        val todayKey = dayKey(System.currentTimeMillis())
        val day = root.optJSONObject(todayKey) ?: JSONObject()
        if (assessment.action == ACTION_BLOCK) {
            day.put("blocked", day.optInt("blocked") + 1)
        } else {
            day.put("silenced", day.optInt("silenced") + 1)
        }

        val reasons = day.optJSONObject("reasons") ?: JSONObject()
        val reason = assessment.primaryReason.ifBlank { "UNKNOWN" }
        reasons.put(reason, reasons.optInt(reason) + 1)
        day.put("reasons", reasons)
        root.put(todayKey, day)

        prefs.edit().putString(KEY_DAYS, root.toString()).apply()
    }

    fun loadLastSevenDays(context: Context): CallStatsSnapshot {
        val root = JSONObject(prefs(context).getString(KEY_DAYS, "{}") ?: "{}")
        prune(root)

        var blocked = 0
        var silenced = 0
        val reasons = mutableMapOf<String, Int>()

        root.keys().forEach { key ->
            val day = root.optJSONObject(key) ?: return@forEach
            blocked += day.optInt("blocked")
            silenced += day.optInt("silenced")

            val dayReasons = day.optJSONObject("reasons") ?: return@forEach
            dayReasons.keys().forEach { reason ->
                reasons[reason] = (reasons[reason] ?: 0) + dayReasons.optInt(reason)
            }
        }

        val topReason = reasons.maxByOrNull { it.value }?.key ?: "Nessun evento"
        return CallStatsSnapshot(blocked, silenced, topReason)
    }

    fun clear(context: Context) {
        prefs(context).edit().clear().apply()
    }

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private fun prune(root: JSONObject) {
        val cutoff = System.currentTimeMillis() - TimeUnit.DAYS.toMillis(7)
        val keysToRemove = buildList {
            root.keys().forEach { key ->
                val time = runCatching { dateFormat.parse(key)?.time ?: 0L }.getOrDefault(0L)
                if (time < cutoff) {
                    add(key)
                }
            }
        }
        keysToRemove.forEach(root::remove)
    }

    private fun dayKey(timeMillis: Long): String = dateFormat.format(Date(timeMillis))
}
