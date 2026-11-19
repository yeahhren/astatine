#include "backend/render_passes.h"

#include "core/error.h"
#include "backend/backend.h"
#include "backend/resources.h"

#include <algorithm>
#include <climits>

namespace at::backend {
    namespace {
        std::size_t mix(std::size_t hash, u64 value) {
            return (hash ^ static_cast<std::size_t>(value)) * 1099511628211ull;
        }
    } // namespace

    std::size_t RenderPassCache::PassKeyHash::operator()(const PassKey &key) const {
        std::size_t hash = 1469598103934665603ull;
        for (const Attachment &attachment: key.colors) {
            hash = mix(hash, attachment.format);
            hash = mix(hash, attachment.load);
            hash = mix(hash, attachment.layout);
        }
        hash = mix(hash, key.hasDepth);
        if (key.hasDepth) {
            hash = mix(hash, key.depth.format);
            hash = mix(hash, key.depth.load);
        }
        return hash;
    }

    std::size_t RenderPassCache::FramebufferKeyHash::operator()(const FramebufferKey &key) const {
        std::size_t hash = mix(1469598103934665603ull, reinterpret_cast<u64>(key.pass));
        for (VkImageView view: key.views) hash = mix(hash, reinterpret_cast<u64>(view));
        hash = mix(hash, key.width);
        return mix(hash, key.height);
    }

    RenderPassCache::~RenderPassCache() {
        for (auto &[key, framebuffer]: framebuffers) vkDestroyFramebuffer(device, framebuffer, nullptr);
        for (auto &[key, pass]: passes) vkDestroyRenderPass(device, pass, nullptr);
    }

    VkRenderPass RenderPassCache::get(const VkFormat *colors, u32 count, VkFormat depth) {
        std::vector<VkAttachmentLoadOp> loads(count, VK_ATTACHMENT_LOAD_OP_LOAD);
        return get(colors, count, depth, loads.data(), VK_ATTACHMENT_LOAD_OP_LOAD, VK_IMAGE_LAYOUT_GENERAL);
    }

    VkRenderPass RenderPassCache::get(const VkFormat *colors, u32 count, VkFormat depth, const VkAttachmentLoadOp *loads,
                                      VkAttachmentLoadOp depthLoad, VkImageLayout colorLayout) {
        PassKey key;
        key.colors.reserve(count);
        for (u32 i = 0; i < count; i++) key.colors.push_back({colors[i], loads[i], colorLayout});
        key.hasDepth = depth != VK_FORMAT_UNDEFINED;
        if (key.hasDepth) key.depth = {depth, depthLoad, VK_IMAGE_LAYOUT_GENERAL};
        std::lock_guard guard(lock);
        auto found = passes.find(key);
        if (found != passes.end()) return found->second;
        VkRenderPass pass = create(key);
        passes.emplace(std::move(key), pass);
        return pass;
    }

    VkRenderPass RenderPassCache::create(const PassKey &key) const {
        std::vector<VkAttachmentDescription> attachments;
        std::vector<VkAttachmentReference> colors(key.colors.size());
        auto describe = [](const Attachment &attachment) {
            VkAttachmentDescription description{};
            description.format = attachment.format;
            description.samples = VK_SAMPLE_COUNT_1_BIT;
            description.loadOp = attachment.load;
            description.storeOp = VK_ATTACHMENT_STORE_OP_STORE;
            description.stencilLoadOp = VK_ATTACHMENT_LOAD_OP_DONT_CARE;
            description.stencilStoreOp = VK_ATTACHMENT_STORE_OP_DONT_CARE;
            description.initialLayout = attachment.layout;
            description.finalLayout = attachment.layout;
            return description;
        };
        for (std::size_t i = 0; i < key.colors.size(); i++) {
            const Attachment &color = key.colors[i];
            if (color.format == VK_FORMAT_UNDEFINED) {
                colors[i] = {VK_ATTACHMENT_UNUSED, VK_IMAGE_LAYOUT_UNDEFINED};
            } else {
                colors[i] = {static_cast<u32>(attachments.size()), color.layout};
                attachments.push_back(describe(color));
            }
        }
        VkSubpassDescription subpass{};
        subpass.pipelineBindPoint = VK_PIPELINE_BIND_POINT_GRAPHICS;
        subpass.colorAttachmentCount = static_cast<u32>(colors.size());
        subpass.pColorAttachments = colors.data();
        VkAttachmentReference depthReference{};
        if (key.hasDepth) {
            depthReference = {static_cast<u32>(attachments.size()), key.depth.layout};
            attachments.push_back(describe(key.depth));
            subpass.pDepthStencilAttachment = &depthReference;
        }
        VkRenderPassCreateInfo info{.sType = VK_STRUCTURE_TYPE_RENDER_PASS_CREATE_INFO};
        info.attachmentCount = static_cast<u32>(attachments.size());
        info.pAttachments = attachments.data();
        info.subpassCount = 1;
        info.pSubpasses = &subpass;
        VkRenderPass pass;
        check(vkCreateRenderPass(device, &info, nullptr, &pass), "vkCreateRenderPass");
        return pass;
    }

    VkFramebuffer RenderPassCache::framebuffer(VkRenderPass pass, const VkImageView *views, u32 count, u32 width, u32 height) {
        if (width == 0 || height == 0 || std::any_of(views, views + count, [](VkImageView view) { return !view; })) {
            fail("invalid framebuffer attachments");
        }
        FramebufferKey key{pass, std::vector<VkImageView>(views, views + count), width, height};
        std::lock_guard guard(lock);
        auto found = framebuffers.find(key);
        if (found != framebuffers.end()) return found->second;
        VkFramebufferCreateInfo info{.sType = VK_STRUCTURE_TYPE_FRAMEBUFFER_CREATE_INFO};
        info.renderPass = pass;
        info.attachmentCount = count;
        info.pAttachments = views;
        info.width = width;
        info.height = height;
        info.layers = 1;
        VkFramebuffer framebuffer;
        check(vkCreateFramebuffer(device, &info, nullptr, &framebuffer), "vkCreateFramebuffer");
        framebuffers.emplace(std::move(key), framebuffer);
        return framebuffer;
    }

    void RenderPassCache::begin(VkCommandBuffer commands, VkRenderPass pass, VkRenderPass compatible, const VkImageView *views,
                                u32 count, u32 width, u32 height, VkRect2D area, const VkClearValue *clears, u32 clearCount) {
        if (count == 0 || area.offset.x < 0 || area.offset.y < 0 || area.extent.width == 0 || area.extent.height == 0 ||
            static_cast<u64>(area.offset.x) + area.extent.width > width ||
            static_cast<u64>(area.offset.y) + area.extent.height > height) {
            fail("invalid render area");
        }
        VkRenderPassBeginInfo info{.sType = VK_STRUCTURE_TYPE_RENDER_PASS_BEGIN_INFO};
        info.renderPass = pass;
        info.framebuffer = framebuffer(compatible, views, count, width, height);
        info.renderArea = area;
        info.clearValueCount = clearCount;
        info.pClearValues = clears;
        vkCmdBeginRenderPass(commands, &info, VK_SUBPASS_CONTENTS_INLINE);
    }

    void RenderPassCache::begin(VkCommandBuffer commands, const ColorAttachment *colors, u32 colorCount,
                                const DepthAttachment *depth, VkRect2D area) {
        std::vector<VkFormat> formats(colorCount, VK_FORMAT_UNDEFINED);
        std::vector<VkAttachmentLoadOp> loads(colorCount, VK_ATTACHMENT_LOAD_OP_LOAD);
        std::vector<VkImageView> views;
        std::vector<VkClearValue> clears;
        u32 width = UINT_MAX;
        u32 height = UINT_MAX;
        for (u32 i = 0; i < colorCount; i++) {
            const ColorAttachment &attachment = colors[i];
            if (!attachment.view) continue;
            TextureView *view = attachment.view;
            formats[i] = view->texture->format;
            loads[i] = attachment.clear ? VK_ATTACHMENT_LOAD_OP_CLEAR : VK_ATTACHMENT_LOAD_OP_LOAD;
            VkClearValue clear{};
            if (attachment.clear) clear.color = clearColor(view->texture->format, attachment.color);
            clears.push_back(clear);
            views.push_back(view->handle);
            width = std::min(width, std::max(1u, view->width()));
            height = std::min(height, std::max(1u, view->height()));
        }
        VkFormat depthFormat = VK_FORMAT_UNDEFINED;
        VkAttachmentLoadOp depthLoad = VK_ATTACHMENT_LOAD_OP_LOAD;
        if (depth && depth->view) {
            TextureView *view = depth->view;
            depthFormat = view->texture->format;
            depthLoad = depth->clear ? VK_ATTACHMENT_LOAD_OP_CLEAR : VK_ATTACHMENT_LOAD_OP_LOAD;
            VkClearValue clear{};
            clear.depthStencil.depth = depth->depth;
            clears.push_back(clear);
            views.push_back(view->handle);
            width = std::min(width, std::max(1u, view->width()));
            height = std::min(height, std::max(1u, view->height()));
        }
        VkRenderPass pass = get(formats.data(), colorCount, depthFormat, loads.data(), depthLoad, VK_IMAGE_LAYOUT_GENERAL);
        VkRenderPass compatible = get(formats.data(), colorCount, depthFormat);
        begin(commands, pass, compatible, views.data(), static_cast<u32>(views.size()), width, height, area, clears.data(),
              static_cast<u32>(clears.size()));
    }

    void RenderPassCache::begin(VkCommandBuffer commands, TextureView *const *colors, u32 colorCount, TextureView *depth,
                                VkRect2D area) {
        std::vector<VkFormat> formats(colorCount);
        std::vector<VkImageView> views(colorCount + (depth ? 1 : 0));
        u32 width = depth ? std::max(1u, depth->width()) : UINT_MAX;
        u32 height = depth ? std::max(1u, depth->height()) : UINT_MAX;
        for (u32 i = 0; i < colorCount; i++) {
            formats[i] = colors[i]->texture->format;
            views[i] = colors[i]->handle;
            width = std::min(width, std::max(1u, colors[i]->width()));
            height = std::min(height, std::max(1u, colors[i]->height()));
        }
        VkFormat depthFormat = depth ? depth->texture->format : VK_FORMAT_UNDEFINED;
        if (depth) views[colorCount] = depth->handle;
        VkRenderPass pass = get(formats.data(), colorCount, depthFormat);
        begin(commands, pass, pass, views.data(), static_cast<u32>(views.size()), width, height, area, nullptr, 0);
    }

    void RenderPassCache::begin(VkCommandBuffer commands, VkFormat depthFormat, VkImageView depthView, u32 width, u32 height,
                                VkAttachmentLoadOp depthLoad) {
        VkRenderPass pass = get(nullptr, 0, depthFormat, nullptr, depthLoad, VK_IMAGE_LAYOUT_GENERAL);
        VkRenderPass compatible = get(nullptr, 0, depthFormat);
        VkRect2D area{{0, 0}, {width, height}};
        begin(commands, pass, compatible, &depthView, 1, width, height, area, nullptr, 0);
    }

    void RenderPassCache::release(VkImageView view) {
        std::vector<VkFramebuffer> retired;
        {
            std::lock_guard guard(lock);
            for (auto it = framebuffers.begin(); it != framebuffers.end();) {
                if (std::find(it->first.views.begin(), it->first.views.end(), view) != it->first.views.end()) {
                    retired.push_back(it->second);
                    it = framebuffers.erase(it);
                } else {
                    ++it;
                }
            }
        }
        if (retired.empty()) return;
        VkDevice owner = device;
        Backend::get().defer([owner, retired] {
            for (VkFramebuffer framebuffer: retired) vkDestroyFramebuffer(owner, framebuffer, nullptr);
        });
    }
} // namespace at::backend
