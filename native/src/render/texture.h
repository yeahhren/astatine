#pragma once

#include "core/common.h"
#include "core/vk.h"
#include "vulkan/descriptors.h"

namespace at {
    class GpuImage;

    class Texture {
    public:
        static constexpr u32 CLAMP_S = 1;
        static constexpr u32 CLAMP_T = 2;
        static constexpr u64 EMPTY = ~0ull;

        Texture();

        ~Texture();

        void attach(GpuImage *target, bool isRenderTarget);

        void detach();

        bool parameter(i32 name, float value);

        u64 handle();

        [[nodiscard]] bool complete() const;

        [[nodiscard]] u32 clampFlags() const;

        [[nodiscard]] u32 width() const;

        [[nodiscard]] u32 height() const;

        GpuImage *image = nullptr;
        bool renderTarget = false;
        VkImageLayout layout = VK_IMAGE_LAYOUT_UNDEFINED;
        u64 usedSerial = 0;
        u64 pendingSerial = 0;
        i32 maxLevel = 1000;

    private:
        static constexpr int CACHE_SIZE = 4;

        [[nodiscard]] bool mipmapped() const;

        [[nodiscard]] bool linear() const;

        [[nodiscard]] u32 lastLevel() const;

        [[nodiscard]] SamplerState samplerState() const;

        [[nodiscard]] VkSamplerAddressMode addressMode(i32 wrap) const;

        VkImageView view(u32 levels);

        i32 minFilter = 0x2702;
        i32 magFilter = 0x2601;
        i32 wrapS = 0x2901;
        i32 wrapT = 0x2901;
        float minLod = -1000.0f;
        float maxLod = 1000.0f;
        float lodBias = 0.0f;

        VkImageView views[17] = {};
        SamplerState cachedStates[CACHE_SIZE] = {};
        u32 cachedLevels[CACHE_SIZE] = {};
        u64 cachedHandles[CACHE_SIZE] = {EMPTY, EMPTY, EMPTY, EMPTY};
        int cacheCursor = 0;
    };
} // namespace at
