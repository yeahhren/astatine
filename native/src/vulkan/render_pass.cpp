#include "vulkan/render_pass.h"

#include "core/error.h"
#include "vulkan/device.h"

namespace at {
    static constexpr VkPipelineStageFlags ATTACHMENT_STAGES = VK_PIPELINE_STAGE_COLOR_ATTACHMENT_OUTPUT_BIT |
                                                              VK_PIPELINE_STAGE_EARLY_FRAGMENT_TESTS_BIT |
                                                              VK_PIPELINE_STAGE_LATE_FRAGMENT_TESTS_BIT;
    static constexpr VkAccessFlags ATTACHMENT_WRITES =
            VK_ACCESS_COLOR_ATTACHMENT_WRITE_BIT | VK_ACCESS_DEPTH_STENCIL_ATTACHMENT_WRITE_BIT;
    static constexpr VkAccessFlags ATTACHMENT_ACCESS = ATTACHMENT_WRITES | VK_ACCESS_COLOR_ATTACHMENT_READ_BIT |
                                                       VK_ACCESS_DEPTH_STENCIL_ATTACHMENT_READ_BIT;

    RenderPassType::RenderPassType(const Device &device, Kind kind, u32 id, VkFormat colorFormat, VkFormat depthFormat,
                                   VkImageLayout surfaceLayout)
        : kind(kind), id(id), colorFormat(colorFormat), depthFormat(depthFormat), surfaceLayout(surfaceLayout),
          device(device) {
    }

    RenderPassType::~RenderPassType() {
        for (VkRenderPass &pass: passes) {
            if (pass) vkDestroyRenderPass(device.handle, pass, nullptr);
            pass = VK_NULL_HANDLE;
        }
    }

    VkRenderPass RenderPassType::handle(VkAttachmentLoadOp colorLoad, VkAttachmentLoadOp depthLoad) {
        u32 index = static_cast<u32>(colorLoad) * 3 + (hasDepth() ? static_cast<u32>(depthLoad) : 0);
        if (!passes[index]) passes[index] = create(colorLoad, depthLoad);
        return passes[index];
    }

    VkRenderPass RenderPassType::create(VkAttachmentLoadOp colorLoad, VkAttachmentLoadOp depthLoad) const {
        bool surface = kind == Kind::Surface;
        VkAttachmentDescription attachments[2]{};
        attachments[0].format = colorFormat;
        attachments[0].samples = VK_SAMPLE_COUNT_1_BIT;
        attachments[0].loadOp = colorLoad;
        attachments[0].storeOp = VK_ATTACHMENT_STORE_OP_STORE;
        attachments[0].stencilLoadOp = VK_ATTACHMENT_LOAD_OP_DONT_CARE;
        attachments[0].stencilStoreOp = VK_ATTACHMENT_STORE_OP_DONT_CARE;
        if (surface) {
            attachments[0].initialLayout = colorLoad == VK_ATTACHMENT_LOAD_OP_LOAD
                                               ? surfaceLayout
                                               : VK_IMAGE_LAYOUT_UNDEFINED;
            attachments[0].finalLayout = surfaceLayout;
        } else {
            attachments[0].initialLayout = VK_IMAGE_LAYOUT_SHADER_READ_ONLY_OPTIMAL;
            attachments[0].finalLayout = VK_IMAGE_LAYOUT_SHADER_READ_ONLY_OPTIMAL;
        }
        VkAttachmentReference colorReference{.attachment = 0, .layout = VK_IMAGE_LAYOUT_COLOR_ATTACHMENT_OPTIMAL};
        VkAttachmentReference depthReference{.attachment = 1, .layout = VK_IMAGE_LAYOUT_DEPTH_STENCIL_ATTACHMENT_OPTIMAL};
        VkSubpassDescription subpass{};
        subpass.pipelineBindPoint = VK_PIPELINE_BIND_POINT_GRAPHICS;
        subpass.colorAttachmentCount = 1;
        subpass.pColorAttachments = &colorReference;
        if (hasDepth()) {
            attachments[1].format = depthFormat;
            attachments[1].samples = VK_SAMPLE_COUNT_1_BIT;
            attachments[1].loadOp = depthLoad;
            attachments[1].storeOp = surface ? VK_ATTACHMENT_STORE_OP_DONT_CARE : VK_ATTACHMENT_STORE_OP_STORE;
            attachments[1].stencilLoadOp = VK_ATTACHMENT_LOAD_OP_DONT_CARE;
            attachments[1].stencilStoreOp = VK_ATTACHMENT_STORE_OP_DONT_CARE;
            attachments[1].initialLayout = !surface || depthLoad == VK_ATTACHMENT_LOAD_OP_LOAD
                                               ? VK_IMAGE_LAYOUT_DEPTH_STENCIL_ATTACHMENT_OPTIMAL
                                               : VK_IMAGE_LAYOUT_UNDEFINED;
            attachments[1].finalLayout = VK_IMAGE_LAYOUT_DEPTH_STENCIL_ATTACHMENT_OPTIMAL;
            subpass.pDepthStencilAttachment = &depthReference;
        }
        VkSubpassDependency dependencies[2]{};
        dependencies[0].srcSubpass = VK_SUBPASS_EXTERNAL;
        dependencies[0].dstSubpass = 0;
        dependencies[0].srcStageMask = ATTACHMENT_STAGES | VK_PIPELINE_STAGE_FRAGMENT_SHADER_BIT |
                                       VK_PIPELINE_STAGE_TRANSFER_BIT;
        dependencies[0].srcAccessMask = ATTACHMENT_WRITES | VK_ACCESS_TRANSFER_WRITE_BIT;
        dependencies[0].dstStageMask = ATTACHMENT_STAGES;
        dependencies[0].dstAccessMask = ATTACHMENT_ACCESS;
        dependencies[1].srcSubpass = 0;
        dependencies[1].dstSubpass = VK_SUBPASS_EXTERNAL;
        dependencies[1].srcStageMask = ATTACHMENT_STAGES;
        dependencies[1].srcAccessMask = ATTACHMENT_WRITES;
        dependencies[1].dstStageMask = ATTACHMENT_STAGES | VK_PIPELINE_STAGE_FRAGMENT_SHADER_BIT |
                                       VK_PIPELINE_STAGE_TRANSFER_BIT;
        dependencies[1].dstAccessMask = ATTACHMENT_ACCESS | VK_ACCESS_SHADER_READ_BIT | VK_ACCESS_TRANSFER_READ_BIT;

        VkRenderPassCreateInfo info{.sType = VK_STRUCTURE_TYPE_RENDER_PASS_CREATE_INFO};
        info.attachmentCount = hasDepth() ? 2 : 1;
        info.pAttachments = attachments;
        info.subpassCount = 1;
        info.pSubpasses = &subpass;
        info.dependencyCount = 2;
        info.pDependencies = dependencies;
        VkRenderPass pass;
        check(vkCreateRenderPass(device.handle, &info, nullptr, &pass), "vkCreateRenderPass");
        return pass;
    }
} // namespace at
