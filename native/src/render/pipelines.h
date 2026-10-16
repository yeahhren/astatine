#pragma once

#include "core/common.h"
#include "core/vk.h"
#include "memory/u64_map.h"

#include <atomic>
#include <condition_variable>
#include <deque>
#include <mutex>
#include <string>
#include <thread>
#include <unordered_map>
#include <unordered_set>
#include <vector>

namespace at {
    class Device;
    class RenderPassType;
    class VertexLayouts;
    struct VertexLayout;

    class ShaderLibrary {
    public:
        explicit ShaderLibrary(const Device &device) : device(device) {
        }

        ~ShaderLibrary();

        VkShaderModule vertex(u32 mask, bool flat);

        VkShaderModule fragment(bool flat, bool bindless);

        VkShaderModule terrainVertex();

        void preload(bool bindless);

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

        void prewarm(VertexLayouts &layouts, RenderPassType *const (&passes)[3]);

        void save(const VertexLayouts &layouts) const;

        void stop();

        VkDescriptorSetLayout frameSetLayout = VK_NULL_HANDLE;
        VkDescriptorSetLayout textureSetLayout = VK_NULL_HANDLE;
        VkPipelineLayout layout = VK_NULL_HANDLE;

    private:
        struct Recipe {
            u64 key;
            const VertexLayout *layout;
            RenderPassType *pass;
        };

        AT_NOINLINE VkPipeline create(u64 key, const VertexLayout &vertexLayout, RenderPassType &pass);

        VkPipeline compile(u64 key, const VertexLayout &vertexLayout, RenderPassType &pass) const;

        void integrate();

        void work();

        const Device &device;
        ShaderLibrary &shaders;
        std::string cacheFile;
        VkPipelineCache cache = VK_NULL_HANDLE;
        U64Map pipelines{256};

        mutable std::mutex lock;
        std::condition_variable compiled;
        std::condition_variable queued;
        std::deque<Recipe> queue;
        std::unordered_set<u64> pending;
        std::vector<std::pair<u64, VkPipeline> > ready;
        std::atomic<bool> hasReady{false};
        std::vector<u64> known;
        std::unordered_set<u64> knownSet;
        bool stopping = false;
        std::vector<std::thread> workers;
    };
} // namespace at
