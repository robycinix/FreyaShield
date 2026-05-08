package com.callguardian.engine

data class PlatformCallInfo(
    val rawPhoneNumber: String,
    val timestampMillis: Long,
    val verificationStatus: Int,
    val direction: Int,
    val userCountryCode: String,
    val deviceNumberHint: String,
    val seenBefore: Boolean,
    val recentCallsFromSameNumber: Int,
    val recentCallsFromSamePrefix: Int,
    val userRejectedCallsFromSamePrefix: Int
)

data class PlatformRiskAssessment(
    val score: Float,
    val action: Int,
    val primaryReason: String,
    val explanation: String
) {
    val shouldBlock: Boolean
        get() = action == 3
}
