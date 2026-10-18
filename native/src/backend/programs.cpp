#include "backend/programs.h"

#include "backend/backend.h"
#include "backend/pipelines.h"
#include "backend/resources.h"
#include "core/error.h"
#include "vulkan/device.h"

namespace at::backend {
    namespace {
        struct ProgramDescription {
            u64 vertex;
            u64 fragment;
            u32 bindingCount;
            u32 attributeCount;
            u32 vertexBindingCount;
            u32 colorCount;
            u32 polygonMode;
            u32 cull;
            u32 topology;
            u32 hasDepthState;
            u32 depthWrite;
            u32 depthCompare;
            float biasConstant;
            float biasSlope;
        };

        static_assert(sizeof(ProgramDescription) == 64);

        VkBlendFactor factor(u32 value) {
            constexpr VkBlendFactor factors[] = {
                VK_BLEND_FACTOR_CONSTANT_ALPHA, VK_BLEND_FACTOR_CONSTANT_COLOR, VK_BLEND_FACTOR_DST_ALPHA,
                VK_BLEND_FACTOR_DST_COLOR, VK_BLEND_FACTOR_ONE, VK_BLEND_FACTOR_ONE_MINUS_CONSTANT_ALPHA,
                VK_BLEND_FACTOR_ONE_MINUS_CONSTANT_COLOR, VK_BLEND_FACTOR_ONE_MINUS_DST_ALPHA, VK_BLEND_FACTOR_ONE_MINUS_DST_COLOR,
                VK_BLEND_FACTOR_ONE_MINUS_SRC_ALPHA, VK_BLEND_FACTOR_ONE_MINUS_SRC_COLOR, VK_BLEND_FACTOR_SRC_ALPHA,
                VK_BLEND_FACTOR_SRC_ALPHA_SATURATE, VK_BLEND_FACTOR_SRC_COLOR, VK_BLEND_FACTOR_ZERO
            };
            return value < 15 ? factors[value] : VK_BLEND_FACTOR_ONE;
        }

        VkBlendOp operation(u32 value) {
            constexpr VkBlendOp operations[] = {
                VK_BLEND_OP_ADD, VK_BLEND_OP_SUBTRACT, VK_BLEND_OP_REVERSE_SUBTRACT, VK_BLEND_OP_MIN, VK_BLEND_OP_MAX
            };
            return value < 5 ? operations[value] : VK_BLEND_OP_ADD;
        }

        VkCompareOp compare(u32 value) {
            constexpr VkCompareOp operations[] = {
                VK_COMPARE_OP_ALWAYS, VK_COMPARE_OP_LESS, VK_COMPARE_OP_LESS_OR_EQUAL, VK_COMPARE_OP_EQUAL,
                VK_COMPARE_OP_NOT_EQUAL, VK_COMPARE_OP_GREATER_OR_EQUAL, VK_COMPARE_OP_GREATER, VK_COMPARE_OP_NEVER
            };
            return value < 8 ? operations[value] : VK_COMPARE_OP_ALWAYS;
        }

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

        VkDescriptorType descriptorType(u32 type) {
            switch (type) {
                case resource_type::UNIFORM_BUFFER: return VK_DESCRIPTOR_TYPE_UNIFORM_BUFFER;
                case resource_type::TEXEL_BUFFER: return VK_DESCRIPTOR_TYPE_UNIFORM_TEXEL_BUFFER;
                default: return VK_DESCRIPTOR_TYPE_COMBINED_IMAGE_SAMPLER;
            }
        }
    } // namespace

    Program::Program(const u8 *data) : device(Backend::get().device.handle) {
        auto description = load<ProgramDescription>(data);
        const u8 *cursor = data + sizeof(ProgramDescription);
        bindings.resize(description.bindingCount);
        for (Binding &binding: bindings) {
            binding = load<Binding>(cursor);
            cursor += sizeof(Binding);
        }
        for (u32 i = 0; i < description.attributeCount; i++) {
            u32 values[4];
            std::memcpy(values, cursor, sizeof(values));
            cursor += sizeof(values);
            attributes.push_back({values[0], values[1], vulkanFormat(values[2]), values[3]});
        }
        for (u32 i = 0; i < description.vertexBindingCount; i++) {
            u32 values[4];
            std::memcpy(values, cursor, sizeof(values));
            cursor += sizeof(values);
            vertexBindings.push_back({values[0], values[1], values[2] ? VK_VERTEX_INPUT_RATE_INSTANCE : VK_VERTEX_INPUT_RATE_VERTEX});
            if (values[2] && values[3] != 1) divisors.push_back({values[0], values[3]});
        }
        colors.resize(description.colorCount);
        for (Color &color: colors) {
            color = load<Color>(cursor);
            cursor += sizeof(Color);
        }
        polygonMode = description.polygonMode;
        cull = description.cull;
        topology = description.topology;
        hasDepthState = description.hasDepthState;
        depthWrite = description.depthWrite;
        depthCompare = description.depthCompare;
        biasConstant = description.biasConstant;
        biasSlope = description.biasSlope;
        if (!description.vertex || !description.fragment) fail("a render pipeline needs vertex and fragment shaders");
        try {
            std::vector<VkDescriptorSetLayoutBinding> layoutBindings;
            for (const Binding &binding: bindings) {
                layoutBindings.push_back({binding.binding, descriptorType(binding.type), 1, VK_SHADER_STAGE_ALL_GRAPHICS, nullptr});
            }
            VkDescriptorSetLayoutCreateInfo setInfo{.sType = VK_STRUCTURE_TYPE_DESCRIPTOR_SET_LAYOUT_CREATE_INFO};
            if (!layoutBindings.empty()) {
                if (!vkCmdPushDescriptorSetKHR) fail("render pipelines with uniforms need VK_KHR_push_descriptor");
                setInfo.flags = VK_DESCRIPTOR_SET_LAYOUT_CREATE_PUSH_DESCRIPTOR_BIT_KHR;
            }
            setInfo.bindingCount = static_cast<u32>(layoutBindings.size());
            setInfo.pBindings = layoutBindings.data();
            check(vkCreateDescriptorSetLayout(device, &setInfo, nullptr, &setLayout), "vkCreateDescriptorSetLayout");
            VkPushConstantRange range{VK_SHADER_STAGE_ALL_GRAPHICS, 0, 128};
            VkPipelineLayoutCreateInfo layoutInfo{.sType = VK_STRUCTURE_TYPE_PIPELINE_LAYOUT_CREATE_INFO};
            layoutInfo.setLayoutCount = 1;
            layoutInfo.pSetLayouts = &setLayout;
            layoutInfo.pushConstantRangeCount = 1;
            layoutInfo.pPushConstantRanges = &range;
            check(vkCreatePipelineLayout(device, &layoutInfo, nullptr, &layout), "vkCreatePipelineLayout");
            vertex = createModule(*pointer<ShaderCode>(static_cast<i64>(description.vertex))->words);
            fragment = createModule(*pointer<ShaderCode>(static_cast<i64>(description.fragment))->words);
        } catch (...) {
            if (vertex) vkDestroyShaderModule(device, vertex, nullptr);
            if (layout) vkDestroyPipelineLayout(device, layout, nullptr);
            if (setLayout) vkDestroyDescriptorSetLayout(device, setLayout, nullptr);
            throw;
        }
    }

    Program::~Program() {
        for (auto &[pass, pipeline]: variants) vkDestroyPipeline(device, pipeline, nullptr);
        vkDestroyShaderModule(device, vertex, nullptr);
        vkDestroyShaderModule(device, fragment, nullptr);
        vkDestroyPipelineLayout(device, layout, nullptr);
        vkDestroyDescriptorSetLayout(device, setLayout, nullptr);
    }

    VkPipeline Program::variant(const PassTarget &target) {
        std::lock_guard guard(lock);
        auto found = variants.find(target.compatible);
        if (found != variants.end()) return found->second;
        VkPipelineShaderStageCreateInfo stages[2]{};
        stages[0] = {.sType = VK_STRUCTURE_TYPE_PIPELINE_SHADER_STAGE_CREATE_INFO};
        stages[0].stage = VK_SHADER_STAGE_VERTEX_BIT;
        stages[0].module = vertex;
        stages[0].pName = "main";
        stages[1] = stages[0];
        stages[1].stage = VK_SHADER_STAGE_FRAGMENT_BIT;
        stages[1].module = fragment;
        VkPipelineVertexInputStateCreateInfo vertexInput{.sType = VK_STRUCTURE_TYPE_PIPELINE_VERTEX_INPUT_STATE_CREATE_INFO};
        vertexInput.vertexBindingDescriptionCount = static_cast<u32>(vertexBindings.size());
        vertexInput.pVertexBindingDescriptions = vertexBindings.data();
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
        assembly.topology = backend::topology(topology);
        VkPipelineViewportStateCreateInfo viewport{.sType = VK_STRUCTURE_TYPE_PIPELINE_VIEWPORT_STATE_CREATE_INFO};
        viewport.viewportCount = 1;
        viewport.scissorCount = 1;
        VkPipelineRasterizationStateCreateInfo raster{.sType = VK_STRUCTURE_TYPE_PIPELINE_RASTERIZATION_STATE_CREATE_INFO};
        raster.polygonMode = polygonMode == 1 ? VK_POLYGON_MODE_LINE : VK_POLYGON_MODE_FILL;
        raster.cullMode = cull ? VK_CULL_MODE_BACK_BIT : VK_CULL_MODE_NONE;
        raster.frontFace = VK_FRONT_FACE_COUNTER_CLOCKWISE;
        raster.lineWidth = 1.0f;
        raster.depthBiasEnable = target.depth && (biasConstant != 0.0f || biasSlope != 0.0f);
        raster.depthBiasConstantFactor = biasConstant;
        raster.depthBiasSlopeFactor = biasSlope;
        VkPipelineMultisampleStateCreateInfo multisample{.sType = VK_STRUCTURE_TYPE_PIPELINE_MULTISAMPLE_STATE_CREATE_INFO};
        multisample.rasterizationSamples = VK_SAMPLE_COUNT_1_BIT;
        VkPipelineDepthStencilStateCreateInfo depth{.sType = VK_STRUCTURE_TYPE_PIPELINE_DEPTH_STENCIL_STATE_CREATE_INFO};
        depth.depthTestEnable = target.depth && hasDepthState;
        depth.depthWriteEnable = target.depth && hasDepthState && depthWrite;
        depth.depthCompareOp = compare(depthCompare);
        std::vector<VkPipelineColorBlendAttachmentState> blends(target.colorCount);
        for (u32 i = 0; i < target.colorCount; i++) {
            VkPipelineColorBlendAttachmentState &state = blends[i];
            state.colorWriteMask = 15;
            state.srcColorBlendFactor = VK_BLEND_FACTOR_ONE;
            state.dstColorBlendFactor = VK_BLEND_FACTOR_ZERO;
            state.srcAlphaBlendFactor = VK_BLEND_FACTOR_ONE;
            state.dstAlphaBlendFactor = VK_BLEND_FACTOR_ZERO;
            if (i >= colors.size()) continue;
            const Color &color = colors[i];
            state.colorWriteMask = color.writeMask & 15;
            state.blendEnable = color.blend != 0;
            if (color.blend) {
                state.srcColorBlendFactor = factor(color.sourceColor);
                state.dstColorBlendFactor = factor(color.destinationColor);
                state.colorBlendOp = operation(color.colorOperation);
                state.srcAlphaBlendFactor = factor(color.sourceAlpha);
                state.dstAlphaBlendFactor = factor(color.destinationAlpha);
                state.alphaBlendOp = operation(color.alphaOperation);
            }
        }
        VkPipelineColorBlendStateCreateInfo colorBlend{.sType = VK_STRUCTURE_TYPE_PIPELINE_COLOR_BLEND_STATE_CREATE_INFO};
        colorBlend.attachmentCount = target.colorCount;
        colorBlend.pAttachments = blends.empty() ? nullptr : blends.data();
        VkDynamicState dynamicStates[] = {VK_DYNAMIC_STATE_VIEWPORT, VK_DYNAMIC_STATE_SCISSOR};
        VkPipelineDynamicStateCreateInfo dynamic{.sType = VK_STRUCTURE_TYPE_PIPELINE_DYNAMIC_STATE_CREATE_INFO};
        dynamic.dynamicStateCount = 2;
        dynamic.pDynamicStates = dynamicStates;
        VkGraphicsPipelineCreateInfo info{.sType = VK_STRUCTURE_TYPE_GRAPHICS_PIPELINE_CREATE_INFO};
        info.stageCount = 2;
        info.pStages = stages;
        info.pVertexInputState = &vertexInput;
        info.pInputAssemblyState = &assembly;
        info.pViewportState = &viewport;
        info.pRasterizationState = &raster;
        info.pMultisampleState = &multisample;
        info.pDepthStencilState = &depth;
        info.pColorBlendState = &colorBlend;
        info.pDynamicState = &dynamic;
        info.layout = layout;
        info.renderPass = target.compatible;
        VkPipeline pipeline;
        check(vkCreateGraphicsPipelines(device, VK_NULL_HANDLE, 1, &info, nullptr, &pipeline), "vkCreateGraphicsPipelines");
        variants.emplace(target.compatible, pipeline);
        return pipeline;
    }

    void Program::release() {
        Program *self = this;
        Backend::get().defer([self] { delete self; });
    }

    void ProgramState::begin(const PassTarget &next) {
        target = next;
        program = nullptr;
        bound = VK_NULL_HANDLE;
        resources.clear();
        dirty = false;
    }

    void ProgramState::end() {
        begin(PassTarget{});
    }

    void ProgramState::use(Program *next) {
        if (next == program) return;
        program = next;
        bound = VK_NULL_HANDLE;
        dirty = true;
    }

    void ProgramState::setResource(u32 index, const Resource &resource) {
        if (resources.size() <= index) resources.resize(index + 1);
        resources[index] = resource;
        resources[index].set = true;
        dirty = true;
    }

    void ProgramState::pushConstants(VkCommandBuffer commands, const void *data, u32 size) const {
        if (!program) fail("no render pipeline is bound");
        if (size == 0 || size > 128 || (size & 3) != 0) fail("invalid push constants");
        vkCmdPushConstants(commands, program->layout, VK_SHADER_STAGE_ALL_GRAPHICS, 0, size, data);
    }

    void ProgramState::prepare(VkCommandBuffer commands) {
        if (!program) return;
        if (!target.compatible) fail("render pipelines can only draw inside an Astatine render pass");
        VkPipeline pipeline = program->variant(target);
        vkCmdBindPipeline(commands, VK_PIPELINE_BIND_POINT_GRAPHICS, pipeline);
        bound = pipeline;
        if (program->bindings.empty()) {
            dirty = false;
            return;
        }
        std::vector<VkWriteDescriptorSet> writes;
        std::vector<VkDescriptorImageInfo> images(program->bindings.size());
        std::vector<VkDescriptorBufferInfo> buffers(program->bindings.size());
        std::vector<VkBufferView> texels(program->bindings.size());
        for (std::size_t i = 0; i < program->bindings.size(); i++) {
            const Program::Binding &binding = program->bindings[i];
            if (binding.binding >= resources.size() || !resources[binding.binding].set) {
                fail("render pipeline uniform " + std::to_string(binding.binding) + " is not bound");
            }
            const Resource &resource = resources[binding.binding];
            VkWriteDescriptorSet write{.sType = VK_STRUCTURE_TYPE_WRITE_DESCRIPTOR_SET};
            write.dstBinding = binding.binding;
            write.descriptorCount = 1;
            write.descriptorType = descriptorType(binding.type);
            switch (binding.type) {
                case resource_type::UNIFORM_BUFFER:
                    buffers[i] = {resource.buffer, resource.offset, resource.length};
                    write.pBufferInfo = &buffers[i];
                    break;
                case resource_type::TEXEL_BUFFER:
                    texels[i] = resource.texel;
                    write.pTexelBufferView = &texels[i];
                    break;
                default:
                    images[i] = {resource.sampler, resource.view, resource.layout};
                    write.pImageInfo = &images[i];
                    break;
            }
            writes.push_back(write);
        }
        vkCmdPushDescriptorSetKHR(commands, VK_PIPELINE_BIND_POINT_GRAPHICS, program->layout, 0, static_cast<u32>(writes.size()),
                                  writes.data());
        dirty = false;
    }
} // namespace at::backend
