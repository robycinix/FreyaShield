package com.callguardian.data

import android.content.Context
import com.callguardian.engine.CoreEngineBridge
import org.json.JSONArray
import org.json.JSONObject

data class PolicySettings(
    val warnThreshold: Float = 0.35f,
    val silenceThreshold: Float = 0.50f,
    val blockThreshold: Float = 0.65f,
    val blockFailedVerification: Boolean = true,
    val warnNeighborSpoof: Boolean = true,
    val blockHighFrequencyRobocall: Boolean = true,
    val blockFirstSeenInternational: Boolean = true,
    val allowVerifiedCalls: Boolean = true,
    val allowWhitelistedPatterns: Boolean = true,
    val autoBlockSimilarNumbers: Boolean = false,
    val temporaryGreylist: Boolean = false,
    val blockUnverifiedSuspicious: Boolean = false,
    val quietHoursFilter: Boolean = false,
    val trustedOnlyMode: Boolean = false,
    val manualFeedbackActions: Boolean = true
)

object AppPreferences {
    private const val PREFS_NAME = "callguardian_local_settings"
    private const val KEY_POLICY = "policy"
    private const val KEY_WHITELIST = "whitelist_patterns"
    private const val KEY_BLOCKLIST = "blocklist_patterns"

    fun loadPolicy(context: Context): PolicySettings {
        val json = loadSensitiveString(context, KEY_POLICY) ?: return PolicySettings()
        return runCatching {
            val obj = JSONObject(json)
            PolicySettings(
                warnThreshold = obj.optDouble("warnThreshold", 0.35).toFloat(),
                silenceThreshold = obj.optDouble("silenceThreshold", 0.50).toFloat(),
                blockThreshold = obj.optDouble("blockThreshold", 0.65).toFloat(),
                blockFailedVerification = obj.optBoolean("blockFailedVerification", true),
                warnNeighborSpoof = obj.optBoolean("warnNeighborSpoof", true),
                blockHighFrequencyRobocall = obj.optBoolean("blockHighFrequencyRobocall", true),
                blockFirstSeenInternational = obj.optBoolean("blockFirstSeenInternational", true),
                allowVerifiedCalls = obj.optBoolean("allowVerifiedCalls", true),
                allowWhitelistedPatterns = obj.optBoolean("allowWhitelistedPatterns", true),
                autoBlockSimilarNumbers = obj.optBoolean("autoBlockSimilarNumbers", false),
                temporaryGreylist = obj.optBoolean("temporaryGreylist", false),
                blockUnverifiedSuspicious = obj.optBoolean("blockUnverifiedSuspicious", false),
                quietHoursFilter = obj.optBoolean("quietHoursFilter", false),
                trustedOnlyMode = obj.optBoolean("trustedOnlyMode", false),
                manualFeedbackActions = obj.optBoolean("manualFeedbackActions", true)
            ).normalized()
        }.getOrDefault(PolicySettings())
    }

    fun savePolicy(context: Context, policy: PolicySettings) {
        saveSensitiveString(context, KEY_POLICY, policy.normalized().toJson())
    }

    fun loadWhitelist(context: Context): List<String> {
        val json = loadSensitiveString(context, KEY_WHITELIST) ?: return listOf("+39347*")
        return runCatching {
            val array = JSONArray(json)
            buildList {
                for (index in 0 until array.length()) {
                    val value = array.optString(index).trim()
                    if (value.isNotEmpty()) {
                        add(value)
                    }
                }
            }.distinct()
        }.getOrDefault(listOf("+39347*"))
    }

    fun saveWhitelist(context: Context, patterns: List<String>) {
        saveSensitiveString(context, KEY_WHITELIST, patterns.toJsonArray())
    }

    fun loadBlocklist(context: Context): List<String> {
        val json = loadSensitiveString(context, KEY_BLOCKLIST) ?: return emptyList()
        return runCatching {
            val array = JSONArray(json)
            buildList {
                for (index in 0 until array.length()) {
                    val value = array.optString(index).trim()
                    if (value.isNotEmpty()) {
                        add(value)
                    }
                }
            }.distinct()
        }.getOrDefault(emptyList())
    }

    fun saveBlocklist(context: Context, patterns: List<String>) {
        saveSensitiveString(context, KEY_BLOCKLIST, patterns.toJsonArray())
    }

    fun applyToCore(context: Context) {
        val policy = loadPolicy(context)
        val whitelist = loadWhitelist(context)
        val blocklist = loadBlocklist(context)
        CoreEngineBridge.updatePolicy(policy.toJson())
        CoreEngineBridge.setWhitelistPatterns(whitelist.toJsonArray())
        CoreEngineBridge.setBlocklistPatterns(blocklist.map { it.blockPatternOnly() }.toJsonArray())
    }

    fun isWhitelisted(context: Context, rawPhoneNumber: String): Boolean {
        return loadPolicy(context).allowWhitelistedPatterns &&
            matchesAnyPattern(normalizePhonePattern(rawPhoneNumber), loadWhitelist(context))
    }

    fun isBlocked(context: Context, rawPhoneNumber: String): Boolean {
        return matchesAnyPattern(normalizePhonePattern(rawPhoneNumber), loadBlocklist(context).map { it.blockPatternOnly() })
    }

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private fun loadSensitiveString(context: Context, key: String): String? {
        val encrypted = SecureStringStore.getString(context, key)
        if (encrypted != null) {
            return encrypted
        }

        val legacyPrefs = prefs(context)
        val legacy = legacyPrefs.getString(key, null) ?: return null
        saveSensitiveString(context, key, legacy)
        legacyPrefs.edit().remove(key).apply()
        return legacy
    }

    private fun saveSensitiveString(context: Context, key: String, value: String) {
        SecureStringStore.putString(context, key, value)
        prefs(context).edit().remove(key).apply()
    }

    private fun PolicySettings.normalized(): PolicySettings {
        val warn = warnThreshold.coerceIn(0.10f, 0.95f)
        val silence = silenceThreshold.coerceIn(warn, 0.95f)
        val block = blockThreshold.coerceIn(silence, 0.95f)
        return copy(
            warnThreshold = warn,
            silenceThreshold = silence,
            blockThreshold = block
        )
    }

    private fun PolicySettings.toJson(): String {
        return JSONObject()
            .put("warnThreshold", warnThreshold.toDouble())
            .put("silenceThreshold", silenceThreshold.toDouble())
            .put("blockThreshold", blockThreshold.toDouble())
            .put("blockFailedVerification", blockFailedVerification)
            .put("warnNeighborSpoof", warnNeighborSpoof)
            .put("blockHighFrequencyRobocall", blockHighFrequencyRobocall)
            .put("blockFirstSeenInternational", blockFirstSeenInternational)
            .put("allowVerifiedCalls", allowVerifiedCalls)
            .put("allowWhitelistedPatterns", allowWhitelistedPatterns)
            .put("autoBlockSimilarNumbers", autoBlockSimilarNumbers)
            .put("temporaryGreylist", temporaryGreylist)
            .put("blockUnverifiedSuspicious", blockUnverifiedSuspicious)
            .put("quietHoursFilter", quietHoursFilter)
            .put("trustedOnlyMode", trustedOnlyMode)
            .put("manualFeedbackActions", manualFeedbackActions)
            .toString()
    }

    private fun List<String>.toJsonArray(): String {
        val array = JSONArray()
        distinct().forEach { pattern ->
            val trimmed = pattern.trim()
            if (trimmed.isNotEmpty()) {
                array.put(trimmed)
            }
        }
        return array.toString()
    }

    private fun String.blockPatternOnly(): String {
        return substringBefore("||").trim()
    }

    private fun matchesAnyPattern(normalizedNumber: String, patterns: List<String>): Boolean {
        if (normalizedNumber.isBlank()) {
            return false
        }
        return patterns.any { pattern ->
            val cleanPattern = pattern.blockPatternOnly()
            when {
                cleanPattern.endsWith("*") -> normalizedNumber.startsWith(cleanPattern.dropLast(1))
                else -> normalizedNumber == cleanPattern
            }
        }
    }

    private fun normalizePhonePattern(rawPhoneNumber: String): String {
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
}
