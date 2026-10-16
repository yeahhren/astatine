#pragma once

#include "core/common.h"
#include "core/vk.h"
#include "vulkan/presenter.h"

#include <vector>

namespace at {
    class Device;

    class Swapchain final : public Presenter {
    public:
        Swapchain(const Device &device, VkSurfaceKHR surface);

        ~Swapchain() override;

        bool create(u32 requestedWidth, u32 requestedHeight, bool vsync) override;

        int acquire(VkSemaphore available) override;

        void sync(SubmitSync &sync, VkCommandBuffer commands, u32 index, bool first, bool presenting) override;

        bool present(u32 index) override;

        bool setVsync(bool) override { return true; }

        VkSwapchainKHR handle = VK_NULL_HANDLE;

    private:
        [[nodiscard]] VkFormat chooseFormat() const;

        [[nodiscard]] VkPresentModeKHR choosePresentMode(bool vsync) const;

        void destroyViews();

        const Device &device;
        VkSurfaceKHR surface;
        VkSemaphore available = VK_NULL_HANDLE;
        std::vector<VkSemaphore> renderFinished;
    };
} // namespace at
