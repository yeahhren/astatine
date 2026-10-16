#include "render/texture.h"

#include "render/renderer.h"
#include "vulkan/device.h"
#include "vulkan/memory.h"

#include <algorithm>
#include <array>
#include <bit>

namespace at {
    namespace tex {
        constexpr i32 MIN_FILTER = 0x2801, MAG_FILTER = 0x2800, WRAP_S = 0x2802, WRAP_T = 0x2803;
        constexpr i32 MIN_LOD = 0x813A, MAX_LOD = 0x813B, LOD_BIAS = 0x8501, MAX_LEVEL = 0x813D;
        constexpr i32 NEAREST = 0x2600, LINEAR = 0x2601, LINEAR_MIPMAP_NEAREST = 0x2701, NEAREST_MIPMAP_LINEAR = 0x2702;
        constexpr i32 LINEAR_MIPMAP_LINEAR = 0x2703;
        constexpr i32 REPEAT = 0x2901, CLAMP = 0x2900, CLAMP_TO_EDGE = 0x812F, CLAMP_TO_BORDER = 0x812D, MIRRORED_REPEAT
                        = 0x8370;
    } // namespace tex

    Texture::Texture() {
        Renderer::get().textures.insert(this);
    }

    Texture::~Texture() {
        detach();
        Renderer::get().textures.erase(this);
    }

    void Texture::attach(GpuImage *target, bool isRenderTarget) {
        detach();
        image = target;
        renderTarget = isRenderTarget;
        layout = VK_IMAGE_LAYOUT_UNDEFINED;
        Renderer::get().track(this);
    }

    void Texture::detach() {
        if (!image) return;
        Renderer &renderer = Renderer::get();
        GpuImage *owned = renderTarget ? nullptr : image;
        GpuImage *source = image;
        VkImageView oldViews[17];
        std::memcpy(oldViews, views, sizeof(views));
        u64 oldHandles[CACHE_SIZE];
        std::memcpy(oldHandles, cachedHandles, sizeof(cachedHandles));
        renderer.defer(
            [&renderer, source, owned, oldViews = std::to_array(oldViews), oldHandles = std::to_array(oldHandles)] {
                for (u64 handle: oldHandles) {
                    if (handle != EMPTY) renderer.context().textures->release(handle);
                }
                for (VkImageView view: oldViews) {
                    if (view) source->destroyView(view);
                }
                delete owned;
            });
        std::memset(views, 0, sizeof(views));
        for (int i = 0; i < CACHE_SIZE; i++) {
            cachedHandles[i] = EMPTY;
            cachedLevels[i] = 0;
        }
        image = nullptr;
        renderer.state.texturesChanged();
    }

    bool Texture::parameter(i32 name, float value) {
        switch (name) {
            case tex::MIN_FILTER: minFilter = static_cast<i32>(value);
                break;
            case tex::MAG_FILTER: magFilter = static_cast<i32>(value);
                break;
            case tex::WRAP_S: wrapS = static_cast<i32>(value);
                break;
            case tex::WRAP_T: wrapT = static_cast<i32>(value);
                break;
            case tex::MIN_LOD: minLod = value;
                break;
            case tex::MAX_LOD: maxLod = value;
                break;
            case tex::LOD_BIAS: lodBias = value;
                break;
            case tex::MAX_LEVEL: maxLevel = static_cast<i32>(value);
                break;
            default: return false;
        }
        return true;
    }

    u32 Texture::width() const {
        return image ? image->width : 0;
    }

    u32 Texture::height() const {
        return image ? image->height : 0;
    }

    bool Texture::mipmapped() const {
        return minFilter != tex::NEAREST && minFilter != tex::LINEAR;
    }

    bool Texture::linear() const {
        return magFilter == tex::LINEAR || minFilter == tex::LINEAR || minFilter == tex::LINEAR_MIPMAP_NEAREST ||
               minFilter == tex::LINEAR_MIPMAP_LINEAR;
    }

    u32 Texture::lastLevel() const {
        u32 largest = std::max({image->width, image->height, 1u});
        u32 power = static_cast<u32>(std::bit_width(largest)) - 1;
        return std::min<u32>(power, static_cast<u32>(std::max(maxLevel, 0)));
    }

    bool Texture::complete() const {
        if (!image) return false;
        return !mipmapped() || image->levels - 1 >= lastLevel();
    }

    u32 Texture::clampFlags() const {
        bool borderS = wrapS == tex::CLAMP && linear();
        bool borderT = wrapT == tex::CLAMP && linear();
        return (borderS ? CLAMP_S : 0) | (borderT ? CLAMP_T : 0);
    }

    VkSamplerAddressMode Texture::addressMode(i32 wrap) const {
        switch (wrap) {
            case tex::REPEAT: return VK_SAMPLER_ADDRESS_MODE_REPEAT;
            case tex::MIRRORED_REPEAT: return VK_SAMPLER_ADDRESS_MODE_MIRRORED_REPEAT;
            case tex::CLAMP_TO_BORDER: return VK_SAMPLER_ADDRESS_MODE_CLAMP_TO_BORDER;
            case tex::CLAMP: return linear()
                                        ? VK_SAMPLER_ADDRESS_MODE_CLAMP_TO_BORDER
                                        : VK_SAMPLER_ADDRESS_MODE_CLAMP_TO_EDGE;
            case tex::CLAMP_TO_EDGE: return VK_SAMPLER_ADDRESS_MODE_CLAMP_TO_EDGE;
            default: return VK_SAMPLER_ADDRESS_MODE_REPEAT;
        }
    }

    SamplerState Texture::samplerState() const {
        bool minLinear = minFilter == tex::LINEAR || minFilter == tex::LINEAR_MIPMAP_NEAREST || minFilter ==
                         tex::LINEAR_MIPMAP_LINEAR;
        VkSamplerMipmapMode mipmapMode = minFilter == tex::NEAREST_MIPMAP_LINEAR || minFilter ==
                                         tex::LINEAR_MIPMAP_LINEAR
                                             ? VK_SAMPLER_MIPMAP_MODE_LINEAR
                                             : VK_SAMPLER_MIPMAP_MODE_NEAREST;
        auto last = static_cast<float>(lastLevel());
        float lowest = mipmapped() ? clamp(minLod, 0.0f, last) : 0.0f;
        float highest = mipmapped() ? clamp(maxLod, lowest, last) : 0.25f;
        float bias = Renderer::get().context().device->maxSamplerLodBias;
        SamplerState state{};
        std::memset(&state, 0, sizeof(state));
        state.magFilter = magFilter == tex::LINEAR ? VK_FILTER_LINEAR : VK_FILTER_NEAREST;
        state.minFilter = minLinear ? VK_FILTER_LINEAR : VK_FILTER_NEAREST;
        state.mipmapMode = mipmapMode;
        state.addressU = addressMode(wrapS);
        state.addressV = addressMode(wrapT);
        state.minLod = lowest;
        state.maxLod = highest;
        state.lodBias = clamp(lodBias, -bias, bias);
        return state;
    }

    VkImageView Texture::view(u32 levels) {
        if (levels == image->levels) return image->view;
        if (!views[levels]) views[levels] = image->createView(levels);
        return views[levels];
    }

    u64 Texture::handle() {
        if (!image) return EMPTY;
        Renderer &renderer = Renderer::get();
        SamplerState state = samplerState();
        u32 levels = mipmapped() ? lastLevel() + 1 : 1;
        levels = std::min(levels, image->levels);
        for (int i = 0; i < CACHE_SIZE; i++) {
            if (cachedHandles[i] != EMPTY && cachedLevels[i] == levels && cachedStates[i] == state)
                return cachedHandles
                        [i];
        }
        int slot = cacheCursor;
        cacheCursor = (cacheCursor + 1) % CACHE_SIZE;
        if (u64 evicted = cachedHandles[slot]; evicted != EMPTY) {
            renderer.defer([&renderer, evicted] { renderer.context().textures->release(evicted); });
        }
        cachedStates[slot] = state;
        cachedLevels[slot] = levels;
        cachedHandles[slot] = renderer.context().textures->acquire(view(levels),
                                                                   renderer.context().samplers->get(state));
        return cachedHandles[slot];
    }
} // namespace at
