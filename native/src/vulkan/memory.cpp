#include "vulkan/memory.h"

#include "core/error.h"
#include "vulkan/device.h"

namespace at {
    MemoryAllocator::~MemoryAllocator() {
        for (auto &block: blocks) vkFreeMemory(device.handle, block->memory, nullptr);
        blocks.clear();
    }

    Allocation MemoryAllocator::allocate(const VkMemoryRequirements &requirements, VkMemoryPropertyFlags required,
                                         VkMemoryPropertyFlags preferred, bool linear) {
        u32 type = device.memoryType(requirements.memoryTypeBits, required, preferred);
        u64 size = requirements.size;
        u64 alignment = requirements.alignment;
        if (size >= BLOCK_SIZE / 2) {
            VkDeviceMemory memory = allocateMemory(type, size);
            return Allocation{.block = nullptr, .memory = memory, .offset = 0, .size = size, .mapped = map(memory, type)};
        }
        for (auto &block: blocks) {
            if (block->type != type || block->linear != linear) continue;
            if (u64 offset = block->ranges.allocate(size, alignment); offset != RangeAllocator::FAILED) {
                return Allocation{
                    .block = block.get(), .memory = block->memory, .offset = offset, .size = size, .mapped = block->mapped ? block->mapped + offset : nullptr
                };
            }
        }
        VkDeviceMemory memory = allocateMemory(type, BLOCK_SIZE);
        auto &block = blocks.emplace_back(
            std::make_unique<MemoryBlock>(memory, type, linear, BLOCK_SIZE, map(memory, type)));
        u64 offset = block->ranges.allocate(size, alignment);
        return Allocation{block.get(), block->memory, offset, size, block->mapped ? block->mapped + offset : nullptr};
    }

    void MemoryAllocator::release(const Allocation &allocation) {
        MemoryBlock *block = allocation.block;
        if (!block) {
            vkFreeMemory(device.handle, allocation.memory, nullptr);
            return;
        }
        block->ranges.release(allocation.offset, allocation.size);
        if (block->ranges.used == 0) {
            vkFreeMemory(device.handle, block->memory, nullptr);
            for (auto it = blocks.begin(); it != blocks.end(); ++it) {
                if (it->get() == block) {
                    blocks.erase(it);
                    break;
                }
            }
        }
    }

    VkDeviceMemory MemoryAllocator::allocateMemory(u32 type, u64 size) const {
        VkMemoryAllocateInfo info{.sType = VK_STRUCTURE_TYPE_MEMORY_ALLOCATE_INFO};
        info.allocationSize = size;
        info.memoryTypeIndex = type;
        VkDeviceMemory memory;
        check(vkAllocateMemory(device.handle, &info, nullptr, &memory), "vkAllocateMemory");
        return memory;
    }

    u8 *MemoryAllocator::map(VkDeviceMemory memory, u32 type) const {
        if (!(device.memoryFlags(type) & VK_MEMORY_PROPERTY_HOST_VISIBLE_BIT)) return nullptr;
        void *pointer = nullptr;
        check(vkMapMemory(device.handle, memory, 0, VK_WHOLE_SIZE, 0, &pointer), "vkMapMemory");
        return static_cast<u8 *>(pointer);
    }

    GpuBuffer::GpuBuffer(const Device &device, MemoryAllocator &allocator, u64 size, VkBufferUsageFlags usage,
                         VkMemoryPropertyFlags required, VkMemoryPropertyFlags preferred, bool transferShared)
        : size(size), device(device), allocator(allocator) {
        VkBufferCreateInfo info{.sType = VK_STRUCTURE_TYPE_BUFFER_CREATE_INFO};
        info.size = size;
        info.usage = usage;
        info.sharingMode = VK_SHARING_MODE_EXCLUSIVE;
        u32 families[2] = {device.queueFamily, device.transferFamily};
        if (transferShared && device.transferQueue) {
            info.sharingMode = VK_SHARING_MODE_CONCURRENT;
            info.queueFamilyIndexCount = 2;
            info.pQueueFamilyIndices = families;
        }
        check(vkCreateBuffer(device.handle, &info, nullptr, &handle), "vkCreateBuffer");
        VkMemoryRequirements requirements;
        vkGetBufferMemoryRequirements(device.handle, handle, &requirements);
        allocation = allocator.allocate(requirements, required, preferred, true);
        check(vkBindBufferMemory(device.handle, handle, allocation.memory, allocation.offset), "vkBindBufferMemory");
    }

    GpuBuffer::~GpuBuffer() {
        vkDestroyBuffer(device.handle, handle, nullptr);
        allocator.release(allocation);
    }

    GpuImage::GpuImage(const Device &device, MemoryAllocator &allocator, u32 width, u32 height, u32 levels,
                       VkFormat format,
                       VkImageAspectFlags aspect, VkImageUsageFlags usage)
        : width(width), height(height), levels(levels), format(format), aspect(aspect), device(device),
          allocator(allocator) {
        VkImageCreateInfo info{.sType = VK_STRUCTURE_TYPE_IMAGE_CREATE_INFO};
        info.imageType = VK_IMAGE_TYPE_2D;
        info.format = format;
        info.extent = {.width = width, .height = height, .depth = 1};
        info.mipLevels = levels;
        info.arrayLayers = 1;
        info.samples = VK_SAMPLE_COUNT_1_BIT;
        info.tiling = VK_IMAGE_TILING_OPTIMAL;
        info.usage = usage;
        info.sharingMode = VK_SHARING_MODE_EXCLUSIVE;
        info.initialLayout = VK_IMAGE_LAYOUT_UNDEFINED;
        check(vkCreateImage(device.handle, &info, nullptr, &handle), "vkCreateImage");
        VkMemoryRequirements requirements;
        vkGetImageMemoryRequirements(device.handle, handle, &requirements);
        allocation = allocator.allocate(requirements, 0, VK_MEMORY_PROPERTY_DEVICE_LOCAL_BIT, false);
        check(vkBindImageMemory(device.handle, handle, allocation.memory, allocation.offset), "vkBindImageMemory");
        view = createView(levels);
    }

    GpuImage::~GpuImage() {
        vkDestroyImageView(device.handle, view, nullptr);
        vkDestroyImage(device.handle, handle, nullptr);
        allocator.release(allocation);
    }

    VkImageView GpuImage::createView(u32 levelCount) const {
        VkImageViewCreateInfo info{.sType = VK_STRUCTURE_TYPE_IMAGE_VIEW_CREATE_INFO};
        info.image = handle;
        info.viewType = VK_IMAGE_VIEW_TYPE_2D;
        info.format = format;
        info.subresourceRange = {.aspectMask = aspect, .baseMipLevel = 0, .levelCount = levelCount, .baseArrayLayer = 0, .layerCount = 1};
        VkImageView created;
        check(vkCreateImageView(device.handle, &info, nullptr, &created), "vkCreateImageView");
        return created;
    }

    void GpuImage::destroyView(VkImageView target) const {
        vkDestroyImageView(device.handle, target, nullptr);
    }
} // namespace at
