#include "spoof_detector.h"

#include <algorithm>

namespace callguardian {

std::vector<RiskSignal> SpoofDetector::detect(const CallInfo& call) const {
    std::vector<RiskSignal> signals;

    for (const auto& signal : {
             detectVerificationFailure(call),
             detectNeighborSpoof(call),
             detectHighFrequency(call),
             detectInternationalAnomaly(call),
             detectArtificialPattern(call),
         }) {
        if (!isEmptySignal(signal)) {
            signals.push_back(signal);
        }
    }

    return signals;
}

RiskSignal SpoofDetector::detectVerificationFailure(const CallInfo& call) {
    if (call.verificationStatus == VerificationStatus::Failed) {
        return {"VERIFICATION_FAILED", 0.55f, "Carrier verification failed for this caller."};
    }

    if (call.verificationStatus == VerificationStatus::Passed) {
        return {"VERIFICATION_PASSED", -0.25f, "Carrier verification passed."};
    }

    return {};
}

RiskSignal SpoofDetector::detectNeighborSpoof(const CallInfo& call) {
    if (call.deviceNumberHint.empty() || call.normalizedPhoneNumber.empty()) {
        return {};
    }

    if (call.verificationStatus == VerificationStatus::Passed) {
        return {};
    }

    if (call.normalizedPhoneNumber == call.deviceNumberHint) {
        return {};
    }

    const int common = commonPrefixLength(call.normalizedPhoneNumber, call.deviceNumberHint);
    if (common >= 7) {
        return {"NEIGHBOR_SPOOF", 0.55f, "Caller shares a long local prefix with the device number."};
    }

    if (common == 6) {
        return {"NEIGHBOR_SPOOF", 0.40f, "Caller shares a medium local prefix with the device number."};
    }

    if (common == 5) {
        return {"NEIGHBOR_SPOOF", 0.25f, "Caller shares a short local prefix with the device number."};
    }

    return {};
}

RiskSignal SpoofDetector::detectHighFrequency(const CallInfo& call) {
    if (call.recentCallsFromSameNumber > 3) {
        return {"HIGH_FREQUENCY_NUMBER", 0.45f, "Same caller exceeded the short-window frequency limit."};
    }

    if (call.recentCallsFromSamePrefix > 8) {
        return {"HIGH_FREQUENCY_PREFIX", 0.35f, "Caller prefix exceeded the rolling frequency limit."};
    }

    if (call.userRejectedCallsFromSamePrefix > 5) {
        return {"REJECTED_PREFIX", 0.25f, "User repeatedly rejected calls from this prefix."};
    }

    return {};
}

RiskSignal SpoofDetector::detectInternationalAnomaly(const CallInfo& call) {
    if (call.normalizedPhoneNumber.empty() || call.seenBefore) {
        return {};
    }

    const bool isItalianUser = call.userCountryCode == "IT" || call.userCountryCode == "it";
    if (isItalianUser && call.normalizedPhoneNumber.rfind("+39", 0) != 0) {
        return {"INTERNATIONAL_FIRST_SEEN", 0.50f, "International caller has not been seen before."};
    }

    return {};
}

RiskSignal SpoofDetector::detectArtificialPattern(const CallInfo& call) {
    const auto& number = call.normalizedPhoneNumber;
    if (number.size() < 8) {
        return {};
    }

    int repeatedRun = 1;
    int bestRun = 1;
    for (std::size_t i = 1; i < number.size(); ++i) {
        if (number[i] == number[i - 1] && number[i] >= '0' && number[i] <= '9') {
            repeatedRun += 1;
            bestRun = std::max(bestRun, repeatedRun);
        } else {
            repeatedRun = 1;
        }
    }

    if (bestRun >= 6) {
        return {"ARTIFICIAL_PATTERN", call.seenBefore ? 0.10f : 0.20f, "Caller number contains a long repeated digit pattern."};
    }

    return {};
}

int SpoofDetector::commonPrefixLength(const std::string& left, const std::string& right) {
    const auto maxSize = std::min(left.size(), right.size());
    int count = 0;
    for (std::size_t i = 0; i < maxSize; ++i) {
        if (left[i] != right[i]) {
            break;
        }
        count += 1;
    }
    return count;
}

bool SpoofDetector::isEmptySignal(const RiskSignal& signal) {
    return signal.code.empty();
}

} // namespace callguardian
