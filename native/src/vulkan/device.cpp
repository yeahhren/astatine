#include "vulkan/device.h"

#include "core/error.h"
#include "vulkan/instance.h"

#include <algorithm>
#include <cstring>
#include <initializer_list>
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
        maxColorAttachments = limits.maxColorAttachments;
        maxUniformBufferRange = limits.maxUniformBufferRange;
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
        runtimeDescriptorArray = bindless && indexing.runtimeDescriptorArray;
        independentBlend = supported.independentBlend;

        VkPhysicalDeviceFeatures features{};
        features.wideLines = wideLines;
        features.logicOp = logicOp;
        features.multiDrawIndirect = multiDrawIndirect;
        features.drawIndirectFirstInstance = multiDrawIndirect;
        features.shaderSampledImageArrayDynamicIndexing = bindless;
        features.independentBlend = independentBlend;
        geometryShader = supported.geometryShader;
        tessellationShader = supported.tessellationShader;
        shaderStores = supported.fragmentStoresAndAtomics && supported.vertexPipelineStoresAndAtomics;
        features.geometryShader = supported.geometryShader;
        features.tessellationShader = supported.tessellationShader;
        features.fragmentStoresAndAtomics = supported.fragmentStoresAndAtomics;
        features.vertexPipelineStoresAndAtomics = supported.vertexPipelineStoresAndAtomics;
        features.shaderStorageImageExtendedFormats = supported.shaderStorageImageExtendedFormats;
        features.shaderStorageImageReadWithoutFormat = supported.shaderStorageImageReadWithoutFormat;
        features.shaderStorageImageWriteWithoutFormat = supported.shaderStorageImageWriteWithoutFormat;
        features.imageCubeArray = supported.imageCubeArray;
        features.shaderImageGatherExtended = supported.shaderImageGatherExtended;
        features.shaderFloat64 = supported.shaderFloat64;
        features.shaderInt64 = supported.shaderInt64;
        features.shaderInt16 = supported.shaderInt16;
        features.samplerAnisotropy = supported.samplerAnisotropy;
        features.fillModeNonSolid = supported.fillModeNonSolid;

        std::vector extensions{VK_KHR_SWAPCHAIN_EXTENSION_NAME};
        if (bindless) {
            extensions.push_back(VK_KHR_MAINTENANCE_3_EXTENSION_NAME);
            extensions.push_back(VK_EXT_DESCRIPTOR_INDEXING_EXTENSION_NAME);
        }

        VkPhysicalDeviceTimelineSemaphoreFeaturesKHR timeline{
            .sType = VK_STRUCTURE_TYPE_PHYSICAL_DEVICE_TIMELINE_SEMAPHORE_FEATURES_KHR
        };
#ifdef _WIN32
        if (instance.externalCapabilities && vkGetPhysicalDeviceFeatures2KHR && vkGetPhysicalDeviceProperties2KHR) {
            const char *interopExtensions[] = {
                VK_KHR_EXTERNAL_MEMORY_EXTENSION_NAME, "VK_KHR_external_memory_win32",
                VK_KHR_EXTERNAL_SEMAPHORE_EXTENSION_NAME, "VK_KHR_external_semaphore_win32",
                VK_KHR_DEDICATED_ALLOCATION_EXTENSION_NAME, VK_KHR_GET_MEMORY_REQUIREMENTS_2_EXTENSION_NAME,
                VK_KHR_TIMELINE_SEMAPHORE_EXTENSION_NAME
            };
            bool available = true;
            for (const char *extension: interopExtensions) available &= hasExtension(physical, extension);
            if (available) {
                VkPhysicalDeviceFeatures2 features2{.sType = VK_STRUCTURE_TYPE_PHYSICAL_DEVICE_FEATURES_2};
                features2.pNext = &timeline;
                vkGetPhysicalDeviceFeatures2KHR(physical, &features2);
                VkPhysicalDeviceIDPropertiesKHR id{.sType = VK_STRUCTURE_TYPE_PHYSICAL_DEVICE_ID_PROPERTIES_KHR};
                VkPhysicalDeviceProperties2 properties2{.sType = VK_STRUCTURE_TYPE_PHYSICAL_DEVICE_PROPERTIES_2};
                properties2.pNext = &id;
                vkGetPhysicalDeviceProperties2KHR(physical, &properties2);
                interop = timeline.timelineSemaphore && id.deviceLUIDValid;
                if (interop) {
                    std::memcpy(luid, id.deviceLUID, sizeof(luid));
                    for (const char *extension: interopExtensions) extensions.push_back(extension);
                }
            }
        }
#endif
        timeline = {.sType = VK_STRUCTURE_TYPE_PHYSICAL_DEVICE_TIMELINE_SEMAPHORE_FEATURES_KHR};
        timeline.timelineSemaphore = interop;

        float priority = 1.0f;
        {
            uint32_t familyCount = 0;
            vkGetPhysicalDeviceQueueFamilyProperties(physical, &familyCount, nullptr);
            std::vector<VkQueueFamilyProperties> families(familyCount);
            vkGetPhysicalDeviceQueueFamilyProperties(physical, &familyCount, families.data());
            int best = -1;
            for (uint32_t i = 0; i < familyCount; i++) {
                VkQueueFlags flags = families[i].queueFlags;
                if (i == queueFamily || !(flags & VK_QUEUE_TRANSFER_BIT) || (flags & VK_QUEUE_GRAPHICS_BIT)) continue;
                if (best < 0 || !(flags & VK_QUEUE_COMPUTE_BIT)) best = static_cast<int>(i);
            }
            if (best >= 0) transferFamily = static_cast<u32>(best);
        }
        VkDeviceQueueCreateInfo queueInfos[2]{};
        queueInfos[0].sType = VK_STRUCTURE_TYPE_DEVICE_QUEUE_CREATE_INFO;
        queueInfos[0].queueFamilyIndex = queueFamily;
        queueInfos[0].queueCount = 1;
        queueInfos[0].pQueuePriorities = &priority;
        queueInfos[1].sType = VK_STRUCTURE_TYPE_DEVICE_QUEUE_CREATE_INFO;
        queueInfos[1].queueFamilyIndex = transferFamily;
        queueInfos[1].queueCount = 1;
        queueInfos[1].pQueuePriorities = &priority;

        VkPhysicalDeviceDescriptorIndexingFeaturesEXT enabled{
            .sType = VK_STRUCTURE_TYPE_PHYSICAL_DEVICE_DESCRIPTOR_INDEXING_FEATURES_EXT
        };
        enabled.shaderSampledImageArrayNonUniformIndexing = VK_TRUE;
        enabled.descriptorBindingSampledImageUpdateAfterBind = VK_TRUE;
        enabled.descriptorBindingPartiallyBound = VK_TRUE;
        enabled.descriptorBindingUpdateUnusedWhilePending = VK_TRUE;
        enabled.runtimeDescriptorArray = runtimeDescriptorArray;

        bool modern = std::min(instance.apiVersion, apiVersion) >= VK_API_VERSION_1_2 && vkGetPhysicalDeviceFeatures2KHR;
        VkPhysicalDeviceVulkan11Features vulkan11{.sType = VK_STRUCTURE_TYPE_PHYSICAL_DEVICE_VULKAN_1_1_FEATURES};
        VkPhysicalDeviceVulkan12Features vulkan12{.sType = VK_STRUCTURE_TYPE_PHYSICAL_DEVICE_VULKAN_1_2_FEATURES};
        VkPhysicalDeviceSynchronization2Features synchronization2{
            .sType = VK_STRUCTURE_TYPE_PHYSICAL_DEVICE_SYNCHRONIZATION_2_FEATURES
        };
        VkPhysicalDeviceDynamicRenderingFeatures dynamicRendering{
            .sType = VK_STRUCTURE_TYPE_PHYSICAL_DEVICE_DYNAMIC_RENDERING_FEATURES
        };
        VkPhysicalDeviceVertexAttributeDivisorFeaturesEXT divisor{
            .sType = VK_STRUCTURE_TYPE_PHYSICAL_DEVICE_VERTEX_ATTRIBUTE_DIVISOR_FEATURES_EXT
        };
        VkPhysicalDeviceMultiDrawFeaturesEXT multiDraw{.sType = VK_STRUCTURE_TYPE_PHYSICAL_DEVICE_MULTI_DRAW_FEATURES_EXT};
        VkPhysicalDeviceComputeShaderDerivativesFeaturesKHR derivatives{
            .sType = VK_STRUCTURE_TYPE_PHYSICAL_DEVICE_COMPUTE_SHADER_DERIVATIVES_FEATURES_KHR
        };
        VkPhysicalDeviceShaderSubgroupPartitionedFeaturesEXT partitioned{
            .sType = VK_STRUCTURE_TYPE_PHYSICAL_DEVICE_SHADER_SUBGROUP_PARTITIONED_FEATURES_EXT
        };
        VkPhysicalDeviceShaderDemoteToHelperInvocationFeaturesEXT demote{
            .sType = VK_STRUCTURE_TYPE_PHYSICAL_DEVICE_SHADER_DEMOTE_TO_HELPER_INVOCATION_FEATURES_EXT
        };
        VkBaseOutStructure *modernChain = nullptr;
        auto link = [&](void *structure) {
            auto *next = static_cast<VkBaseOutStructure *>(structure);
            next->pNext = modernChain;
            modernChain = next;
        };
        auto offer = [&](std::initializer_list<const char *> names, void *structure) {
            for (const char *name: names) {
                if (!hasExtension(physical, name)) return false;
            }
            for (const char *name: names) {
                if (std::none_of(extensions.begin(), extensions.end(), [&](const char *present) {
                    return std::strcmp(present, name) == 0;
                }))
                    extensions.push_back(name);
            }
            if (structure) link(structure);
            return true;
        };
#define AT_NOTE(source, prefix, member) \
    if (source.member) enabledFeatures.emplace_back(prefix #member);
        if (modern) {
            link(&vulkan11);
            link(&vulkan12);
            offer({VK_KHR_SYNCHRONIZATION_2_EXTENSION_NAME}, &synchronization2);
            offer({VK_KHR_DYNAMIC_RENDERING_EXTENSION_NAME}, &dynamicRendering);
            offer({VK_KHR_PUSH_DESCRIPTOR_EXTENSION_NAME}, nullptr);
            offer({VK_EXT_VERTEX_ATTRIBUTE_DIVISOR_EXTENSION_NAME}, &divisor);
            offer({VK_EXT_MULTI_DRAW_EXTENSION_NAME}, &multiDraw);
            if (!offer({VK_KHR_COMPUTE_SHADER_DERIVATIVES_EXTENSION_NAME}, &derivatives))
                offer({VK_NV_COMPUTE_SHADER_DERIVATIVES_EXTENSION_NAME}, &derivatives);
            offer({VK_EXT_SHADER_SUBGROUP_PARTITIONED_EXTENSION_NAME}, &partitioned);
            offer({VK_EXT_SHADER_DEMOTE_TO_HELPER_INVOCATION_EXTENSION_NAME}, &demote);
            VkPhysicalDeviceFeatures2 query{.sType = VK_STRUCTURE_TYPE_PHYSICAL_DEVICE_FEATURES_2};
            query.pNext = modernChain;
            vkGetPhysicalDeviceFeatures2KHR(physical, &query);

            VkPhysicalDeviceVulkan11Features supported11 = vulkan11;
            vulkan11 = {.sType = VK_STRUCTURE_TYPE_PHYSICAL_DEVICE_VULKAN_1_1_FEATURES, .pNext = supported11.pNext};
            VkPhysicalDeviceVulkan12Features supported12 = vulkan12;
            vulkan12 = {.sType = VK_STRUCTURE_TYPE_PHYSICAL_DEVICE_VULKAN_1_2_FEATURES, .pNext = supported12.pNext};
#define AT_TAKE(target, source, prefix, member) \
            if (source.member) { \
                target.member = VK_TRUE; \
                enabledFeatures.emplace_back(prefix #member); \
            }
            AT_TAKE(vulkan11, supported11, "VkPhysicalDeviceVulkan11Features.", storageBuffer16BitAccess)
            AT_TAKE(vulkan11, supported11, "VkPhysicalDeviceVulkan11Features.", uniformAndStorageBuffer16BitAccess)
            AT_TAKE(vulkan11, supported11, "VkPhysicalDeviceVulkan11Features.", storagePushConstant16)
            AT_TAKE(vulkan11, supported11, "VkPhysicalDeviceVulkan11Features.", storageInputOutput16)
            AT_TAKE(vulkan11, supported11, "VkPhysicalDeviceVulkan11Features.", shaderDrawParameters)
            AT_TAKE(vulkan12, supported12, "VkPhysicalDeviceVulkan12Features.", timelineSemaphore)
            AT_TAKE(vulkan12, supported12, "VkPhysicalDeviceVulkan12Features.", hostQueryReset)
            AT_TAKE(vulkan12, supported12, "VkPhysicalDeviceVulkan12Features.", descriptorBindingSampledImageUpdateAfterBind)
            AT_TAKE(vulkan12, supported12, "VkPhysicalDeviceVulkan12Features.", descriptorBindingUniformTexelBufferUpdateAfterBind)
            AT_TAKE(vulkan12, supported12, "VkPhysicalDeviceVulkan12Features.", storageBuffer8BitAccess)
            AT_TAKE(vulkan12, supported12, "VkPhysicalDeviceVulkan12Features.", shaderFloat16)
            AT_TAKE(vulkan12, supported12, "VkPhysicalDeviceVulkan12Features.", shaderInt8)
            AT_TAKE(vulkan12, supported12, "VkPhysicalDeviceVulkan12Features.", shaderSubgroupExtendedTypes)
            if (bindless) {
                AT_TAKE(vulkan12, supported12, "VkPhysicalDeviceVulkan12Features.", descriptorIndexing)
                AT_TAKE(vulkan12, supported12, "VkPhysicalDeviceVulkan12Features.", shaderSampledImageArrayNonUniformIndexing)
                AT_TAKE(vulkan12, supported12, "VkPhysicalDeviceVulkan12Features.", descriptorBindingPartiallyBound)
                AT_TAKE(vulkan12, supported12, "VkPhysicalDeviceVulkan12Features.", descriptorBindingUpdateUnusedWhilePending)
                if (runtimeDescriptorArray) {
                    AT_TAKE(vulkan12, supported12, "VkPhysicalDeviceVulkan12Features.", runtimeDescriptorArray)
                }
            }
#undef AT_TAKE
            AT_NOTE(synchronization2, "VkPhysicalDeviceSynchronization2Features.", synchronization2)
            AT_NOTE(dynamicRendering, "VkPhysicalDeviceDynamicRenderingFeatures.", dynamicRendering)
            AT_NOTE(divisor, "VkPhysicalDeviceVertexAttributeDivisorFeaturesEXT.", vertexAttributeInstanceRateDivisor)
            AT_NOTE(divisor, "VkPhysicalDeviceVertexAttributeDivisorFeaturesEXT.", vertexAttributeInstanceRateZeroDivisor)
            AT_NOTE(multiDraw, "VkPhysicalDeviceMultiDrawFeaturesEXT.", multiDraw)
            AT_NOTE(derivatives, "VkPhysicalDeviceComputeShaderDerivativesFeaturesKHR.", computeDerivativeGroupQuads)
            AT_NOTE(derivatives, "VkPhysicalDeviceComputeShaderDerivativesFeaturesKHR.", computeDerivativeGroupLinear)
            AT_NOTE(partitioned, "VkPhysicalDeviceShaderSubgroupPartitionedFeaturesEXT.", shaderSubgroupPartitioned)
            AT_NOTE(demote, "VkPhysicalDeviceShaderDemoteToHelperInvocationFeaturesEXT.", shaderDemoteToHelperInvocation)
        }
        AT_NOTE(features, "VkPhysicalDeviceFeatures2.", imageCubeArray)
        AT_NOTE(features, "VkPhysicalDeviceFeatures2.", independentBlend)
        AT_NOTE(features, "VkPhysicalDeviceFeatures2.", geometryShader)
        AT_NOTE(features, "VkPhysicalDeviceFeatures2.", tessellationShader)
        AT_NOTE(features, "VkPhysicalDeviceFeatures2.", logicOp)
        AT_NOTE(features, "VkPhysicalDeviceFeatures2.", multiDrawIndirect)
        AT_NOTE(features, "VkPhysicalDeviceFeatures2.", drawIndirectFirstInstance)
        AT_NOTE(features, "VkPhysicalDeviceFeatures2.", fillModeNonSolid)
        AT_NOTE(features, "VkPhysicalDeviceFeatures2.", wideLines)
        AT_NOTE(features, "VkPhysicalDeviceFeatures2.", samplerAnisotropy)
        AT_NOTE(features, "VkPhysicalDeviceFeatures2.", vertexPipelineStoresAndAtomics)
        AT_NOTE(features, "VkPhysicalDeviceFeatures2.", fragmentStoresAndAtomics)
        AT_NOTE(features, "VkPhysicalDeviceFeatures2.", shaderImageGatherExtended)
        AT_NOTE(features, "VkPhysicalDeviceFeatures2.", shaderStorageImageExtendedFormats)
        AT_NOTE(features, "VkPhysicalDeviceFeatures2.", shaderStorageImageReadWithoutFormat)
        AT_NOTE(features, "VkPhysicalDeviceFeatures2.", shaderStorageImageWriteWithoutFormat)
        AT_NOTE(features, "VkPhysicalDeviceFeatures2.", shaderSampledImageArrayDynamicIndexing)
        AT_NOTE(features, "VkPhysicalDeviceFeatures2.", shaderFloat64)
        AT_NOTE(features, "VkPhysicalDeviceFeatures2.", shaderInt64)
        AT_NOTE(features, "VkPhysicalDeviceFeatures2.", shaderInt16)
#undef AT_NOTE

        VkDeviceCreateInfo info{.sType = VK_STRUCTURE_TYPE_DEVICE_CREATE_INFO};
        info.queueCreateInfoCount = transferFamily != ~0u ? 2 : 1;
        info.pQueueCreateInfos = queueInfos;
        info.enabledExtensionCount = static_cast<uint32_t>(extensions.size());
        info.ppEnabledExtensionNames = extensions.data();
        info.pEnabledFeatures = &features;
        enabledExtensions.assign(extensions.begin(), extensions.end());
        void *chain = nullptr;
        if (modern) {
            chain = modernChain;
        } else {
            if (bindless) {
                enabled.pNext = chain;
                chain = &enabled;
            }
            if (interop) {
                timeline.pNext = chain;
                chain = &timeline;
            }
        }
        info.pNext = chain;
        check(vkCreateDevice(physical, &info, nullptr, &handle), "vkCreateDevice");
        loadDevice(handle);
        vkGetDeviceQueue(handle, queueFamily, 0, &queue);
        if (transferFamily != ~0u) vkGetDeviceQueue(handle, transferFamily, 0, &transferQueue);

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
        packDepthFormat = supportsDepth(VK_FORMAT_D32_SFLOAT) ? VK_FORMAT_D32_SFLOAT : depthFormat;
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
