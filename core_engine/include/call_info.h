#pragma once

#include <cstdint>
#include <string>

namespace callguardian {

enum class CallDirection {
    Incoming = 0,
    Outgoing = 1,
    Unknown = 2
};

enum class VerificationStatus {
    Unknown = 0,
    Passed = 1,
    Failed = 2,
    NotValidated = 3
};

struct CallInfo {
    std::string rawPhoneNumber;
    std::string normalizedPhoneNumber;
    std::string userCountryCode = "IT";
    std::string deviceNumberHint;
    std::int64_t timestampMillis = 0;
    CallDirection direction = CallDirection::Unknown;
    VerificationStatus verificationStatus = VerificationStatus::Unknown;
    bool isRoaming = false;
    bool seenBefore = false;
    int recentCallsFromSameNumber = 0;
    int recentCallsFromSamePrefix = 0;
    int userRejectedCallsFromSamePrefix = 0;
};

} // namespace callguardian
