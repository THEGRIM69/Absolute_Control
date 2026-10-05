#include "input_policy.h"

#include <algorithm>

namespace absolute_control {

Mode InputPolicy::mode() const noexcept {
    return mode_;
}

void InputPolicy::enterRemote() noexcept {
    mode_ = Mode::Remote;
}

void InputPolicy::beginLocalDraining() noexcept {
    mode_ = pendingRemoteOwnership() != 0U || escapeReleasePending_
        ? Mode::LocalDraining
        : Mode::Local;
}

void InputPolicy::failOpen() noexcept {
    mode_ = Mode::Local;
    std::fill(keyOwners_.begin(), keyOwners_.end(), Owner::None);
    std::fill(mouseOwners_.begin(), mouseOwners_.end(), Owner::None);
    pendingRemoteCount_ = 0;
    escapeReleasePending_ = false;
}

void InputPolicy::seedLocalKeyDown(const std::uint32_t virtualKey) noexcept {
    if (virtualKey < keyOwners_.size() && keyOwners_[virtualKey] == Owner::None) {
        keyOwners_[virtualKey] = Owner::Local;
    }
}

void InputPolicy::seedLocalMouseDown(const MouseButton button) noexcept {
    const auto index = static_cast<std::size_t>(button);
    if (index < mouseOwners_.size() && mouseOwners_[index] == Owner::None) {
        mouseOwners_[index] = Owner::Local;
    }
}

Decision InputPolicy::decideOwned(const Owner owner) noexcept {
    const bool remote = owner == Owner::Remote;
    return Decision{remote, remote, false, false, owner};
}

Decision InputPolicy::keyboard(
    const std::uint32_t virtualKey,
    const bool pressed,
    const bool injected) noexcept {
    if (injected) {
        return Decision{false, false, false, true, Owner::None};
    }

    if (virtualKey >= keyOwners_.size()) {
        return {};
    }

    if (virtualKey == kEscapeVirtualKey) {
        if (pressed && mode_ == Mode::Remote) {
            mode_ = Mode::LocalDraining;
            if (keyOwners_[virtualKey] != Owner::Remote) {
                ++pendingRemoteCount_;
            }
            keyOwners_[virtualKey] = Owner::Remote;
            escapeReleasePending_ = true;
            return Decision{true, true, true, false, Owner::Remote};
        }

        if (!pressed && escapeReleasePending_) {
            if (keyOwners_[virtualKey] == Owner::Remote &&
                pendingRemoteCount_ != 0U) {
                --pendingRemoteCount_;
            }
            keyOwners_[virtualKey] = Owner::None;
            escapeReleasePending_ = false;
            const Decision decision{
                true, true, false, false, Owner::Remote};
            finishDrainingIfComplete();
            return decision;
        }
    }

    Owner& owner = keyOwners_[virtualKey];
    if (pressed) {
        if (owner == Owner::None) {
            owner = mode_ == Mode::Remote ? Owner::Remote : Owner::Local;
            if (owner == Owner::Remote) {
                ++pendingRemoteCount_;
            }
        }
        return decideOwned(owner);
    }

    // An orphan release is allowed. It may correspond to a key held before the
    // helper started observing input.
    if (owner == Owner::None) {
        return {};
    }

    const Decision decision = decideOwned(owner);
    if (owner == Owner::Remote && pendingRemoteCount_ != 0U) {
        --pendingRemoteCount_;
    }
    owner = Owner::None;
    finishDrainingIfComplete();
    return decision;
}

Decision InputPolicy::mouseButton(
    const MouseButton button,
    const bool pressed,
    const bool injected) noexcept {
    if (injected) {
        return Decision{false, false, false, true, Owner::None};
    }

    const auto index = static_cast<std::size_t>(button);
    if (index >= mouseOwners_.size()) {
        return {};
    }

    Owner& owner = mouseOwners_[index];
    if (pressed) {
        if (owner == Owner::None) {
            owner = mode_ == Mode::Remote ? Owner::Remote : Owner::Local;
            if (owner == Owner::Remote) {
                ++pendingRemoteCount_;
            }
        }
        return decideOwned(owner);
    }

    if (owner == Owner::None) {
        return {};
    }

    const Decision decision = decideOwned(owner);
    if (owner == Owner::Remote && pendingRemoteCount_ != 0U) {
        --pendingRemoteCount_;
    }
    owner = Owner::None;
    finishDrainingIfComplete();
    return decision;
}

Decision InputPolicy::mouseUnpaired(const bool injected) const noexcept {
    if (injected) {
        return Decision{false, false, false, true, Owner::None};
    }

    if (mode_ == Mode::Remote) {
        return Decision{true, true, false, false, Owner::Remote};
    }
    return {};
}

std::size_t InputPolicy::pendingRemoteOwnership() const noexcept {
    return pendingRemoteCount_;
}

bool InputPolicy::escapeReleasePending() const noexcept {
    return escapeReleasePending_;
}

void InputPolicy::finishDrainingIfComplete() noexcept {
    if (mode_ == Mode::LocalDraining &&
        pendingRemoteOwnership() == 0U &&
        !escapeReleasePending_) {
        mode_ = Mode::Local;
    }
}

}  // namespace absolute_control
