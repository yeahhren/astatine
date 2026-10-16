#pragma once

#include "core/common.h"
#include "core/vk.h"
#include "memory/u64_map.h"

#include <string>
#include <unordered_map>

namespace at {
    class Device;
    class RenderPassType;
    struct VertexLayout;

    class ShaderLibrary {
    public:
        explicit ShaderLibrary(const Device &device) : device(device) {
        }

        ~ShaderLibrary();

        VkShaderModule vertex(u32 mask, bool flat);

        VkShaderModule fragment(bool flat, bool bindless);

        VkShaderModule terrainVertex();

    private:
        VkShaderModule module(const std::string &module_name);

        const Device &device;
        std::unordered_map<std::string, VkShaderModule> modules;
    };

    class Pipelines {
    public:
        Pipelines(const Device &device, ShaderLibrary &shaders, std::string cacheFile);

        ~Pipelines();

        AT_INLINE VkPipeline get(u64 key, const VertexLayout &vertexLayout, RenderPassType &pass) {
            u64 found = pipelines.get(key);
            if (AT_LIKELY(found != 0)) return reinterpret_cast<VkPipeline>(found);
            return create(key, vertexLayout, pass);
        }

        void save() const;

        VkDescriptorSetLayout frameSetLayout = VK_NULL_HANDLE;
        VkDescriptorSetLayout textureSetLayout = VK_NULL_HANDLE;
        VkPipelineLayout layout = VK_NULL_HANDLE;

    private:
        AT_NOINLINE VkPipeline create(u64 key, const VertexLayout &vertexLayout, RenderPassType &pass);

        const Device &device;
        ShaderLibrary &shaders;
        std::string cacheFile;
        VkPipelineCache cache = VK_NULL_HANDLE;
        U64Map pipelines{256};
    };
} // namespace at
