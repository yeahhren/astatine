#include "backend/encoder.h"

#include "core/error.h"
#include "render/renderer.h"
#include "backend/backend.h"
#include "backend/programs.h"
#include "backend/resources.h"
#include "backend/synchronization.h"
#include "backend/transient.h"
#include "vulkan/device.h"

#include <vector>

namespace at::backend::encoder {
    namespace {
        TransientMemory::Allocation stage(const u8 *source, u64 size, u64 alignment) {
            TransientMemory::Allocation staging = Backend::get().transient->allocate(size, alignment);
            std::memcpy(staging.address, source, size);
            return staging;
        }

        void markExternal(Renderer &renderer, bool inside) {
            renderer.externalPass = inside;
            renderer.state.dirty |= RenderState::ENVIRONMENT | RenderState::VIEWPORT;
        }
    } // namespace

    void clearColorTexture(Texture *texture, const float *rgba) {
        VkCommandBuffer commands = Backend::get().commands();
        memoryBarrier(commands);
        VkClearColorValue value = clearColor(texture->format, rgba);
        VkImageSubresourceRange range{VK_IMAGE_ASPECT_COLOR_BIT, 0, texture->levels, 0, texture->layers};
        vkCmdClearColorImage(commands, texture->image, VK_IMAGE_LAYOUT_GENERAL, &value, 1, &range);
        memoryBarrier(commands);
    }

    void clearDepthTexture(Texture *texture, float depth) {
        VkCommandBuffer commands = Backend::get().commands();
        memoryBarrier(commands);
        VkClearDepthStencilValue value{depth, 0};
        VkImageSubresourceRange range{aspectMask(texture->format), 0, texture->levels, 0, texture->layers};
        vkCmdClearDepthStencilImage(commands, texture->image, VK_IMAGE_LAYOUT_GENERAL, &value, 1, &range);
        memoryBarrier(commands);
    }

    void writeBuffer(Buffer *buffer, u64 offset, const u8 *source, u64 size) {
        if (size == 0) return;
        TransientMemory::Allocation staging = stage(source, size, 4);
        VkCommandBuffer commands = Backend::get().commands();
        memoryBarrier(commands);
        VkBufferCopy region{staging.offset, offset, size};
        vkCmdCopyBuffer(commands, staging.buffer->handle, buffer->handle, 1, &region);
        memoryBarrier(commands);
    }

    void writeTexture(Texture *texture, const u8 *source, u64 size, u32 level, u32 layer, i32 x, i32 y, u32 width,
                      u32 height) {
        if (size == 0) return;
        TransientMemory::Allocation staging = stage(source, size, 16);
        VkCommandBuffer commands = Backend::get().commands();
        memoryBarrier(commands);
        VkBufferImageCopy region{};
        region.bufferOffset = staging.offset;
        region.bufferRowLength = width;
        region.bufferImageHeight = height;
        region.imageSubresource = {aspectMask(texture->format), level, layer, 1};
        region.imageOffset = {x, y, 0};
        region.imageExtent = {width, height, 1};
        vkCmdCopyBufferToImage(commands, staging.buffer->handle, texture->image, VK_IMAGE_LAYOUT_GENERAL, 1, &region);
        memoryBarrier(commands);
    }

    void copyTexture(Texture *source, Texture *destination, u32 level, i32 destinationX, i32 destinationY, i32 sourceX,
                     i32 sourceY, u32 width, u32 height) {
        VkCommandBuffer commands = Backend::get().commands();
        memoryBarrier(commands);
        VkImageCopy region{};
        region.srcSubresource = {aspectMask(source->format), level, 0, 1};
        region.dstSubresource = {aspectMask(destination->format), level, 0, 1};
        region.srcOffset = {sourceX, sourceY, 0};
        region.dstOffset = {destinationX, destinationY, 0};
        region.extent = {width, height, 1};
        vkCmdCopyImage(commands, source->image, VK_IMAGE_LAYOUT_GENERAL, destination->image, VK_IMAGE_LAYOUT_GENERAL, 1,
                       &region);
        memoryBarrier(commands);
    }

    void beginPass(const ColorAttachment *colors, u32 colorCount, const DepthAttachment *depth, VkRect2D area) {
        Backend &backend = Backend::get();
        Renderer &renderer = backend.renderer;
        if (renderer.externalPass) fail("a render pass is already open");
        VkCommandBuffer commands = backend.commands();
        backend.passes->begin(commands, colors, colorCount, depth, area);
        std::vector<VkFormat> formats(colorCount, VK_FORMAT_UNDEFINED);
        for (u32 i = 0; i < colorCount; i++) {
            if (colors[i].view) formats[i] = colors[i].view->texture->format;
        }
        VkFormat depthFormat = depth && depth->view ? depth->view->texture->format : VK_FORMAT_UNDEFINED;
        backend.programs->begin({backend.passes->get(formats.data(), colorCount, depthFormat), colorCount, depthFormat != VK_FORMAT_UNDEFINED});
        VkViewport viewport{
            0, 0, static_cast<float>(area.offset.x + static_cast<i32>(area.extent.width)),
            static_cast<float>(area.offset.y + static_cast<i32>(area.extent.height)), 0, 1
        };
        vkCmdSetViewport(commands, 0, 1, &viewport);
        vkCmdSetScissor(commands, 0, 1, &area);
        markExternal(renderer, true);
    }

    void endPass(bool world) {
        Backend &backend = Backend::get();
        Renderer &renderer = backend.renderer;
        if (!renderer.externalPass) fail("no render pass is open");
        VkCommandBuffer commands = backend.commands();
        vkCmdEndRenderPass(commands);
        backend.programs->end();
        markExternal(renderer, false);
        if (world) backend.sync->afterWorldPass(commands);
        else memoryBarrier(commands);
    }

    void switchAttachments(TextureView *const *colors, u32 colorCount, TextureView *depth, VkRect2D area) {
        Backend &backend = Backend::get();
        VkCommandBuffer commands = backend.commands();
        vkCmdEndRenderPass(commands);
        backend.sync->afterWorldPass(commands);
        backend.passes->begin(commands, colors, colorCount, depth, area);
        backend.programs->end();
    }

    void clearColors(TextureView *const *views, u32 count, const float *rgba) {
        if (count == 0) return;
        Backend &backend = Backend::get();
        VkCommandBuffer commands = backend.commands();
        std::vector<SyncAccess> accesses;
        for (u32 i = 0; i < count; i++) {
            accesses.push_back({
                {true, reinterpret_cast<u64>(views[i]->texture->image)}, VK_PIPELINE_STAGE_TRANSFER_BIT,
                VK_ACCESS_TRANSFER_WRITE_BIT, true
            });
        }
        backend.sync->before(commands, accesses);
        for (u32 i = 0; i < count; i++) {
            Texture *texture = views[i]->texture;
            VkClearColorValue value = clearColor(texture->format, rgba);
            VkImageSubresourceRange range{VK_IMAGE_ASPECT_COLOR_BIT, views[i]->baseLevel, views[i]->levels, 0, texture->layers};
            vkCmdClearColorImage(commands, texture->image, VK_IMAGE_LAYOUT_GENERAL, &value, 1, &range);
        }
        backend.sync->after(commands);
    }

    void copyTextureSynced(Texture *source, Texture *destination, u32 width, u32 height) {
        Backend &backend = Backend::get();
        VkCommandBuffer commands = backend.commands();
        SyncAccess accesses[2] = {
            {{true, reinterpret_cast<u64>(source->image)}, VK_PIPELINE_STAGE_TRANSFER_BIT, VK_ACCESS_TRANSFER_READ_BIT, false},
            {
                {true, reinterpret_cast<u64>(destination->image)}, VK_PIPELINE_STAGE_TRANSFER_BIT, VK_ACCESS_TRANSFER_WRITE_BIT,
                true
            },
        };
        backend.sync->before(commands, accesses, 2);
        copyTexture(source, destination, 0, 0, 0, 0, 0, width, height);
    }

    void mainTargetAccess(Texture *texture, bool general) {
        VkCommandBuffer commands = Backend::get().commands();
        VkImageMemoryBarrier barrier{.sType = VK_STRUCTURE_TYPE_IMAGE_MEMORY_BARRIER};
        barrier.oldLayout = general ? VK_IMAGE_LAYOUT_SHADER_READ_ONLY_OPTIMAL : VK_IMAGE_LAYOUT_GENERAL;
        barrier.newLayout = general ? VK_IMAGE_LAYOUT_GENERAL : VK_IMAGE_LAYOUT_SHADER_READ_ONLY_OPTIMAL;
        barrier.srcAccessMask = VK_ACCESS_MEMORY_READ_BIT | VK_ACCESS_MEMORY_WRITE_BIT;
        barrier.dstAccessMask = VK_ACCESS_MEMORY_READ_BIT | VK_ACCESS_MEMORY_WRITE_BIT;
        barrier.srcQueueFamilyIndex = VK_QUEUE_FAMILY_IGNORED;
        barrier.dstQueueFamilyIndex = VK_QUEUE_FAMILY_IGNORED;
        barrier.image = texture->image;
        barrier.subresourceRange = {VK_IMAGE_ASPECT_COLOR_BIT, 0, 1, 0, 1};
        vkCmdPipelineBarrier(commands, VK_PIPELINE_STAGE_ALL_COMMANDS_BIT, VK_PIPELINE_STAGE_ALL_COMMANDS_BIT, 0, 0, nullptr, 0,
                             nullptr, 1, &barrier);
    }

    Buffer *createStorageBuffer(const char *name, u64 size, const u8 *content, u64 contentSize) {
        Backend &backend = Backend::get();
        if (size == 0 || contentSize > size) fail(std::string("invalid storage buffer size or content: ") + name);
        u64 maximum = backend.properties.limits.maxStorageBufferRange;
        if (size > maximum) {
            fail(std::string("storage buffer exceeds device limit: ") + name + " (" + std::to_string(size) + " > " +
                 std::to_string(maximum) + ")");
        }
        u64 aligned = alignUp(size, 4);
        Buffer *buffer = Buffer::create(buffer_usage::STORAGE | buffer_usage::COPY_SRC | buffer_usage::COPY_DST |
                                        buffer_usage::INDIRECT, aligned, false, name);
        buffer->size = size;
        VkCommandBuffer commands = backend.commands();
        memoryBarrier(commands);
        vkCmdFillBuffer(commands, buffer->handle, 0, VK_WHOLE_SIZE, 0);
        memoryBarrier(commands);
        if (contentSize > 0) {
            u64 staged = alignUp(contentSize, 4);
            TransientMemory::Allocation staging = backend.transient->allocate(staged, 4);
            std::memset(staging.address, 0, staged);
            std::memcpy(staging.address, content, contentSize);
            commands = backend.commands();
            VkBufferCopy region{staging.offset, 0, staged};
            vkCmdCopyBuffer(commands, staging.buffer->handle, buffer->handle, 1, &region);
            memoryBarrier(commands);
        }
        return buffer;
    }

    void uploadImage(Texture *texture, const u8 *source, u64 size) {
        TransientMemory::Allocation staging = stage(source, size, 16);
        VkCommandBuffer commands = Backend::get().commands();
        memoryBarrier(commands);
        VkBufferImageCopy region{};
        region.bufferOffset = staging.offset;
        region.imageSubresource = {VK_IMAGE_ASPECT_COLOR_BIT, 0, 0, 1};
        region.imageExtent = {texture->width, texture->height, texture->depth};
        vkCmdCopyBufferToImage(commands, staging.buffer->handle, texture->image, VK_IMAGE_LAYOUT_GENERAL, 1, &region);
        memoryBarrier(commands);
    }

    void clearImage(Texture *texture) {
        VkCommandBuffer commands = Backend::get().commands();
        memoryBarrier(commands);
        VkClearColorValue value{};
        VkImageSubresourceRange range{VK_IMAGE_ASPECT_COLOR_BIT, 0, 1, 0, 1};
        vkCmdClearColorImage(commands, texture->image, VK_IMAGE_LAYOUT_GENERAL, &value, 1, &range);
        memoryBarrier(commands);
    }

    VkBufferView texelView(Buffer *buffer, u64 offset, u64 length, VkFormat format) {
        Backend &backend = Backend::get();
        VkBufferViewCreateInfo info{.sType = VK_STRUCTURE_TYPE_BUFFER_VIEW_CREATE_INFO};
        info.buffer = buffer->handle;
        info.offset = offset;
        info.range = length;
        info.format = format;
        VkBufferView view;
        check(vkCreateBufferView(backend.device.handle, &info, nullptr, &view), "vkCreateBufferView");
        VkDevice device = backend.device.handle;
        backend.defer([device, view] { vkDestroyBufferView(device, view, nullptr); });
        return view;
    }

    void copyBuffer(Buffer *source, u64 sourceOffset, Buffer *destination, u64 destinationOffset, u64 size) {
        if (size == 0) return;
        VkCommandBuffer commands = Backend::get().commands();
        memoryBarrier(commands);
        VkBufferCopy region{sourceOffset, destinationOffset, size};
        vkCmdCopyBuffer(commands, source->handle, destination->handle, 1, &region);
        memoryBarrier(commands);
    }

    void copyTextureToBuffer(Texture *source, Buffer *destination, u64 offset, u32 level, i32 x, i32 y, u32 width,
                             u32 height) {
        VkCommandBuffer commands = Backend::get().commands();
        memoryBarrier(commands);
        VkBufferImageCopy region{};
        region.bufferOffset = offset;
        region.imageSubresource = {isDepthFormat(source->format) ? VK_IMAGE_ASPECT_DEPTH_BIT : aspectMask(source->format), level, 0, 1};
        region.imageOffset = {x, y, 0};
        region.imageExtent = {width, height, 1};
        vkCmdCopyImageToBuffer(commands, source->image, VK_IMAGE_LAYOUT_GENERAL, destination->handle, 1, &region);
        memoryBarrier(commands);
    }
} // namespace at::backend::encoder
