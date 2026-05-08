#pragma once

#include "call_info.h"
#include "decision_engine.h"
#include "policy_config.h"
#include "risk_assessment.h"
#include "spoof_detector.h"
#include "whitelist_matcher.h"

#include <vector>

namespace callguardian {

class CallAnalyzer {
public:
    explicit CallAnalyzer(PolicyConfig policy = defaultPolicyConfig());

    void setPolicy(PolicyConfig policy);
    void setWhitelistPatterns(std::vector<std::string> patterns);
    void setBlocklistPatterns(std::vector<std::string> patterns);
    RiskAssessment evaluate(CallInfo call) const;

private:
    PolicyConfig policy_;
    SpoofDetector spoofDetector_;
    DecisionEngine decisionEngine_;
    WhitelistMatcher whitelistMatcher_;
    WhitelistMatcher blocklistMatcher_;
};

} // namespace callguardian
