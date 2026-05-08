#pragma once

#include <string>
#include <vector>

namespace callguardian {

enum class RecommendedAction {
    Allow = 0,
    Warn = 1,
    Silence = 2,
    Block = 3
};

struct RiskSignal {
    std::string code;
    float weight = 0.0f;
    std::string explanation;
};

struct RiskAssessment {
    float score = 0.0f;
    RecommendedAction action = RecommendedAction::Allow;
    std::string primaryReason = "NONE";
    std::vector<RiskSignal> signals;
};

} // namespace callguardian
