#pragma once

#include "core/common.h"
#include "core/vk.h"
#include "vulkan/memory.h"

#include <functional>
#include <memory>
#include <vector>

namespace at {
    class Device;

    class FrameResources {
    public:
        explicit FrameResources(const Device &device);

        ~FrameResources();

        void defer(std::function<void()> release) { releases.push_back(std::move(release)); }

        void await();

        void reset() const;

        VkCommandPool pool = VK_NULL_HANDLE;
        VkCommandBuffer setup = VK_NULL_HANDLE;
        VkCommandBuffer main = VK_NULL_HANDLE;
        VkFence fence = VK_NULL_HANDLE;
        VkSemaphore imageAvailable = VK_NULL_HANDLE;

    private:
        void runReleases();

        const Device &device;
        std::vector<std::function<void()> > releases;
    };

    class FrameStream {
    public:
        FrameStream(const Device &device, MemoryAllocator &allocator, u64 frameSize, u32 frames,
                    VkBufferUsageFlags usage, u64 slack = 0);

        void begin(u32 frame) {
            cursor = frame * frameSize;
            end = cursor + frameSize;
        }

        [[nodiscard]] u64 remaining(u64 alignment) const {
            u64 aligned = alignUp(cursor, alignment);
            return aligned > end ? 0 : end - aligned;
        }

        static constexpr u64 FAILED = ~0ull;

        u64 allocate(u64 size, u64 alignment) {
            u64 aligned = alignUp(cursor, alignment);
            if (aligned + size > end) return FAILED;
            cursor = aligned + size;
            return aligned;
        }

        [[nodiscard]] u8 *address(u64 offset) const { return base + offset; }
        [[nodiscard]] VkBuffer handle() const { return buffer->handle; }

        u64 frameSize;
        std::unique_ptr<GpuBuffer> buffer;

    private:
        u8 *base;
        u64 cursor = 0;
        u64 end = 0;
    };
} // namespace at
