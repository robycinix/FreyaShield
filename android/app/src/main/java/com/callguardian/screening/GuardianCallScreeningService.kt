package com.callguardian.screening

import android.os.Build
import android.telecom.Call
import android.telecom.CallScreeningService
import android.util.Log
import com.callguardian.data.AppPreferences
import com.callguardian.data.CallEventStore
import com.callguardian.data.CallPatternStore
import com.callguardian.data.CallStatsStore
import com.callguardian.data.PolicySettings
import com.callguardian.data.ScreeningDiagnosticsStore
import com.callguardian.engine.CoreEngineBridge
import com.callguardian.engine.PlatformCallInfo
import com.callguardian.engine.PlatformRiskAssessment
import java.util.Calendar

class GuardianCallScreeningService : CallScreeningService() {
    override fun onScreenCall(details: Call.Details) {
        val number = details.handle?.schemeSpecificPart.orEmpty()
        ScreeningDiagnosticsStore.recordInvocation(this, number)
        Log.i(TAG, "Screening invoked for ${maskPhoneNumber(number)}")

        try {
            AppPreferences.applyToCore(this)
            val policy = AppPreferences.loadPolicy(this)
            val patternSignal = CallPatternStore.inspect(this, number)
            val isWhitelisted = AppPreferences.isWhitelisted(this, number)

            val verificationStatus = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                details.callerNumberVerificationStatus
            } else {
                0
            }

            val assessment = CoreEngineBridge.analyzeCall(
                PlatformCallInfo(
                    rawPhoneNumber = number,
                    timestampMillis = System.currentTimeMillis(),
                    verificationStatus = verificationStatus,
                    direction = 0,
                    userCountryCode = "IT",
                    deviceNumberHint = "",
                    seenBefore = patternSignal.seenBefore,
                    recentCallsFromSameNumber = patternSignal.recentCallsFromSameNumber,
                    recentCallsFromSamePrefix = patternSignal.recentCallsFromSamePrefix,
                    userRejectedCallsFromSamePrefix = 0
                )
            )
            val finalAssessment = applyLocalCallCenterFilters(
                assessment = assessment,
                verificationStatus = verificationStatus,
                policy = policy,
                isWhitelisted = isWhitelisted,
                greylistedPrefix = patternSignal.greylistedPrefix,
                recentCallsFromSamePrefix = patternSignal.recentCallsFromSamePrefix
            ).limitedByConsent(policy)
            CallStatsStore.recordAssessment(this, finalAssessment)
            CallEventStore.record(this, number, finalAssessment)
            CallPatternStore.record(this, number, finalAssessment, policy)
            Log.i(TAG, "Screening result action=${finalAssessment.action} score=${finalAssessment.score} reason=${finalAssessment.primaryReason}")

            val response = when (finalAssessment.action) {
                ACTION_BLOCK -> CallResponse.Builder()
                    .setDisallowCall(true)
                    .setRejectCall(true)
                    .setSkipCallLog(false)
                    .setSkipNotification(false)
                    .build()

                ACTION_SILENCE -> CallResponse.Builder()
                    .setDisallowCall(false)
                    .setSilenceCall(true)
                    .build()

                else -> allowResponse()
            }

            if (policy.manualFeedbackActions && finalAssessment.action <= ACTION_WARN) {
                CallChoiceNotifier.show(this, number, finalAssessment)
            }

            respondToCall(details, response)
        } catch (error: Throwable) {
            Log.e(TAG, "Screening failed; allowing call", error)
            respondToCall(details, allowResponse())
        }
    }

    private fun applyLocalCallCenterFilters(
        assessment: PlatformRiskAssessment,
        verificationStatus: Int,
        policy: PolicySettings,
        isWhitelisted: Boolean,
        greylistedPrefix: Boolean,
        recentCallsFromSamePrefix: Int
    ): PlatformRiskAssessment {
        if (isWhitelisted) {
            return assessment
        }

        if (policy.trustedOnlyMode) {
            return assessment.blocked("TRUSTED_ONLY_MODE", 1.0f)
        }

        if (policy.temporaryGreylist && greylistedPrefix) {
            return assessment.blocked("TEMPORARY_GREYLIST", maxOf(assessment.score, 0.90f))
        }

        if (policy.autoBlockSimilarNumbers &&
            recentCallsFromSamePrefix >= 3 &&
            assessment.action >= ACTION_WARN
        ) {
            return assessment.blocked("AUTO_BLOCK_SIMILAR", maxOf(assessment.score, policy.blockThreshold))
        }

        if (policy.blockUnverifiedSuspicious &&
            verificationStatus != VERIFICATION_PASSED &&
            assessment.action >= ACTION_WARN
        ) {
            return assessment.blocked("UNVERIFIED_SUSPICIOUS", maxOf(assessment.score, policy.blockThreshold))
        }

        if (policy.quietHoursFilter &&
            isQuietHours() &&
            verificationStatus != VERIFICATION_PASSED &&
            assessment.action >= ACTION_WARN
        ) {
            return assessment.silenced("QUIET_HOURS_FILTER", maxOf(assessment.score, policy.silenceThreshold))
        }

        return assessment
    }

    private companion object {
        const val TAG = "CallGuardianScreening"
        const val ACTION_WARN = 1
        const val ACTION_SILENCE = 2
        const val ACTION_BLOCK = 3
        const val VERIFICATION_PASSED = 1

        fun allowResponse(): CallResponse {
            return CallResponse.Builder()
                .setDisallowCall(false)
                .build()
        }

        fun maskPhoneNumber(number: String): String {
            val cleaned = number.trim()
            if (cleaned.length <= 6) {
                return "******"
            }
            return cleaned.take(6) + "******"
        }

        fun isQuietHours(): Boolean {
            val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
            return hour >= 20 || hour < 8
        }

        fun PlatformRiskAssessment.blocked(reason: String, score: Float): PlatformRiskAssessment {
            return copy(
                score = score.coerceIn(0.0f, 1.0f),
                action = ACTION_BLOCK,
                primaryReason = reason,
                explanation = appendExplanation(explanation, reason)
            )
        }

        fun PlatformRiskAssessment.silenced(reason: String, score: Float): PlatformRiskAssessment {
            return copy(
                score = score.coerceIn(0.0f, 1.0f),
                action = ACTION_SILENCE,
                primaryReason = reason,
                explanation = appendExplanation(explanation, reason)
            )
        }

        fun PlatformRiskAssessment.limitedByConsent(policy: PolicySettings): PlatformRiskAssessment {
            val maxAction = policy.consentLevel.coerceIn(ACTION_ALLOW, ACTION_BLOCK)
            if (action <= maxAction) {
                return this
            }

            val reason = "CONSENT_LEVEL_$maxAction"
            return copy(
                action = maxAction,
                primaryReason = if (maxAction == ACTION_ALLOW) "CONSENT_REQUIRED" else primaryReason,
                explanation = appendExplanation(explanation, reason)
            )
        }

        fun appendExplanation(existing: String, reason: String): String {
            return if (existing.isBlank()) reason else "$existing; $reason"
        }

        const val ACTION_ALLOW = 0
    }
}
