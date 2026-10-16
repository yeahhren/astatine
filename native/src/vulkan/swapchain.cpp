#include "vulkan/swapchain.h"

#include "core/error.h"
#include "vulkan/device.h"

namespace at {
    Swapchain::Swapchain(const Device &device, VkSurfaceKHR surface) : device(device), surface(surface) {
        format = chooseFormat();
        layout = VK_IMAGE_LAYOUT_PRESENT_SRC_KHR;
    }

    Swapchain::~Swapchain() {
        destroyViews();
        if (handle) vkDestroySwapchainKHR(device.handle, handle, nullptr);
    }

    bool Swapchain::create(u32 requestedWidth, u32 requestedHeight, bool vsync) {
        VkSurfaceCapabilitiesKHR capabilities;
        check(vkGetPhysicalDeviceSurfaceCapabilitiesKHR(device.physical, surface, &capabilities),
              "vkGetPhysicalDeviceSurfaceCapabilitiesKHR");
        VkExtent2D current = capabilities.currentExtent;
        u32 extentWidth = current.width == 0xFFFFFFFFu
                              ? clamp(requestedWidth, capabilities.minImageExtent.width,
                                      capabilities.maxImageExtent.width)
                              : current.width;
        u32 extentHeight = current.height == 0xFFFFFFFFu
                               ? clamp(requestedHeight, capabilities.minImageExtent.height,
                                       capabilities.maxImageExtent.height)
                               : current.height;
        if (extentWidth == 0 || extentHeight == 0) return false;

        u32 imageCount = capabilities.minImageCount + 1;
        if (capabilities.maxImageCount > 0 && imageCount > capabilities.maxImageCount)
            imageCount = capabilities.maxImageCount;
        VkCompositeAlphaFlagBitsKHR compositeAlpha = VK_COMPOSITE_ALPHA_OPAQUE_BIT_KHR;
        if (!(capabilities.supportedCompositeAlpha & VK_COMPOSITE_ALPHA_OPAQUE_BIT_KHR)) {
            u32 supported = capabilities.supportedCompositeAlpha;
            compositeAlpha = static_cast<VkCompositeAlphaFlagBitsKHR>(supported & (~supported + 1));
        }

        VkSwapchainKHR old = handle;
        VkSwapchainCreateInfoKHR info{.sType = VK_STRUCTURE_TYPE_SWAPCHAIN_CREATE_INFO_KHR};
        info.surface = surface;
        info.minImageCount = imageCount;
        info.imageFormat = format;
        info.imageColorSpace = VK_COLOR_SPACE_SRGB_NONLINEAR_KHR;
        info.imageExtent = {.width = extentWidth, .height = extentHeight};
        info.imageArrayLayers = 1;
        info.imageUsage = VK_IMAGE_USAGE_COLOR_ATTACHMENT_BIT | (
                              capabilities.supportedUsageFlags & (VK_IMAGE_USAGE_TRANSFER_SRC_BIT |
                                                                  VK_IMAGE_USAGE_TRANSFER_DST_BIT));
        blitTarget = (capabilities.supportedUsageFlags & VK_IMAGE_USAGE_TRANSFER_DST_BIT) != 0;
        info.imageSharingMode = VK_SHARING_MODE_EXCLUSIVE;
        info.preTransform = capabilities.currentTransform;
        info.compositeAlpha = compositeAlpha;
        info.presentMode = choosePresentMode(vsync);
        info.clipped = VK_TRUE;
        info.oldSwapchain = old;
        VkSwapchainKHR created;
        check(vkCreateSwapchainKHR(device.handle, &info, nullptr, &created), "vkCreateSwapchainKHR");
        destroyViews();
        if (old) vkDestroySwapchainKHR(device.handle, old, nullptr);
        handle = created;
        width = extentWidth;
        height = extentHeight;

        uint32_t count = 0;
        check(vkGetSwapchainImagesKHR(device.handle, handle, &count, nullptr), "vkGetSwapchainImagesKHR");
        images.resize(count);
        check(vkGetSwapchainImagesKHR(device.handle, handle, &count, images.data()), "vkGetSwapchainImagesKHR");
        views.resize(count);
        renderFinished.resize(count);
        for (VkSemaphore &semaphore: renderFinished) {
            VkSemaphoreCreateInfo semaphoreInfo{VK_STRUCTURE_TYPE_SEMAPHORE_CREATE_INFO};
            check(vkCreateSemaphore(device.handle, &semaphoreInfo, nullptr, &semaphore), "vkCreateSemaphore");
        }
        for (u32 i = 0; i < count; i++) {
            VkImageViewCreateInfo viewInfo{VK_STRUCTURE_TYPE_IMAGE_VIEW_CREATE_INFO};
            viewInfo.image = images[i];
            viewInfo.viewType = VK_IMAGE_VIEW_TYPE_2D;
            viewInfo.format = format;
            viewInfo.subresourceRange = {VK_IMAGE_ASPECT_COLOR_BIT, 0, 1, 0, 1};
            check(vkCreateImageView(device.handle, &viewInfo, nullptr, &views[i]), "vkCreateImageView");
        }
        return true;
    }

    int Swapchain::acquire(VkSemaphore semaphore) {
        available = semaphore;
        uint32_t index = 0;
        VkResult result = vkAcquireNextImageKHR(device.handle, handle, ~0ull, semaphore, VK_NULL_HANDLE, &index);
        if (result == VK_SUCCESS || result == VK_SUBOPTIMAL_KHR) return static_cast<int>(index);
        if (result == VK_ERROR_OUT_OF_DATE_KHR) return -1;
        throw VulkanError(result, "vkAcquireNextImageKHR");
    }

    void Swapchain::sync(SubmitSync &sync, VkCommandBuffer, u32 index, bool first, bool presenting) {
        if (first)
            sync.wait(available, VK_PIPELINE_STAGE_COLOR_ATTACHMENT_OUTPUT_BIT | VK_PIPELINE_STAGE_TRANSFER_BIT);
        if (presenting) sync.signal(renderFinished[index]);
    }

    bool Swapchain::present(u32 index) {
        VkSemaphore semaphore = renderFinished[index];
        VkPresentInfoKHR info{.sType = VK_STRUCTURE_TYPE_PRESENT_INFO_KHR};
        info.waitSemaphoreCount = 1;
        info.pWaitSemaphores = &semaphore;
        info.swapchainCount = 1;
        info.pSwapchains = &handle;
        info.pImageIndices = &index;
        VkResult result = vkQueuePresentKHR(device.queue, &info);
        if (result == VK_SUCCESS) return true;
        if (result == VK_SUBOPTIMAL_KHR || result == VK_ERROR_OUT_OF_DATE_KHR) return false;
        throw VulkanError(result, "vkQueuePresentKHR");
    }

    void Swapchain::destroyViews() {
        for (VkImageView view: views) vkDestroyImageView(device.handle, view, nullptr);
        views.clear();
        for (VkSemaphore semaphore: renderFinished) vkDestroySemaphore(device.handle, semaphore, nullptr);
        renderFinished.clear();
    }

    VkFormat Swapchain::chooseFormat() const {
        uint32_t count = 0;
        check(vkGetPhysicalDeviceSurfaceFormatsKHR(device.physical, surface, &count, nullptr),
              "vkGetPhysicalDeviceSurfaceFormatsKHR");
        std::vector<VkSurfaceFormatKHR> formats(count);
        check(vkGetPhysicalDeviceSurfaceFormatsKHR(device.physical, surface, &count, formats.data()),
              "vkGetPhysicalDeviceSurfaceFormatsKHR");
        if (count == 1 && formats[0].format == VK_FORMAT_UNDEFINED) return VK_FORMAT_B8G8R8A8_UNORM;
        for (VkFormat preferred: {
                 VK_FORMAT_B8G8R8A8_UNORM, VK_FORMAT_R8G8B8A8_UNORM, VK_FORMAT_A8B8G8R8_UNORM_PACK32
             }) {
            for (const auto &candidate: formats) {
                if (candidate.format == preferred && candidate.colorSpace == VK_COLOR_SPACE_SRGB_NONLINEAR_KHR)
                    return
                            preferred;
            }
        }
        return formats[0].format;
    }

    VkPresentModeKHR Swapchain::choosePresentMode(bool vsync) const {
        if (vsync) return VK_PRESENT_MODE_FIFO_KHR;
        uint32_t count = 0;
        vkGetPhysicalDeviceSurfacePresentModesKHR(device.physical, surface, &count, nullptr);
        std::vector<VkPresentModeKHR> modes(count);
        vkGetPhysicalDeviceSurfacePresentModesKHR(device.physical, surface, &count, modes.data());
        for (VkPresentModeKHR preferred: {VK_PRESENT_MODE_IMMEDIATE_KHR, VK_PRESENT_MODE_MAILBOX_KHR}) {
            for (VkPresentModeKHR mode: modes) {
                if (mode == preferred) return preferred;
            }
        }
        return VK_PRESENT_MODE_FIFO_KHR;
    }
} // namespace at
