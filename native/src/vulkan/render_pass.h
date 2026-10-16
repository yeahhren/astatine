#pragma once

#include "core/common.h"
#include "core/vk.h"

namespace at {
    class Device;

    class RenderPassType {
    public:
        enum class Kind { Offscreen, Surface };

        RenderPassType(const Device &device, Kind kind, u32 id, VkFormat colorFormat, VkFormat depthFormat);

        ~RenderPassType();

        RenderPassType(const RenderPassType &) = delete;

        RenderPassType &operator=(const RenderPassType &) = delete;

        VkRenderPass handle(VkAttachmentLoadOp colorLoad, VkAttachmentLoadOp depthLoad);

        VkRenderPass compatible() { return handle(VK_ATTACHMENT_LOAD_OP_DONT_CARE, VK_ATTACHMENT_LOAD_OP_DONT_CARE); }

        [[nodiscard]] bool hasDepth() const { return depthFormat != VK_FORMAT_UNDEFINED; }
        [[nodiscard]] bool flipY() const { return kind == Kind::Surface; }

        const Kind kind;
        const u32 id;
        const VkFormat colorFormat;
        const VkFormat depthFormat;

    private:
        [[nodiscard]] VkRenderPass create(VkAttachmentLoadOp colorLoad, VkAttachmentLoadOp depthLoad) const;

        const Device &device;
        VkRenderPass passes[9] = {};
    };
} // namespace at
