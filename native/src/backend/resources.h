#pragma once

#include "core/common.h"
#include "core/vk.h"

#include <memory>

namespace at {
    class GpuBuffer;
    class GpuImage;
    class Texture;
}

namespace at::backend {
    namespace buffer_usage {
        constexpr u32 MAP_READ = 1;
        constexpr u32 MAP_WRITE = 2;
        constexpr u32 COPY_DST = 8;
        constexpr u32 COPY_SRC = 16;
        constexpr u32 VERTEX = 32;
        constexpr u32 INDEX = 64;
        constexpr u32 UNIFORM = 128;
        constexpr u32 UNIFORM_TEXEL_BUFFER = 256;
        constexpr u32 INDIRECT = 512;
        constexpr u32 STORAGE = 1u << 20;
        constexpr u32 ALL = COPY_DST | COPY_SRC | VERTEX | INDEX | UNIFORM | UNIFORM_TEXEL_BUFFER | INDIRECT | STORAGE;
    }

    namespace texture_usage {
        constexpr u32 COPY_DST = 1;
        constexpr u32 COPY_SRC = 2;
        constexpr u32 TEXTURE_BINDING = 4;
        constexpr u32 RENDER_ATTACHMENT = 8;
        constexpr u32 CUBEMAP_COMPATIBLE = 16;
        constexpr u32 STORAGE = 1u << 20;
    }

    VkFormat vulkanFormat(u32 gpuFormat);

    i32 gpuFormat(VkFormat format);

    bool isDepthFormat(VkFormat format);

    bool isStencilFormat(VkFormat format);

    bool isIntegerFormat(VkFormat format);

    bool isUnsignedFormat(VkFormat format);

    VkImageAspectFlags aspectMask(VkFormat format);

    VkClearColorValue clearColor(VkFormat format, const float *rgba);

    struct Buffer {
        VkBuffer handle = VK_NULL_HANDLE;
        u64 size = 0;
        u32 usage = 0;
        u8 *mapped = nullptr;
        GpuBuffer *owned = nullptr;

        static Buffer *create(u32 usage, u64 size, bool hostVisible, const char *label);

        static Buffer *wrap(VkBuffer handle, u64 size, u32 usage);

        void release();
    };

    struct Texture {
        VkImage image = VK_NULL_HANDLE;
        VkFormat format = VK_FORMAT_UNDEFINED;
        VkImageType type = VK_IMAGE_TYPE_2D;
        u32 width = 1;
        u32 height = 1;
        u32 depth = 1;
        u32 layers = 1;
        u32 levels = 1;
        u32 usage = 0;
        bool cube = false;
        GpuImage *owned = nullptr;

        static Texture *create(u32 usage, VkFormat format, u32 width, u32 height, u32 layers, u32 levels, const char *label);

        static Texture *createImage(VkImageType type, VkFormat format, u32 width, u32 height, u32 depth, bool storage,
                                    const char *label);

        static Texture *wrap(VkImage image, VkFormat format, u32 width, u32 height, u32 levels, u32 usage);

        [[nodiscard]] u32 levelWidth(u32 level) const { return width >> level > 0 ? width >> level : 1; }
        [[nodiscard]] u32 levelHeight(u32 level) const { return height >> level > 0 ? height >> level : 1; }

        void release();
    };

    struct TextureView {
        Texture *texture = nullptr;
        VkImageView handle = VK_NULL_HANDLE;
        u32 baseLevel = 0;
        u32 levels = 1;
        VkImageLayout layout = VK_IMAGE_LAYOUT_GENERAL;
        bool owned = false;

        static TextureView *create(Texture *texture, u32 baseLevel, u32 levels);

        static TextureView *wrap(Texture *texture, VkImageView handle, u32 baseLevel, u32 levels, VkImageLayout layout);

        [[nodiscard]] u32 width() const { return texture->levelWidth(baseLevel); }
        [[nodiscard]] u32 height() const { return texture->levelHeight(baseLevel); }

        void release();
    };

    struct Sampler {
        VkSampler handle = VK_NULL_HANDLE;
        bool owned = false;

        static Sampler *create(bool clampU, bool clampV, bool linearMin, bool linearMag, u32 maxAnisotropy, float maxLod);

        static Sampler *wrap(VkSampler handle);

        void release();
    };
} // namespace at::backend
