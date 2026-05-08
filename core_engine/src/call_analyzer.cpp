#include "call_analyzer.h"
#include "phone_number_normalizer.h"

#include <algorithm>

namespace callguardian {

CallAnalyzer::CallAnalyzer(PolicyConfig policy)
    : policy_(policy),
      decisionEngine_(policy) {}

void CallAnalyzer::setPolicy(PolicyConfig policy) {
    policy_ = policy;
    decisionEngine_ = DecisionEngine(policy);
}

void CallAnalyzer::setWhitelistPatterns(std::vector<std::string> patterns) {
    whitelistMatcher_.setPatterns(std::move(patterns));
}

void CallAnalyzer::setBlocklistPatterns(std::vector<std::string> patterns) {
    blocklistMatcher_.setPatterns(std::move(patterns));
}

RiskAssessment CallAnalyzer::evaluate(CallInfo call) const {
    if (call.normalizedPhoneNumber.empty()) {
        call.normalizedPhoneNumber = PhoneNumberNormalizer::normalize(call.rawPhoneNumber, call.userCountryCode);
    }

    if (!call.deviceNumberHint.empty()) {
        call.deviceNumberHint = PhoneNumberNormalizer::normalize(call.deviceNumberHint, call.userCountryCode);
    }

    RiskAssessment assessment;

    if (policy_.allowWhitelistedPatterns && whitelistMatcher_.matches(call.normalizedPhoneNumber)) {
        assessment.score = 0.0f;
        assessment.action = RecommendedAction::Allow;
        assessment.primaryReason = "WHITELIST";
        assessment.signals.push_back({"WHITELIST", -1.0f, "Caller matched a trusted whitelist pattern."});
        return assessment;
    }

    if (blocklistMatcher_.matches(call.normalizedPhoneNumber)) {
        assessment.score = 1.0f;
        assessment.action = RecommendedAction::Block;
        assessment.primaryReason = "BLOCKLIST";
        assessment.signals.push_back({"BLOCKLIST", 1.0f, "Caller matched a blocked pattern."});
        return assessment;
    }

    assessment.signals = spoofDetector_.detect(call);
    if (!policy_.warnNeighborSpoof) {
        assessment.signals.erase(
            std::remove_if(
                assessment.signals.begin(),
                assessment.signals.end(),
                [](const RiskSignal& signal) { return signal.code == "NEIGHBOR_SPOOF"; }
            ),
            assessment.signals.end()
        );
    }

    for (const auto& signal : assessment.signals) {
        assessment.score += signal.weight;
    }

    if (policy_.allowVerifiedCalls && call.verificationStatus == VerificationStatus::Passed) {
        assessment.score = std::min(assessment.score, 0.30f);
    }

    return decisionEngine_.decide(assessment);
}

} // namespace callguardian
