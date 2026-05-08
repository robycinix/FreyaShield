#include "whitelist_matcher.h"

namespace callguardian {

void WhitelistMatcher::setPatterns(std::vector<std::string> patterns) {
    patterns_ = std::move(patterns);
}

bool WhitelistMatcher::matches(const std::string& normalizedPhoneNumber) const {
    for (const auto& pattern : patterns_) {
        if (matchPattern(pattern, normalizedPhoneNumber)) {
            return true;
        }
    }
    return false;
}

bool WhitelistMatcher::matchPattern(const std::string& pattern, const std::string& normalizedPhoneNumber) {
    if (pattern.empty()) {
        return false;
    }

    if (pattern.back() == '*') {
        const auto prefix = pattern.substr(0, pattern.size() - 1);
        return normalizedPhoneNumber.rfind(prefix, 0) == 0;
    }

    return pattern == normalizedPhoneNumber;
}

} // namespace callguardian
