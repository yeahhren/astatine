#pragma once

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
        bool properties2 = false;
        bool externalCapabilities = false;

    private:
        VkDebugUtilsMessengerEXT messenger = VK_NULL_HANDLE;
    };
} // namespace at
