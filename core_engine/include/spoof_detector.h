#pragma once

#include "call_info.h"
#include "risk_assessment.h"

#include <vector>

namespace callguardian {

class SpoofDetector {
public:
    std::vector<RiskSignal> detect(const CallInfo& call) const;

private:
    static RiskSignal detectVerificationFailure(const CallInfo& call);
    static RiskSignal detectNeighborSpoof(const CallInfo& call);
    static RiskSignal detectHighFrequency(const CallInfo& call);
    static RiskSignal detectInternationalAnomaly(const CallInfo& call);
    static RiskSignal detectArtificialPattern(const CallInfo& call);
    static int commonPrefixLength(const std::string& left, const std::string& right);
    static bool isEmptySignal(const RiskSignal& signal);
};

} // namespace callguardian
