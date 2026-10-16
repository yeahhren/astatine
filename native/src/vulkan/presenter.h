#pragma once

#include "core/common.h"
#include "core/vk.h"

#include <vector>

namespace at {
    struct SubmitSync {
        VkSemaphore waits[2]{};
        u64 waitValues[2]{};
        VkPipelineStageFlags waitStages[2]{};
        u32 waitCount = 0;
        VkSemaphore signals[2]{};
        u64 signalValues[2]{};
        u32 signalCount = 0;
        bool timeline = false;

        void wait(VkSemaphore semaphore, VkPipelineStageFlags stage, u64 value = 0) {
            waits[waitCount] = semaphore;
            waitStages[waitCount] = stage;
            waitValues[waitCount] = value;
            waitCount++;
        }

        void signal(VkSemaphore semaphore, u64 value = 0) {
            signals[signalCount] = semaphore;
            signalValues[signalCount] = value;
            signalCount++;
        }
    };

    class Presenter {
    public:
        virtual ~Presenter() = default;

        virtual bool create(u32 requestedWidth, u32 requestedHeight, bool vsync) = 0;

        virtual int acquire(VkSemaphore available) = 0;

        virtual void sync(SubmitSync &sync, VkCommandBuffer commands, u32 index, bool first, bool presenting) = 0;

        virtual bool present(u32 index) = 0;

        virtual bool setVsync(bool enabled) = 0;

        VkFormat format = VK_FORMAT_B8G8R8A8_UNORM;
        VkImageLayout layout = VK_IMAGE_LAYOUT_PRESENT_SRC_KHR;
        bool blitTarget = false;
        u32 width = 0;
        u32 height = 0;
        std::vector<VkImage> images;
        std::vector<VkImageView> views;
    };
} // namespace at
