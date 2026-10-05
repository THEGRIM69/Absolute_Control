#pragma once

#include "input_policy.h"

#include <cstdint>

namespace absolute_control {

constexpr std::uint64_t kDrainingTimeoutMs = 750U;

enum class WatchdogDecision : std::uint8_t {
    None,
    LeaseExpired,
    DrainingExpired,
};

[[nodiscard]] constexpr WatchdogDecision evaluateWatchdog(
    const Mode mode,
    const std::uint64_t now,
    const std::uint64_t leaseDeadline,
    const std::uint64_t drainingDeadline) noexcept {
    if (mode == Mode::Remote && now >= leaseDeadline) {
        return WatchdogDecision::LeaseExpired;
    }
    if (mode == Mode::LocalDraining && now >= drainingDeadline) {
        return WatchdogDecision::DrainingExpired;
    }
    return WatchdogDecision::None;
}

}  // namespace absolute_control
