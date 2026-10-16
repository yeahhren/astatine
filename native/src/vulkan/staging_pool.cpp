#include "vulkan/staging_pool.h"

#include "core/error.h"
#include "vulkan/device.h"

namespace at {
    StagingPool::StagingPool(const Device &device) : device(device) {
    }

    StagingPool::~StagingPool() {
        for (u32 i = 0; i < count; i++) {
            Block &block = blocks[i];
            if (block.mapped) vkUnmapMemory(device.handle, block.memory);
            vkDestroyBuffer(device.handle, block.buffer, nullptr);
            vkFreeMemory(device.handle, block.memory, nullptr);
        }
    }

    bool StagingPool::grow() {
        if (count >= MAX_BLOCKS) return false;
        Block &block = blocks[count];
        VkBufferCreateInfo info{.sType = VK_STRUCTURE_TYPE_BUFFER_CREATE_INFO};
        info.size = BLOCK_SIZE;
        info.usage = VK_BUFFER_USAGE_TRANSFER_SRC_BIT;
        info.sharingMode = VK_SHARING_MODE_EXCLUSIVE;
        if (vkCreateBuffer(device.handle, &info, nullptr, &block.buffer) != VK_SUCCESS) return false;
        VkMemoryRequirements requirements;
        vkGetBufferMemoryRequirements(device.handle, block.buffer, &requirements);
        VkMemoryAllocateInfo allocate{.sType = VK_STRUCTURE_TYPE_MEMORY_ALLOCATE_INFO};
        allocate.allocationSize = requirements.size;
        allocate.memoryTypeIndex = device.memoryType(requirements.memoryTypeBits,
                                                     VK_MEMORY_PROPERTY_HOST_VISIBLE_BIT |
                                                     VK_MEMORY_PROPERTY_HOST_COHERENT_BIT);
        if (vkAllocateMemory(device.handle, &allocate, nullptr, &block.memory) != VK_SUCCESS) {
            vkDestroyBuffer(device.handle, block.buffer, nullptr);
            block = Block{};
            return false;
        }
        void *mapped = nullptr;
        if (vkBindBufferMemory(device.handle, block.buffer, block.memory, 0) != VK_SUCCESS ||
            vkMapMemory(device.handle, block.memory, 0, VK_WHOLE_SIZE, 0, &mapped) != VK_SUCCESS) {
            vkDestroyBuffer(device.handle, block.buffer, nullptr);
            vkFreeMemory(device.handle, block.memory, nullptr);
            block = Block{};
            return false;
        }
        block.mapped = static_cast<u8 *>(mapped);
        count++;
        return true;
    }

    StagingSpan StagingPool::allocate(u64 size) {
        size = alignUp(size, 16);
        if (size == 0 || size > BLOCK_SIZE) return {};
        std::lock_guard guard(lock);
        for (u32 attempt = 0; attempt < 2; attempt++) {
            for (u32 i = 0; i < count; i++) {
                Block &block = blocks[i];
                if (block.used + size > BLOCK_SIZE) continue;
                StagingSpan span{.block = i, .offset = block.used, .size = size, .address = block.mapped + block.used};
                block.used += size;
                block.users++;
                return span;
            }
            if (!grow()) break;
        }
        return {};
    }

    void StagingPool::release(const StagingSpan &span) {
        if (!span.valid()) return;
        std::lock_guard guard(lock);
        Block &block = blocks[span.block];
        if (block.users > 0 && --block.users == 0) block.used = 0;
    }
} // namespace at
