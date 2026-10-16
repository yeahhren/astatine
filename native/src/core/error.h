#pragma once

#include <stdexcept>
#include <string>

#include <vulkan/vulkan_core.h>

namespace at {
    class Error : public std::runtime_error {
    public:
        using std::runtime_error::runtime_error;
    };

    class VulkanError : public Error {
    public:
        VulkanError(const VkResult result, const char *action)
            : Error(std::string(action) + " failed with VkResult " + std::to_string(result)),
              result(result) {
        }

        VkResult result;
    };

    [[noreturn]] inline void fail(const std::string &message) {
        throw Error(message);
    }

    inline VkResult check(VkResult result, const char *action) {
        if (result != VK_SUCCESS) [[unlikely]] {
            throw VulkanError(result, action);
        }
        return result;
    }
} // namespace at
