#pragma once

#include "policy_config.h"
#include "risk_assessment.h"

namespace callguardian {

class DecisionEngine {
public:
    explicit DecisionEngine(PolicyConfig policy = defaultPolicyConfig());

    RiskAssessment decide(RiskAssessment assessment) const;

private:
    PolicyConfig policy_;
};

} // namespace callguardian
