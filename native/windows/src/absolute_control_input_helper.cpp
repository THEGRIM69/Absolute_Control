#define WIN32_LEAN_AND_MEAN
#include <windows.h>

#include "bounded_spsc_queue.h"
#include "input_policy.h"
#include "watchdog_decision.h"

#include <array>
#include <atomic>
#include <chrono>
#include <cstdint>
#include <iostream>
#include <mutex>
#include <sstream>
#include <string>
#include <thread>

namespace {

using absolute_control::BoundedSpscQueue;
using absolute_control::Decision;
using absolute_control::InputPolicy;
using absolute_control::Mode;
using absolute_control::MouseButton;
using absolute_control::WatchdogDecision;

constexpr UINT kSetRemoteMessage = WM_APP + 1U;
constexpr UINT kSetLocalMessage = WM_APP + 2U;
constexpr UINT kFailOpenMessage = WM_APP + 3U;
constexpr UINT kStopMessage = WM_APP + 4U;
constexpr ULONGLONG kLeaseDurationMs = 1500U;
constexpr ULONGLONG kLeaseRenewalMs = 250U;
constexpr ULONG_PTR kInjectedTestMarker =
    static_cast<ULONG_PTR>(0x41424344U);

enum class Device : std::uint8_t {
    Keyboard,
    Mouse,
};

enum class Action : std::uint8_t {
    Down,
    Up,
    Move,
    Wheel,
    HorizontalWheel,
};

enum class RecoveryReason : std::uint8_t {
    None,
    LeaseExpired,
    QueueFull,
    InternalError,
    ControllerClosed,
    HookLoopError,
    DrainingTimeout,
};

struct CapturedEvent {
    Device device = Device::Keyboard;
    Action action = Action::Down;
    std::uint32_t code = 0;
    LONG x = 0;
    LONG y = 0;
    LONG data = 0;
    DWORD timestamp = 0;
    bool capturedRemote = false;
    bool injected = false;
};

constexpr std::uint64_t kStateEnforcedBit = 1ULL << 8U;
constexpr unsigned int kStatePendingShift = 16U;

constexpr std::uint64_t encodePublicState(
    const Mode mode,
    const bool enforced,
    const std::size_t pendingRemote) noexcept {
    return static_cast<std::uint64_t>(mode) |
        (enforced ? kStateEnforcedBit : 0ULL) |
        (static_cast<std::uint64_t>(pendingRemote) << kStatePendingShift);
}

struct PublicState {
    Mode mode = Mode::Local;
    bool enforced = false;
    std::size_t pendingRemote = 0;
};

PublicState decodePublicState(const std::uint64_t encoded) noexcept {
    return PublicState{
        static_cast<Mode>(encoded & 0xFFU),
        (encoded & kStateEnforcedBit) != 0U,
        static_cast<std::size_t>(encoded >> kStatePendingShift),
    };
}

InputPolicy g_policy;
BoundedSpscQueue<CapturedEvent, 8192> g_events;
HHOOK g_keyboardHook = nullptr;
HHOOK g_mouseHook = nullptr;
HANDLE g_hookReadyEvent = nullptr;
DWORD g_hookThreadId = 0;

std::atomic<bool> g_running{true};
std::atomic<bool> g_hooksInstalled{false};
std::atomic<bool> g_policyEnforced{false};
std::atomic<bool> g_autoRenewLease{false};
std::atomic<std::uint64_t> g_publicState{
    encodePublicState(Mode::Local, false, 0U)};
std::atomic<ULONGLONG> g_leaseDeadline{0};
std::atomic<ULONGLONG> g_drainingDeadline{0};
std::atomic<RecoveryReason> g_recoveryReason{RecoveryReason::None};
std::atomic<std::uint64_t> g_escapeRecoveries{0};
std::atomic<std::uint64_t> g_injectedObserved{0};
std::atomic<std::uint64_t> g_remotePhysicalObserved{0};

std::mutex g_outputMutex;

void printLine(const std::string& line) {
    const std::lock_guard<std::mutex> lock(g_outputMutex);
    std::cout << line << std::endl;
}

const char* modeName(const Mode mode) noexcept {
    switch (mode) {
        case Mode::Remote:
            return "REMOTO";
        case Mode::LocalDraining:
            return "LOCAL_DRAINING";
        case Mode::Local:
        default:
            return "LOCAL";
    }
}

Mode publishedMode() noexcept {
    return decodePublicState(
        g_publicState.load(std::memory_order_acquire)).mode;
}

void publishState(
    const Mode mode,
    const bool enforced,
    const std::size_t pendingRemote) noexcept {
    g_publicState.store(
        encodePublicState(mode, enforced, pendingRemote),
        std::memory_order_release);
}

const char* reasonName(const RecoveryReason reason) noexcept {
    switch (reason) {
        case RecoveryReason::LeaseExpired:
            return "lease vencida";
        case RecoveryReason::QueueFull:
            return "cola llena";
        case RecoveryReason::InternalError:
            return "error interno";
        case RecoveryReason::ControllerClosed:
            return "controlador cerrado/EOF";
        case RecoveryReason::HookLoopError:
            return "error del message loop";
        case RecoveryReason::DrainingTimeout:
            return "timeout de LOCAL_DRAINING";
        case RecoveryReason::None:
        default:
            return "sin motivo";
    }
}

void postToHookThread(const UINT message) noexcept {
    const DWORD threadId = g_hookThreadId;
    if (threadId != 0U) {
        (void)PostThreadMessageW(threadId, message, 0, 0);
    }
}

void requestFailOpen(const RecoveryReason reason) noexcept {
    // Disable suppression before asking the hook thread to reconcile state.
    // Thus a delayed/full message queue cannot keep input blocked.
    g_policyEnforced.store(false, std::memory_order_release);
    g_autoRenewLease.store(false, std::memory_order_release);
    publishState(Mode::Local, false, 0U);
    g_recoveryReason.store(reason, std::memory_order_release);
    postToHookThread(kFailOpenMessage);
}

bool enqueueEvent(const CapturedEvent& event) noexcept {
    if (g_events.tryPush(event)) {
        if (event.injected) {
            g_injectedObserved.fetch_add(1, std::memory_order_relaxed);
        }
        if (event.capturedRemote && !event.injected) {
            g_remotePhysicalObserved.fetch_add(1, std::memory_order_relaxed);
        }
        return true;
    }

    requestFailOpen(RecoveryReason::QueueFull);
    return false;
}

LRESULT finishHookDecision(
    const bool suppressCurrent,
    const int hookCode,
    const WPARAM wordParam,
    const LPARAM longParam) noexcept {
    if (suppressCurrent) {
        return 1;
    }
    return CallNextHookEx(nullptr, hookCode, wordParam, longParam);
}

void publishPolicyStateAfterDecision() noexcept {
    if (!g_policyEnforced.load(std::memory_order_acquire)) {
        publishState(Mode::Local, false, 0U);
        return;
    }

    const Mode mode = g_policy.mode();
    const std::size_t pending = g_policy.pendingRemoteOwnership();
    if (mode == Mode::Local) {
        // The current callback may still consume the final remote release; all
        // callbacks after it observe a completely disarmed LOCAL state.
        g_policyEnforced.store(false, std::memory_order_release);
        publishState(Mode::Local, false, 0U);
        return;
    }
    publishState(mode, true, pending);
}

LRESULT CALLBACK keyboardHook(
    const int hookCode,
    const WPARAM wordParam,
    const LPARAM longParam) noexcept {
    if (hookCode < 0) {
        return CallNextHookEx(nullptr, hookCode, wordParam, longParam);
    }

    try {
        const bool pressed =
            wordParam == WM_KEYDOWN || wordParam == WM_SYSKEYDOWN;
        const bool released =
            wordParam == WM_KEYUP || wordParam == WM_SYSKEYUP;
        if (!pressed && !released) {
            return CallNextHookEx(nullptr, hookCode, wordParam, longParam);
        }

        const auto* const data =
            reinterpret_cast<const KBDLLHOOKSTRUCT*>(longParam);
        const bool injected = (data->flags & LLKHF_INJECTED) != 0U;
        const Decision decision =
            g_policy.keyboard(data->vkCode, pressed, injected);

        if (decision.emergencyEscape) {
            g_autoRenewLease.store(false, std::memory_order_release);
            g_drainingDeadline.store(
                GetTickCount64() + absolute_control::kDrainingTimeoutMs,
                std::memory_order_release);
            g_escapeRecoveries.fetch_add(1, std::memory_order_relaxed);
        }

        const CapturedEvent event{
            Device::Keyboard,
            pressed ? Action::Down : Action::Up,
            data->vkCode,
            0,
            0,
            0,
            data->time,
            decision.capturedRemote,
            injected,
        };
        const bool queued = enqueueEvent(event);
        const bool suppressCurrent = queued && decision.suppress &&
            g_policyEnforced.load(std::memory_order_acquire);
        if (queued) {
            publishPolicyStateAfterDecision();
        }
        return finishHookDecision(
            suppressCurrent, hookCode, wordParam, longParam);
    } catch (...) {
        requestFailOpen(RecoveryReason::InternalError);
        return CallNextHookEx(nullptr, hookCode, wordParam, longParam);
    }
}

bool decodeMouseMessage(
    const WPARAM wordParam,
    const MSLLHOOKSTRUCT& data,
    Action& action,
    MouseButton& button,
    bool& hasButton,
    LONG& eventData) noexcept {
    hasButton = true;
    eventData = 0;
    switch (wordParam) {
        case WM_LBUTTONDOWN:
            action = Action::Down;
            button = MouseButton::Left;
            return true;
        case WM_LBUTTONUP:
            action = Action::Up;
            button = MouseButton::Left;
            return true;
        case WM_RBUTTONDOWN:
            action = Action::Down;
            button = MouseButton::Right;
            return true;
        case WM_RBUTTONUP:
            action = Action::Up;
            button = MouseButton::Right;
            return true;
        case WM_MBUTTONDOWN:
            action = Action::Down;
            button = MouseButton::Middle;
            return true;
        case WM_MBUTTONUP:
            action = Action::Up;
            button = MouseButton::Middle;
            return true;
        case WM_XBUTTONDOWN:
        case WM_XBUTTONUP:
            action = wordParam == WM_XBUTTONDOWN ? Action::Down : Action::Up;
            button = HIWORD(data.mouseData) == XBUTTON1
                ? MouseButton::X1
                : MouseButton::X2;
            return true;
        case WM_MOUSEMOVE:
            hasButton = false;
            action = Action::Move;
            return true;
        case WM_MOUSEWHEEL:
            hasButton = false;
            action = Action::Wheel;
            eventData = static_cast<SHORT>(HIWORD(data.mouseData));
            return true;
        case WM_MOUSEHWHEEL:
            hasButton = false;
            action = Action::HorizontalWheel;
            eventData = static_cast<SHORT>(HIWORD(data.mouseData));
            return true;
        default:
            return false;
    }
}

LRESULT CALLBACK mouseHook(
    const int hookCode,
    const WPARAM wordParam,
    const LPARAM longParam) noexcept {
    if (hookCode < 0) {
        return CallNextHookEx(nullptr, hookCode, wordParam, longParam);
    }

    try {
        const auto* const data =
            reinterpret_cast<const MSLLHOOKSTRUCT*>(longParam);
        Action action = Action::Move;
        MouseButton button = MouseButton::Left;
        bool hasButton = false;
        LONG eventData = 0;
        if (!decodeMouseMessage(
                wordParam, *data, action, button, hasButton, eventData)) {
            return CallNextHookEx(nullptr, hookCode, wordParam, longParam);
        }

        const bool injected = (data->flags & LLMHF_INJECTED) != 0U;
        const Decision decision = hasButton
            ? g_policy.mouseButton(button, action == Action::Down, injected)
            : g_policy.mouseUnpaired(injected);

        const CapturedEvent event{
            Device::Mouse,
            action,
            hasButton ? static_cast<std::uint32_t>(button) : 0U,
            data->pt.x,
            data->pt.y,
            eventData,
            data->time,
            decision.capturedRemote,
            injected,
        };
        const bool queued = enqueueEvent(event);
        const bool suppressCurrent = queued && decision.suppress &&
            g_policyEnforced.load(std::memory_order_acquire);
        if (queued && hasButton) {
            publishPolicyStateAfterDecision();
        }
        return finishHookDecision(
            suppressCurrent, hookCode, wordParam, longParam);
    } catch (...) {
        requestFailOpen(RecoveryReason::InternalError);
        return CallNextHookEx(nullptr, hookCode, wordParam, longParam);
    }
}

void seedInputsAlreadyDown() noexcept {
    for (int key = 1; key < 256; ++key) {
        if ((GetAsyncKeyState(key) & 0x8000) != 0) {
            g_policy.seedLocalKeyDown(static_cast<std::uint32_t>(key));
        }
    }

    const std::array<std::pair<int, MouseButton>, 5> buttons{{
        {VK_LBUTTON, MouseButton::Left},
        {VK_RBUTTON, MouseButton::Right},
        {VK_MBUTTON, MouseButton::Middle},
        {VK_XBUTTON1, MouseButton::X1},
        {VK_XBUTTON2, MouseButton::X2},
    }};
    for (const auto& entry : buttons) {
        if ((GetAsyncKeyState(entry.first) & 0x8000) != 0) {
            g_policy.seedLocalMouseDown(entry.second);
        }
    }
}

void processHookControlMessage(const UINT message) noexcept {
    switch (message) {
        case kSetRemoteMessage:
            // Preserve pending ownership after a controlled LOCAL transition.
            // A prior fail-open has already disabled policy enforcement and
            // requires stale ownership to be discarded instead.
            if (!g_policyEnforced.load(std::memory_order_acquire)) {
                g_policy.failOpen();
            }
            seedInputsAlreadyDown();
            g_policy.enterRemote();
            g_policyEnforced.store(true, std::memory_order_release);
            publishState(
                Mode::Remote, true, g_policy.pendingRemoteOwnership());
            break;
        case kSetLocalMessage:
            if (!g_policyEnforced.load(std::memory_order_acquire)) {
                g_policy.failOpen();
                g_autoRenewLease.store(false, std::memory_order_release);
                publishState(Mode::Local, false, 0U);
                break;
            }
            g_policy.beginLocalDraining();
            g_autoRenewLease.store(false, std::memory_order_release);
            if (g_policy.mode() == Mode::LocalDraining) {
                g_drainingDeadline.store(
                    GetTickCount64() + absolute_control::kDrainingTimeoutMs,
                    std::memory_order_release);
                g_policyEnforced.store(true, std::memory_order_release);
                publishState(
                    Mode::LocalDraining,
                    true,
                    g_policy.pendingRemoteOwnership());
            } else {
                g_policyEnforced.store(false, std::memory_order_release);
                publishState(Mode::Local, false, 0U);
            }
            break;
        case kFailOpenMessage:
            g_policy.failOpen();
            g_policyEnforced.store(false, std::memory_order_release);
            g_autoRenewLease.store(false, std::memory_order_release);
            publishState(Mode::Local, false, 0U);
            break;
        default:
            break;
    }
}

void hookThreadMain() noexcept {
    g_hookThreadId = GetCurrentThreadId();
    MSG message{};
    (void)PeekMessageW(&message, nullptr, WM_USER, WM_USER, PM_NOREMOVE);

    g_keyboardHook = SetWindowsHookExW(
        WH_KEYBOARD_LL, keyboardHook, GetModuleHandleW(nullptr), 0);
    if (g_keyboardHook != nullptr) {
        g_mouseHook = SetWindowsHookExW(
            WH_MOUSE_LL, mouseHook, GetModuleHandleW(nullptr), 0);
    }

    const bool installed =
        g_keyboardHook != nullptr && g_mouseHook != nullptr;
    g_hooksInstalled.store(installed, std::memory_order_release);
    if (g_hookReadyEvent != nullptr) {
        (void)SetEvent(g_hookReadyEvent);
    }

    if (!installed) {
        g_policyEnforced.store(false, std::memory_order_release);
        if (g_keyboardHook != nullptr) {
            (void)UnhookWindowsHookEx(g_keyboardHook);
            g_keyboardHook = nullptr;
        }
        return;
    }

    while (g_running.load(std::memory_order_acquire)) {
        const BOOL result = GetMessageW(&message, nullptr, 0, 0);
        if (result == 0) {
            break;
        }
        if (result == -1) {
            requestFailOpen(RecoveryReason::HookLoopError);
            break;
        }

        if (message.message == kStopMessage) {
            break;
        }
        if (message.message >= kSetRemoteMessage &&
            message.message <= kFailOpenMessage) {
            processHookControlMessage(message.message);
            continue;
        }
        TranslateMessage(&message);
        DispatchMessageW(&message);
    }

    g_policyEnforced.store(false, std::memory_order_release);
    publishState(Mode::Local, false, 0U);
    if (g_mouseHook != nullptr) {
        (void)UnhookWindowsHookEx(g_mouseHook);
        g_mouseHook = nullptr;
    }
    if (g_keyboardHook != nullptr) {
        (void)UnhookWindowsHookEx(g_keyboardHook);
        g_keyboardHook = nullptr;
    }
    g_hooksInstalled.store(false, std::memory_order_release);
}

const char* actionName(const Action action) noexcept {
    switch (action) {
        case Action::Down:
            return "DOWN";
        case Action::Up:
            return "UP";
        case Action::Move:
            return "MOVE";
        case Action::Wheel:
            return "WHEEL";
        case Action::HorizontalWheel:
            return "HWHEEL";
        default:
            return "?";
    }
}

void reportEvent(const CapturedEvent& event) {
    // Mouse motion is aggregated by reporterThreadMain to avoid turning console
    // output into backpressure for the hook queue.
    std::ostringstream line;
    line << (event.capturedRemote ? "[REMOTO] " : "[LOCAL] ")
         << (event.injected ? "INYECTADO " : "FISICO ")
         << (event.device == Device::Keyboard ? "KEY " : "MOUSE ")
         << actionName(event.action);
    if (event.device == Device::Keyboard) {
        line << " vk=" << event.code;
    } else if (event.action == Action::Down || event.action == Action::Up) {
        line << " button=" << event.code;
    } else if (event.action == Action::Wheel ||
               event.action == Action::HorizontalWheel) {
        line << " delta=" << event.data;
    }
    printLine(line.str());
}

void reporterThreadMain() {
    std::uint64_t physicalMoves = 0;
    std::uint64_t injectedMoves = 0;
    std::uint64_t remoteMoves = 0;
    auto nextMoveReport =
        std::chrono::steady_clock::now() + std::chrono::milliseconds(250);
    std::uint64_t lastEscapeCount = 0;

    while (true) {
        bool drainedAny = false;
        CapturedEvent event{};
        while (g_events.tryPop(event)) {
            drainedAny = true;
            if (event.device == Device::Mouse &&
                event.action == Action::Move) {
                if (event.injected) {
                    ++injectedMoves;
                } else {
                    ++physicalMoves;
                }
                if (event.capturedRemote) {
                    ++remoteMoves;
                }
            } else {
                reportEvent(event);
            }
        }

        const auto now = std::chrono::steady_clock::now();
        if (now >= nextMoveReport &&
            (physicalMoves != 0U || injectedMoves != 0U)) {
            std::ostringstream line;
            line << "[MOUSE MOVE/250ms] fisicos=" << physicalMoves
                 << " remotos=" << remoteMoves
                 << " inyectados=" << injectedMoves;
            printLine(line.str());
            physicalMoves = 0;
            remoteMoves = 0;
            injectedMoves = 0;
            nextMoveReport = now + std::chrono::milliseconds(250);
        }

        const std::uint64_t escapeCount =
            g_escapeRecoveries.load(std::memory_order_acquire);
        if (escapeCount != lastEscapeCount) {
            lastEscapeCount = escapeCount;
            printLine(
                "*** ESCAPE FISICO: REMOTO -> LOCAL_DRAINING; press "
                "consumido y release reservado para consumo ***");
        }

        const RecoveryReason reason =
            g_recoveryReason.exchange(
                RecoveryReason::None, std::memory_order_acq_rel);
        if (reason != RecoveryReason::None) {
            std::ostringstream line;
            line << "*** FAIL-OPEN -> LOCAL: " << reasonName(reason)
                 << " ***";
            printLine(line.str());
        }

        if (!g_running.load(std::memory_order_acquire) && !drainedAny) {
            break;
        }
        std::this_thread::sleep_for(std::chrono::milliseconds(10));
    }
}

void leaseRenewalThreadMain() noexcept {
    while (g_running.load(std::memory_order_acquire)) {
        if (g_autoRenewLease.load(std::memory_order_acquire) &&
            publishedMode() == Mode::Remote) {
            g_leaseDeadline.store(
                GetTickCount64() + kLeaseDurationMs,
                std::memory_order_release);
        }
        std::this_thread::sleep_for(
            std::chrono::milliseconds(kLeaseRenewalMs));
    }
}

void watchdogThreadMain() noexcept {
    while (g_running.load(std::memory_order_acquire)) {
        const Mode mode = publishedMode();
        const WatchdogDecision decision = absolute_control::evaluateWatchdog(
            mode,
            GetTickCount64(),
            g_leaseDeadline.load(std::memory_order_acquire),
            g_drainingDeadline.load(std::memory_order_acquire));
        if (decision == WatchdogDecision::LeaseExpired) {
            requestFailOpen(RecoveryReason::LeaseExpired);
        } else if (decision == WatchdogDecision::DrainingExpired) {
            requestFailOpen(RecoveryReason::DrainingTimeout);
        }
        std::this_thread::sleep_for(std::chrono::milliseconds(25));
    }
}

bool waitForMode(const Mode desired, const DWORD timeoutMs) noexcept {
    const ULONGLONG deadline = GetTickCount64() + timeoutMs;
    while (GetTickCount64() < deadline) {
        if (publishedMode() == desired) {
            return true;
        }
        Sleep(10);
    }
    return publishedMode() == desired;
}

bool activateRemote(const bool renewLease) {
    printLine("REMOTO se activara en 3 segundos. Enfoque la app de prueba.");
    for (int remaining = 3; remaining > 0; --remaining) {
        printLine(std::to_string(remaining) + "...");
        std::this_thread::sleep_for(std::chrono::seconds(1));
    }

    g_autoRenewLease.store(renewLease, std::memory_order_release);
    g_leaseDeadline.store(
        GetTickCount64() + kLeaseDurationMs, std::memory_order_release);
    postToHookThread(kSetRemoteMessage);
    if (!waitForMode(Mode::Remote, 1000)) {
        requestFailOpen(RecoveryReason::InternalError);
        printLine("No se pudo activar REMOTO; se mantiene LOCAL.");
        return false;
    }

    printLine(
        renewLease
            ? "MODO REMOTO activo. Escape fisico restaura LOCAL."
            : "MODO REMOTO sin renovacion. El watchdog restaurara LOCAL "
              "en <= 1500 ms.");
    return true;
}

void runInjectedTest() {
    if (!activateRemote(true)) {
        return;
    }

    const std::uint64_t injectedBefore =
        g_injectedObserved.load(std::memory_order_acquire);
    const std::uint64_t remoteBefore =
        g_remotePhysicalObserved.load(std::memory_order_acquire);

    std::array<INPUT, 4> inputs{};
    inputs[0].type = INPUT_KEYBOARD;
    inputs[0].ki.wVk = VK_F24;
    inputs[0].ki.dwExtraInfo = kInjectedTestMarker;
    inputs[1] = inputs[0];
    inputs[1].ki.dwFlags = KEYEVENTF_KEYUP;
    inputs[2].type = INPUT_MOUSE;
    inputs[2].mi.dx = 1;
    inputs[2].mi.dwFlags = MOUSEEVENTF_MOVE;
    inputs[2].mi.dwExtraInfo = kInjectedTestMarker;
    inputs[3] = inputs[2];
    inputs[3].mi.dx = -1;

    SetLastError(ERROR_SUCCESS);
    const UINT sent = SendInput(
        static_cast<UINT>(inputs.size()),
        inputs.data(),
        sizeof(INPUT));
    const DWORD sendInputError = GetLastError();
    std::this_thread::sleep_for(std::chrono::milliseconds(300));

    const std::uint64_t injectedAfter =
        g_injectedObserved.load(std::memory_order_acquire);
    const std::uint64_t remoteAfter =
        g_remotePhysicalObserved.load(std::memory_order_acquire);
    std::ostringstream result;
    result << "inject-test: SendInput=" << sent << '/' << inputs.size()
           << ", GetLastError=" << sendInputError
           << ", observados como inyectados="
           << (injectedAfter - injectedBefore)
           << ", fisicos remotos adicionales="
           << (remoteAfter - remoteBefore);
    printLine(result.str());

    g_autoRenewLease.store(false, std::memory_order_release);
    postToHookThread(kSetLocalMessage);
    (void)waitForMode(Mode::Local, 1000);
    printLine("inject-test finalizado; modo LOCAL.");
}

void printHelp() {
    printLine("Comandos:");
    printLine("  remote         REMOTO con lease renovada cada 250 ms");
    printLine("  remote-timeout REMOTO sin renovar; fail-open en <= 1500 ms");
    printLine("  inject-test    REMOTO + SendInput(F24/mouse) + retorno LOCAL");
    printLine("  local          transicion controlada a LOCAL");
    printLine("  status         estado y contadores");
    printLine("  help           mostrar comandos");
    printLine("  quit           fail-open, quitar hooks y salir");
}

void printStatus() {
    const PublicState state = decodePublicState(
        g_publicState.load(std::memory_order_acquire));
    const char* suppression = "NO";
    if (state.enforced) {
        suppression = state.mode == Mode::Remote
            ? "SI"
            : state.mode == Mode::LocalDraining
                ? "LIMITADA"
                : "INCONSISTENTE";
    }

    std::ostringstream status;
    status << "modo="
           << modeName(state.mode)
           << " hooks="
           << (g_hooksInstalled.load(std::memory_order_acquire) ? "OK" : "NO")
           << " supresion_armada="
           << suppression
           << " ownership_remoto_pendiente="
           << state.pendingRemote
           << " auto_lease="
           << (g_autoRenewLease.load(std::memory_order_acquire) ? "SI" : "NO")
           << " eventos_remotos="
           << g_remotePhysicalObserved.load(std::memory_order_acquire)
           << " inyectados="
           << g_injectedObserved.load(std::memory_order_acquire);
    printLine(status.str());
}

BOOL WINAPI consoleControlHandler(const DWORD controlType) noexcept {
    switch (controlType) {
        case CTRL_C_EVENT:
        case CTRL_BREAK_EVENT:
        case CTRL_CLOSE_EVENT:
        case CTRL_LOGOFF_EVENT:
        case CTRL_SHUTDOWN_EVENT:
            requestFailOpen(RecoveryReason::ControllerClosed);
            g_running.store(false, std::memory_order_release);
            postToHookThread(kStopMessage);
            return TRUE;
        default:
            return FALSE;
    }
}

}  // namespace

int main() {
    g_hookReadyEvent = CreateEventW(nullptr, TRUE, FALSE, nullptr);
    if (g_hookReadyEvent == nullptr) {
        std::cerr << "No se pudo crear el evento de inicializacion." << std::endl;
        return 1;
    }

    (void)SetConsoleCtrlHandler(consoleControlHandler, TRUE);
    std::thread hookThread(hookThreadMain);
    const DWORD ready = WaitForSingleObject(g_hookReadyEvent, 5000);
    CloseHandle(g_hookReadyEvent);
    g_hookReadyEvent = nullptr;

    if (ready != WAIT_OBJECT_0 ||
        !g_hooksInstalled.load(std::memory_order_acquire)) {
        g_running.store(false, std::memory_order_release);
        postToHookThread(kStopMessage);
        hookThread.join();
        std::cerr
            << "No se pudieron instalar WH_KEYBOARD_LL y WH_MOUSE_LL. "
               "No se bloqueo entrada."
            << std::endl;
        return 1;
    }

    std::thread reporterThread(reporterThreadMain);
    std::thread leaseThread(leaseRenewalThreadMain);
    std::thread watchdogThread(watchdogThreadMain);

    printLine("Absolute Control - prototipo aislado de hooks Windows");
    printLine("Estado inicial: LOCAL. No hay red ni integracion Java.");
    printHelp();

    std::string command;
    while (g_running.load(std::memory_order_acquire)) {
        {
            const std::lock_guard<std::mutex> lock(g_outputMutex);
            std::cout << "> " << std::flush;
        }
        if (!std::getline(std::cin, command)) {
            requestFailOpen(RecoveryReason::ControllerClosed);
            break;
        }

        if (command == "remote") {
            (void)activateRemote(true);
        } else if (command == "remote-timeout") {
            (void)activateRemote(false);
        } else if (command == "inject-test") {
            runInjectedTest();
        } else if (command == "local") {
            g_autoRenewLease.store(false, std::memory_order_release);
            postToHookThread(kSetLocalMessage);
            (void)waitForMode(Mode::Local, 1000);
            printLine("MODO LOCAL solicitado.");
        } else if (command == "status") {
            printStatus();
        } else if (command == "help" || command == "?") {
            printHelp();
        } else if (command == "quit" || command == "exit") {
            break;
        } else if (!command.empty()) {
            printLine("Comando desconocido. Use help.");
        }
    }

    requestFailOpen(RecoveryReason::ControllerClosed);
    g_running.store(false, std::memory_order_release);
    postToHookThread(kStopMessage);

    hookThread.join();
    leaseThread.join();
    watchdogThread.join();
    reporterThread.join();
    (void)SetConsoleCtrlHandler(consoleControlHandler, FALSE);
    printLine("Hooks retirados. Salida en modo LOCAL/fail-open.");
    return 0;
}
