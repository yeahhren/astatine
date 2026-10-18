#pragma once

#include "core/common.h"
#include "core/vk.h"
#include "backend/render_passes.h"

namespace at::backend {
    struct Buffer;
    struct Texture;
    struct TextureView;

    namespace encoder {
        void clearColorTexture(Texture *texture, const float *rgba);

        void clearDepthTexture(Texture *texture, float depth);

        void writeBuffer(Buffer *buffer, u64 offset, const u8 *source, u64 size);

        void writeTexture(Texture *texture, const u8 *source, u64 size, u32 level, u32 layer, i32 x, i32 y, u32 width,
                          u32 height);

        void copyTexture(Texture *source, Texture *destination, u32 level, i32 destinationX, i32 destinationY, i32 sourceX,
                         i32 sourceY, u32 width, u32 height);

        void beginPass(const ColorAttachment *colors, u32 colorCount, const DepthAttachment *depth, VkRect2D area);

        void endPass(bool world);

        void switchAttachments(TextureView *const *colors, u32 colorCount, TextureView *depth, VkRect2D area);

        void clearColors(TextureView *const *views, u32 count, const float *rgba);

        void copyTextureSynced(Texture *source, Texture *destination, u32 width, u32 height);

        void mainTargetAccess(Texture *texture, bool general);

        Buffer *createStorageBuffer(const char *name, u64 size, const u8 *content, u64 contentSize);

        void uploadImage(Texture *texture, const u8 *source, u64 size);

        void clearImage(Texture *texture);

        VkBufferView texelView(Buffer *buffer, u64 offset, u64 length, VkFormat format);

        void copyBuffer(Buffer *source, u64 sourceOffset, Buffer *destination, u64 destinationOffset, u64 size);

        void copyTextureToBuffer(Texture *source, Buffer *destination, u64 offset, u32 level, i32 x, i32 y, u32 width,
                                 u32 height);
    } // namespace encoder
} // namespace at::backend
