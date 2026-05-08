package com.callguardian.engine

object CoreEngineBridge {
    init {
        System.loadLibrary("callguardian_jni")
    }

    external fun analyzeCall(callInfo: PlatformCallInfo): PlatformRiskAssessment
    external fun updatePolicy(jsonPolicy: String)
    external fun setWhitelistPatterns(patternsJson: String)
    external fun setBlocklistPatterns(patternsJson: String)
}
