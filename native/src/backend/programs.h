#pragma once

#include "core/common.h"
#include "core/vk.h"

#include <memory>
#include <mutex>
#include <unordered_map>
#include <vector>

namespace at::backend {
    namespace resource_type {
        constexpr u32 SAMPLER = 0;
        constexpr u32 UNIFORM_BUFFER = 1;
        constexpr u32 TEXEL_BUFFER = 2;
    }

    struct PassTarget {
        VkRenderPass compatible = VK_NULL_HANDLE;
        u32 colorCount = 0;
        bool depth = false;
    };

    class Program {
    public:
        explicit Program(const u8 *description);

        ~Program();

        Program(const Program &) = delete;

        Program &operator=(const Program &) = delete;

        VkPipeline variant(const PassTarget &target);

        void release();

        struct Binding {
            u32 binding;
            u32 type;
        };

        struct Color {
            u32 writeMask;
            u32 blend;
            u32 sourceColor;
            u32 destinationColor;
            u32 colorOperation;
            u32 sourceAlpha;
            u32 destinationAlpha;
            u32 alphaOperation;
        };

        VkPipelineLayout layout = VK_NULL_HANDLE;
        std::vector<Binding> bindings;

    private:
        VkDevice device;
        VkDescriptorSetLayout setLayout = VK_NULL_HANDLE;
        VkShaderModule vertex = VK_NULL_HANDLE;
        VkShaderModule fragment = VK_NULL_HANDLE;
        std::vector<VkVertexInputAttributeDescription> attributes;
        std::vector<VkVertexInputBindingDescription> vertexBindings;
        std::vector<VkVertexInputBindingDivisorDescriptionEXT> divisors;
        std::vector<Color> colors;
        u32 polygonMode = 0;
        u32 cull = 0;
        u32 topology = 0;
        u32 hasDepthState = 0;
        u32 depthWrite = 0;
        u32 depthCompare = 0;
        float biasConstant = 0.0f;
        float biasSlope = 0.0f;
        std::mutex lock;
        std::unordered_map<VkRenderPass, VkPipeline> variants;
    };

    class ProgramState {
    public:
        struct Resource {
            u32 type = 0;
            VkImageView view = VK_NULL_HANDLE;
            VkImageLayout layout = VK_IMAGE_LAYOUT_GENERAL;
            VkSampler sampler = VK_NULL_HANDLE;
            VkBuffer buffer = VK_NULL_HANDLE;
            u64 offset = 0;
            u64 length = 0;
            VkBufferView texel = VK_NULL_HANDLE;
            bool set = false;
        };

        void begin(const PassTarget &target);

        void end();

        void use(Program *next);

        void setResource(u32 index, const Resource &resource);

        void pushConstants(VkCommandBuffer commands, const void *data, u32 size) const;

        void prepare(VkCommandBuffer commands);

        [[nodiscard]] bool active() const { return program != nullptr; }

    private:
        PassTarget target;
        Program *program = nullptr;
        VkPipeline bound = VK_NULL_HANDLE;
        std::vector<Resource> resources;
        bool dirty = false;
    };
} // namespace at::backend
