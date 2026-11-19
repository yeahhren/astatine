#include "backend/pipelines.h"

#include "core/error.h"
#include "core/log.h"
#include "backend/backend.h"
#include "backend/render_passes.h"
#include "backend/resources.h"
#include "shaderpack/compiler.h"
#include "vulkan/device.h"

#include <mutex>
#include <unordered_set>

namespace at::backend {
    namespace {
        struct GraphicsDescription {
            u64 heap;
            u64 uniformSets;
            u64 frameSets;
            u64 zeroVertexBuffer;
            u64 stages[shader_stage::COUNT];
            u32 zeroVertexBinding;
            u32 colorCount;
            i32 depthFormat;
            u32 world;
            u32 polygonMode;
            u32 cull;
            u32 frontFaceClockwise;
            u32 topology;
            u32 depthTest;
            u32 depthWrite;
            u32 depthCompare;
            float biasConstant;
            float biasSlope;
            u32 attributeCount;
            u32 bindingCount;
            u32 reserved;
        };

        static_assert(sizeof(GraphicsDescription) == 144);

        struct ColorDescription {
            i32 format;
            u32 writeMask;
            u32 blend;
            u32 sourceColor;
            u32 destinationColor;
            u32 sourceAlpha;
            u32 destinationAlpha;
        };

        struct AttributeDescription {
            u32 location;
            u32 binding;
            u32 format;
            u32 offset;
        };

        struct BindingDescription {
            u32 binding;
            u32 stride;
            u32 instanced;
            u32 divisor;
        };

        struct HeapRecord {
            u32 binding;
            u32 index;
            u32 texel;
            u32 samplerKind;
            u64 view;
            u64 sampler;
        };

        struct BufferRecord {
            u64 buffer;
            u64 offset;
            u64 length;
        };

        VkPrimitiveTopology topology(u32 value) {
            switch (value) {
                case 1: return VK_PRIMITIVE_TOPOLOGY_LINE_LIST;
                case 2: return VK_PRIMITIVE_TOPOLOGY_LINE_STRIP;
                case 3: return VK_PRIMITIVE_TOPOLOGY_POINT_LIST;
                case 5: return VK_PRIMITIVE_TOPOLOGY_TRIANGLE_STRIP;
                case 6: return VK_PRIMITIVE_TOPOLOGY_TRIANGLE_FAN;
                default: return VK_PRIMITIVE_TOPOLOGY_TRIANGLE_LIST;
            }
        }

        VkCompareOp compareOp(u32 value) {
            constexpr VkCompareOp operations[] = {
                VK_COMPARE_OP_ALWAYS, VK_COMPARE_OP_LESS, VK_COMPARE_OP_LESS_OR_EQUAL, VK_COMPARE_OP_EQUAL,
                VK_COMPARE_OP_NOT_EQUAL, VK_COMPARE_OP_GREATER_OR_EQUAL, VK_COMPARE_OP_GREATER, VK_COMPARE_OP_NEVER
            };
            return value < 8 ? operations[value] : VK_COMPARE_OP_ALWAYS;
        }

        VkBlendFactor blendFactor(u32 gl) {
            switch (gl) {
                case 0: return VK_BLEND_FACTOR_ZERO;
                case 1: return VK_BLEND_FACTOR_ONE;
                case 0x300: return VK_BLEND_FACTOR_SRC_COLOR;
                case 0x301: return VK_BLEND_FACTOR_ONE_MINUS_SRC_COLOR;
                case 0x306: return VK_BLEND_FACTOR_DST_COLOR;
                case 0x307: return VK_BLEND_FACTOR_ONE_MINUS_DST_COLOR;
                case 0x302: return VK_BLEND_FACTOR_SRC_ALPHA;
                case 0x303: return VK_BLEND_FACTOR_ONE_MINUS_SRC_ALPHA;
                case 0x304: return VK_BLEND_FACTOR_DST_ALPHA;
                case 0x305: return VK_BLEND_FACTOR_ONE_MINUS_DST_ALPHA;
                case 0x8001: return VK_BLEND_FACTOR_CONSTANT_COLOR;
                case 0x8002: return VK_BLEND_FACTOR_ONE_MINUS_CONSTANT_COLOR;
                case 0x8003: return VK_BLEND_FACTOR_CONSTANT_ALPHA;
                case 0x8004: return VK_BLEND_FACTOR_ONE_MINUS_CONSTANT_ALPHA;
                case 0x308: return VK_BLEND_FACTOR_SRC_ALPHA_SATURATE;
                default: fail("unsupported blend factor: " + std::to_string(gl));
            }
        }

        bool blendSupported(VkFormat format) {
            static std::mutex lock;
            static std::unordered_set<VkFormat> unsupported;
            if (Backend::get().formatFeatures(format, VK_FORMAT_FEATURE_COLOR_ATTACHMENT_BLEND_BIT)) return true;
            std::lock_guard guard(lock);
            if (unsupported.insert(format).second) {
                log::warn("Blending is not supported for Vulkan format %d. Disabling blending for attachments using this format.",
                          static_cast<int>(format));
            }
            return false;
        }

        VkDescriptorSetLayout emptyLayout(VkDevice device) {
            VkDescriptorSetLayoutCreateInfo info{.sType = VK_STRUCTURE_TYPE_DESCRIPTOR_SET_LAYOUT_CREATE_INFO};
            VkDescriptorSetLayout layout;
            check(vkCreateDescriptorSetLayout(device, &info, nullptr, &layout), "vkCreateDescriptorSetLayout");
            return layout;
        }

        u64 hashSource(const std::string &source, u64 seed, u64 prime) {
            u64 hash = seed;
            for (unsigned char character: source) hash = (hash ^ character) * prime;
            return hash;
        }
    } // namespace

    VkShaderStageFlagBits shaderStageBit(u32 stage) {
        switch (stage) {
            case shader_stage::VERTEX: return VK_SHADER_STAGE_VERTEX_BIT;
            case shader_stage::GEOMETRY: return VK_SHADER_STAGE_GEOMETRY_BIT;
            case shader_stage::TESS_CONTROL: return VK_SHADER_STAGE_TESSELLATION_CONTROL_BIT;
            case shader_stage::TESS_EVAL: return VK_SHADER_STAGE_TESSELLATION_EVALUATION_BIT;
            case shader_stage::FRAGMENT: return VK_SHADER_STAGE_FRAGMENT_BIT;
            default: return VK_SHADER_STAGE_COMPUTE_BIT;
        }
    }

    ShaderCache::ShaderCache(u32 vulkanVersion) : compiler(std::make_unique<shaderpack::ShaderCompiler>(vulkanVersion)) {
    }

    ShaderCache::~ShaderCache() = default;

    std::shared_ptr<const std::vector<u32> > ShaderCache::compileWords(u32 stage, const std::string &name,
                                                                       const std::string &source) {
        Key key{
            stage, hashSource(source, 1469598103934665603ull, 1099511628211ull),
            hashSource(source, 0x84222325cbf29ce4ull, 0x100000001b3ull ^ 0x5bd1e995ull), source.size()
        };
        {
            std::lock_guard guard(lock);
            auto found = entries.find(key);
            if (found != entries.end()) {
                order.splice(order.end(), order, found->second.second);
                return found->second.first;
            }
        }
        shaderpack::CompiledShader compiled = compiler->compile(static_cast<shaderpack::ShaderStage>(stage), name, source);
        if (!compiled.ok()) fail("failed to compile " + name + ": " + compiled.log);
        auto words = std::make_shared<const std::vector<u32> >(std::move(compiled.code));
        std::lock_guard guard(lock);
        auto found = entries.find(key);
        if (found != entries.end()) return found->second.first;
        u64 size = words->size() * sizeof(u32);
        if (size <= MAX_BYTES) {
            order.push_back(key);
            entries.emplace(key, std::make_pair(words, std::prev(order.end())));
            bytes += size;
            while (bytes > MAX_BYTES && !order.empty()) {
                auto oldest = entries.find(order.front());
                bytes -= oldest->second.first->size() * sizeof(u32);
                entries.erase(oldest);
                order.pop_front();
            }
        }
        return words;
    }

    ShaderCode *ShaderCache::compile(u32 stage, const std::string &name, const std::string &source) {
        return new ShaderCode{stage, compileWords(stage, name, source)};
    }

    VkShaderModule createModule(const std::vector<u32> &words) {
        if (words.empty()) fail("invalid SPIR-V buffer");
        VkShaderModuleCreateInfo info{.sType = VK_STRUCTURE_TYPE_SHADER_MODULE_CREATE_INFO};
        info.codeSize = words.size() * sizeof(u32);
        info.pCode = words.data();
        VkShaderModule module;
        check(vkCreateShaderModule(Backend::get().device.handle, &info, nullptr, &module), "vkCreateShaderModule");
        return module;
    }

    void BindingData::read(const u8 *data, SamplerHeap *samplerHeap) {
        u32 heapCount = load<u32>(data);
        u32 drawCount = load<u32>(data + 4);
        u32 bufferCount = load<u32>(data + 8);
        u32 viewCount = load<u32>(data + 12);
        hasFrame = load<u32>(data + 16) != 0;
        u32 accessCount = load<u32>(data + 20);
        const u8 *cursor = data + 24;
        auto readHeap = [&](std::vector<HeapEntry> &target, u32 count) {
            target.resize(count);
            for (u32 i = 0; i < count; i++) {
                auto record = load<HeapRecord>(cursor);
                cursor += sizeof(HeapRecord);
                HeapEntry &entry = target[i];
                entry.binding = record.binding;
                entry.index = record.index;
                entry.texel = record.texel;
                if (record.texel) {
                    entry.view = record.view;
                    entry.sampler = 0;
                    entry.layout = 0;
                } else {
                    auto *view = pointer<TextureView>(static_cast<i64>(record.view));
                    entry.view = reinterpret_cast<u64>(view->handle);
                    entry.layout = static_cast<u32>(view->layout);
                    entry.sampler = record.samplerKind == 1
                                        ? reinterpret_cast<u64>(pointer<Sampler>(static_cast<i64>(record.sampler))->handle)
                                        : reinterpret_cast<u64>(samplerHeap->sampler(static_cast<u32>(record.sampler)));
                }
            }
        };
        readHeap(heap, heapCount);
        readHeap(draws, drawCount);
        buffers.resize(bufferCount);
        for (u32 i = 0; i < bufferCount; i++) {
            auto record = load<BufferRecord>(cursor);
            cursor += sizeof(BufferRecord);
            buffers[i] = {pointer<Buffer>(static_cast<i64>(record.buffer))->handle, record.offset, record.length};
        }
        views.resize(viewCount);
        for (u32 i = 0; i < viewCount; i++) {
            views[i] = pointer<TextureView>(load<i64>(cursor))->handle;
            cursor += 8;
        }
        if (hasFrame) {
            auto record = load<BufferRecord>(cursor);
            cursor += sizeof(BufferRecord);
            frame = {pointer<Buffer>(static_cast<i64>(record.buffer))->handle, record.offset, record.length};
        }
        accesses.resize(accessCount);
        for (u32 i = 0; i < accessCount; i++) {
            accesses[i] = Synchronization::decode(load<AccessRecord>(cursor), 1);
            cursor += sizeof(AccessRecord);
        }
    }

    void GraphicsPipeline::createLayout(ResourceSets *frame) {
        if (!uniformSets) emptySetLayout = emptyLayout(device);
        std::vector<VkDescriptorSetLayout> layouts = heap->pipelineSetLayouts(
            uniformSets ? uniformSets->layout : emptySetLayout, frame ? frame->layout : VK_NULL_HANDLE);
        VkPushConstantRange range{VK_SHADER_STAGE_ALL_GRAPHICS, 0, 128};
        VkPipelineLayoutCreateInfo info{.sType = VK_STRUCTURE_TYPE_PIPELINE_LAYOUT_CREATE_INFO};
        info.setLayoutCount = static_cast<u32>(layouts.size());
        info.pSetLayouts = layouts.data();
        info.pushConstantRangeCount = 1;
        info.pPushConstantRanges = &range;
        check(vkCreatePipelineLayout(device, &info, nullptr, &layout), "vkCreatePipelineLayout");
    }

    GraphicsPipeline::GraphicsPipeline(const u8 *data) : device(Backend::get().device.handle) {
        Backend &backend = Backend::get();
        auto description = load<GraphicsDescription>(data);
        const u8 *cursor = data + sizeof(GraphicsDescription);
        heap = pointer<SamplerHeap>(static_cast<i64>(description.heap));
        uniformSets = pointer<ResourceSets>(static_cast<i64>(description.uniformSets));
        frameSets = pointer<ResourceSets>(static_cast<i64>(description.frameSets));
        if (description.zeroVertexBuffer) {
            zeroVertexBuffer = pointer<Buffer>(static_cast<i64>(description.zeroVertexBuffer))->handle;
            zeroVertexBinding = description.zeroVertexBinding;
        }
        bool tessellation = description.stages[shader_stage::TESS_CONTROL] != 0;
        if (!description.stages[shader_stage::VERTEX] || !description.stages[shader_stage::FRAGMENT] ||
            description.stages[shader_stage::COMPUTE] || tessellation != (description.stages[shader_stage::TESS_EVAL] != 0)) {
            fail("invalid graphics shader stages");
        }
        std::vector<VkFormat> formats(description.colorCount);
        std::vector<VkPipelineColorBlendAttachmentState> blends(description.colorCount);
        for (u32 i = 0; i < description.colorCount; i++) {
            auto color = load<ColorDescription>(cursor);
            cursor += sizeof(ColorDescription);
            formats[i] = color.format < 0 ? VK_FORMAT_UNDEFINED : vulkanFormat(static_cast<u32>(color.format));
            bool blend = color.blend && color.writeMask != 0 && formats[i] != VK_FORMAT_UNDEFINED &&
                         !isIntegerFormat(formats[i]) && blendSupported(formats[i]);
            VkPipelineColorBlendAttachmentState &state = blends[i];
            state.blendEnable = blend;
            state.srcColorBlendFactor = blend ? blendFactor(color.sourceColor) : VK_BLEND_FACTOR_ONE;
            state.dstColorBlendFactor = blend ? blendFactor(color.destinationColor) : VK_BLEND_FACTOR_ZERO;
            state.colorBlendOp = VK_BLEND_OP_ADD;
            state.srcAlphaBlendFactor = blend ? blendFactor(color.sourceAlpha) : VK_BLEND_FACTOR_ONE;
            state.dstAlphaBlendFactor = blend ? blendFactor(color.destinationAlpha) : VK_BLEND_FACTOR_ZERO;
            state.alphaBlendOp = VK_BLEND_OP_ADD;
            state.colorWriteMask = color.writeMask & 15;
        }
        if (description.colorCount > 8) fail("too many color blend attachments");
        std::vector<VkVertexInputAttributeDescription> attributes(description.attributeCount);
        for (u32 i = 0; i < description.attributeCount; i++) {
            auto attribute = load<AttributeDescription>(cursor);
            cursor += sizeof(AttributeDescription);
            attributes[i] = {attribute.location, attribute.binding, vulkanFormat(attribute.format), attribute.offset};
        }
        std::vector<VkVertexInputBindingDescription> bindings(description.bindingCount);
        std::vector<VkVertexInputBindingDivisorDescriptionEXT> divisors;
        for (u32 i = 0; i < description.bindingCount; i++) {
            auto binding = load<BindingDescription>(cursor);
            cursor += sizeof(BindingDescription);
            bindings[i] = {
                binding.binding, binding.stride, binding.instanced ? VK_VERTEX_INPUT_RATE_INSTANCE : VK_VERTEX_INPUT_RATE_VERTEX
            };
            if (binding.instanced && binding.divisor != 1) divisors.push_back({binding.binding, binding.divisor});
        }
        VkFormat depthFormat = description.depthFormat < 0
                                   ? VK_FORMAT_UNDEFINED
                                   : vulkanFormat(static_cast<u32>(description.depthFormat));
        if (formats.empty() && depthFormat == VK_FORMAT_UNDEFINED) fail("color formats and blend states must match");

        std::vector<VkShaderModule> modules;
        std::vector<VkPipelineShaderStageCreateInfo> stages;
        try {
            createLayout(frameSets);
            for (u32 stage = 0; stage < shader_stage::COUNT; stage++) {
                if (!description.stages[stage]) continue;
                auto *code = pointer<ShaderCode>(static_cast<i64>(description.stages[stage]));
                modules.push_back(createModule(*code->words));
                VkPipelineShaderStageCreateInfo info{.sType = VK_STRUCTURE_TYPE_PIPELINE_SHADER_STAGE_CREATE_INFO};
                info.stage = shaderStageBit(stage);
                info.module = modules.back();
                info.pName = "main";
                stages.push_back(info);
            }
            VkPipelineRasterizationStateCreateInfo raster{.sType = VK_STRUCTURE_TYPE_PIPELINE_RASTERIZATION_STATE_CREATE_INFO};
            raster.polygonMode = description.world && description.polygonMode == 1 ? VK_POLYGON_MODE_LINE : VK_POLYGON_MODE_FILL;
            raster.cullMode = description.cull ? VK_CULL_MODE_BACK_BIT : VK_CULL_MODE_NONE;
            raster.frontFace = description.frontFaceClockwise ? VK_FRONT_FACE_CLOCKWISE : VK_FRONT_FACE_COUNTER_CLOCKWISE;
            raster.lineWidth = 1.0f;
            raster.depthBiasEnable = description.biasConstant != 0.0f || description.biasSlope != 0.0f;
            raster.depthBiasConstantFactor = description.biasConstant;
            raster.depthBiasSlopeFactor = description.biasSlope;
            VkPipelineDepthStencilStateCreateInfo depth{.sType = VK_STRUCTURE_TYPE_PIPELINE_DEPTH_STENCIL_STATE_CREATE_INFO};
            depth.depthTestEnable = description.depthTest;
            depth.depthWriteEnable = description.depthWrite;
            depth.depthCompareOp = compareOp(description.depthCompare);
            VkPipelineVertexInputStateCreateInfo vertexInput{.sType = VK_STRUCTURE_TYPE_PIPELINE_VERTEX_INPUT_STATE_CREATE_INFO};
            vertexInput.vertexBindingDescriptionCount = static_cast<u32>(bindings.size());
            vertexInput.pVertexBindingDescriptions = bindings.data();
            vertexInput.vertexAttributeDescriptionCount = static_cast<u32>(attributes.size());
            vertexInput.pVertexAttributeDescriptions = attributes.data();
            VkPipelineVertexInputDivisorStateCreateInfoEXT divisorInfo{
                .sType = VK_STRUCTURE_TYPE_PIPELINE_VERTEX_INPUT_DIVISOR_STATE_CREATE_INFO_EXT
            };
            if (!divisors.empty()) {
                divisorInfo.vertexBindingDivisorCount = static_cast<u32>(divisors.size());
                divisorInfo.pVertexBindingDivisors = divisors.data();
                vertexInput.pNext = &divisorInfo;
            }
            VkPipelineInputAssemblyStateCreateInfo assembly{.sType = VK_STRUCTURE_TYPE_PIPELINE_INPUT_ASSEMBLY_STATE_CREATE_INFO};
            assembly.topology = tessellation
                                    ? VK_PRIMITIVE_TOPOLOGY_PATCH_LIST
                                    : description.world ? topology(description.topology) : VK_PRIMITIVE_TOPOLOGY_TRIANGLE_LIST;
            VkPipelineViewportStateCreateInfo viewport{.sType = VK_STRUCTURE_TYPE_PIPELINE_VIEWPORT_STATE_CREATE_INFO};
            viewport.viewportCount = 1;
            viewport.scissorCount = 1;
            VkPipelineMultisampleStateCreateInfo multisample{.sType = VK_STRUCTURE_TYPE_PIPELINE_MULTISAMPLE_STATE_CREATE_INFO};
            multisample.rasterizationSamples = VK_SAMPLE_COUNT_1_BIT;
            VkPipelineColorBlendStateCreateInfo colorBlend{.sType = VK_STRUCTURE_TYPE_PIPELINE_COLOR_BLEND_STATE_CREATE_INFO};
            colorBlend.attachmentCount = static_cast<u32>(blends.size());
            colorBlend.pAttachments = blends.empty() ? nullptr : blends.data();
            VkDynamicState dynamicStates[] = {VK_DYNAMIC_STATE_VIEWPORT, VK_DYNAMIC_STATE_SCISSOR};
            VkPipelineDynamicStateCreateInfo dynamic{.sType = VK_STRUCTURE_TYPE_PIPELINE_DYNAMIC_STATE_CREATE_INFO};
            dynamic.dynamicStateCount = 2;
            dynamic.pDynamicStates = dynamicStates;
            VkPipelineTessellationDomainOriginStateCreateInfo origin{
                .sType = VK_STRUCTURE_TYPE_PIPELINE_TESSELLATION_DOMAIN_ORIGIN_STATE_CREATE_INFO
            };
            origin.domainOrigin = VK_TESSELLATION_DOMAIN_ORIGIN_LOWER_LEFT;
            VkPipelineTessellationStateCreateInfo tessellationState{
                .sType = VK_STRUCTURE_TYPE_PIPELINE_TESSELLATION_STATE_CREATE_INFO
            };
            tessellationState.pNext = &origin;
            tessellationState.patchControlPoints = 3;
            VkGraphicsPipelineCreateInfo info{.sType = VK_STRUCTURE_TYPE_GRAPHICS_PIPELINE_CREATE_INFO};
            info.stageCount = static_cast<u32>(stages.size());
            info.pStages = stages.data();
            info.pVertexInputState = &vertexInput;
            info.pInputAssemblyState = &assembly;
            info.pTessellationState = tessellation ? &tessellationState : nullptr;
            info.pViewportState = &viewport;
            info.pRasterizationState = &raster;
            info.pMultisampleState = &multisample;
            info.pDepthStencilState = &depth;
            info.pColorBlendState = &colorBlend;
            info.pDynamicState = &dynamic;
            info.layout = layout;
            info.renderPass = backend.passes->get(formats.data(), static_cast<u32>(formats.size()), depthFormat);
            check(vkCreateGraphicsPipelines(device, VK_NULL_HANDLE, 1, &info, nullptr, &pipeline), "vkCreateGraphicsPipelines");
        } catch (...) {
            for (VkShaderModule module: modules) vkDestroyShaderModule(device, module, nullptr);
            if (layout) vkDestroyPipelineLayout(device, layout, nullptr);
            if (emptySetLayout) vkDestroyDescriptorSetLayout(device, emptySetLayout, nullptr);
            throw;
        }
        for (VkShaderModule module: modules) vkDestroyShaderModule(device, module, nullptr);
    }

    GraphicsPipeline::~GraphicsPipeline() {
        if (pipeline) vkDestroyPipeline(device, pipeline, nullptr);
        if (layout) vkDestroyPipelineLayout(device, layout, nullptr);
        if (emptySetLayout) vkDestroyDescriptorSetLayout(device, emptySetLayout, nullptr);
    }

    void GraphicsPipeline::bind(VkCommandBuffer commands, SamplerHeap::Snapshot *heapSnapshot,
                                ResourceSets::Snapshot *uniforms) const {
        if (uniforms) uniforms->bind(commands, layout, VK_PIPELINE_BIND_POINT_GRAPHICS);
        heapSnapshot->bind(commands, layout, VK_PIPELINE_BIND_POINT_GRAPHICS);
        vkCmdBindPipeline(commands, VK_PIPELINE_BIND_POINT_GRAPHICS, pipeline);
        if (zeroVertexBuffer) {
            VkDeviceSize offset = 0;
            vkCmdBindVertexBuffers(commands, zeroVertexBinding, 1, &zeroVertexBuffer, &offset);
        }
    }

    void GraphicsPipeline::bind(VkCommandBuffer commands, const BindingData &bindings) {
        SamplerHeap::Snapshot *base = heap->cachedSnapshot(bindings.heap.data(), static_cast<u32>(bindings.heap.size()));
        SamplerHeap::Snapshot *heapSnapshot = heap->withDraws(base, bindings.draws.data(), static_cast<u32>(bindings.draws.size()));
        ResourceSets::Snapshot *uniforms = nullptr;
        try {
            if (uniformSets) {
                uniforms = uniformSets->snapshot(bindings.buffers.data(), static_cast<u32>(bindings.buffers.size()),
                                                 bindings.views.data(), static_cast<u32>(bindings.views.size()));
            }
            if (frameSets && bindings.hasFrame) {
                frameSets->persistent(bindings.frame)->bind(commands, layout, VK_PIPELINE_BIND_POINT_GRAPHICS);
            }
            bind(commands, heapSnapshot, uniforms);
        } catch (...) {
            heapSnapshot->release();
            if (uniforms) uniforms->release();
            throw;
        }
        Backend::get().defer([heapSnapshot, uniforms] {
            heapSnapshot->release();
            if (uniforms) uniforms->release();
        });
    }

    void GraphicsPipeline::pushConstants(VkCommandBuffer commands, const void *data, u32 size) const {
        if (size == 0 || size > 128 || (size & 3) != 0) fail("invalid graphics push constants");
        vkCmdPushConstants(commands, layout, VK_SHADER_STAGE_ALL_GRAPHICS, 0, size, data);
    }

    void GraphicsPipeline::release() {
        GraphicsPipeline *self = this;
        Backend::get().defer([self] { delete self; });
    }

    ComputePipeline::ComputePipeline(SamplerHeap *heap, ResourceSets *sets, ResourceSets *frameSets, const ShaderCode &code)
        : device(Backend::get().device.handle), heap(heap), sets(sets), frameSets(frameSets) {
        const std::vector<u32> &words = *code.words;
        bool foundSize = false;
        for (std::size_t i = 5; i < words.size();) {
            u32 instruction = words[i];
            u32 count = instruction >> 16;
            u32 operation = instruction & 0xFFFF;
            if (count == 0) fail("invalid compute SPIR-V");
            if (operation == 16 && count == 6 && i + 5 < words.size() && words[i + 2] == 17) {
                localSize[0] = words[i + 3];
                localSize[1] = words[i + 4];
                localSize[2] = words[i + 5];
                foundSize = true;
            }
            i += count;
        }
        if (!foundSize) fail("compute requires a literal local workgroup size");
        VkShaderModule module = VK_NULL_HANDLE;
        try {
            if (!sets) emptySetLayout = emptyLayout(device);
            std::vector<VkDescriptorSetLayout> layouts = heap->pipelineSetLayouts(
                sets ? sets->layout : emptySetLayout, frameSets ? frameSets->layout : VK_NULL_HANDLE);
            VkPipelineLayoutCreateInfo layoutInfo{.sType = VK_STRUCTURE_TYPE_PIPELINE_LAYOUT_CREATE_INFO};
            layoutInfo.setLayoutCount = static_cast<u32>(layouts.size());
            layoutInfo.pSetLayouts = layouts.data();
            check(vkCreatePipelineLayout(device, &layoutInfo, nullptr, &layout), "vkCreatePipelineLayout");
            module = createModule(words);
            VkComputePipelineCreateInfo info{.sType = VK_STRUCTURE_TYPE_COMPUTE_PIPELINE_CREATE_INFO};
            info.stage = {.sType = VK_STRUCTURE_TYPE_PIPELINE_SHADER_STAGE_CREATE_INFO};
            info.stage.stage = VK_SHADER_STAGE_COMPUTE_BIT;
            info.stage.module = module;
            info.stage.pName = "main";
            info.layout = layout;
            check(vkCreateComputePipelines(device, VK_NULL_HANDLE, 1, &info, nullptr, &pipeline), "vkCreateComputePipelines");
        } catch (...) {
            if (module) vkDestroyShaderModule(device, module, nullptr);
            if (layout) vkDestroyPipelineLayout(device, layout, nullptr);
            if (emptySetLayout) vkDestroyDescriptorSetLayout(device, emptySetLayout, nullptr);
            throw;
        }
        vkDestroyShaderModule(device, module, nullptr);
    }

    ComputePipeline::~ComputePipeline() {
        if (pipeline) vkDestroyPipeline(device, pipeline, nullptr);
        if (layout) vkDestroyPipelineLayout(device, layout, nullptr);
        if (emptySetLayout) vkDestroyDescriptorSetLayout(device, emptySetLayout, nullptr);
    }

    void ComputePipeline::dispatch(VkCommandBuffer commands, const BindingData &bindings, u32 x, u32 y, u32 z, VkBuffer indirect,
                                   u64 indirectOffset) {
        const VkPhysicalDeviceLimits &limits = Backend::get().properties.limits;
        if (!indirect && (x > limits.maxComputeWorkGroupCount[0] || y > limits.maxComputeWorkGroupCount[1] ||
                          z > limits.maxComputeWorkGroupCount[2])) {
            fail("invalid dispatch size");
        }
        SamplerHeap::Snapshot *base = heap->cachedSnapshot(bindings.heap.data(), static_cast<u32>(bindings.heap.size()));
        SamplerHeap::Snapshot *heapSnapshot = heap->withDraws(base, bindings.draws.data(), static_cast<u32>(bindings.draws.size()));
        ResourceSets::Snapshot *snapshot = nullptr;
        try {
            if (sets) {
                snapshot = sets->snapshot(bindings.buffers.data(), static_cast<u32>(bindings.buffers.size()),
                                          bindings.views.data(), static_cast<u32>(bindings.views.size()));
            }
            Synchronization &sync = *Backend::get().sync;
            std::vector<SyncAccess> accesses = bindings.accesses;
            if (indirect) {
                accesses.push_back({
                    {false, reinterpret_cast<u64>(indirect)}, VK_PIPELINE_STAGE_DRAW_INDIRECT_BIT,
                    VK_ACCESS_INDIRECT_COMMAND_READ_BIT, false
                });
            }
            sync.before(commands, accesses);
            vkCmdBindPipeline(commands, VK_PIPELINE_BIND_POINT_COMPUTE, pipeline);
            heapSnapshot->bind(commands, layout, VK_PIPELINE_BIND_POINT_COMPUTE);
            if (frameSets && bindings.hasFrame) {
                frameSets->persistent(bindings.frame)->bind(commands, layout, VK_PIPELINE_BIND_POINT_COMPUTE);
            }
            if (snapshot) snapshot->bind(commands, layout, VK_PIPELINE_BIND_POINT_COMPUTE);
            if (indirect) vkCmdDispatchIndirect(commands, indirect, indirectOffset);
            else vkCmdDispatch(commands, x, y, z);
            sync.after(commands);
        } catch (...) {
            heapSnapshot->release();
            if (snapshot) snapshot->release();
            throw;
        }
        Backend::get().defer([heapSnapshot, snapshot] {
            heapSnapshot->release();
            if (snapshot) snapshot->release();
        });
    }

    void ComputePipeline::release() {
        ComputePipeline *self = this;
        Backend::get().defer([self] { delete self; });
    }
} // namespace at::backend
