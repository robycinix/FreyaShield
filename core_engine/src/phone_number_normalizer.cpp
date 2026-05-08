#include "phone_number_normalizer.h"

#include <cctype>

namespace callguardian {

std::string PhoneNumberNormalizer::normalize(const std::string& rawNumber, const std::string& userCountryCode) {
    auto cleaned = digitsAndPlusOnly(rawNumber);
    if (cleaned.empty()) {
        return {};
    }

    if (cleaned.rfind("00", 0) == 0) {
        return "+" + cleaned.substr(2);
    }

    if (cleaned.front() == '+') {
        return cleaned;
    }

    return defaultDialingPrefix(userCountryCode) + cleaned;
}

std::string PhoneNumberNormalizer::digitsAndPlusOnly(const std::string& rawNumber) {
    std::string out;
    out.reserve(rawNumber.size());

    for (char c : rawNumber) {
        const auto uc = static_cast<unsigned char>(c);
        if (std::isdigit(uc)) {
            out.push_back(c);
        } else if (c == '+' && out.empty()) {
            out.push_back(c);
        }
    }

    return out;
}

std::string PhoneNumberNormalizer::defaultDialingPrefix(const std::string& userCountryCode) {
    if (userCountryCode == "IT" || userCountryCode == "it") {
        return "+39";
    }

    if (userCountryCode == "US" || userCountryCode == "us") {
        return "+1";
    }

    return "+";
}

} // namespace callguardian
