#pragma once

#include "core/common.h"
#include "core/vk.h"

#include <vector>

namespace at {
    class Device;

    class Swapchain {
    public:
        Swapchain(const Device &device, VkSurfaceKHR surface);

        ~Swapchain();

        bool create(u32 requestedWidth, u32 requestedHeight, bool vsync);

        int acquire(VkSemaphore semaphore) const;

        bool present(VkSemaphore semaphore, u32 index) const;

        VkSwapchainKHR handle = VK_NULL_HANDLE;
        VkFormat format;
        u32 width = 0;
        u32 height = 0;
        std::vector<VkImage> images;
        std::vector<VkImageView> views;

    private:
        [[nodiscard]] VkFormat chooseFormat() const;

        [[nodiscard]] VkPresentModeKHR choosePresentMode(bool vsync) const;

        void destroyViews();

        const Device &device;
        VkSurfaceKHR surface;
    };
} // namespace at
