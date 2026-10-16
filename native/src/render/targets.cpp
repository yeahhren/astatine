#include "render/targets.h"

#include "core/error.h"
#include "core/log.h"
#include "render/renderer.h"
#include "vulkan/device.h"
#include "vulkan/render_pass.h"

namespace at {
    Framebuffer::Framebuffer(const Device &device, MemoryAllocator &allocator, RenderPassType &pass, u32 width,
                             u32 height)
        : DrawTarget(pass), device(device), widthValue(width), heightValue(height) {
        color = std::make_unique<GpuImage>(device, allocator, width, height, 1, pass.colorFormat,
                                           VK_IMAGE_ASPECT_COLOR_BIT,
                                           VK_IMAGE_USAGE_COLOR_ATTACHMENT_BIT | VK_IMAGE_USAGE_SAMPLED_BIT |
                                           VK_IMAGE_USAGE_TRANSFER_SRC_BIT |
                                           VK_IMAGE_USAGE_TRANSFER_DST_BIT);
        if (pass.hasDepth()) {
            depth = std::make_unique<GpuImage>(device, allocator, width, height, 1, pass.depthFormat,
                                               device.depthAspect,
                                               VK_IMAGE_USAGE_DEPTH_STENCIL_ATTACHMENT_BIT);
        }
        VkImageView attachments[2] = {color->view, depth ? depth->view : VK_NULL_HANDLE};
        VkFramebufferCreateInfo info{VK_STRUCTURE_TYPE_FRAMEBUFFER_CREATE_INFO};
        info.renderPass = pass.compatible();
        info.attachmentCount = depth ? 2 : 1;
        info.pAttachments = attachments;
        info.width = width;
        info.height = height;
        info.layers = 1;
        check(vkCreateFramebuffer(device.handle, &info, nullptr, &handle), "vkCreateFramebuffer");
        texture.attach(color.get(), true);
        if (depth) Renderer::get().trackDepth(depth.get());
    }

    Framebuffer::~Framebuffer() {
        vkDestroyFramebuffer(device.handle, handle, nullptr);
    }

    SurfaceTarget::SurfaceTarget(const Device &device, MemoryAllocator &allocator, void *window,
                                 WindowSizeFunction windowSize,
                                 RenderPassType &pass, Presenter &presenter)
        : DrawTarget(pass), device(device), allocator(allocator), window(window), windowSize(windowSize),
          presenter(presenter) {
    }

    SurfaceTarget::~SurfaceTarget() {
        destroyResources();
    }

    u32 SurfaceTarget::width() const {
        return presenter.width;
    }

    u32 SurfaceTarget::height() const {
        return presenter.height;
    }

    VkImage SurfaceTarget::image() const {
        return presenter.images[static_cast<std::size_t>(imageIndex)];
    }

    VkFramebuffer SurfaceTarget::framebuffer() {
        if (imageIndex < 0 && !acquire()) return VK_NULL_HANDLE;
        return framebuffers[static_cast<std::size_t>(imageIndex)];
    }

    void SurfaceTarget::sync(SubmitSync &sync, VkCommandBuffer commands, bool presenting) {
        if (imageIndex < 0) return;
        presenter.sync(sync, commands, static_cast<u32>(imageIndex), !waited, presenting);
        waited = true;
    }

    bool SurfaceTarget::present() {
        bool presented = presenter.present(static_cast<u32>(imageIndex));
        if (!presented) valid = false;
        return presented;
    }

    void SurfaceTarget::endFrame() {
        imageIndex = -1;
        waited = false;
        startedThisFrame = false;
        written = false;
        if (valid && !sizeMatches()) valid = false;
    }

    bool SurfaceTarget::acquire() {
        for (int attempt = 0; attempt < 2; attempt++) {
            if (!valid && !recreate()) return false;
            if (int index = presenter.acquire(acquireSemaphore); index >= 0) {
                imageIndex = index;
                return true;
            }
            valid = false;
        }
        return false;
    }

    bool SurfaceTarget::recreate() {
        int width = 0, height = 0;
        windowSize(window, &width, &height);
        if (width <= 0 || height <= 0) return false;
        device.waitIdle();
        destroyResources();
        if (!presenter.create(static_cast<u32>(width), static_cast<u32>(height), vsync)) return false;
        log::info("Surface resized to %dx%d", width, height);
        depth = std::make_unique<GpuImage>(device, allocator, presenter.width, presenter.height, 1, pass.depthFormat,
                                           device.depthAspect,
                                           VK_IMAGE_USAGE_DEPTH_STENCIL_ATTACHMENT_BIT);
        framebuffers.resize(presenter.views.size());
        for (std::size_t i = 0; i < presenter.views.size(); i++) {
            VkImageView attachments[2] = {presenter.views[i], depth->view};
            VkFramebufferCreateInfo info{VK_STRUCTURE_TYPE_FRAMEBUFFER_CREATE_INFO};
            info.renderPass = pass.compatible();
            info.attachmentCount = 2;
            info.pAttachments = attachments;
            info.width = presenter.width;
            info.height = presenter.height;
            info.layers = 1;
            check(vkCreateFramebuffer(device.handle, &info, nullptr, &framebuffers[i]), "vkCreateFramebuffer");
        }
        valid = true;
        return true;
    }

    bool SurfaceTarget::sizeMatches() const {
        int width = 0, height = 0;
        windowSize(window, &width, &height);
        return static_cast<u32>(width) == presenter.width && static_cast<u32>(height) == presenter.height;
    }

    void SurfaceTarget::destroyResources() {
        for (VkFramebuffer framebuffer: framebuffers) vkDestroyFramebuffer(device.handle, framebuffer, nullptr);
        framebuffers.clear();
        depth.reset();
        valid = false;
    }
} // namespace at
