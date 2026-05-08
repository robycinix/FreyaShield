#include "phone_number_normalizer.h"
#include "test_support.h"

using callguardian::PhoneNumberNormalizer;

void testItalianLocalNumber() {
    CG_ASSERT_EQ(PhoneNumberNormalizer::normalize("02 1234 5678", "IT"), "+390212345678");
}

void testInternationalPrefix() {
    CG_ASSERT_EQ(PhoneNumberNormalizer::normalize("0039 347 1234567", "IT"), "+393471234567");
}

void testAlreadyInternational() {
    CG_ASSERT_EQ(PhoneNumberNormalizer::normalize("+39 347 1234567", "IT"), "+393471234567");
}
