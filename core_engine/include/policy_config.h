#pragma once

namespace callguardian {

struct PolicyConfig {
    float warnThreshold = 0.35f;
    float silenceThreshold = 0.50f;
    float blockThreshold = 0.65f;

    bool blockFailedVerification = true;
    bool warnNeighborSpoof = true;
    bool blockHighFrequencyRobocall = true;
    bool blockFirstSeenInternational = true;
    bool allowVerifiedCalls = true;
    bool allowWhitelistedPatterns = true;
};

PolicyConfig defaultPolicyConfig();

} // namespace callguardian
