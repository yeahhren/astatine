#include "vulkan/frame.h"

#include "core/error.h"
#include "vulkan/device.h"

namespace at {
    FrameResources::FrameResources(const Device &device) : device(device) {
        VkCommandPoolCreateInfo poolInfo{.sType = VK_STRUCTURE_TYPE_COMMAND_POOL_CREATE_INFO};
        poolInfo.flags = VK_COMMAND_POOL_CREATE_TRANSIENT_BIT;
        poolInfo.queueFamilyIndex = device.queueFamily;
        check(vkCreateCommandPool(device.handle, &poolInfo, nullptr, &pool), "vkCreateCommandPool");

        VkCommandBufferAllocateInfo bufferInfo{.sType = VK_STRUCTURE_TYPE_COMMAND_BUFFER_ALLOCATE_INFO};
        bufferInfo.commandPool = pool;
        bufferInfo.level = VK_COMMAND_BUFFER_LEVEL_PRIMARY;
        bufferInfo.commandBufferCount = 2;
        VkCommandBuffer buffers[2];
        check(vkAllocateCommandBuffers(device.handle, &bufferInfo, buffers), "vkAllocateCommandBuffers");
        setup = buffers[0];
        main = buffers[1];

        VkFenceCreateInfo fenceInfo{.sType = VK_STRUCTURE_TYPE_FENCE_CREATE_INFO};
        fenceInfo.flags = VK_FENCE_CREATE_SIGNALED_BIT;
        check(vkCreateFence(device.handle, &fenceInfo, nullptr, &fence), "vkCreateFence");

        VkSemaphoreCreateInfo semaphoreInfo{.sType = VK_STRUCTURE_TYPE_SEMAPHORE_CREATE_INFO};
        check(vkCreateSemaphore(device.handle, &semaphoreInfo, nullptr, &imageAvailable), "vkCreateSemaphore");

        if (device.timestampMask) {
            VkQueryPoolCreateInfo queryInfo{.sType = VK_STRUCTURE_TYPE_QUERY_POOL_CREATE_INFO};
            queryInfo.queryType = VK_QUERY_TYPE_TIMESTAMP;
            queryInfo.queryCount = 2;
            check(vkCreateQueryPool(device.handle, &queryInfo, nullptr, &timestamps), "vkCreateQueryPool");
        }
    }

    FrameResources::~FrameResources() {
        runReleases();
        if (timestamps) vkDestroyQueryPool(device.handle, timestamps, nullptr);
        vkDestroySemaphore(device.handle, imageAvailable, nullptr);
        vkDestroyFence(device.handle, fence, nullptr);
        vkDestroyCommandPool(device.handle, pool, nullptr);
    }

    void FrameResources::await() {
        check(vkWaitForFences(device.handle, 1, &fence, VK_TRUE, ~0ull), "vkWaitForFences");
        if (timed) {
            timed = false;
            u64 values[2];
            if (vkGetQueryPoolResults(device.handle, timestamps, 0, 2, sizeof(values), values, sizeof(u64),
                                      VK_QUERY_RESULT_64_BIT) == VK_SUCCESS) {
                u64 ticks = (values[1] - values[0]) & device.timestampMask;
                device.gpuNanos += static_cast<u64>(static_cast<double>(ticks) * device.timestampPeriod);
            }
        }
        runReleases();
    }

    void FrameResources::reset() const {
        check(vkResetFences(device.handle, 1, &fence), "vkResetFences");
        check(vkResetCommandPool(device.handle, pool, 0), "vkResetCommandPool");
    }

    void FrameResources::runReleases() {
        std::vector<std::function<void()> > pending;
        pending.swap(releases);
        for (auto &release: pending) release();
    }

    FrameStream::FrameStream(const Device &device, MemoryAllocator &allocator, u64 frameSize, u32 frames,
                             VkBufferUsageFlags usage, u64 slack)
        : frameSize(frameSize) {
        buffer = std::make_unique<GpuBuffer>(device, allocator, frameSize * frames + slack, usage,
                                             VK_MEMORY_PROPERTY_HOST_VISIBLE_BIT | VK_MEMORY_PROPERTY_HOST_COHERENT_BIT,
                                             VK_MEMORY_PROPERTY_DEVICE_LOCAL_BIT);
        base = buffer->mapped();
    }
} // namespace at
