#pragma once

#include "core/common.h"
#include "core/vk.h"

#include <array>
#include <atomic>
#include <mutex>

namespace at {
    class Device;

    struct StagingSpan {
        u32 block = ~0u;
        u64 offset = 0;
        u64 size = 0;
        u8 *address = nullptr;

        [[nodiscard]] bool valid() const { return block != ~0u; }
    };

    class StagingPool {
    public:
        static constexpr u64 BLOCK_SIZE = 8ull << 20;
        static constexpr u32 MAX_BLOCKS = 32;

        explicit StagingPool(const Device &device);

        ~StagingPool();

        StagingPool(const StagingPool &) = delete;

        StagingPool &operator=(const StagingPool &) = delete;

        StagingSpan allocate(u64 size);

        void release(const StagingSpan &span);

        [[nodiscard]] VkBuffer handle(u32 block) const { return blocks[block].buffer; }

    private:
        struct Block {
            VkBuffer buffer = VK_NULL_HANDLE;
            VkDeviceMemory memory = VK_NULL_HANDLE;
            u8 *mapped = nullptr;
            u64 used = 0;
            u32 users = 0;
        };

        bool grow();

        const Device &device;
        std::mutex lock;
        std::array<Block, MAX_BLOCKS> blocks{};
        u32 count = 0;
    };

    class StagedData {
    public:
        StagedData() = default;

        StagedData(StagingPool *pool, StagingSpan span) : pool(pool), span(span) {
        }

        StagedData(StagedData &&other) noexcept : pool(other.pool), span(other.span) {
            other.pool = nullptr;
        }

        StagedData &operator=(StagedData &&other) noexcept {
            if (this != &other) {
                reset();
                pool = other.pool;
                span = other.span;
                other.pool = nullptr;
            }
            return *this;
        }

        StagedData(const StagedData &) = delete;

        StagedData &operator=(const StagedData &) = delete;

        ~StagedData() { reset(); }

        [[nodiscard]] bool valid() const { return pool != nullptr; }
        [[nodiscard]] const StagingSpan &get() const { return span; }

        StagingSpan release() {
            pool = nullptr;
            return span;
        }

        void reset() {
            if (pool) pool->release(span);
            pool = nullptr;
        }

    private:
        StagingPool *pool = nullptr;
        StagingSpan span;
    };
} // namespace at
