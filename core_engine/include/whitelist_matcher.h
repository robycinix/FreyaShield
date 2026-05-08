#pragma once

#include <string>
#include <vector>

namespace callguardian {

class WhitelistMatcher {
public:
    void setPatterns(std::vector<std::string> patterns);
    bool matches(const std::string& normalizedPhoneNumber) const;

private:
    std::vector<std::string> patterns_;
    static bool matchPattern(const std::string& pattern, const std::string& normalizedPhoneNumber);
};

} // namespace callguardian
