#pragma once

#include <cstddef>
#include <cstdint>
#include <cstring>

namespace at {
    using u8 = std::uint8_t;
    using u16 = std::uint16_t;
    using u32 = std::uint32_t;
    using u64 = std::uint64_t;
    using i8 = std::int8_t;
    using i16 = std::int16_t;
    using i32 = std::int32_t;
    using i64 = std::int64_t;

#if defined(_MSC_VER) && !defined(__clang__)
#define AT_INLINE __forceinline
#define AT_NOINLINE __declspec(noinline)
#define AT_LIKELY(x) (x)
#define AT_UNLIKELY(x) (x)
#else
#define AT_INLINE inline __attribute__((always_inline))
#define AT_NOINLINE __attribute__((noinline))
#define AT_LIKELY(x) __builtin_expect(!!(x), 1)
#define AT_UNLIKELY(x) __builtin_expect(!!(x), 0)
#endif

    constexpr u64 alignUp(const u64 value, const u64 alignment) {
        return (value + alignment - 1) / alignment * alignment;
    }

    template<typename T>
    AT_INLINE T load(const void *address) {
        T value;
        std::memcpy(&value, address, sizeof(T));
        return value;
    }

    template<typename T>
    AT_INLINE

    void store(void *address, T value) {
        std::memcpy(address, &value, sizeof(T));
    }

    template<typename T>
    AT_INLINE T *pointer(const i64 address) {
        return reinterpret_cast<T *>(static_cast<std::uintptr_t>(address));
    }

    template<typename T>
    AT_INLINE i64 handle(T *object) {
        return static_cast<i64>(reinterpret_cast<std::uintptr_t>(object));
    }

    template<typename T>
    constexpr T clamp(T value, T low, T high) {
        return value < low ? low : (value > high ? high : value);
    }

    template<typename T>
    struct Span {
        T *data = nullptr;
        std::size_t size = 0;

        T *begin() const { return data; }
        T *end() const { return data + size; }
        T &operator[](std::size_t index) const { return data[index]; }
        [[nodiscard]] bool empty() const { return size == 0; }
    };
} // namespace at
