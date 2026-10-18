#pragma once

#include "core/common.h"
#include "core/vk.h"

#include <string>
#include <vector>

namespace at {
    class Instance {
    public:
        Instance(PFN_vkGetInstanceProcAddr getInstanceProcAddr, const std::vector<std::string> &windowExtensions,
                 bool validation);

        ~Instance();

        Instance(const Instance &) = delete;

        Instance &operator=(const Instance &) = delete;

        VkInstance handle = VK_NULL_HANDLE;
        PFN_vkGetInstanceProcAddr getInstanceProcAddr = nullptr;
        u32 apiVersion = VK_API_VERSION_1_0;
        std::vector<std::string> enabledExtensions;
        bool properties2 = false;
        bool externalCapabilities = false;

    private:
        VkDebugUtilsMessengerEXT messenger = VK_NULL_HANDLE;
    };
} // namespace at
