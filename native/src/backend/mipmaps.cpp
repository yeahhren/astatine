#include "backend/mipmaps.h"

#include "core/error.h"
#include "backend/backend.h"
#include "backend/pipelines.h"
#include "backend/render_passes.h"
#include "backend/resources.h"
#include "backend/synchronization.h"
#include "vulkan/device.h"
#include "vulkan/memory.h"

#include <algorithm>

namespace at::backend {
    namespace {
        const char *qualifier(VkFormat format) {
            switch (format) {
                case VK_FORMAT_R8_UNORM: return "r8";
                case VK_FORMAT_R8_SNORM: return "r8_snorm";
                case VK_FORMAT_R8G8_UNORM: return "rg8";
                case VK_FORMAT_R8G8_SNORM: return "rg8_snorm";
                case VK_FORMAT_R8G8B8A8_UNORM: return "rgba8";
                case VK_FORMAT_R8G8B8A8_SNORM: return "rgba8_snorm";
                case VK_FORMAT_R16_UNORM: return "r16";
                case VK_FORMAT_R16_SNORM: return "r16_snorm";
                case VK_FORMAT_R16G16_UNORM: return "rg16";
                case VK_FORMAT_R16G16_SNORM: return "rg16_snorm";
                case VK_FORMAT_R16G16B16A16_UNORM: return "rgba16";
                case VK_FORMAT_R16G16B16A16_SNORM: return "rgba16_snorm";
                case VK_FORMAT_R16_SFLOAT: return "r16f";
                case VK_FORMAT_R16G16_SFLOAT: return "rg16f";
                case VK_FORMAT_R16G16B16A16_SFLOAT: return "rgba16f";
                case VK_FORMAT_R32_SFLOAT: return "r32f";
                case VK_FORMAT_R32G32_SFLOAT: return "rg32f";
                case VK_FORMAT_R32G32B32A32_SFLOAT: return "rgba32f";
                case VK_FORMAT_A2B10G10R10_UNORM_PACK32: return "rgb10_a2";
                case VK_FORMAT_B10G11R11_UFLOAT_PACK32: return "r11f_g11f_b10f";
                default: return nullptr;
            }
        }

        u32 consecutiveMips(u32 width, u32 height, u32 remaining) {
            u32 limit = std::min(remaining, std::max(width, height) > 4096 ? 6u : ColorMipmaps::MAX_MIPS);
            u32 count = 0;
            bool oneDimensional = width == 1 || height == 1;
            while (count < limit && (width == 1 || width % 2 == 0) && (height == 1 || height % 2 == 0)) {
                width = std::max(1u, width / 2);
                height = std::max(1u, height / 2);
                count++;
                if (!oneDimensional && (width == 1 || height == 1)) break;
            }
            return count;
        }

        std::string replace(std::string text, const std::string &target, const std::string &value) {
            std::size_t position = text.find(target);
            if (position != std::string::npos) text.replace(position, target.size(), value);
            return text;
        }

        VkSampler createSampler(VkFilter filter, float maxLod) {
            VkSamplerCreateInfo info{.sType = VK_STRUCTURE_TYPE_SAMPLER_CREATE_INFO};
            info.minFilter = filter;
            info.magFilter = filter;
            info.mipmapMode = filter == VK_FILTER_LINEAR ? VK_SAMPLER_MIPMAP_MODE_LINEAR : VK_SAMPLER_MIPMAP_MODE_NEAREST;
            info.addressModeU = VK_SAMPLER_ADDRESS_MODE_CLAMP_TO_EDGE;
            info.addressModeV = VK_SAMPLER_ADDRESS_MODE_CLAMP_TO_EDGE;
            info.addressModeW = VK_SAMPLER_ADDRESS_MODE_CLAMP_TO_EDGE;
            info.maxLod = maxLod;
            VkSampler sampler;
            check(vkCreateSampler(Backend::get().device.handle, &info, nullptr, &sampler), "vkCreateSampler");
            return sampler;
        }
    } // namespace

    void blitMipmaps(Texture *texture, bool linear) {
        if (aspectMask(texture->format) != VK_IMAGE_ASPECT_COLOR_BIT) {
            fail("color mipmap generation requires a color texture");
        }
        if (texture->levels <= 1) return;
        Backend &backend = Backend::get();
        VkFormatFeatureFlags required = VK_FORMAT_FEATURE_BLIT_SRC_BIT | VK_FORMAT_FEATURE_BLIT_DST_BIT |
                                        (linear ? VK_FORMAT_FEATURE_SAMPLED_IMAGE_FILTER_LINEAR_BIT : 0);
        if (!backend.formatFeatures(texture->format, required)) {
            fail("cannot generate mipmaps for format " + std::to_string(texture->format));
        }
        VkCommandBuffer commands = backend.commands();
        SyncAccess access{
            {true, reinterpret_cast<u64>(texture->image)}, VK_PIPELINE_STAGE_TRANSFER_BIT,
            VK_ACCESS_TRANSFER_READ_BIT | VK_ACCESS_TRANSFER_WRITE_BIT, true
        };
        for (u32 level = 1; level < texture->levels; level++) {
            backend.sync->before(commands, &access, 1);
            VkImageBlit region{};
            region.srcSubresource = {VK_IMAGE_ASPECT_COLOR_BIT, level - 1, 0, texture->layers};
            region.dstSubresource = {VK_IMAGE_ASPECT_COLOR_BIT, level, 0, texture->layers};
            region.srcOffsets[1] = {
                static_cast<i32>(texture->levelWidth(level - 1)), static_cast<i32>(texture->levelHeight(level - 1)), 1
            };
            region.dstOffsets[1] = {static_cast<i32>(texture->levelWidth(level)), static_cast<i32>(texture->levelHeight(level)), 1};
            vkCmdBlitImage(commands, texture->image, VK_IMAGE_LAYOUT_GENERAL, texture->image, VK_IMAGE_LAYOUT_GENERAL, 1, &region,
                           linear ? VK_FILTER_LINEAR : VK_FILTER_NEAREST);
        }
        backend.sync->after(commands);
    }

    ColorMipmaps::~ColorMipmaps() {
        VkDevice device = Backend::get().device.handle;
        for (auto &[texture, entry]: resources) {
            for (TextureView *view: entry.levels) {
                vkDestroyImageView(device, view->handle, nullptr);
                delete view;
            }
            vkDestroyDescriptorPool(device, entry.pool, nullptr);
        }
        for (auto &[format, program]: programs) {
            vkDestroyPipeline(device, program.spd, nullptr);
            vkDestroyPipeline(device, program.area, nullptr);
        }
        if (sampler) vkDestroySampler(device, sampler, nullptr);
        if (layout) vkDestroyPipelineLayout(device, layout, nullptr);
        if (setLayout) vkDestroyDescriptorSetLayout(device, setLayout, nullptr);
        if (counter) {
            delete counter->owned;
            delete counter;
        }
    }

    void ColorMipmaps::setSources(std::string core, std::string spd, std::string spdMain, std::string area, std::string tail) {
        sourceCore = std::move(core);
        sourceSpd = std::move(spd);
        sourceSpdMain = std::move(spdMain);
        sourceArea = std::move(area);
        sourceTail = std::move(tail);
    }

    bool ColorMipmaps::supports(VkFormat format) {
        auto found = supported.find(format);
        if (found != supported.end()) return found->second;
        Backend &backend = Backend::get();
        const VkPhysicalDeviceLimits &limits = backend.properties.limits;
        bool result = qualifier(format) && !sourceCore.empty() &&
                      backend.formatFeatures(format, VK_FORMAT_FEATURE_STORAGE_IMAGE_BIT | VK_FORMAT_FEATURE_SAMPLED_IMAGE_BIT) &&
                      limits.maxPerStageDescriptorStorageImages >= MAX_MIPS && limits.maxDescriptorSetStorageImages >= MAX_MIPS &&
                      limits.maxComputeWorkGroupInvocations >= 256 && limits.maxComputeWorkGroupSize[0] >= 256;
        supported.emplace(format, result);
        return result;
    }

    void ColorMipmaps::initialize() {
        if (layout) return;
        Backend &backend = Backend::get();
        VkDevice device = backend.device.handle;
        subgroups = backend.subgroupQuads;
        VkDescriptorSetLayoutBinding bindings[3] = {
            {0, VK_DESCRIPTOR_TYPE_COMBINED_IMAGE_SAMPLER, 1, VK_SHADER_STAGE_COMPUTE_BIT, nullptr},
            {1, VK_DESCRIPTOR_TYPE_STORAGE_IMAGE, MAX_MIPS, VK_SHADER_STAGE_COMPUTE_BIT, nullptr},
            {2, VK_DESCRIPTOR_TYPE_STORAGE_BUFFER, 1, VK_SHADER_STAGE_COMPUTE_BIT, nullptr},
        };
        VkDescriptorSetLayoutCreateInfo setInfo{.sType = VK_STRUCTURE_TYPE_DESCRIPTOR_SET_LAYOUT_CREATE_INFO};
        setInfo.bindingCount = 3;
        setInfo.pBindings = bindings;
        check(vkCreateDescriptorSetLayout(device, &setInfo, nullptr, &setLayout), "vkCreateDescriptorSetLayout");
        VkPushConstantRange range{VK_SHADER_STAGE_COMPUTE_BIT, 0, 20};
        VkPipelineLayoutCreateInfo layoutInfo{.sType = VK_STRUCTURE_TYPE_PIPELINE_LAYOUT_CREATE_INFO};
        layoutInfo.setLayoutCount = 1;
        layoutInfo.pSetLayouts = &setLayout;
        layoutInfo.pushConstantRangeCount = 1;
        layoutInfo.pPushConstantRanges = &range;
        check(vkCreatePipelineLayout(device, &layoutInfo, nullptr, &layout), "vkCreatePipelineLayout");
        sampler = createSampler(VK_FILTER_NEAREST, 0.0f);
        counter = Buffer::create(buffer_usage::STORAGE | buffer_usage::COPY_DST | buffer_usage::COPY_SRC | buffer_usage::INDIRECT,
                                 4, false, "Iris mipmap counter");
        VkCommandBuffer commands = backend.commands();
        memoryBarrier(commands);
        vkCmdFillBuffer(commands, counter->handle, 0, VK_WHOLE_SIZE, 0);
        memoryBarrier(commands);
    }

    VkPipeline ColorMipmaps::compile(const char *name, const std::string &source) const {
        Backend &backend = Backend::get();
        auto words = backend.shaders->compileWords(shader_stage::COMPUTE, std::string("mipmaps/") + name + ".csh", source);
        VkShaderModule module = createModule(*words);
        VkComputePipelineCreateInfo info{.sType = VK_STRUCTURE_TYPE_COMPUTE_PIPELINE_CREATE_INFO};
        info.stage = {.sType = VK_STRUCTURE_TYPE_PIPELINE_SHADER_STAGE_CREATE_INFO};
        info.stage.stage = VK_SHADER_STAGE_COMPUTE_BIT;
        info.stage.module = module;
        info.stage.pName = "main";
        info.layout = layout;
        VkPipeline pipeline;
        VkResult result = vkCreateComputePipelines(backend.device.handle, VK_NULL_HANDLE, 1, &info, nullptr, &pipeline);
        vkDestroyShaderModule(backend.device.handle, module, nullptr);
        check(result, "vkCreateComputePipelines");
        return pipeline;
    }

    ColorMipmaps::Programs ColorMipmaps::createPrograms(VkFormat format) {
        initialize();
        std::string header = std::string("#version 450\n") + (subgroups ? "" : "#define FFX_SPD_NO_WAVE_OPERATIONS\n") +
                             "layout(set = 0, binding = 0) uniform sampler2D sourceImage;\n" +
                             "layout(" + qualifier(format) +
                             ", set = 0, binding = 1) coherent uniform image2D mipImages[12];\n" +
                             "layout(std430, set = 0, binding = 2) coherent buffer Counter { uint counter; };\n" +
                             "layout(push_constant) uniform Parameters { ivec2 sourceSize; uint mips; uint numWorkGroups; uint initialMips; };\n";
        std::string accessors = "void storeMip(uint mip, ivec2 tex, vec4 value) { switch (mip) {\n";
        for (u32 i = 0; i < MAX_MIPS; i++) {
            accessors += "case " + std::to_string(i) + ": imageStore(mipImages[" + std::to_string(i) + "], tex, value); break;\n";
        }
        accessors += "}}\n";
        accessors += "vec4 loadMip(uint mip, ivec2 tex) { switch (mip) {\ncase 0: return texelFetch(sourceImage, tex, 0);\n";
        for (u32 i = 0; i < MAX_MIPS - 1; i++) {
            accessors += "case " + std::to_string(i + 1) + ": return imageLoad(mipImages[" + std::to_string(i) + "], tex);\n";
        }
        accessors += "} return vec4(0.0); }\n";
        std::string reductions = accessors + sourceTail;
        VkPipeline spd = compile("spd", header + sourceCore + reductions + replace(sourceSpdMain, "// FFX_SPD", sourceSpd));
        try {
            return {spd, compile("area", header + reductions + sourceArea)};
        } catch (...) {
            vkDestroyPipeline(Backend::get().device.handle, spd, nullptr);
            throw;
        }
    }

    VkDescriptorSet ColorMipmaps::descriptorSet(Resources &entry, u32 base, u32 count) const {
        if (entry.sets[base]) return entry.sets[base];
        VkDevice device = Backend::get().device.handle;
        VkDescriptorImageInfo source{sampler, entry.levels[base]->handle, VK_IMAGE_LAYOUT_GENERAL};
        VkDescriptorImageInfo outputs[MAX_MIPS];
        for (u32 i = 0; i < MAX_MIPS; i++) {
            outputs[i] = {VK_NULL_HANDLE, entry.levels[base + std::min(i + 1, count)]->handle, VK_IMAGE_LAYOUT_GENERAL};
        }
        VkDescriptorBufferInfo buffer{counter->handle, 0, 4};
        VkDescriptorSetAllocateInfo allocateInfo{.sType = VK_STRUCTURE_TYPE_DESCRIPTOR_SET_ALLOCATE_INFO};
        allocateInfo.descriptorPool = entry.pool;
        allocateInfo.descriptorSetCount = 1;
        allocateInfo.pSetLayouts = &setLayout;
        VkDescriptorSet set;
        check(vkAllocateDescriptorSets(device, &allocateInfo, &set), "vkAllocateDescriptorSets");
        VkWriteDescriptorSet writes[3]{};
        for (VkWriteDescriptorSet &write: writes) {
            write.sType = VK_STRUCTURE_TYPE_WRITE_DESCRIPTOR_SET;
            write.dstSet = set;
        }
        writes[0].dstBinding = 0;
        writes[0].descriptorType = VK_DESCRIPTOR_TYPE_COMBINED_IMAGE_SAMPLER;
        writes[0].descriptorCount = 1;
        writes[0].pImageInfo = &source;
        writes[1].dstBinding = 1;
        writes[1].descriptorType = VK_DESCRIPTOR_TYPE_STORAGE_IMAGE;
        writes[1].descriptorCount = MAX_MIPS;
        writes[1].pImageInfo = outputs;
        writes[2].dstBinding = 2;
        writes[2].descriptorType = VK_DESCRIPTOR_TYPE_STORAGE_BUFFER;
        writes[2].descriptorCount = 1;
        writes[2].pBufferInfo = &buffer;
        vkUpdateDescriptorSets(device, 3, writes, 0, nullptr);
        entry.sets[base] = set;
        return set;
    }

    bool ColorMipmaps::generate(Texture *texture) {
        if (texture->levels <= 1) return true;
        if (texture->layers != 1 || !(texture->usage & texture_usage::STORAGE) || !supports(texture->format)) return false;
        Backend &backend = Backend::get();
        VkDevice device = backend.device.handle;
        auto program = programs.find(texture->format);
        if (program == programs.end()) program = programs.emplace(texture->format, createPrograms(texture->format)).first;
        auto found = resources.find(texture);
        if (found == resources.end()) {
            Resources created;
            for (u32 level = 0; level < texture->levels; level++) created.levels.push_back(TextureView::create(texture, level, 1));
            u32 capacity = texture->levels - 1;
            VkDescriptorPoolSize sizes[3] = {
                {VK_DESCRIPTOR_TYPE_COMBINED_IMAGE_SAMPLER, capacity},
                {VK_DESCRIPTOR_TYPE_STORAGE_IMAGE, capacity * MAX_MIPS},
                {VK_DESCRIPTOR_TYPE_STORAGE_BUFFER, capacity},
            };
            VkDescriptorPoolCreateInfo poolInfo{.sType = VK_STRUCTURE_TYPE_DESCRIPTOR_POOL_CREATE_INFO};
            poolInfo.maxSets = capacity;
            poolInfo.poolSizeCount = 3;
            poolInfo.pPoolSizes = sizes;
            check(vkCreateDescriptorPool(device, &poolInfo, nullptr, &created.pool), "vkCreateDescriptorPool");
            created.sets.assign(texture->levels, VK_NULL_HANDLE);
            found = resources.emplace(texture, std::move(created)).first;
        }
        Resources &entry = found->second;
        VkCommandBuffer commands = backend.commands();
        SyncAccess image = Synchronization::shaderImage(texture->image, VK_PIPELINE_STAGE_COMPUTE_SHADER_BIT, true);
        SyncAccess withCounter[2] = {
            image, {
                {false, reinterpret_cast<u64>(counter->handle)}, VK_PIPELINE_STAGE_COMPUTE_SHADER_BIT,
                VK_ACCESS_SHADER_READ_BIT | VK_ACCESS_SHADER_WRITE_BIT, true
            }
        };
        u32 levels = texture->levels;
        for (u32 base = 0; base < levels - 1;) {
            u32 width = std::max(1u, texture->levelWidth(base));
            u32 height = std::max(1u, texture->levelHeight(base));
            u32 count = consecutiveMips(width, height, levels - base - 1);
            bool area = count == 0;
            if (area) count = 1;
            u32 initialMips = count;
            if (std::max(1u, width >> count) <= 64 && std::max(1u, height >> count) <= 64) {
                count = std::min(MAX_MIPS, levels - base - 1);
                if (count > initialMips) initialMips = std::min(initialMips, 6u);
            }
            u32 groupsX = area ? (std::max(1u, width / 2) + 15) / 16 : (width + 63) / 64;
            u32 groupsY = area ? (std::max(1u, height / 2) + 15) / 16 : (height + 63) / 64;
            u32 groups = groupsX * groupsY;
            VkDescriptorSet set = descriptorSet(entry, base, count);
            bool usesCounter = initialMips > 6 || (count > initialMips && groups > 1);
            if (usesCounter) backend.sync->before(commands, withCounter, 2);
            else backend.sync->before(commands, &image, 1);
            vkCmdBindPipeline(commands, VK_PIPELINE_BIND_POINT_COMPUTE, area ? program->second.area : program->second.spd);
            vkCmdBindDescriptorSets(commands, VK_PIPELINE_BIND_POINT_COMPUTE, layout, 0, 1, &set, 0, nullptr);
            u32 constants[5] = {width, height, count, groups, initialMips};
            vkCmdPushConstants(commands, layout, VK_SHADER_STAGE_COMPUTE_BIT, 0, sizeof(constants), constants);
            vkCmdDispatch(commands, groupsX, groupsY, 1);
            base += count;
        }
        backend.sync->after(commands);
        return true;
    }

    void ColorMipmaps::release(Texture *texture) {
        auto found = resources.find(texture);
        if (found == resources.end()) return;
        Resources retired = std::move(found->second);
        resources.erase(found);
        VkDevice device = Backend::get().device.handle;
        for (TextureView *view: retired.levels) view->release();
        VkDescriptorPool pool = retired.pool;
        Backend::get().defer([device, pool] { vkDestroyDescriptorPool(device, pool, nullptr); });
    }

    DepthMipmaps::~DepthMipmaps() {
        VkDevice device = Backend::get().device.handle;
        for (auto &[texture, views]: levels) {
            for (TextureView *view: views) {
                vkDestroyImageView(device, view->handle, nullptr);
                delete view;
            }
        }
        if (pipeline) vkDestroyPipeline(device, pipeline, nullptr);
        if (layout) vkDestroyPipelineLayout(device, layout, nullptr);
        if (setLayout) vkDestroyDescriptorSetLayout(device, setLayout, nullptr);
        if (sampler) vkDestroySampler(device, sampler, nullptr);
    }

    void DepthMipmaps::initialize() {
        Backend &backend = Backend::get();
        VkDevice device = backend.device.handle;
        if (!vkCmdPushDescriptorSetKHR) fail("depth mipmaps need VK_KHR_push_descriptor");
        VkDescriptorSetLayoutBinding binding{0, VK_DESCRIPTOR_TYPE_COMBINED_IMAGE_SAMPLER, 1, VK_SHADER_STAGE_FRAGMENT_BIT, nullptr};
        VkDescriptorSetLayoutCreateInfo setInfo{.sType = VK_STRUCTURE_TYPE_DESCRIPTOR_SET_LAYOUT_CREATE_INFO};
        setInfo.flags = VK_DESCRIPTOR_SET_LAYOUT_CREATE_PUSH_DESCRIPTOR_BIT_KHR;
        setInfo.bindingCount = 1;
        setInfo.pBindings = &binding;
        check(vkCreateDescriptorSetLayout(device, &setInfo, nullptr, &setLayout), "vkCreateDescriptorSetLayout");
        VkPipelineLayoutCreateInfo layoutInfo{.sType = VK_STRUCTURE_TYPE_PIPELINE_LAYOUT_CREATE_INFO};
        layoutInfo.setLayoutCount = 1;
        layoutInfo.pSetLayouts = &setLayout;
        check(vkCreatePipelineLayout(device, &layoutInfo, nullptr, &layout), "vkCreatePipelineLayout");
        sampler = createSampler(VK_FILTER_LINEAR, 0.0f);
        auto vertex = backend.shaders->compileWords(shader_stage::VERTEX, "depth-mips.vsh",
                                                    "#version 450\nvoid main(){vec2 p=vec2((gl_VertexIndex<<1)&2,gl_VertexIndex&2);"
                                                    "gl_Position=vec4(p*2.0-1.0,0,1);}\n");
        auto fragment = backend.shaders->compileWords(shader_stage::FRAGMENT, "depth-mips.fsh",
                                                      "#version 450\nlayout(set = 0, binding = 0) uniform sampler2D sourceDepth;\n"
                                                      "void main() {\n"
                                                      "    ivec2 size = max(textureSize(sourceDepth, 0) / 2, ivec2(1));\n"
                                                      "    vec2 uv = gl_FragCoord.xy / vec2(size);\n"
                                                      "    gl_FragDepth = textureLod(sourceDepth, uv, 0.0).r;\n"
                                                      "}\n");
        VkShaderModule modules[2] = {createModule(*vertex), createModule(*fragment)};
        VkPipelineShaderStageCreateInfo stages[2]{};
        for (u32 i = 0; i < 2; i++) {
            stages[i].sType = VK_STRUCTURE_TYPE_PIPELINE_SHADER_STAGE_CREATE_INFO;
            stages[i].stage = i == 0 ? VK_SHADER_STAGE_VERTEX_BIT : VK_SHADER_STAGE_FRAGMENT_BIT;
            stages[i].module = modules[i];
            stages[i].pName = "main";
        }
        VkPipelineVertexInputStateCreateInfo vertexInput{.sType = VK_STRUCTURE_TYPE_PIPELINE_VERTEX_INPUT_STATE_CREATE_INFO};
        VkPipelineInputAssemblyStateCreateInfo assembly{.sType = VK_STRUCTURE_TYPE_PIPELINE_INPUT_ASSEMBLY_STATE_CREATE_INFO};
        assembly.topology = VK_PRIMITIVE_TOPOLOGY_TRIANGLE_LIST;
        VkPipelineViewportStateCreateInfo viewport{.sType = VK_STRUCTURE_TYPE_PIPELINE_VIEWPORT_STATE_CREATE_INFO};
        viewport.viewportCount = 1;
        viewport.scissorCount = 1;
        VkPipelineRasterizationStateCreateInfo raster{.sType = VK_STRUCTURE_TYPE_PIPELINE_RASTERIZATION_STATE_CREATE_INFO};
        raster.polygonMode = VK_POLYGON_MODE_FILL;
        raster.cullMode = VK_CULL_MODE_NONE;
        raster.frontFace = VK_FRONT_FACE_COUNTER_CLOCKWISE;
        raster.lineWidth = 1.0f;
        VkPipelineMultisampleStateCreateInfo multisample{.sType = VK_STRUCTURE_TYPE_PIPELINE_MULTISAMPLE_STATE_CREATE_INFO};
        multisample.rasterizationSamples = VK_SAMPLE_COUNT_1_BIT;
        VkPipelineDepthStencilStateCreateInfo depth{.sType = VK_STRUCTURE_TYPE_PIPELINE_DEPTH_STENCIL_STATE_CREATE_INFO};
        depth.depthTestEnable = VK_TRUE;
        depth.depthWriteEnable = VK_TRUE;
        depth.depthCompareOp = VK_COMPARE_OP_ALWAYS;
        VkPipelineColorBlendStateCreateInfo colorBlend{.sType = VK_STRUCTURE_TYPE_PIPELINE_COLOR_BLEND_STATE_CREATE_INFO};
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
        info.renderPass = backend.passes->get(nullptr, 0, VK_FORMAT_D32_SFLOAT);
        VkResult result = vkCreateGraphicsPipelines(device, VK_NULL_HANDLE, 1, &info, nullptr, &pipeline);
        vkDestroyShaderModule(device, modules[0], nullptr);
        vkDestroyShaderModule(device, modules[1], nullptr);
        check(result, "vkCreateGraphicsPipelines");
    }

    void DepthMipmaps::generate(Texture *texture) {
        if (texture->format != VK_FORMAT_D32_SFLOAT) fail("expected live D32 shadow depth");
        if (texture->levels < 2) return;
        if (!pipeline) initialize();
        auto found = levels.find(texture);
        if (found == levels.end()) {
            std::vector<TextureView *> views;
            for (u32 level = 0; level < texture->levels; level++) views.push_back(TextureView::create(texture, level, 1));
            found = levels.emplace(texture, std::move(views)).first;
        }
        std::vector<TextureView *> &views = found->second;
        Backend &backend = Backend::get();
        VkCommandBuffer commands = backend.commands();
        memoryBarrier(commands);
        for (u32 level = 1; level < views.size(); level++) {
            u32 width = std::max(1u, texture->levelWidth(level));
            u32 height = std::max(1u, texture->levelHeight(level));
            backend.passes->begin(commands, VK_FORMAT_D32_SFLOAT, views[level]->handle, width, height,
                                  VK_ATTACHMENT_LOAD_OP_DONT_CARE);
            VkViewport viewport{0, 0, static_cast<float>(width), static_cast<float>(height), 0, 1};
            vkCmdSetViewport(commands, 0, 1, &viewport);
            VkRect2D scissor{{0, 0}, {width, height}};
            vkCmdSetScissor(commands, 0, 1, &scissor);
            vkCmdBindPipeline(commands, VK_PIPELINE_BIND_POINT_GRAPHICS, pipeline);
            VkDescriptorImageInfo source{sampler, views[level - 1]->handle, VK_IMAGE_LAYOUT_GENERAL};
            VkWriteDescriptorSet write{.sType = VK_STRUCTURE_TYPE_WRITE_DESCRIPTOR_SET};
            write.dstBinding = 0;
            write.descriptorCount = 1;
            write.descriptorType = VK_DESCRIPTOR_TYPE_COMBINED_IMAGE_SAMPLER;
            write.pImageInfo = &source;
            vkCmdPushDescriptorSetKHR(commands, VK_PIPELINE_BIND_POINT_GRAPHICS, layout, 0, 1, &write);
            vkCmdDraw(commands, 3, 1, 0, 0);
            vkCmdEndRenderPass(commands);
            memoryBarrier(commands);
        }
    }

    void DepthMipmaps::release(Texture *texture) {
        auto found = levels.find(texture);
        if (found == levels.end()) return;
        std::vector<TextureView *> views = std::move(found->second);
        levels.erase(found);
        for (TextureView *view: views) view->release();
    }
} // namespace at::backend
