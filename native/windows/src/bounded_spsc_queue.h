#pragma once

#include <array>
#include <atomic>
#include <cstddef>

namespace absolute_control {

// Fixed-capacity single-producer/single-consumer queue. The hook thread is the
// only producer and the reporter thread is the only consumer.
template <typename T, std::size_t Capacity>
class BoundedSpscQueue final {
    static_assert(Capacity >= 2, "Capacity must leave room for a full marker");

public:
    [[nodiscard]] bool tryPush(const T& value) noexcept {
        const std::size_t head = head_.load(std::memory_order_relaxed);
        const std::size_t next = increment(head);
        if (next == tail_.load(std::memory_order_acquire)) {
            return false;
        }
        storage_[head] = value;
        head_.store(next, std::memory_order_release);
        return true;
    }

    [[nodiscard]] bool tryPop(T& value) noexcept {
        const std::size_t tail = tail_.load(std::memory_order_relaxed);
        if (tail == head_.load(std::memory_order_acquire)) {
            return false;
        }
        value = storage_[tail];
        tail_.store(increment(tail), std::memory_order_release);
        return true;
    }

private:
    [[nodiscard]] static constexpr std::size_t increment(
        const std::size_t index) noexcept {
        return (index + 1U) % Capacity;
    }

    std::array<T, Capacity> storage_{};
    std::atomic<std::size_t> head_{0};
    std::atomic<std::size_t> tail_{0};
};

}  // namespace absolute_control
