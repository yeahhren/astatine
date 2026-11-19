#pragma once

#include "core/common.h"
#include "core/vk.h"

#include <mutex>
#include <unordered_map>
#include <vector>

namespace at::backend {
    struct TextureView;

    struct ColorAttachment {
        TextureView *view;
        bool clear;
        float color[4];
    };

    struct DepthAttachment {
        TextureView *view;
        bool clear;
        float depth;
    };

    class RenderPassCache {
    public:
        explicit RenderPassCache(VkDevice device) : device(device) {
        }

        ~RenderPassCache();

        RenderPassCache(const RenderPassCache &) = delete;

        RenderPassCache &operator=(const RenderPassCache &) = delete;

        VkRenderPass get(const VkFormat *colors, u32 count, VkFormat depth);

        VkRenderPass get(const VkFormat *colors, u32 count, VkFormat depth, const VkAttachmentLoadOp *loads,
                         VkAttachmentLoadOp depthLoad, VkImageLayout colorLayout);

        void begin(VkCommandBuffer commands, const ColorAttachment *colors, u32 colorCount, const DepthAttachment *depth,
                   VkRect2D area);

        void begin(VkCommandBuffer commands, TextureView *const *colors, u32 colorCount, TextureView *depth, VkRect2D area);

        void begin(VkCommandBuffer commands, VkFormat depthFormat, VkImageView depthView, u32 width, u32 height,
                   VkAttachmentLoadOp depthLoad);

        void release(VkImageView view);

    private:
        struct Attachment {
            VkFormat format;
            VkAttachmentLoadOp load;
            VkImageLayout layout;

            bool operator==(const Attachment &other) const {
                return format == other.format && load == other.load && layout == other.layout;
            }
        };

        struct PassKey {
            std::vector<Attachment> colors;
            bool hasDepth = false;
            Attachment depth{};

            bool operator==(const PassKey &other) const {
                return colors == other.colors && hasDepth == other.hasDepth && (!hasDepth || depth == other.depth);
            }
        };

        struct PassKeyHash {
            std::size_t operator()(const PassKey &key) const;
        };

        struct FramebufferKey {
            VkRenderPass pass;
            std::vector<VkImageView> views;
            u32 width;
            u32 height;

            bool operator==(const FramebufferKey &other) const {
                return pass == other.pass && views == other.views && width == other.width && height == other.height;
            }
        };

        struct FramebufferKeyHash {
            std::size_t operator()(const FramebufferKey &key) const;
        };

        VkRenderPass create(const PassKey &key) const;

        VkFramebuffer framebuffer(VkRenderPass pass, const VkImageView *views, u32 count, u32 width, u32 height);

        void begin(VkCommandBuffer commands, VkRenderPass pass, VkRenderPass compatible, const VkImageView *views, u32 count,
                   u32 width, u32 height, VkRect2D area, const VkClearValue *clears, u32 clearCount);

        VkDevice device;
        std::mutex lock;
        std::unordered_map<PassKey, VkRenderPass, PassKeyHash> passes;
        std::unordered_map<FramebufferKey, VkFramebuffer, FramebufferKeyHash> framebuffers;
    };
} // namespace at::backend
