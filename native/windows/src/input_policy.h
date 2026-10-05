#pragma once

#include <array>
#include <cstddef>
#include <cstdint>

namespace absolute_control {

enum class Mode : std::uint8_t {
    Local,
    Remote,
    LocalDraining,
};

enum class Owner : std::uint8_t {
    None,
    Local,
    Remote,
};

enum class MouseButton : std::uint8_t {
    Left,
    Right,
    Middle,
    X1,
    X2,
    Count,
};

struct Decision {
    bool suppress = false;
    bool capturedRemote = false;
    bool emergencyEscape = false;
    bool injected = false;
    Owner owner = Owner::None;
};

// This class has no Windows dependencies so its transition rules can be tested
// without installing global hooks. It is owned and called only by the hook
// thread in the executable.
class InputPolicy final {
public:
    static constexpr std::uint32_t kEscapeVirtualKey = 0x1B;

    [[nodiscard]] Mode mode() const noexcept;

    // A normal mode transition preserves ownership. Releases therefore follow
    // the destination selected by their corresponding presses.
    void enterRemote() noexcept;
    void beginLocalDraining() noexcept;

    // Fail-open deliberately drops ownership: every subsequent event is sent
    // to Windows, even if a press was captured while REMOTE was active.
    void failOpen() noexcept;

    // Keys/buttons already physically down when REMOTE starts belong to LOCAL.
    void seedLocalKeyDown(std::uint32_t virtualKey) noexcept;
    void seedLocalMouseDown(MouseButton button) noexcept;

    [[nodiscard]] Decision keyboard(
        std::uint32_t virtualKey,
        bool pressed,
        bool injected) noexcept;
    [[nodiscard]] Decision mouseButton(
        MouseButton button,
        bool pressed,
        bool injected) noexcept;
    [[nodiscard]] Decision mouseUnpaired(bool injected) const noexcept;
    [[nodiscard]] std::size_t pendingRemoteOwnership() const noexcept;
    [[nodiscard]] bool escapeReleasePending() const noexcept;

private:
    static constexpr std::size_t kVirtualKeyCount = 256;
    static constexpr std::size_t kMouseButtonCount =
        static_cast<std::size_t>(MouseButton::Count);

    [[nodiscard]] static Decision decideOwned(Owner owner) noexcept;
    void finishDrainingIfComplete() noexcept;

    Mode mode_ = Mode::Local;
    std::array<Owner, kVirtualKeyCount> keyOwners_{};
    std::array<Owner, kMouseButtonCount> mouseOwners_{};
    std::size_t pendingRemoteCount_ = 0;
    bool escapeReleasePending_ = false;
};

}  // namespace absolute_control
