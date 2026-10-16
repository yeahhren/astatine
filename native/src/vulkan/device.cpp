#include "vulkan/device.h"

#include "core/error.h"
#include "vulkan/instance.h"

#include <algorithm>
#include <vector>

namespace at {
    static constexpr u32 BINDLESS_CAPACITY = 8192;
    static constexpr u32 MIN_BINDLESS_CAPACITY = 1024;

    static bool hasExtension(const VkPhysicalDevice device, const char *name) {
        uint32_t count = 0;
        vkEnumerateDeviceExtensionProperties(device, nullptr, &count, nullptr);
        std::vector<VkExtensionProperties> extensions(count);
        vkEnumerateDeviceExtensionProperties(device, nullptr, &count, extensions.data());
        for (const auto &[extensionName, specVersion]: extensions) {
            if (std::strcmp(extensionName, name) == 0) return true;
        }
        return false;
    }

    static int presentFamily(VkPhysicalDevice device, VkSurfaceKHR surface) {
        uint32_t count = 0;
        vkGetPhysicalDeviceQueueFamilyProperties(device, &count, nullptr);
        std::vector<VkQueueFamilyProperties> families(count);
        vkGetPhysicalDeviceQueueFamilyProperties(device, &count, families.data());
        for (uint32_t i = 0; i < count; i++) {
            if (!(families[i].queueFlags & VK_QUEUE_GRAPHICS_BIT)) continue;
            VkBool32 supported = VK_FALSE;
            vkGetPhysicalDeviceSurfaceSupportKHR(device, i, surface, &supported);
            if (supported) return static_cast<int>(i);
        }
        return -1;
    }

    static VkPhysicalDevice select(const Instance &instance, VkSurfaceKHR surface) {
        uint32_t count = 0;
        check(vkEnumeratePhysicalDevices(instance.handle, &count, nullptr), "vkEnumeratePhysicalDevices");
        std::vector<VkPhysicalDevice> devices(count);
        check(vkEnumeratePhysicalDevices(instance.handle, &count, devices.data()), "vkEnumeratePhysicalDevices");
        VkPhysicalDevice best = VK_NULL_HANDLE;
        int bestScore = -1;
        for (VkPhysicalDevice candidate: devices) {
            if (presentFamily(candidate, surface) < 0 || !hasExtension(candidate, VK_KHR_SWAPCHAIN_EXTENSION_NAME))
                continue;
            VkPhysicalDeviceProperties properties;
            vkGetPhysicalDeviceProperties(candidate, &properties);
            int score = 0;
            switch (properties.deviceType) {
                case VK_PHYSICAL_DEVICE_TYPE_DISCRETE_GPU: score = 3;
                    break;
                case VK_PHYSICAL_DEVICE_TYPE_INTEGRATED_GPU: score = 2;
                    break;
                case VK_PHYSICAL_DEVICE_TYPE_VIRTUAL_GPU: score = 1;
                    break;
                default: break;
            }
            if (score > bestScore) {
                best = candidate;
                bestScore = score;
            }
        }
        if (!best) fail("No Vulkan device can present to the window");
        return best;
    }

    Device::Device(const Instance &instance, VkSurfaceKHR surface) {
        physical = select(instance, surface);
        queueFamily = static_cast<u32>(presentFamily(physical, surface));

        VkPhysicalDeviceProperties properties;
        vkGetPhysicalDeviceProperties(physical, &properties);
        const VkPhysicalDeviceLimits &limits = properties.limits;
        name = properties.deviceName;
        driverVersion = properties.driverVersion;
        apiVersion = properties.apiVersion;
        vendorId = properties.vendorID;
        maxImageDimension = limits.maxImageDimension2D;
        maxSamplerLodBias = limits.maxSamplerLodBias;
        uniformAlignment = std::max<u64>(limits.minUniformBufferOffsetAlignment, 16);
        storageAlignment = std::max<u64>(limits.minStorageBufferOffsetAlignment, 16);
        vkGetPhysicalDeviceMemoryProperties(physical, &memory);
        {
            uint32_t count = 0;
            vkGetPhysicalDeviceQueueFamilyProperties(physical, &count, nullptr);
            std::vector<VkQueueFamilyProperties> families(count);
            vkGetPhysicalDeviceQueueFamilyProperties(physical, &count, families.data());
            u32 bits = families[queueFamily].timestampValidBits;
            if (bits > 0 && limits.timestampPeriod > 0.0f) {
                timestampPeriod = limits.timestampPeriod;
                timestampMask = bits >= 64 ? ~0ull : (1ull << bits) - 1;
            }
        }

        VkPhysicalDeviceFeatures supported;
        vkGetPhysicalDeviceFeatures(physical, &supported);
        wideLines = supported.wideLines;
        logicOp = supported.logicOp;
        lineWidthMax = wideLines ? limits.lineWidthRange[1] : 1.0f;
        multiDrawIndirect = supported.multiDrawIndirect && supported.drawIndirectFirstInstance;
        maxDrawIndirectCount = multiDrawIndirect ? std::min<u32>(limits.maxDrawIndirectCount, 0x7FFFFFFF) : 1;

        VkPhysicalDeviceDescriptorIndexingFeaturesEXT indexing{
            .sType = VK_STRUCTURE_TYPE_PHYSICAL_DEVICE_DESCRIPTOR_INDEXING_FEATURES_EXT
        };
        u32 capacity = 0;
        if (instance.properties2 && vkGetPhysicalDeviceFeatures2KHR && vkGetPhysicalDeviceProperties2KHR &&
            hasExtension(physical, VK_EXT_DESCRIPTOR_INDEXING_EXTENSION_NAME) && hasExtension(
                physical, VK_KHR_MAINTENANCE_3_EXTENSION_NAME)) {
            VkPhysicalDeviceFeatures2 features2{.sType = VK_STRUCTURE_TYPE_PHYSICAL_DEVICE_FEATURES_2};
            features2.pNext = &indexing;
            vkGetPhysicalDeviceFeatures2KHR(physical, &features2);
            VkPhysicalDeviceDescriptorIndexingPropertiesEXT indexingProperties{
                .sType = VK_STRUCTURE_TYPE_PHYSICAL_DEVICE_DESCRIPTOR_INDEXING_PROPERTIES_EXT
            };
            VkPhysicalDeviceProperties2 properties2{.sType = VK_STRUCTURE_TYPE_PHYSICAL_DEVICE_PROPERTIES_2};
            properties2.pNext = &indexingProperties;
            vkGetPhysicalDeviceProperties2KHR(physical, &properties2);
            capacity = std::min({
                BINDLESS_CAPACITY, indexingProperties.maxDescriptorSetUpdateAfterBindSampledImages,
                indexingProperties.maxPerStageDescriptorUpdateAfterBindSampledImages,
                indexingProperties.maxDescriptorSetUpdateAfterBindSamplers
            });
        }
        bindless = capacity >= MIN_BINDLESS_CAPACITY && supported.shaderSampledImageArrayDynamicIndexing &&
                   indexing.shaderSampledImageArrayNonUniformIndexing && indexing.
                   descriptorBindingSampledImageUpdateAfterBind &&
                   indexing.descriptorBindingPartiallyBound && indexing.descriptorBindingUpdateUnusedWhilePending;
        bindlessCapacity = bindless ? capacity : 0;

        VkPhysicalDeviceFeatures features{};
        features.wideLines = wideLines;
        features.logicOp = logicOp;
        features.multiDrawIndirect = multiDrawIndirect;
        features.drawIndirectFirstInstance = multiDrawIndirect;
        features.shaderSampledImageArrayDynamicIndexing = bindless;

        std::vector extensions{VK_KHR_SWAPCHAIN_EXTENSION_NAME};
        if (bindless) {
            extensions.push_back(VK_KHR_MAINTENANCE_3_EXTENSION_NAME);
            extensions.push_back(VK_EXT_DESCRIPTOR_INDEXING_EXTENSION_NAME);
        }

        float priority = 1.0f;
        VkDeviceQueueCreateInfo queueInfo{.sType = VK_STRUCTURE_TYPE_DEVICE_QUEUE_CREATE_INFO};
        queueInfo.queueFamilyIndex = queueFamily;
        queueInfo.queueCount = 1;
        queueInfo.pQueuePriorities = &priority;

        VkPhysicalDeviceDescriptorIndexingFeaturesEXT enabled{
            .sType = VK_STRUCTURE_TYPE_PHYSICAL_DEVICE_DESCRIPTOR_INDEXING_FEATURES_EXT
        };
        enabled.shaderSampledImageArrayNonUniformIndexing = VK_TRUE;
        enabled.descriptorBindingSampledImageUpdateAfterBind = VK_TRUE;
        enabled.descriptorBindingPartiallyBound = VK_TRUE;
        enabled.descriptorBindingUpdateUnusedWhilePending = VK_TRUE;

        VkDeviceCreateInfo info{.sType = VK_STRUCTURE_TYPE_DEVICE_CREATE_INFO};
        info.queueCreateInfoCount = 1;
        info.pQueueCreateInfos = &queueInfo;
        info.enabledExtensionCount = static_cast<uint32_t>(extensions.size());
        info.ppEnabledExtensionNames = extensions.data();
        info.pEnabledFeatures = &features;
        if (bindless) info.pNext = &enabled;
        check(vkCreateDevice(physical, &info, nullptr, &handle), "vkCreateDevice");
        loadDevice(handle);
        vkGetDeviceQueue(handle, queueFamily, 0, &queue);

        for (VkFormat format: {
                 VK_FORMAT_X8_D24_UNORM_PACK32, VK_FORMAT_D24_UNORM_S8_UINT, VK_FORMAT_D32_SFLOAT, VK_FORMAT_D16_UNORM
             }) {
            if (supportsDepth(format)) {
                depthFormat = format;
                break;
            }
        }
        if (depthFormat == VK_FORMAT_UNDEFINED) fail("No depth format is supported");
        depthAspect = depthFormat == VK_FORMAT_D24_UNORM_S8_UINT
                          ? VK_IMAGE_ASPECT_DEPTH_BIT | VK_IMAGE_ASPECT_STENCIL_BIT
                          : VK_IMAGE_ASPECT_DEPTH_BIT;
    }

    Device::~Device() {
        vkDestroyDevice(handle, nullptr);
    }

    u32 Device::memoryType(u32 typeBits, VkMemoryPropertyFlags required, VkMemoryPropertyFlags preferred) const {
        int fallback = -1;
        for (u32 i = 0; i < memory.memoryTypeCount; i++) {
            if (!(typeBits & (1u << i))) continue;
            VkMemoryPropertyFlags flags = memory.memoryTypes[i].propertyFlags;
            if ((flags & required) != required) continue;
            if ((flags & preferred) == preferred) return i;
            if (fallback < 0) fallback = static_cast<int>(i);
        }
        if (fallback < 0)
            fail(
                "No memory type for bits " + std::to_string(typeBits) + " with flags " + std::to_string(required));
        return static_cast<u32>(fallback);
    }

    bool Device::supportsDepth(VkFormat format) const {
        VkFormatProperties properties;
        vkGetPhysicalDeviceFormatProperties(physical, format, &properties);
        return properties.optimalTilingFeatures & VK_FORMAT_FEATURE_DEPTH_STENCIL_ATTACHMENT_BIT;
    }
} // namespace at
