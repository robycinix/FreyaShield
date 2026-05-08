#pragma once

#include <cstdlib>
#include <iostream>
#include <string>

#define CG_ASSERT_TRUE(expr) \
    do { \
        if (!(expr)) { \
            std::cerr << "Assertion failed: " #expr << " at " << __FILE__ << ":" << __LINE__ << "\n"; \
            std::exit(1); \
        } \
    } while (false)

#define CG_ASSERT_EQ(left, right) \
    do { \
        const auto leftValue = (left); \
        const auto rightValue = (right); \
        if (!(leftValue == rightValue)) { \
            std::cerr << "Assertion failed: " #left " == " #right << " at " << __FILE__ << ":" << __LINE__ << "\n"; \
            std::exit(1); \
        } \
    } while (false)
