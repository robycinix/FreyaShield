#include "call_analyzer.h"
#include "test_support.h"

using namespace callguardian;

void testItalianLocalNumber();
void testInternationalPrefix();
void testAlreadyInternational();
void testNeighborSpoofSignal();
void testVerificationPassedSignal();

void testFailedVerificationBlocks() {
    CallAnalyzer analyzer;
    CallInfo call;
    call.rawPhoneNumber = "+393471234567";
    call.verificationStatus = VerificationStatus::Failed;

    const auto result = analyzer.evaluate(call);
    CG_ASSERT_EQ(result.action, RecommendedAction::Block);
    CG_ASSERT_EQ(result.primaryReason, "VERIFICATION_FAILED");
}

void testWhitelistAllows() {
    CallAnalyzer analyzer;
    analyzer.setWhitelistPatterns({"+39347*"});

    CallInfo call;
    call.rawPhoneNumber = "+393471234567";
    call.verificationStatus = VerificationStatus::Failed;

    const auto result = analyzer.evaluate(call);
    CG_ASSERT_EQ(result.action, RecommendedAction::Allow);
    CG_ASSERT_EQ(result.primaryReason, "WHITELIST");
}

void testBlocklistBlocksPrefix() {
    CallAnalyzer analyzer;
    analyzer.setBlocklistPatterns({"+39081123456*"});

    CallInfo call;
    call.rawPhoneNumber = "+39081123456789";
    call.verificationStatus = VerificationStatus::Passed;

    const auto result = analyzer.evaluate(call);
    CG_ASSERT_EQ(result.action, RecommendedAction::Block);
    CG_ASSERT_EQ(result.primaryReason, "BLOCKLIST");
}

void testBlocklistBlocksCountryPrefix() {
    CallAnalyzer analyzer;
    analyzer.setBlocklistPatterns({"+44*"});

    CallInfo call;
    call.rawPhoneNumber = "+442079460123";

    const auto result = analyzer.evaluate(call);
    CG_ASSERT_EQ(result.action, RecommendedAction::Block);
    CG_ASSERT_EQ(result.primaryReason, "BLOCKLIST");
}

void testBlocklistBlocksItalianAreaPrefix() {
    CallAnalyzer analyzer;
    analyzer.setBlocklistPatterns({"+39081*"});

    CallInfo call;
    call.rawPhoneNumber = "081123456789";
    call.userCountryCode = "IT";

    const auto result = analyzer.evaluate(call);
    CG_ASSERT_EQ(result.action, RecommendedAction::Block);
    CG_ASSERT_EQ(result.primaryReason, "BLOCKLIST");
}

void testWhitelistOverridesBlocklist() {
    CallAnalyzer analyzer;
    analyzer.setWhitelistPatterns({"+39081123456789"});
    analyzer.setBlocklistPatterns({"+39081123456*"});

    CallInfo call;
    call.rawPhoneNumber = "+39081123456789";

    const auto result = analyzer.evaluate(call);
    CG_ASSERT_EQ(result.action, RecommendedAction::Allow);
    CG_ASSERT_EQ(result.primaryReason, "WHITELIST");
}

void testHighFrequencyBlocks() {
    CallAnalyzer analyzer;
    CallInfo call;
    call.rawPhoneNumber = "+390211111111";
    call.recentCallsFromSameNumber = 4;

    const auto result = analyzer.evaluate(call);
    CG_ASSERT_EQ(result.action, RecommendedAction::Block);
}

void testFirstSeenInternationalBlocks() {
    CallAnalyzer analyzer;
    CallInfo call;
    call.rawPhoneNumber = "+46738123456";
    call.userCountryCode = "IT";
    call.seenBefore = false;

    const auto result = analyzer.evaluate(call);
    CG_ASSERT_EQ(result.action, RecommendedAction::Block);
    CG_ASSERT_EQ(result.primaryReason, "INTERNATIONAL_FIRST_SEEN");
}

void testFirstSeenInternationalSilencesWhenPolicyDisabled() {
    PolicyConfig policy;
    policy.blockFirstSeenInternational = false;
    CallAnalyzer analyzer(policy);
    CallInfo call;
    call.rawPhoneNumber = "+46738123456";
    call.userCountryCode = "IT";
    call.seenBefore = false;

    const auto result = analyzer.evaluate(call);
    CG_ASSERT_EQ(result.action, RecommendedAction::Silence);
    CG_ASSERT_EQ(result.primaryReason, "INTERNATIONAL_FIRST_SEEN");
}

int main() {
    testItalianLocalNumber();
    testInternationalPrefix();
    testAlreadyInternational();
    testNeighborSpoofSignal();
    testVerificationPassedSignal();
    testFailedVerificationBlocks();
    testWhitelistAllows();
    testBlocklistBlocksPrefix();
    testBlocklistBlocksCountryPrefix();
    testBlocklistBlocksItalianAreaPrefix();
    testWhitelistOverridesBlocklist();
    testHighFrequencyBlocks();
    testFirstSeenInternationalBlocks();
    testFirstSeenInternationalSilencesWhenPolicyDisabled();
    return 0;
}
