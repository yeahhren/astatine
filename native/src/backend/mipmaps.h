#pragma once

#include "core/common.h"
#include "core/vk.h"

#include <memory>
#include <string>
#include <unordered_map>
#include <vector>

namespace at::backend {
    struct Buffer;
    struct Texture;
    struct TextureView;
    class GraphicsPipeline;

    void blitMipmaps(Texture *texture, bool linear);

    class ColorMipmaps {
    public:
        static constexpr u32 MAX_MIPS = 12;

        ColorMipmaps() = default;

        ~ColorMipmaps();

        ColorMipmaps(const ColorMipmaps &) = delete;

        ColorMipmaps &operator=(const ColorMipmaps &) = delete;

        void setSources(std::string core, std::string spd, std::string spdMain, std::string area, std::string tail);

        bool supports(VkFormat format);

        bool generate(Texture *texture);

        void release(Texture *texture);

    private:
        struct Programs {
            VkPipeline spd;
            VkPipeline area;
        };

        struct Resources {
            std::vector<TextureView *> levels;
            VkDescriptorPool pool;
            std::vector<VkDescriptorSet> sets;
        };

        void initialize();

        Programs createPrograms(VkFormat format);

        VkPipeline compile(const char *name, const std::string &source) const;

        VkDescriptorSet descriptorSet(Resources &resources, u32 base, u32 count) const;

        std::string sourceCore;
        std::string sourceSpd;
        std::string sourceSpdMain;
        std::string sourceArea;
        std::string sourceTail;
        std::unordered_map<VkFormat, bool> supported;
        std::unordered_map<VkFormat, Programs> programs;
        std::unordered_map<Texture *, Resources> resources;
        Buffer *counter = nullptr;
        VkDescriptorSetLayout setLayout = VK_NULL_HANDLE;
        VkPipelineLayout layout = VK_NULL_HANDLE;
        VkSampler sampler = VK_NULL_HANDLE;
        bool subgroups = false;
    };

    class DepthMipmaps {
    public:
        DepthMipmaps() = default;

        ~DepthMipmaps();

        DepthMipmaps(const DepthMipmaps &) = delete;

        DepthMipmaps &operator=(const DepthMipmaps &) = delete;

        void generate(Texture *texture);

        void release(Texture *texture);

    private:
        void initialize();

        std::unordered_map<Texture *, std::vector<TextureView *> > levels;
        VkDescriptorSetLayout setLayout = VK_NULL_HANDLE;
        VkPipelineLayout layout = VK_NULL_HANDLE;
        VkPipeline pipeline = VK_NULL_HANDLE;
        VkSampler sampler = VK_NULL_HANDLE;
    };
} // namespace at::backend
