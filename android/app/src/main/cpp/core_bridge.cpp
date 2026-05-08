#include <jni.h>

#include <algorithm>
#include <cctype>
#include <cstdlib>
#include <memory>
#include <mutex>
#include <string>
#include <vector>

#include "call_analyzer.h"
#include "call_info.h"
#include "policy_config.h"
#include "risk_assessment.h"

using callguardian::CallAnalyzer;
using callguardian::CallDirection;
using callguardian::CallInfo;
using callguardian::PolicyConfig;
using callguardian::RecommendedAction;
using callguardian::RiskAssessment;
using callguardian::VerificationStatus;

namespace {

std::unique_ptr<CallAnalyzer> g_analyzer;
PolicyConfig g_policy;
std::vector<std::string> g_whitelistPatterns;
std::vector<std::string> g_blocklistPatterns;
std::mutex g_analyzerMutex;

CallAnalyzer& analyzer() {
    if (!g_analyzer) {
        g_analyzer = std::make_unique<CallAnalyzer>(g_policy);
        g_analyzer->setWhitelistPatterns(g_whitelistPatterns);
        g_analyzer->setBlocklistPatterns(g_blocklistPatterns);
    }
    return *g_analyzer;
}

std::string readJString(JNIEnv* env, jstring value) {
    if (!value) {
        return {};
    }

    const char* chars = env->GetStringUTFChars(value, nullptr);
    std::string out = chars ? chars : "";
    env->ReleaseStringUTFChars(value, chars);
    return out;
}

std::string readString(JNIEnv* env, jobject obj, jclass cls, const char* fieldName) {
    const jfieldID field = env->GetFieldID(cls, fieldName, "Ljava/lang/String;");
    if (!field) {
        return {};
    }

    auto value = static_cast<jstring>(env->GetObjectField(obj, field));
    if (!value) {
        return {};
    }

    std::string out = readJString(env, value);
    env->DeleteLocalRef(value);
    return out;
}

std::string compactJson(std::string json) {
    json.erase(
        std::remove_if(
            json.begin(),
            json.end(),
            [](unsigned char ch) { return std::isspace(ch) != 0; }
        ),
        json.end()
    );
    return json;
}

bool findBool(const std::string& json, const char* key, bool fallback) {
    const std::string needle = "\"" + std::string(key) + "\":";
    const auto pos = json.find(needle);
    if (pos == std::string::npos) {
        return fallback;
    }

    const auto valueStart = pos + needle.size();
    if (json.compare(valueStart, 4, "true") == 0) {
        return true;
    }
    if (json.compare(valueStart, 5, "false") == 0) {
        return false;
    }
    return fallback;
}

float findFloat(const std::string& json, const char* key, float fallback) {
    const std::string needle = "\"" + std::string(key) + "\":";
    const auto pos = json.find(needle);
    if (pos == std::string::npos) {
        return fallback;
    }

    char* end = nullptr;
    const char* start = json.c_str() + pos + needle.size();
    const float value = std::strtof(start, &end);
    return end == start ? fallback : value;
}

std::vector<std::string> parseStringArray(const std::string& json) {
    std::vector<std::string> out;
    const auto start = json.find('[');
    const auto end = json.rfind(']');
    if (start == std::string::npos || end == std::string::npos || end <= start) {
        return out;
    }

    bool inString = false;
    bool escaping = false;
    std::string current;
    for (std::size_t i = start + 1; i < end; ++i) {
        const char ch = json[i];
        if (!inString) {
            if (ch == '"') {
                inString = true;
                current.clear();
            }
            continue;
        }

        if (escaping) {
            current.push_back(ch);
            escaping = false;
            continue;
        }

        if (ch == '\\') {
            escaping = true;
            continue;
        }

        if (ch == '"') {
            inString = false;
            if (!current.empty()) {
                out.push_back(current);
            }
            continue;
        }

        current.push_back(ch);
    }

    return out;
}

PolicyConfig parsePolicy(const std::string& rawJson, PolicyConfig fallback) {
    const std::string json = compactJson(rawJson);
    PolicyConfig policy = fallback;
    policy.warnThreshold = findFloat(json, "warnThreshold", policy.warnThreshold);
    policy.silenceThreshold = findFloat(json, "silenceThreshold", policy.silenceThreshold);
    policy.blockThreshold = findFloat(json, "blockThreshold", policy.blockThreshold);
    policy.blockFailedVerification = findBool(json, "blockFailedVerification", policy.blockFailedVerification);
    policy.warnNeighborSpoof = findBool(json, "warnNeighborSpoof", policy.warnNeighborSpoof);
    policy.blockHighFrequencyRobocall = findBool(json, "blockHighFrequencyRobocall", policy.blockHighFrequencyRobocall);
    policy.blockFirstSeenInternational = findBool(json, "blockFirstSeenInternational", policy.blockFirstSeenInternational);
    policy.allowVerifiedCalls = findBool(json, "allowVerifiedCalls", policy.allowVerifiedCalls);
    policy.allowWhitelistedPatterns = findBool(json, "allowWhitelistedPatterns", policy.allowWhitelistedPatterns);
    return policy;
}

jlong readLong(JNIEnv* env, jobject obj, jclass cls, const char* fieldName) {
    const jfieldID field = env->GetFieldID(cls, fieldName, "J");
    return field ? env->GetLongField(obj, field) : 0;
}

jint readInt(JNIEnv* env, jobject obj, jclass cls, const char* fieldName) {
    const jfieldID field = env->GetFieldID(cls, fieldName, "I");
    return field ? env->GetIntField(obj, field) : 0;
}

jboolean readBoolean(JNIEnv* env, jobject obj, jclass cls, const char* fieldName) {
    const jfieldID field = env->GetFieldID(cls, fieldName, "Z");
    return field ? env->GetBooleanField(obj, field) : JNI_FALSE;
}

VerificationStatus mapVerificationStatus(int status) {
    switch (status) {
        case 1:
            return VerificationStatus::Passed;
        case 2:
            return VerificationStatus::Failed;
        case 3:
            return VerificationStatus::NotValidated;
        default:
            return VerificationStatus::Unknown;
    }
}

CallDirection mapDirection(int direction) {
    switch (direction) {
        case 0:
            return CallDirection::Incoming;
        case 1:
            return CallDirection::Outgoing;
        default:
            return CallDirection::Unknown;
    }
}

jobject createAssessment(JNIEnv* env, const RiskAssessment& assessment) {
    jclass cls = env->FindClass("com/callguardian/engine/PlatformRiskAssessment");
    if (!cls) {
        return nullptr;
    }

    jmethodID ctor = env->GetMethodID(
        cls,
        "<init>",
        "(FILjava/lang/String;Ljava/lang/String;)V"
    );
    if (!ctor) {
        return nullptr;
    }

    std::string explanation;
    for (const auto& signal : assessment.signals) {
        if (!explanation.empty()) {
            explanation += "; ";
        }
        explanation += signal.code;
    }

    jstring reason = env->NewStringUTF(assessment.primaryReason.c_str());
    jstring details = env->NewStringUTF(explanation.c_str());
    jobject out = env->NewObject(
        cls,
        ctor,
        assessment.score,
        static_cast<jint>(assessment.action),
        reason,
        details
    );

    env->DeleteLocalRef(reason);
    env->DeleteLocalRef(details);
    env->DeleteLocalRef(cls);
    return out;
}

} // namespace

extern "C" JNIEXPORT jobject JNICALL
Java_com_callguardian_engine_CoreEngineBridge_analyzeCall(
    JNIEnv* env,
    jobject,
    jobject callInfoObj
) {
    if (!callInfoObj) {
        RiskAssessment empty;
        empty.primaryReason = "EMPTY_CALL_INFO";
        return createAssessment(env, empty);
    }

    jclass infoClass = env->GetObjectClass(callInfoObj);
    CallInfo call;
    call.rawPhoneNumber = readString(env, callInfoObj, infoClass, "rawPhoneNumber");
    call.timestampMillis = readLong(env, callInfoObj, infoClass, "timestampMillis");
    call.verificationStatus = mapVerificationStatus(readInt(env, callInfoObj, infoClass, "verificationStatus"));
    call.direction = mapDirection(readInt(env, callInfoObj, infoClass, "direction"));
    call.userCountryCode = readString(env, callInfoObj, infoClass, "userCountryCode");
    call.deviceNumberHint = readString(env, callInfoObj, infoClass, "deviceNumberHint");
    call.seenBefore = readBoolean(env, callInfoObj, infoClass, "seenBefore") == JNI_TRUE;
    call.recentCallsFromSameNumber = readInt(env, callInfoObj, infoClass, "recentCallsFromSameNumber");
    call.recentCallsFromSamePrefix = readInt(env, callInfoObj, infoClass, "recentCallsFromSamePrefix");
    call.userRejectedCallsFromSamePrefix = readInt(env, callInfoObj, infoClass, "userRejectedCallsFromSamePrefix");
    env->DeleteLocalRef(infoClass);

    std::lock_guard<std::mutex> lock(g_analyzerMutex);
    return createAssessment(env, analyzer().evaluate(call));
}

extern "C" JNIEXPORT void JNICALL
Java_com_callguardian_engine_CoreEngineBridge_updatePolicy(JNIEnv* env, jobject, jstring jsonPolicy) {
    std::lock_guard<std::mutex> lock(g_analyzerMutex);
    g_policy = parsePolicy(readJString(env, jsonPolicy), g_policy);
    analyzer().setPolicy(g_policy);
}

extern "C" JNIEXPORT void JNICALL
Java_com_callguardian_engine_CoreEngineBridge_setWhitelistPatterns(JNIEnv* env, jobject, jstring patternsJson) {
    std::lock_guard<std::mutex> lock(g_analyzerMutex);
    g_whitelistPatterns = parseStringArray(readJString(env, patternsJson));
    analyzer().setWhitelistPatterns(g_whitelistPatterns);
}

extern "C" JNIEXPORT void JNICALL
Java_com_callguardian_engine_CoreEngineBridge_setBlocklistPatterns(JNIEnv* env, jobject, jstring patternsJson) {
    std::lock_guard<std::mutex> lock(g_analyzerMutex);
    g_blocklistPatterns = parseStringArray(readJString(env, patternsJson));
    analyzer().setBlocklistPatterns(g_blocklistPatterns);
}
