#pragma once

#include "core/common.h"
#include "core/vk.h"
#include "backend/descriptors.h"
#include "backend/synchronization.h"

#include <list>
#include <memory>
#include <mutex>
#include <string>
#include <unordered_map>
#include <vector>

namespace at::shaderpack {
    class ShaderCompiler;
}

namespace at::backend {
    namespace shader_stage {
        constexpr u32 VERTEX = 0;
        constexpr u32 GEOMETRY = 1;
        constexpr u32 TESS_CONTROL = 2;
        constexpr u32 TESS_EVAL = 3;
        constexpr u32 FRAGMENT = 4;
        constexpr u32 COMPUTE = 5;
        constexpr u32 COUNT = 6;
    }

    VkShaderStageFlagBits shaderStageBit(u32 stage);

    struct ShaderCode {
        u32 stage;
        std::shared_ptr<const std::vector<u32> > words;
    };

    class ShaderCache {
    public:
        explicit ShaderCache(u32 vulkanVersion);

        ~ShaderCache();

        ShaderCode *compile(u32 stage, const std::string &name, const std::string &source);

        std::shared_ptr<const std::vector<u32> > compileWords(u32 stage, const std::string &name, const std::string &source);

    private:
        static constexpr u64 MAX_BYTES = 64ull << 20;

        struct Key {
            u32 stage;
            u64 first;
            u64 second;
            u64 length;

            bool operator==(const Key &other) const {
                return stage == other.stage && first == other.first && second == other.second && length == other.length;
            }
        };

        struct KeyHash {
            std::size_t operator()(const Key &key) const { return key.first ^ (key.second << 1) ^ key.stage; }
        };

        std::unique_ptr<shaderpack::ShaderCompiler> compiler;
        std::mutex lock;
        std::list<Key> order;
        std::unordered_map<Key, std::pair<std::shared_ptr<const std::vector<u32> >, std::list<Key>::iterator>, KeyHash> entries;
        u64 bytes = 0;
    };

    VkShaderModule createModule(const std::vector<u32> &words);

    struct BindingData {
        std::vector<HeapEntry> heap;
        std::vector<HeapEntry> draws;
        std::vector<BufferSlice> buffers;
        std::vector<VkImageView> views;
        bool hasFrame = false;
        BufferSlice frame{};
        std::vector<SyncAccess> accesses;

        void read(const u8 *data, SamplerHeap *samplerHeap);
    };

    class GraphicsPipeline {
    public:
        explicit GraphicsPipeline(const u8 *description);

        ~GraphicsPipeline();

        GraphicsPipeline(const GraphicsPipeline &) = delete;

        GraphicsPipeline &operator=(const GraphicsPipeline &) = delete;

        void bind(VkCommandBuffer commands, const BindingData &bindings);

        void bind(VkCommandBuffer commands, SamplerHeap::Snapshot *heapSnapshot, ResourceSets::Snapshot *uniforms) const;

        void pushConstants(VkCommandBuffer commands, const void *data, u32 size) const;

        void release();

    private:
        void createLayout(ResourceSets *frameSets);

        VkDevice device;
        SamplerHeap *heap = nullptr;
        ResourceSets *uniformSets = nullptr;
        ResourceSets *frameSets = nullptr;
        VkBuffer zeroVertexBuffer = VK_NULL_HANDLE;
        u32 zeroVertexBinding = 0;
        VkDescriptorSetLayout emptySetLayout = VK_NULL_HANDLE;
        VkPipelineLayout layout = VK_NULL_HANDLE;
        VkPipeline pipeline = VK_NULL_HANDLE;
    };

    class ComputePipeline {
    public:
        ComputePipeline(SamplerHeap *heap, ResourceSets *sets, ResourceSets *frameSets, const ShaderCode &code);

        ~ComputePipeline();

        ComputePipeline(const ComputePipeline &) = delete;

        ComputePipeline &operator=(const ComputePipeline &) = delete;

        void dispatch(VkCommandBuffer commands, const BindingData &bindings, u32 x, u32 y, u32 z, VkBuffer indirect,
                      u64 indirectOffset);

        void release();

        u32 localSize[3] = {1, 1, 1};

    private:
        VkDevice device;
        SamplerHeap *heap;
        ResourceSets *sets;
        ResourceSets *frameSets;
        VkDescriptorSetLayout emptySetLayout = VK_NULL_HANDLE;
        VkPipelineLayout layout = VK_NULL_HANDLE;
        VkPipeline pipeline = VK_NULL_HANDLE;
    };
} // namespace at::backend
