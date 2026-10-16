#pragma once

#include "core/common.h"
#include "core/vk.h"
#include "render/texture.h"
#include "vulkan/memory.h"

#include <memory>
#include <vector>

namespace at {
    class Device;
    class RenderPassType;
    class Swapchain;

    class DrawTarget {
    public:
        explicit DrawTarget(RenderPassType &pass) : pass(pass) {
        }

        virtual ~DrawTarget() = default;

        [[nodiscard]] virtual u32 width() const = 0;

        [[nodiscard]] virtual u32 height() const = 0;

        virtual VkFramebuffer framebuffer() = 0;

        [[nodiscard]] virtual VkAttachmentLoadOp loadOp() const = 0;

        virtual void passStarted() {
        }

        RenderPassType &pass;
    };

    class Framebuffer final : public DrawTarget {
    public:
        Framebuffer(const Device &device, MemoryAllocator &allocator, RenderPassType &pass, u32 width, u32 height);

        ~Framebuffer() override;

        [[nodiscard]] u32 width() const override { return widthValue; }
        [[nodiscard]] u32 height() const override { return heightValue; }
        VkFramebuffer framebuffer() override { return handle; }
        [[nodiscard]] VkAttachmentLoadOp loadOp() const override { return VK_ATTACHMENT_LOAD_OP_LOAD; }

        std::unique_ptr<GpuImage> color;
        std::unique_ptr<GpuImage> depth;
        Texture texture;

    private:
        const Device &device;
        u32 widthValue;
        u32 heightValue;
        VkFramebuffer handle = VK_NULL_HANDLE;
    };

    using WindowSizeFunction = bool (*)(void *window, int *width, int *height);

    class SurfaceTarget final : public DrawTarget {
    public:
        SurfaceTarget(const Device &device, MemoryAllocator &allocator, void *window, WindowSizeFunction windowSize,
                      RenderPassType &pass,
                      Swapchain &swapchain);

        ~SurfaceTarget() override;

        [[nodiscard]] u32 width() const override;

        [[nodiscard]] u32 height() const override;

        VkFramebuffer framebuffer() override;

        [[nodiscard]] VkAttachmentLoadOp loadOp() const override {
            return startedThisFrame ? VK_ATTACHMENT_LOAD_OP_LOAD : VK_ATTACHMENT_LOAD_OP_DONT_CARE;
        }

        void passStarted() override { startedThisFrame = true; }

        [[nodiscard]] VkSemaphore renderFinished() const {
            return renderFinishedSemaphores[static_cast<std::size_t>(imageIndex)];
        }

        [[nodiscard]] VkImage image() const;

        bool present();

        void endFrame();

        void setVsync(bool enabled) {
            if (vsync != enabled) valid = false;
            vsync = enabled;
        }

        [[nodiscard]] bool vsyncEnabled() const { return vsync; }

        int imageIndex = -1;
        bool waited = false;
        VkSemaphore acquireSemaphore = VK_NULL_HANDLE;

    private:
        bool acquire();

        bool recreate();

        [[nodiscard]] bool sizeMatches() const;

        void destroyResources();

        const Device &device;
        MemoryAllocator &allocator;
        void *window;
        WindowSizeFunction windowSize;
        Swapchain &swapchain;
        std::vector<VkFramebuffer> framebuffers;
        std::vector<VkSemaphore> renderFinishedSemaphores;
        std::unique_ptr<GpuImage> depth;
        bool valid = false;
        bool startedThisFrame = false;
        bool vsync = true;
    };
} // namespace at
