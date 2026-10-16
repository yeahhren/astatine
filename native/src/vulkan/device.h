#pragma once

#include "core/common.h"
#include "core/vk.h"

#include <string>

namespace at {
    class Instance;

    class Device {
    public:
        Device(const Instance &instance, VkSurfaceKHR surface);

        ~Device();

        Device(const Device &) = delete;

        Device &operator=(const Device &) = delete;

        u32 memoryType(u32 typeBits, VkMemoryPropertyFlags required, VkMemoryPropertyFlags preferred = 0) const;

        VkMemoryPropertyFlags memoryFlags(u32 type) const { return memory.memoryTypes[type].propertyFlags; }
        void waitIdle() const { vkDeviceWaitIdle(handle); }

        VkPhysicalDevice physical = VK_NULL_HANDLE;
        VkDevice handle = VK_NULL_HANDLE;
        VkQueue queue = VK_NULL_HANDLE;
        u32 queueFamily = 0;
        std::string name;
        u32 driverVersion = 0;
        u32 apiVersion = 0;
        u32 vendorId = 0;
        VkFormat depthFormat = VK_FORMAT_UNDEFINED;
        VkImageAspectFlags depthAspect = 0;
        bool wideLines = false;
        bool logicOp = false;
        bool multiDrawIndirect = false;
        u32 maxDrawIndirectCount = 1;
        bool bindless = false;
        bool interop = false;
        u8 luid[8] = {};
        u32 bindlessCapacity = 0;
        float lineWidthMax = 1.0f;
        u32 maxImageDimension = 0;
        float maxSamplerLodBias = 0.0f;
        u64 uniformAlignment = 256;
        u64 storageAlignment = 256;
        double timestampPeriod = 0.0;
        u64 timestampMask = 0;
        mutable u64 gpuNanos = 0;
        VkPhysicalDeviceMemoryProperties memory{};

    private:
        [[nodiscard]] bool supportsDepth(VkFormat format) const;
    };
} // namespace at
