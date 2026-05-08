#include "decision_engine.h"

#include <algorithm>

namespace callguardian {

DecisionEngine::DecisionEngine(PolicyConfig policy) : policy_(policy) {}

RiskAssessment DecisionEngine::decide(RiskAssessment assessment) const {
    assessment.score = std::clamp(assessment.score, 0.0f, 1.0f);

    for (const auto& signal : assessment.signals) {
        if (signal.code == "VERIFICATION_FAILED" && policy_.blockFailedVerification) {
            assessment.score = std::max(assessment.score, policy_.blockThreshold);
            assessment.primaryReason = signal.code;
        }
        if ((signal.code == "HIGH_FREQUENCY_NUMBER" || signal.code == "HIGH_FREQUENCY_PREFIX") &&
            policy_.blockHighFrequencyRobocall) {
            assessment.score = std::max(assessment.score, policy_.blockThreshold);
            assessment.primaryReason = signal.code;
        }
        if (signal.code == "INTERNATIONAL_FIRST_SEEN" && policy_.blockFirstSeenInternational) {
            assessment.score = std::max(assessment.score, policy_.blockThreshold);
            assessment.primaryReason = signal.code;
        }
    }

    if (assessment.score >= policy_.blockThreshold) {
        assessment.action = RecommendedAction::Block;
    } else if (assessment.score >= policy_.silenceThreshold) {
        assessment.action = RecommendedAction::Silence;
    } else if (assessment.score >= policy_.warnThreshold) {
        assessment.action = RecommendedAction::Warn;
    } else {
        assessment.action = RecommendedAction::Allow;
    }

    if (assessment.primaryReason == "NONE" && !assessment.signals.empty()) {
        assessment.primaryReason = assessment.signals.front().code;
    }

    return assessment;
}

} // namespace callguardian
