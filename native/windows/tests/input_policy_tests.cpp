#include "bounded_spsc_queue.h"
#include "input_policy.h"
#include "watchdog_decision.h"

#include <cstdint>
#include <iostream>
#include <string>

namespace {

using absolute_control::BoundedSpscQueue;
using absolute_control::InputPolicy;
using absolute_control::Mode;
using absolute_control::MouseButton;
using absolute_control::WatchdogDecision;

constexpr std::uint32_t kShift = 0x10;
constexpr std::uint32_t kControl = 0x11;
constexpr std::uint32_t kAlt = 0x12;

int g_failures = 0;

void expect(const bool condition, const std::string& message) {
    if (!condition) {
        ++g_failures;
        std::cerr << "FAIL: " << message << '\n';
    }
}

void testLocalPasses() {
    InputPolicy policy;
    expect(policy.mode() == Mode::Local, "initial mode is LOCAL");
    expect(!policy.keyboard('A', true, false).suppress,
           "LOCAL key press passes");
    expect(!policy.keyboard('A', false, false).suppress,
           "LOCAL key release passes");
    expect(!policy.mouseButton(MouseButton::Left, true, false).suppress,
           "LOCAL mouse press passes");
    expect(!policy.mouseButton(MouseButton::Left, false, false).suppress,
           "LOCAL mouse release passes");
    expect(!policy.mouseUnpaired(false).suppress,
           "LOCAL motion/wheel passes");
}

void testRemoteSuppresses() {
    InputPolicy policy;
    policy.enterRemote();
    const auto keyDown = policy.keyboard('A', true, false);
    expect(keyDown.suppress && keyDown.capturedRemote,
           "REMOTO physical key press is captured and suppressed");
    expect(policy.keyboard('A', false, false).suppress,
           "REMOTO-owned key release is suppressed");
    expect(policy.mouseButton(MouseButton::Left, true, false).suppress,
           "REMOTO physical mouse press is suppressed");
    expect(policy.mouseButton(MouseButton::Left, false, false).suppress,
           "REMOTO-owned mouse release is suppressed");
    expect(policy.mouseUnpaired(false).suppress,
           "REMOTO physical motion/wheel is suppressed");
}

void testOwnershipAcrossTransitions() {
    InputPolicy policy;
    expect(!policy.keyboard(kShift, true, false).suppress,
           "LOCAL Shift press passes");
    expect(!policy.mouseButton(MouseButton::Right, true, false).suppress,
           "LOCAL right press passes");

    policy.enterRemote();
    expect(!policy.keyboard(kShift, true, false).suppress,
           "LOCAL-owned key repeat still passes in REMOTO");
    expect(!policy.keyboard(kShift, false, false).suppress,
           "LOCAL-owned key release passes in REMOTO");
    expect(!policy.mouseButton(MouseButton::Right, false, false).suppress,
           "LOCAL-owned mouse release passes in REMOTO");

    expect(policy.keyboard(kControl, true, false).suppress,
           "REMOTO Ctrl press is suppressed");
    expect(policy.mouseButton(MouseButton::Middle, true, false).suppress,
           "REMOTO middle press is suppressed");
    policy.beginLocalDraining();
    expect(policy.mode() == Mode::LocalDraining,
           "controlled transition with owners enters LOCAL_DRAINING");
    expect(policy.pendingRemoteOwnership() == 2U,
           "draining reports pending key and mouse button");
    expect(policy.keyboard(kControl, false, false).suppress,
           "REMOTO-owned Ctrl release stays suppressed while draining");
    expect(policy.mode() == Mode::LocalDraining,
           "draining waits for remaining mouse owner");
    expect(policy.mouseButton(MouseButton::Middle, false, false).suppress,
           "REMOTO-owned mouse release stays suppressed while draining");
    expect(policy.mode() == Mode::Local,
           "last remote owner completes draining");
    expect(policy.pendingRemoteOwnership() == 0U,
           "completed draining has no remote ownership");
    expect(!policy.keyboard('B', true, false).suppress,
           "new LOCAL press passes after transition");

    policy.enterRemote();
    expect(policy.keyboard('C', true, false).suppress,
           "second REMOTO press is suppressed");
    policy.beginLocalDraining();
    policy.enterRemote();
    expect(policy.keyboard('C', false, false).suppress,
           "REMOTO owner survives draining and REMOTO re-entry");
}

void testSeededState() {
    InputPolicy policy;
    policy.seedLocalKeyDown(kAlt);
    policy.seedLocalMouseDown(MouseButton::X1);
    policy.enterRemote();
    expect(!policy.keyboard(kAlt, false, false).suppress,
           "key held before REMOTO releases to LOCAL");
    expect(!policy.mouseButton(MouseButton::X1, false, false).suppress,
           "button held before REMOTO releases to LOCAL");
}

void testEscapeEmergency() {
    InputPolicy policy;
    policy.enterRemote();
    const auto down =
        policy.keyboard(InputPolicy::kEscapeVirtualKey, true, false);
    expect(down.suppress && down.emergencyEscape,
           "physical Escape press is consumed in REMOTO");
    expect(policy.mode() == Mode::LocalDraining,
           "physical Escape changes mode to LOCAL_DRAINING");
    expect(policy.escapeReleasePending(),
           "Escape release is explicitly pending");
    expect(policy.pendingRemoteOwnership() == 1U,
           "Escape is the only pending remote owner");
    const auto up =
        policy.keyboard(InputPolicy::kEscapeVirtualKey, false, false);
    expect(up.suppress && !up.emergencyEscape,
           "matching Escape release is consumed");
    expect(policy.mode() == Mode::Local,
           "Escape release completes draining when no other owner remains");
    expect(!policy.escapeReleasePending(),
           "Escape pending flag clears on release");
    expect(policy.pendingRemoteOwnership() == 0U,
           "Escape completion clears pending ownership");
    expect(!policy.keyboard(InputPolicy::kEscapeVirtualKey, true, false).suppress,
           "next Escape press passes in LOCAL");
}

void testRemoteKeyDrainsAfterEscape() {
    InputPolicy policy;
    policy.enterRemote();
    expect(policy.keyboard(kControl, true, false).suppress,
           "REMOTO Ctrl press is suppressed before Escape");
    expect(policy.keyboard(InputPolicy::kEscapeVirtualKey, true, false)
               .emergencyEscape,
           "Escape starts controlled draining");
    expect(policy.pendingRemoteOwnership() == 2U,
           "Ctrl and Escape are pending remote owners");
    expect(policy.keyboard(InputPolicy::kEscapeVirtualKey, false, false)
               .suppress,
           "Escape release is consumed first");
    expect(policy.mode() == Mode::LocalDraining,
           "Ctrl keeps LOCAL_DRAINING active");
    expect(policy.keyboard(kControl, false, false).suppress,
           "remote Ctrl release is consumed during draining");
    expect(policy.mode() == Mode::Local,
           "Ctrl release completes LOCAL_DRAINING");
}

void testRemoteMouseDrainsAfterEscape() {
    InputPolicy policy;
    policy.enterRemote();
    expect(policy.mouseButton(MouseButton::Left, true, false).suppress,
           "REMOTO mouse press is suppressed before Escape");
    (void)policy.keyboard(InputPolicy::kEscapeVirtualKey, true, false);
    (void)policy.keyboard(InputPolicy::kEscapeVirtualKey, false, false);
    expect(policy.mode() == Mode::LocalDraining,
           "mouse owner keeps LOCAL_DRAINING active");
    expect(policy.mouseButton(MouseButton::Left, false, false).suppress,
           "remote mouse release is consumed during draining");
    expect(policy.mode() == Mode::Local,
           "mouse release completes LOCAL_DRAINING");
}

void testNewInputPassesDuringDraining() {
    InputPolicy policy;
    policy.enterRemote();
    (void)policy.keyboard(InputPolicy::kEscapeVirtualKey, true, false);
    expect(policy.mode() == Mode::LocalDraining,
           "precondition: Escape started draining");

    expect(!policy.keyboard('N', true, false).suppress,
           "new key passes during LOCAL_DRAINING");
    expect(!policy.mouseButton(MouseButton::Right, true, false).suppress,
           "new click passes during LOCAL_DRAINING");
    expect(!policy.mouseUnpaired(false).suppress,
           "mouse movement/wheel passes during LOCAL_DRAINING");
    expect(policy.pendingRemoteOwnership() == 1U,
           "new local input does not add remote ownership");

    expect(policy.keyboard(InputPolicy::kEscapeVirtualKey, false, false)
               .suppress,
           "pending Escape release is consumed");
    expect(policy.mode() == Mode::Local,
           "draining completes despite locally-owned inputs being held");
    expect(!policy.keyboard('N', false, false).suppress,
           "new draining key releases to LOCAL");
    expect(!policy.mouseButton(MouseButton::Right, false, false).suppress,
           "new draining click releases to LOCAL");
}

void testRemoteRepeatDuringDraining() {
    InputPolicy policy;
    policy.enterRemote();
    (void)policy.keyboard('R', true, false);
    (void)policy.keyboard(InputPolicy::kEscapeVirtualKey, true, false);
    expect(policy.keyboard('R', true, false).suppress,
           "repeat of a REMOTO-owned key remains suppressed while draining");
    (void)policy.keyboard(InputPolicy::kEscapeVirtualKey, false, false);
    expect(policy.keyboard('R', false, false).suppress,
           "release of repeated REMOTO key is consumed");
    expect(policy.mode() == Mode::Local,
           "repeated remote key drains completely");
}

void testAbnormalRecoveryIsImmediateLocal() {
    InputPolicy timeoutPolicy;
    timeoutPolicy.enterRemote();
    (void)timeoutPolicy.keyboard(kControl, true, false);
    (void)timeoutPolicy.keyboard(
        InputPolicy::kEscapeVirtualKey, true, false);
    expect(timeoutPolicy.mode() == Mode::LocalDraining,
           "precondition: lost release leaves draining active");
    timeoutPolicy.failOpen();
    expect(timeoutPolicy.mode() == Mode::Local,
           "draining timeout goes directly to LOCAL");
    expect(timeoutPolicy.pendingRemoteOwnership() == 0U &&
               !timeoutPolicy.escapeReleasePending(),
           "draining timeout clears ownership and Escape pending");
    expect(!timeoutPolicy.keyboard(kControl, false, false).suppress,
           "lost remote release passes after draining timeout");

    InputPolicy watchdogPolicy;
    watchdogPolicy.enterRemote();
    (void)watchdogPolicy.keyboard(kControl, true, false);
    watchdogPolicy.failOpen();
    expect(watchdogPolicy.mode() == Mode::Local,
           "watchdog recovery bypasses LOCAL_DRAINING");
    expect(watchdogPolicy.pendingRemoteOwnership() == 0U,
           "watchdog recovery clears ownership");

    InputPolicy eofPolicy;
    eofPolicy.enterRemote();
    (void)eofPolicy.mouseButton(MouseButton::Left, true, false);
    eofPolicy.failOpen();
    expect(eofPolicy.mode() == Mode::Local,
           "EOF/internal failure goes directly to LOCAL");
    expect(!eofPolicy.mouseButton(MouseButton::Left, false, false).suppress,
           "EOF/internal failure leaves mouse fail-open");
}

void testWatchdogDeadlines() {
    constexpr std::uint64_t start = 1000U;
    constexpr std::uint64_t drainingDeadline =
        start + absolute_control::kDrainingTimeoutMs;
    expect(
        absolute_control::evaluateWatchdog(
            Mode::LocalDraining,
            drainingDeadline - 1U,
            0U,
            drainingDeadline) == WatchdogDecision::None,
        "draining remains active before 750 ms deadline");
    expect(
        absolute_control::evaluateWatchdog(
            Mode::LocalDraining,
            drainingDeadline,
            0U,
            drainingDeadline) == WatchdogDecision::DrainingExpired,
        "lost release expires exactly at draining deadline");
    expect(
        absolute_control::evaluateWatchdog(
            Mode::Remote, 2500U, 2500U, 1U) ==
            WatchdogDecision::LeaseExpired,
        "REMOTO watchdog selects direct lease fail-open");
    expect(
        absolute_control::evaluateWatchdog(
            Mode::Local, 9999U, 1U, 1U) == WatchdogDecision::None,
        "LOCAL never creates a watchdog recovery");
}

void testInjectedAlwaysPasses() {
    InputPolicy policy;
    policy.enterRemote();
    const auto escape =
        policy.keyboard(InputPolicy::kEscapeVirtualKey, true, true);
    expect(escape.injected && !escape.suppress && !escape.emergencyEscape,
           "injected Escape passes and is not emergency Escape");
    expect(policy.mode() == Mode::Remote,
           "injected Escape does not leave REMOTO");
    expect(!policy.keyboard('A', true, true).suppress,
           "injected keyboard event passes in REMOTO");
    expect(!policy.mouseButton(MouseButton::Left, true, true).suppress,
           "injected mouse button passes in REMOTO");
    expect(!policy.mouseUnpaired(true).suppress,
           "injected mouse motion passes in REMOTO");

    (void)policy.keyboard(InputPolicy::kEscapeVirtualKey, true, false);
    expect(!policy.keyboard('B', true, true).suppress,
           "injected keyboard event passes in LOCAL_DRAINING");
    expect(!policy.mouseUnpaired(true).suppress,
           "injected mouse event passes in LOCAL_DRAINING");

    // Because injection never owns the key, an orphan physical release is
    // allowed rather than being misclassified as a remote pair.
    expect(!policy.keyboard('A', false, false).suppress,
           "injected press does not create physical ownership");
}

void testFailOpenClearsOwnership() {
    InputPolicy policy;
    policy.enterRemote();
    expect(policy.keyboard(kControl, true, false).suppress,
           "precondition: REMOTO Ctrl press suppressed");
    expect(policy.mouseButton(MouseButton::Left, true, false).suppress,
           "precondition: REMOTO click suppressed");
    policy.failOpen();
    expect(policy.mode() == Mode::Local, "fail-open selects LOCAL");
    expect(!policy.keyboard(kControl, false, false).suppress,
           "fail-open lets pending key release reach Windows");
    expect(!policy.mouseButton(MouseButton::Left, false, false).suppress,
           "fail-open lets pending mouse release reach Windows");
}

void testBoundedQueue() {
    BoundedSpscQueue<int, 4> queue;
    expect(queue.tryPush(1), "queue accepts first value");
    expect(queue.tryPush(2), "queue accepts second value");
    expect(queue.tryPush(3), "queue accepts capacity minus sentinel");
    expect(!queue.tryPush(4), "queue reports full without blocking");
    int value = 0;
    expect(queue.tryPop(value) && value == 1, "queue preserves FIFO order 1");
    expect(queue.tryPop(value) && value == 2, "queue preserves FIFO order 2");
    expect(queue.tryPop(value) && value == 3, "queue preserves FIFO order 3");
    expect(!queue.tryPop(value), "queue reports empty without blocking");
}

}  // namespace

int main() {
    testLocalPasses();
    testRemoteSuppresses();
    testOwnershipAcrossTransitions();
    testSeededState();
    testEscapeEmergency();
    testRemoteKeyDrainsAfterEscape();
    testRemoteMouseDrainsAfterEscape();
    testNewInputPassesDuringDraining();
    testRemoteRepeatDuringDraining();
    testAbnormalRecoveryIsImmediateLocal();
    testWatchdogDeadlines();
    testInjectedAlwaysPasses();
    testFailOpenClearsOwnership();
    testBoundedQueue();

    if (g_failures != 0) {
        std::cerr << g_failures << " test(s) failed\n";
        return 1;
    }
    std::cout << "InputPolicy/BoundedSpscQueue: OK\n";
    return 0;
}
