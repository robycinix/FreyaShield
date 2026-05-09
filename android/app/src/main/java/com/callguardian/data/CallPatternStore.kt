package com.callguardian.data

import android.content.Context
import com.callguardian.engine.PlatformRiskAssessment
import org.json.JSONArray
import org.json.JSONObject
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.concurrent.TimeUnit

data class CallPatternSignal(
    val seenBefore: Boolean,
    val recentCallsFromSameNumber: Int,
    val recentCallsFromSamePrefix: Int,
    val greylistedPrefix: Boolean
)

object CallPatternStore {
    private const val PREFS_NAME = "callguardian_pattern_memory"
    private const val KEY_EVENTS = "events"
    private const val KEY_GREYLIST = "greylist"
    private const val KEY_SALT = "salt"
    private const val MAX_EVENTS = 120
    private val recentNumberWindowMillis = TimeUnit.MINUTES.toMillis(10)
    private val recentPrefixWindowMillis = TimeUnit.MINUTES.toMillis(60)
    private val seenBeforeWindowMillis = TimeUnit.DAYS.toMillis(30)
    private val greylistWindowMillis = TimeUnit.DAYS.toMillis(7)

    fun inspect(context: Context, rawPhoneNumber: String): CallPatternSignal {
        val normalized = normalizePhone(rawPhoneNumber)
        if (normalized.isBlank()) {
            return CallPatternSignal(false, 0, 0, false)
        }

        val now = System.currentTimeMillis()
        val numberHash = hash(context, normalized)
        val prefixHash = hash(context, prefixKey(normalized))
        val events = pruneEvents(JSONArray(prefs(context).getString(KEY_EVENTS, "[]") ?: "[]"), now)
        val greylist = pruneGreylist(JSONArray(prefs(context).getString(KEY_GREYLIST, "[]") ?: "[]"), now)
        persistPruned(context, events, greylist)

        var seenBefore = false
        var sameNumber = 0
        var samePrefix = 0
        for (index in 0 until events.length()) {
            val event = events.optJSONObject(index) ?: continue
            val timestamp = event.optLong("timestampMillis", 0L)
            if (event.optString("numberHash") == numberHash) {
                if (now - timestamp <= seenBeforeWindowMillis) {
                    seenBefore = true
                }
                if (now - timestamp <= recentNumberWindowMillis) {
                    sameNumber += 1
                }
            }
            if (event.optString("prefixHash") == prefixHash && now - timestamp <= recentPrefixWindowMillis) {
                samePrefix += 1
            }
        }

        val greylisted = (0 until greylist.length()).any { index ->
            greylist.optJSONObject(index)?.optString("prefixHash") == prefixHash
        }
        return CallPatternSignal(seenBefore, sameNumber, samePrefix, greylisted)
    }

    fun record(context: Context, rawPhoneNumber: String, assessment: PlatformRiskAssessment, policy: PolicySettings) {
        val normalized = normalizePhone(rawPhoneNumber)
        if (normalized.isBlank()) {
            return
        }

        val now = System.currentTimeMillis()
        val numberHash = hash(context, normalized)
        val prefixHash = hash(context, prefixKey(normalized))
        val events = pruneEvents(JSONArray(prefs(context).getString(KEY_EVENTS, "[]") ?: "[]"), now)
        val updated = JSONArray()
        updated.put(
            JSONObject()
                .put("numberHash", numberHash)
                .put("prefixHash", prefixHash)
                .put("action", assessment.action)
                .put("timestampMillis", now)
        )

        val keep = minOf(events.length(), MAX_EVENTS - 1)
        for (index in 0 until keep) {
            updated.put(events.getJSONObject(index))
        }

        val greylist = pruneGreylist(JSONArray(prefs(context).getString(KEY_GREYLIST, "[]") ?: "[]"), now)
        if ((policy.autoBlockSimilarNumbers || policy.temporaryGreylist) &&
            assessment.action >= 2 &&
            countPrefixEvents(updated, prefixHash, now) >= 3
        ) {
            addGreylistEntry(greylist, prefixHash, now + greylistWindowMillis)
        }

        prefs(context).edit()
            .putString(KEY_EVENTS, updated.toString())
            .putString(KEY_GREYLIST, greylist.toString())
            .apply()
    }

    fun clear(context: Context) {
        prefs(context).edit().remove(KEY_EVENTS).remove(KEY_GREYLIST).apply()
    }

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private fun persistPruned(context: Context, events: JSONArray, greylist: JSONArray) {
        prefs(context).edit()
            .putString(KEY_EVENTS, events.toString())
            .putString(KEY_GREYLIST, greylist.toString())
            .apply()
    }

    private fun pruneEvents(events: JSONArray, now: Long): JSONArray {
        val kept = JSONArray()
        val cutoff = now - seenBeforeWindowMillis
        for (index in 0 until events.length()) {
            val event = events.optJSONObject(index) ?: continue
            if (event.optLong("timestampMillis", 0L) >= cutoff) {
                kept.put(event)
            }
        }
        return kept
    }

    private fun pruneGreylist(greylist: JSONArray, now: Long): JSONArray {
        val kept = JSONArray()
        for (index in 0 until greylist.length()) {
            val entry = greylist.optJSONObject(index) ?: continue
            if (entry.optLong("expiresAtMillis", 0L) > now) {
                kept.put(entry)
            }
        }
        return kept
    }

    private fun countPrefixEvents(events: JSONArray, prefixHash: String, now: Long): Int {
        var count = 0
        for (index in 0 until events.length()) {
            val event = events.optJSONObject(index) ?: continue
            if (event.optString("prefixHash") == prefixHash &&
                now - event.optLong("timestampMillis", 0L) <= recentPrefixWindowMillis
            ) {
                count += 1
            }
        }
        return count
    }

    private fun addGreylistEntry(greylist: JSONArray, prefixHash: String, expiresAtMillis: Long) {
        for (index in 0 until greylist.length()) {
            val entry = greylist.optJSONObject(index) ?: continue
            if (entry.optString("prefixHash") == prefixHash) {
                entry.put("expiresAtMillis", expiresAtMillis)
                return
            }
        }
        greylist.put(JSONObject().put("prefixHash", prefixHash).put("expiresAtMillis", expiresAtMillis))
    }

    private fun hash(context: Context, value: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val input = "${salt(context)}:$value".toByteArray(Charsets.UTF_8)
        return digest.digest(input).joinToString("") { "%02x".format(it) }
    }

    private fun salt(context: Context): String {
        val prefs = prefs(context)
        prefs.getString(KEY_SALT, null)?.let { return it }
        val bytes = ByteArray(16)
        SecureRandom().nextBytes(bytes)
        val generated = bytes.joinToString("") { "%02x".format(it) }
        prefs.edit().putString(KEY_SALT, generated).apply()
        return generated
    }

    private fun normalizePhone(rawPhoneNumber: String): String {
        var cleaned = rawPhoneNumber.trim()
            .replace(" ", "")
            .replace("-", "")
            .replace("(", "")
            .replace(")", "")
        if (cleaned.startsWith("00")) {
            cleaned = "+" + cleaned.drop(2)
        } else if (!cleaned.startsWith("+") && cleaned.isNotBlank()) {
            cleaned = "+39$cleaned"
        }
        return cleaned
    }

    private fun prefixKey(normalizedNumber: String): String {
        return normalizedNumber.take(minOf(7, normalizedNumber.length))
    }
}
