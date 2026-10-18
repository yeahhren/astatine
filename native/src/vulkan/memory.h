#pragma once

#include "core/common.h"
#include "core/vk.h"
#include "memory/range_allocator.h"

#include <memory>
#include <vector>

namespace at {
    class Device;

    struct MemoryBlock {
        VkDeviceMemory memory = VK_NULL_HANDLE;
        u32 type = 0;
        bool linear = false;
        u8 *mapped = nullptr;
        RangeAllocator ranges;

        MemoryBlock(VkDeviceMemory memory, u32 type, bool linear, u64 size, u8 *mapped)
            : memory(memory), type(type), linear(linear), mapped(mapped), ranges(size) {
        }
    };

    struct Allocation {
        MemoryBlock *block = nullptr;
        VkDeviceMemory memory = VK_NULL_HANDLE;
        u64 offset = 0;
        u64 size = 0;
        u8 *mapped = nullptr;
    };

    class MemoryAllocator {
    public:
        explicit MemoryAllocator(const Device &device) : device(device) {
        }

        ~MemoryAllocator();

        Allocation allocate(const VkMemoryRequirements &requirements, VkMemoryPropertyFlags required,
                            VkMemoryPropertyFlags preferred, bool linear);

        void release(const Allocation &allocation);

    private:
        static constexpr u64 BLOCK_SIZE = 64ull << 20;

        [[nodiscard]] VkDeviceMemory allocateMemory(u32 type, u64 size) const;

        u8 *map(VkDeviceMemory memory, u32 type) const;

        const Device &device;
        std::vector<std::unique_ptr<MemoryBlock> > blocks;
    };

    class GpuBuffer {
    public:
        GpuBuffer(const Device &device, MemoryAllocator &allocator, u64 size, VkBufferUsageFlags usage,
                  VkMemoryPropertyFlags required, VkMemoryPropertyFlags preferred = 0, bool transferShared = false);

        ~GpuBuffer();

        GpuBuffer(const GpuBuffer &) = delete;

        GpuBuffer &operator=(const GpuBuffer &) = delete;

        [[nodiscard]] u8 *mapped() const { return allocation.mapped; }

        VkBuffer handle = VK_NULL_HANDLE;
        u64 size;

    private:
        const Device &device;
        MemoryAllocator &allocator;
        Allocation allocation;
    };

    struct ImageShape {
        u32 width;
        u32 height;
        u32 depth = 1;
        u32 layers = 1;
        VkImageViewType viewType = VK_IMAGE_VIEW_TYPE_2D;
    };

    class GpuImage {
    public:
        GpuImage(const Device &device, MemoryAllocator &allocator, u32 width, u32 height, u32 levels, VkFormat format,
                 VkImageAspectFlags aspect, VkImageUsageFlags usage);

        GpuImage(const Device &device, MemoryAllocator &allocator, const ImageShape &shape, u32 levels, VkFormat format,
                 VkImageAspectFlags aspect, VkImageUsageFlags usage);

        ~GpuImage();

        GpuImage(const GpuImage &) = delete;

        GpuImage &operator=(const GpuImage &) = delete;

        [[nodiscard]] VkImageView createView(u32 levelCount) const;

        void destroyView(VkImageView target) const;

        VkImage handle = VK_NULL_HANDLE;
        VkImageView view = VK_NULL_HANDLE;
        u32 width;
        u32 height;
        u32 depth = 1;
        u32 layers = 1;
        u32 levels;
        VkFormat format;
        VkImageAspectFlags aspect;
        VkImageViewType viewType = VK_IMAGE_VIEW_TYPE_2D;

    private:
        const Device &device;
        MemoryAllocator &allocator;
        Allocation allocation;
    };
} // namespace at
