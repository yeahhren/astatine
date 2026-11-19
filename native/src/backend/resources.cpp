#include "backend/resources.h"

#include "core/error.h"
#include "render/renderer.h"
#include "backend/backend.h"
#include "backend/synchronization.h"
#include "vulkan/device.h"
#include "vulkan/memory.h"

#include <cmath>

namespace at::backend {
    namespace {
        constexpr VkFormat FORMATS[] = {
            VK_FORMAT_R8_UNORM, VK_FORMAT_R8_SNORM, VK_FORMAT_R8G8_UNORM, VK_FORMAT_R8G8_SNORM,
            VK_FORMAT_R8G8B8_UNORM, VK_FORMAT_R8G8B8_SNORM, VK_FORMAT_R8G8B8A8_UNORM, VK_FORMAT_R8G8B8A8_SNORM,
            VK_FORMAT_R16_UNORM, VK_FORMAT_R16_SNORM, VK_FORMAT_R16G16_UNORM, VK_FORMAT_R16G16_SNORM,
            VK_FORMAT_R16G16B16_UNORM, VK_FORMAT_R16G16B16_SNORM, VK_FORMAT_R16G16B16A16_UNORM,
            VK_FORMAT_R16G16B16A16_SNORM, VK_FORMAT_R8_UINT, VK_FORMAT_R8_SINT, VK_FORMAT_R8G8_UINT,
            VK_FORMAT_R8G8_SINT, VK_FORMAT_R8G8B8_UINT, VK_FORMAT_R8G8B8_SINT, VK_FORMAT_R8G8B8A8_UINT,
            VK_FORMAT_R8G8B8A8_SINT, VK_FORMAT_R16_UINT, VK_FORMAT_R16_SINT, VK_FORMAT_R16G16_UINT,
            VK_FORMAT_R16G16_SINT, VK_FORMAT_R16G16B16_UINT, VK_FORMAT_R16G16B16_SINT, VK_FORMAT_R16G16B16A16_UINT,
            VK_FORMAT_R16G16B16A16_SINT, VK_FORMAT_R32_UINT, VK_FORMAT_R32_SINT, VK_FORMAT_R32G32_UINT,
            VK_FORMAT_R32G32_SINT, VK_FORMAT_R32G32B32_UINT, VK_FORMAT_R32G32B32_SINT, VK_FORMAT_R32G32B32A32_UINT,
            VK_FORMAT_R32G32B32A32_SINT, VK_FORMAT_R16_SFLOAT, VK_FORMAT_R16G16_SFLOAT, VK_FORMAT_R16G16B16_SFLOAT,
            VK_FORMAT_R16G16B16A16_SFLOAT, VK_FORMAT_R32_SFLOAT, VK_FORMAT_R32G32_SFLOAT, VK_FORMAT_R32G32B32_SFLOAT,
            VK_FORMAT_R32G32B32A32_SFLOAT, VK_FORMAT_A2B10G10R10_UNORM_PACK32, VK_FORMAT_A2B10G10R10_UINT_PACK32,
            VK_FORMAT_B10G11R11_UFLOAT_PACK32, VK_FORMAT_D32_SFLOAT, VK_FORMAT_D32_SFLOAT_S8_UINT,
            VK_FORMAT_D24_UNORM_S8_UINT, VK_FORMAT_D16_UNORM, VK_FORMAT_S8_UINT, VK_FORMAT_R5G6B5_UNORM_PACK16,
            VK_FORMAT_A1R5G5B5_UNORM_PACK16, VK_FORMAT_E5B9G9R9_UFLOAT_PACK32, VK_FORMAT_B8G8R8A8_UNORM,
        };

        constexpr u32 FORMAT_COUNT = sizeof(FORMATS) / sizeof(FORMATS[0]);

        VkBufferUsageFlags bufferUsage(u32 usage) {
            VkBufferUsageFlags result = VK_BUFFER_USAGE_TRANSFER_DST_BIT | VK_BUFFER_USAGE_TRANSFER_SRC_BIT;
            if (usage & buffer_usage::VERTEX) result |= VK_BUFFER_USAGE_VERTEX_BUFFER_BIT;
            if (usage & buffer_usage::INDEX) result |= VK_BUFFER_USAGE_INDEX_BUFFER_BIT;
            if (usage & buffer_usage::UNIFORM) result |= VK_BUFFER_USAGE_UNIFORM_BUFFER_BIT;
            if (usage & buffer_usage::UNIFORM_TEXEL_BUFFER) result |= VK_BUFFER_USAGE_UNIFORM_TEXEL_BUFFER_BIT;
            if (usage & buffer_usage::INDIRECT) result |= VK_BUFFER_USAGE_INDIRECT_BUFFER_BIT;
            if (usage & buffer_usage::STORAGE) result |= VK_BUFFER_USAGE_STORAGE_BUFFER_BIT;
            return result;
        }

        VkImageUsageFlags textureUsage(u32 usage, VkFormat format) {
            VkImageUsageFlags result = VK_IMAGE_USAGE_TRANSFER_SRC_BIT | VK_IMAGE_USAGE_TRANSFER_DST_BIT;
            if (usage & texture_usage::RENDER_ATTACHMENT) {
                result |= isDepthFormat(format) || isStencilFormat(format)
                              ? VK_IMAGE_USAGE_DEPTH_STENCIL_ATTACHMENT_BIT
                              : VK_IMAGE_USAGE_COLOR_ATTACHMENT_BIT;
            }
            if (usage & texture_usage::TEXTURE_BINDING) result |= VK_IMAGE_USAGE_SAMPLED_BIT;
            if (usage & texture_usage::STORAGE) result |= VK_IMAGE_USAGE_STORAGE_BIT;
            return result;
        }

        void transitionToGeneral(VkImage image, VkImageAspectFlags aspect, u32 levels, u32 layers) {
            VkCommandBuffer commands = Backend::get().commands();
            VkImageMemoryBarrier barrier{.sType = VK_STRUCTURE_TYPE_IMAGE_MEMORY_BARRIER};
            barrier.oldLayout = VK_IMAGE_LAYOUT_UNDEFINED;
            barrier.newLayout = VK_IMAGE_LAYOUT_GENERAL;
            barrier.srcAccessMask = 0;
            barrier.dstAccessMask = VK_ACCESS_MEMORY_READ_BIT | VK_ACCESS_MEMORY_WRITE_BIT;
            barrier.srcQueueFamilyIndex = VK_QUEUE_FAMILY_IGNORED;
            barrier.dstQueueFamilyIndex = VK_QUEUE_FAMILY_IGNORED;
            barrier.image = image;
            barrier.subresourceRange = {aspect, 0, levels, 0, layers};
            vkCmdPipelineBarrier(commands, VK_PIPELINE_STAGE_TOP_OF_PIPE_BIT, VK_PIPELINE_STAGE_ALL_COMMANDS_BIT, 0, 0,
                                 nullptr, 0, nullptr, 1, &barrier);
        }
    } // namespace

    VkFormat vulkanFormat(u32 gpuFormat) {
        if (gpuFormat >= FORMAT_COUNT) fail("unknown renderpearl format " + std::to_string(gpuFormat));
        return FORMATS[gpuFormat];
    }

    i32 gpuFormat(VkFormat format) {
        for (u32 i = 0; i < FORMAT_COUNT; i++) {
            if (FORMATS[i] == format) return static_cast<i32>(i);
        }
        return -1;
    }

    bool isDepthFormat(VkFormat format) {
        return format == VK_FORMAT_D32_SFLOAT || format == VK_FORMAT_D32_SFLOAT_S8_UINT ||
               format == VK_FORMAT_D24_UNORM_S8_UINT || format == VK_FORMAT_D16_UNORM ||
               format == VK_FORMAT_X8_D24_UNORM_PACK32 || format == VK_FORMAT_D16_UNORM_S8_UINT;
    }

    bool isStencilFormat(VkFormat format) {
        return format == VK_FORMAT_S8_UINT || format == VK_FORMAT_D32_SFLOAT_S8_UINT ||
               format == VK_FORMAT_D24_UNORM_S8_UINT || format == VK_FORMAT_D16_UNORM_S8_UINT;
    }

    bool isUnsignedFormat(VkFormat format) {
        switch (format) {
            case VK_FORMAT_R8_UINT:
            case VK_FORMAT_R8G8_UINT:
            case VK_FORMAT_R8G8B8_UINT:
            case VK_FORMAT_R8G8B8A8_UINT:
            case VK_FORMAT_R16_UINT:
            case VK_FORMAT_R16G16_UINT:
            case VK_FORMAT_R16G16B16_UINT:
            case VK_FORMAT_R16G16B16A16_UINT:
            case VK_FORMAT_R32_UINT:
            case VK_FORMAT_R32G32_UINT:
            case VK_FORMAT_R32G32B32_UINT:
            case VK_FORMAT_R32G32B32A32_UINT:
            case VK_FORMAT_A2B10G10R10_UINT_PACK32:
                return true;
            default:
                return false;
        }
    }

    bool isIntegerFormat(VkFormat format) {
        switch (format) {
            case VK_FORMAT_R8_SINT:
            case VK_FORMAT_R8G8_SINT:
            case VK_FORMAT_R8G8B8_SINT:
            case VK_FORMAT_R8G8B8A8_SINT:
            case VK_FORMAT_R16_SINT:
            case VK_FORMAT_R16G16_SINT:
            case VK_FORMAT_R16G16B16_SINT:
            case VK_FORMAT_R16G16B16A16_SINT:
            case VK_FORMAT_R32_SINT:
            case VK_FORMAT_R32G32_SINT:
            case VK_FORMAT_R32G32B32_SINT:
            case VK_FORMAT_R32G32B32A32_SINT:
                return true;
            default:
                return isUnsignedFormat(format);
        }
    }

    VkImageAspectFlags aspectMask(VkFormat format) {
        VkImageAspectFlags result = 0;
        if (isDepthFormat(format)) result |= VK_IMAGE_ASPECT_DEPTH_BIT;
        if (isStencilFormat(format)) result |= VK_IMAGE_ASPECT_STENCIL_BIT;
        return result ? result : VK_IMAGE_ASPECT_COLOR_BIT;
    }

    VkClearColorValue clearColor(VkFormat format, const float *rgba) {
        VkClearColorValue value{};
        bool unsignedFormat = isUnsignedFormat(format);
        bool signedFormat = isIntegerFormat(format) && !unsignedFormat;
        for (int channel = 0; channel < 4; channel++) {
            float component = rgba[channel];
            if (signedFormat) {
                value.int32[channel] = static_cast<i32>(component);
            } else if (unsignedFormat) {
                double clamped = std::fmin(std::fmax(static_cast<double>(component), 0.0), 4294967295.0);
                value.uint32[channel] = static_cast<u32>(static_cast<u64>(clamped));
            } else {
                value.float32[channel] = component;
            }
        }
        return value;
    }

    Buffer *Buffer::create(u32 usage, u64 size, bool hostVisible, const char *label) {
        Backend &backend = Backend::get();
        backend.waitIdle();
        bool mapped = hostVisible || (usage & (buffer_usage::MAP_READ | buffer_usage::MAP_WRITE));
        VkMemoryPropertyFlags required = mapped
                                             ? VK_MEMORY_PROPERTY_HOST_VISIBLE_BIT | VK_MEMORY_PROPERTY_HOST_COHERENT_BIT
                                             : VK_MEMORY_PROPERTY_DEVICE_LOCAL_BIT;
        VkMemoryPropertyFlags preferred = mapped
                                              ? (usage & buffer_usage::MAP_READ
                                                     ? VK_MEMORY_PROPERTY_HOST_CACHED_BIT
                                                     : VK_MEMORY_PROPERTY_DEVICE_LOCAL_BIT)
                                              : 0;
        auto *buffer = new Buffer();
        buffer->owned = new GpuBuffer(backend.device, backend.allocator, size < 4 ? 4 : size, bufferUsage(usage), required,
                                      preferred);
        buffer->handle = buffer->owned->handle;
        buffer->size = size;
        buffer->usage = usage;
        buffer->mapped = mapped ? buffer->owned->mapped() : nullptr;
        backend.setName(VK_OBJECT_TYPE_BUFFER, reinterpret_cast<u64>(buffer->handle), label);
        return buffer;
    }

    Buffer *Buffer::wrap(VkBuffer handle, u64 size, u32 usage) {
        auto *buffer = new Buffer();
        buffer->handle = handle;
        buffer->size = size;
        buffer->usage = usage;
        return buffer;
    }

    void Buffer::release() {
        GpuBuffer *memory = owned;
        Buffer *self = this;
        Backend::get().defer([memory, self] {
            delete memory;
            delete self;
        });
    }

    Texture *Texture::create(u32 usage, VkFormat format, u32 width, u32 height, u32 layers, u32 levels, const char *label) {
        Backend &backend = Backend::get();
        backend.waitIdle();
        bool cube = (usage & texture_usage::CUBEMAP_COMPATIBLE) != 0;
        ImageShape shape{
            .width = width, .height = height, .depth = 1, .layers = layers,
            .viewType = cube ? VK_IMAGE_VIEW_TYPE_CUBE : layers > 1 ? VK_IMAGE_VIEW_TYPE_2D_ARRAY : VK_IMAGE_VIEW_TYPE_2D
        };
        auto *texture = new Texture();
        texture->owned = new GpuImage(backend.device, backend.allocator, shape, levels, format, aspectMask(format),
                                      textureUsage(usage, format));
        texture->image = texture->owned->handle;
        texture->format = format;
        texture->width = width;
        texture->height = height;
        texture->layers = layers;
        texture->levels = levels;
        texture->usage = usage;
        texture->cube = cube;
        backend.setName(VK_OBJECT_TYPE_IMAGE, reinterpret_cast<u64>(texture->image), label);
        transitionToGeneral(texture->image, aspectMask(format), levels, layers);
        return texture;
    }

    Texture *Texture::createImage(VkImageType type, VkFormat format, u32 width, u32 height, u32 depth, bool storage,
                                  const char *label) {
        Backend &backend = Backend::get();
        backend.waitIdle();
        if (type != VK_IMAGE_TYPE_3D) depth = 1;
        if (type == VK_IMAGE_TYPE_1D) height = 1;
        VkImageUsageFlags usage = (storage ? VK_IMAGE_USAGE_STORAGE_BIT : 0) | VK_IMAGE_USAGE_SAMPLED_BIT |
                                  VK_IMAGE_USAGE_TRANSFER_SRC_BIT | VK_IMAGE_USAGE_TRANSFER_DST_BIT;
        VkImageFormatProperties limits{};
        VkResult result = vkGetPhysicalDeviceImageFormatProperties(backend.device.physical, format, type,
                                                                   VK_IMAGE_TILING_OPTIMAL, usage, 0, &limits);
        if (result != VK_SUCCESS || width > limits.maxExtent.width || height > limits.maxExtent.height ||
            depth > limits.maxExtent.depth) {
            fail(std::string("unsupported custom image ") + (label ? label : "") + ": " + std::to_string(width) + "x" +
                 std::to_string(height) + "x" + std::to_string(depth));
        }
        ImageShape shape{
            .width = width, .height = height, .depth = depth, .layers = 1,
            .viewType = type == VK_IMAGE_TYPE_1D ? VK_IMAGE_VIEW_TYPE_1D : type == VK_IMAGE_TYPE_3D
                                                                           ? VK_IMAGE_VIEW_TYPE_3D
                                                                           : VK_IMAGE_VIEW_TYPE_2D
        };
        auto *texture = new Texture();
        texture->owned = new GpuImage(backend.device, backend.allocator, shape, 1, format, VK_IMAGE_ASPECT_COLOR_BIT, usage);
        texture->image = texture->owned->handle;
        texture->format = format;
        texture->type = type;
        texture->width = width;
        texture->height = height;
        texture->depth = depth;
        texture->usage = texture_usage::TEXTURE_BINDING | texture_usage::COPY_SRC | texture_usage::COPY_DST |
                         (storage ? texture_usage::STORAGE : 0);
        backend.setName(VK_OBJECT_TYPE_IMAGE, reinterpret_cast<u64>(texture->image), label);
        backend.setName(VK_OBJECT_TYPE_IMAGE_VIEW, reinterpret_cast<u64>(texture->owned->view), label);
        transitionToGeneral(texture->image, VK_IMAGE_ASPECT_COLOR_BIT, 1, 1);
        return texture;
    }

    Texture *Texture::wrap(VkImage image, VkFormat format, u32 width, u32 height, u32 levels, u32 usage) {
        auto *texture = new Texture();
        texture->image = image;
        texture->format = format;
        texture->width = width;
        texture->height = height;
        texture->levels = levels;
        texture->usage = usage;
        return texture;
    }

    void Texture::release() {
        Backend &backend = Backend::get();
        backend.waitIdle();
        backend.releaseTexture(this);
        GpuImage *memory = owned;
        Texture *self = this;
        backend.defer([memory, self] {
            delete memory;
            delete self;
        });
    }

    TextureView *TextureView::create(Texture *texture, u32 baseLevel, u32 levels) {
        Backend &backend = Backend::get();
        bool cube = texture->layers == 6 && texture->cube;
        VkImageViewCreateInfo info{.sType = VK_STRUCTURE_TYPE_IMAGE_VIEW_CREATE_INFO};
        info.image = texture->image;
        info.viewType = cube ? VK_IMAGE_VIEW_TYPE_CUBE : texture->layers > 1 ? VK_IMAGE_VIEW_TYPE_2D_ARRAY : VK_IMAGE_VIEW_TYPE_2D;
        if (texture->type == VK_IMAGE_TYPE_1D) info.viewType = VK_IMAGE_VIEW_TYPE_1D;
        if (texture->type == VK_IMAGE_TYPE_3D) info.viewType = VK_IMAGE_VIEW_TYPE_3D;
        info.format = texture->format;
        VkImageAspectFlags aspect = isDepthFormat(texture->format) ? VK_IMAGE_ASPECT_DEPTH_BIT : aspectMask(texture->format);
        info.subresourceRange = {aspect, baseLevel, levels, 0, texture->layers};
        VkImageView handle;
        check(vkCreateImageView(backend.device.handle, &info, nullptr, &handle), "vkCreateImageView");
        auto *view = new TextureView();
        view->texture = texture;
        view->handle = handle;
        view->baseLevel = baseLevel;
        view->levels = levels;
        view->layout = VK_IMAGE_LAYOUT_GENERAL;
        view->owned = true;
        return view;
    }

    TextureView *TextureView::wrap(Texture *texture, VkImageView handle, u32 baseLevel, u32 levels, VkImageLayout layout) {
        auto *view = new TextureView();
        view->texture = texture;
        view->handle = handle;
        view->baseLevel = baseLevel;
        view->levels = levels;
        view->layout = layout;
        return view;
    }

    void TextureView::release() {
        Backend &backend = Backend::get();
        backend.waitIdle();
        backend.releaseView(this);
        VkDevice device = backend.device.handle;
        VkImageView image = owned ? handle : VK_NULL_HANDLE;
        TextureView *self = this;
        backend.defer([device, image, self] {
            if (image) vkDestroyImageView(device, image, nullptr);
            delete self;
        });
    }

    Sampler *Sampler::create(bool clampU, bool clampV, bool linearMin, bool linearMag, u32 maxAnisotropy, float maxLod) {
        Backend &backend = Backend::get();
        bool anisotropic = maxAnisotropy > 1 && backend.anisotropy;
        VkSamplerCreateInfo info{.sType = VK_STRUCTURE_TYPE_SAMPLER_CREATE_INFO};
        info.magFilter = linearMag ? VK_FILTER_LINEAR : VK_FILTER_NEAREST;
        info.minFilter = linearMin ? VK_FILTER_LINEAR : VK_FILTER_NEAREST;
        info.mipmapMode = linearMin ? VK_SAMPLER_MIPMAP_MODE_LINEAR : VK_SAMPLER_MIPMAP_MODE_NEAREST;
        info.addressModeU = clampU ? VK_SAMPLER_ADDRESS_MODE_CLAMP_TO_EDGE : VK_SAMPLER_ADDRESS_MODE_REPEAT;
        info.addressModeV = clampV ? VK_SAMPLER_ADDRESS_MODE_CLAMP_TO_EDGE : VK_SAMPLER_ADDRESS_MODE_REPEAT;
        info.addressModeW = info.addressModeU;
        info.anisotropyEnable = anisotropic;
        info.maxAnisotropy = anisotropic ? static_cast<float>(maxAnisotropy) : 1.0f;
        info.minLod = 0.0f;
        info.maxLod = std::isnan(maxLod) ? VK_LOD_CLAMP_NONE : maxLod;
        auto *sampler = new Sampler();
        check(vkCreateSampler(backend.device.handle, &info, nullptr, &sampler->handle), "vkCreateSampler");
        sampler->owned = true;
        return sampler;
    }

    Sampler *Sampler::wrap(VkSampler handle) {
        auto *sampler = new Sampler();
        sampler->handle = handle;
        return sampler;
    }

    void Sampler::release() {
        Backend &backend = Backend::get();
        VkDevice device = backend.device.handle;
        VkSampler sampler = owned ? handle : VK_NULL_HANDLE;
        Sampler *self = this;
        backend.defer([device, sampler, self] {
            if (sampler) vkDestroySampler(device, sampler, nullptr);
            delete self;
        });
    }
} // namespace at::backend
