#include "spoof_detector.h"
#include "test_support.h"

using namespace callguardian;

void testNeighborSpoofSignal() {
    CallInfo call;
    call.normalizedPhoneNumber = "+393479998888";
    call.deviceNumberHint = "+393471234567";
    call.verificationStatus = VerificationStatus::NotValidated;

    SpoofDetector detector;
    const auto signals = detector.detect(call);

    bool found = false;
    for (const auto& signal : signals) {
        found = found || signal.code == "NEIGHBOR_SPOOF";
    }
    CG_ASSERT_TRUE(found);
}

void testVerificationPassedSignal() {
    CallInfo call;
    call.normalizedPhoneNumber = "+393471111111";
    call.verificationStatus = VerificationStatus::Passed;

    SpoofDetector detector;
    const auto signals = detector.detect(call);

    bool found = false;
    for (const auto& signal : signals) {
        found = found || signal.code == "VERIFICATION_PASSED";
    }
    CG_ASSERT_TRUE(found);
}
