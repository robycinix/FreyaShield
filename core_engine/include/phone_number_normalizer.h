#pragma once

#include <string>

namespace callguardian {

class PhoneNumberNormalizer {
public:
    static std::string normalize(const std::string& rawNumber, const std::string& userCountryCode);

private:
    static std::string digitsAndPlusOnly(const std::string& rawNumber);
    static std::string defaultDialingPrefix(const std::string& userCountryCode);
};

} // namespace callguardian
