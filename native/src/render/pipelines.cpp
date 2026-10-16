#include "render/pipelines.h"

#include "core/error.h"
#include "core/log.h"
#include "render/pipeline_key.h"
#include "render/vertex_layout.h"
#include "vulkan/device.h"
#include "vulkan/render_pass.h"

#include <cstring>
#include <filesystem>
#include <fstream>
#include <ranges>
#include <vector>

#ifdef _WIN32
#include <windows.h>
#endif

namespace at {
    namespace {
        struct EmbeddedShader {
            const char *name;
            const uint32_t *code;
            std::size_t words;
        };
    }

#include "shader_blob.inc"

#ifndef EMBEDDED_SHADERS_H
#define EMBEDDED_SHADERS_H
    static constexpr EmbeddedShader EMBEDDED_SHADERS[] = {};
#endif

    ShaderLibrary::~ShaderLibrary() {
        for (auto &module: modules | std::views::values) vkDestroyShaderModule(device.handle, module, nullptr);
    }

    VkShaderModule ShaderLibrary::vertex(u32 mask, bool flat) {
        return module("fixed_vert_" + std::to_string(mask & 15) + "_" + (flat ? "1" : "0"));
    }

    VkShaderModule ShaderLibrary::fragment(bool flat, bool bindless) {
        return module(std::string("fixed_frag_") + (flat ? "1" : "0") + "_" + (bindless ? "1" : "0"));
    }

    VkShaderModule ShaderLibrary::terrainVertex() {
        return module("terrain_vert");
    }

    void ShaderLibrary::preload(bool bindless) {
        for (u32 mask = 0; mask < 16; mask++) {
            vertex(mask, false);
            vertex(mask, true);
        }
        fragment(false, bindless);
        fragment(true, bindless);
        terrainVertex();
    }

    VkShaderModule ShaderLibrary::module(const std::string &module_name) {
        if (auto found = modules.find(module_name); found != modules.end()) return found->second;
        for (const auto &[name, code, words]: EMBEDDED_SHADERS) {
            if (module_name != name) continue;
            VkShaderModuleCreateInfo info{.sType = VK_STRUCTURE_TYPE_SHADER_MODULE_CREATE_INFO};
            info.codeSize = words * 4;
            info.pCode = code;
            VkShaderModule created;
            check(vkCreateShaderModule(device.handle, &info, nullptr, &created), "vkCreateShaderModule");
            modules.emplace(module_name, created);
            return created;
        }
        fail("Missing embedded shader " + module_name);
    }

    static VkDescriptorSetLayout createFrameSetLayout(const Device &device) {
        VkDescriptorSetLayoutBinding bindings[3]{};
        bindings[0].binding = 0;
        bindings[0].descriptorType = VK_DESCRIPTOR_TYPE_STORAGE_BUFFER;
        bindings[0].descriptorCount = 1;
        bindings[0].stageFlags = VK_SHADER_STAGE_VERTEX_BIT | VK_SHADER_STAGE_FRAGMENT_BIT;
        bindings[1].binding = 1;
        bindings[1].descriptorType = VK_DESCRIPTOR_TYPE_STORAGE_BUFFER;
        bindings[1].descriptorCount = 1;
        bindings[1].stageFlags = VK_SHADER_STAGE_VERTEX_BIT;
        bindings[2].binding = 2;
        bindings[2].descriptorType = VK_DESCRIPTOR_TYPE_STORAGE_BUFFER;
        bindings[2].descriptorCount = 1;
        bindings[2].stageFlags = VK_SHADER_STAGE_VERTEX_BIT;
        VkDescriptorSetLayoutCreateInfo info{.sType = VK_STRUCTURE_TYPE_DESCRIPTOR_SET_LAYOUT_CREATE_INFO};
        info.bindingCount = 3;
        info.pBindings = bindings;
        VkDescriptorSetLayout layout;
        check(vkCreateDescriptorSetLayout(device.handle, &info, nullptr, &layout), "vkCreateDescriptorSetLayout");
        return layout;
    }

    static VkDescriptorSetLayout createTextureSetLayout(const Device &device) {
        VkDescriptorSetLayoutBinding binding{};
        binding.binding = 0;
        binding.descriptorType = VK_DESCRIPTOR_TYPE_COMBINED_IMAGE_SAMPLER;
        binding.descriptorCount = device.bindless ? device.bindlessCapacity : 1;
        binding.stageFlags = VK_SHADER_STAGE_FRAGMENT_BIT;
        VkDescriptorSetLayoutCreateInfo info{.sType = VK_STRUCTURE_TYPE_DESCRIPTOR_SET_LAYOUT_CREATE_INFO};
        info.bindingCount = 1;
        info.pBindings = &binding;
        VkDescriptorBindingFlagsEXT flags = VK_DESCRIPTOR_BINDING_UPDATE_AFTER_BIND_BIT_EXT |
                                            VK_DESCRIPTOR_BINDING_PARTIALLY_BOUND_BIT_EXT |
                                            VK_DESCRIPTOR_BINDING_UPDATE_UNUSED_WHILE_PENDING_BIT_EXT;
        VkDescriptorSetLayoutBindingFlagsCreateInfoEXT flagsInfo{
            .sType = VK_STRUCTURE_TYPE_DESCRIPTOR_SET_LAYOUT_BINDING_FLAGS_CREATE_INFO_EXT
        };
        flagsInfo.bindingCount = 1;
        flagsInfo.pBindingFlags = &flags;
        if (device.bindless) {
            info.flags = VK_DESCRIPTOR_SET_LAYOUT_CREATE_UPDATE_AFTER_BIND_POOL_BIT_EXT;
            info.pNext = &flagsInfo;
        }
        VkDescriptorSetLayout layout;
        check(vkCreateDescriptorSetLayout(device.handle, &info, nullptr, &layout), "vkCreateDescriptorSetLayout");
        return layout;
    }

    static std::vector<char> readFile(const std::string &path) {
        std::ifstream stream(path, std::ios::binary | std::ios::ate);
        if (!stream) return {};
        std::streamsize size = stream.tellg();
        if (size <= 0) return {};
        std::vector<char> bytes(static_cast<std::size_t>(size));
        stream.seekg(0);
        stream.read(bytes.data(), size);
        return stream ? bytes : std::vector<char>{};
    }

    Pipelines::Pipelines(const Device &device, ShaderLibrary &shaders, std::string cacheFile)
        : device(device), shaders(shaders), cacheFile(std::move(cacheFile)) {
        frameSetLayout = createFrameSetLayout(device);
        textureSetLayout = createTextureSetLayout(device);

        VkDescriptorSetLayout sets[3] = {frameSetLayout, textureSetLayout, textureSetLayout};
        VkPipelineLayoutCreateInfo layoutInfo{.sType = VK_STRUCTURE_TYPE_PIPELINE_LAYOUT_CREATE_INFO};
        layoutInfo.setLayoutCount = device.bindless ? 2 : 3;
        layoutInfo.pSetLayouts = sets;
        check(vkCreatePipelineLayout(device.handle, &layoutInfo, nullptr, &layout), "vkCreatePipelineLayout");

        std::vector<char> saved = readFile(this->cacheFile);
        VkPipelineCacheCreateInfo cacheInfo{.sType = VK_STRUCTURE_TYPE_PIPELINE_CACHE_CREATE_INFO};
        cacheInfo.initialDataSize = saved.size();
        cacheInfo.pInitialData = saved.empty() ? nullptr : saved.data();
        if (VkResult result = vkCreatePipelineCache(device.handle, &cacheInfo, nullptr, &cache);
            result != VK_SUCCESS && !saved.empty()) {
            log::warn("Ignoring the saved pipeline cache, it was rejected with %d", static_cast<int>(result));
            cacheInfo.initialDataSize = 0;
            cacheInfo.pInitialData = nullptr;
            check(vkCreatePipelineCache(device.handle, &cacheInfo, nullptr, &cache), "vkCreatePipelineCache");
        } else {
            check(result, "vkCreatePipelineCache");
        }
    }

    void Pipelines::stop() {
        {
            std::lock_guard guard(lock);
            stopping = true;
            queue.clear();
        }
        queued.notify_all();
        for (std::thread &worker: workers) {
            if (worker.joinable()) worker.join();
        }
        workers.clear();
        {
            std::lock_guard guard(lock);
            pending.clear();
        }
        compiled.notify_all();
    }

    Pipelines::~Pipelines() {
        stop();
        integrate();
        pipelines.forEach([&](u64, u64 pipeline) {
            vkDestroyPipeline(device.handle, reinterpret_cast<VkPipeline>(pipeline), nullptr);
        });
        vkDestroyPipelineCache(device.handle, cache, nullptr);
        vkDestroyPipelineLayout(device.handle, layout, nullptr);
        vkDestroyDescriptorSetLayout(device.handle, frameSetLayout, nullptr);
        vkDestroyDescriptorSetLayout(device.handle, textureSetLayout, nullptr);
    }

    static constexpr u32 RECIPE_MAGIC = 0x4B504154u;
    static constexpr u32 RECIPE_VERSION = 1;
    static constexpr u64 LAYOUT_MASK = (1ull << key::LAYOUT_SHIFT) - 1;

    void Pipelines::prewarm(VertexLayouts &layouts, RenderPassType *const (&passes)[3]) {
        shaders.preload(device.bindless);
        for (RenderPassType *pass: passes) pass->compatible();

        std::vector<char> bytes = readFile(cacheFile + ".keys");
        std::size_t at = 0;
        auto read = [&](void *out, std::size_t size) {
            if (at + size > bytes.size()) return false;
            std::memcpy(out, bytes.data() + at, size);
            at += size;
            return true;
        };
        u32 magic = 0, version = 0, layoutCount = 0;
        if (!read(&magic, 4) || !read(&version, 4) || magic != RECIPE_MAGIC || version != RECIPE_VERSION ||
            !read(&layoutCount, 4))
            return;
        std::vector<u32> remap(layoutCount, ~0u);
        for (u32 i = 0; i < layoutCount; i++) {
            u32 length = 0;
            if (!read(&length, 4) || length > 4096) return;
            std::vector<i32> descriptor(length);
            if (length && !read(descriptor.data(), length * 4)) return;
            if (descriptor.empty()) {
                remap[i] = layouts.terrainLayout();
            } else if ((descriptor.size() - 1) % 5 == 0) {
                remap[i] = layouts.registerFormat(descriptor.data(), static_cast<u32>((descriptor.size() - 1) / 5),
                                                  static_cast<u32>(descriptor.back()));
            }
        }
        u32 entryCount = 0;
        if (!read(&entryCount, 4)) return;
        std::vector<Recipe> recipes;
        recipes.reserve(entryCount);
        for (u32 i = 0; i < entryCount; i++) {
            u64 stored = 0;
            if (!read(&stored, 8)) break;
            u32 layoutIndex = static_cast<u32>(stored >> key::LAYOUT_SHIFT);
            u32 passIndex = key::field(stored, key::PASS_SHIFT, 2);
            if (layoutIndex >= layoutCount || remap[layoutIndex] == ~0u || passIndex > 2) continue;
            u64 runtime = (stored & LAYOUT_MASK) | (static_cast<u64>(remap[layoutIndex]) << key::LAYOUT_SHIFT);
            recipes.push_back({runtime, &layouts[remap[layoutIndex]], passes[passIndex]});
        }
        if (recipes.empty()) return;

        {
            std::lock_guard guard(lock);
            for (const Recipe &recipe: recipes) {
                if (!pending.insert(recipe.key).second) continue;
                queue.push_back(recipe);
                if (knownSet.insert(recipe.key).second) known.push_back(recipe.key);
            }
        }
        u32 threads = std::clamp(std::thread::hardware_concurrency() / 4, 1u, 4u);
        for (u32 i = 0; i < threads; i++) {
            workers.emplace_back([this] { work(); });
#ifdef _WIN32
            SetThreadPriority(workers.back().native_handle(), THREAD_PRIORITY_BELOW_NORMAL);
#endif
        }
        log::info("Compiling %zu recorded pipelines on %u threads", recipes.size(), threads);
    }

    void Pipelines::work() {
        while (true) {
            Recipe recipe{};
            {
                std::unique_lock guard(lock);
                queued.wait(guard, [this] { return stopping || !queue.empty(); });
                if (stopping) return;
                recipe = queue.front();
                queue.pop_front();
            }
            VkPipeline pipeline = VK_NULL_HANDLE;
            try {
                pipeline = compile(recipe.key, *recipe.layout, *recipe.pass);
            } catch (const std::exception &error) {
                log::warn("Unable to precompile a pipeline: %s", error.what());
            }
            {
                std::lock_guard guard(lock);
                pending.erase(recipe.key);
                if (pipeline) {
                    ready.emplace_back(recipe.key, pipeline);
                    hasReady.store(true, std::memory_order_release);
                }
            }
            compiled.notify_all();
        }
    }

    void Pipelines::integrate() {
        if (!hasReady.load(std::memory_order_acquire)) return;
        std::vector<std::pair<u64, VkPipeline> > finished;
        {
            std::lock_guard guard(lock);
            finished.swap(ready);
            hasReady.store(false, std::memory_order_relaxed);
        }
        for (const auto &[key, pipeline]: finished) {
            if (pipelines.get(key) != 0) {
                vkDestroyPipeline(device.handle, pipeline, nullptr);
                continue;
            }
            pipelines.put(key, reinterpret_cast<u64>(pipeline));
        }
    }

    void Pipelines::save(const VertexLayouts &layouts) const {
        std::vector<u64> keys;
        {
            std::lock_guard guard(lock);
            keys = known;
        }
        std::vector<u32> used;
        std::vector<u32> index(layouts.count(), ~0u);
        std::vector<u64> stored;
        stored.reserve(keys.size());
        for (u64 runtime: keys) {
            u32 layoutId = static_cast<u32>(runtime >> key::LAYOUT_SHIFT);
            if (layoutId >= layouts.count()) continue;
            if (index[layoutId] == ~0u) {
                index[layoutId] = static_cast<u32>(used.size());
                used.push_back(layoutId);
            }
            stored.push_back((runtime & LAYOUT_MASK) | (static_cast<u64>(index[layoutId]) << key::LAYOUT_SHIFT));
        }
        std::vector<char> out;
        auto write = [&](const void *data, std::size_t size) {
            const char *bytes = static_cast<const char *>(data);
            out.insert(out.end(), bytes, bytes + size);
        };
        u32 header[3] = {RECIPE_MAGIC, RECIPE_VERSION, static_cast<u32>(used.size())};
        write(header, sizeof(header));
        for (u32 layoutId: used) {
            const std::vector<i32> &descriptor = layouts.descriptor(layoutId);
            auto length = static_cast<u32>(descriptor.size());
            write(&length, 4);
            if (length) write(descriptor.data(), length * 4);
        }
        auto entries = static_cast<u32>(stored.size());
        write(&entries, 4);
        if (entries) write(stored.data(), stored.size() * 8);
        std::error_code keysError;
        std::filesystem::path keysPath(cacheFile + ".keys");
        std::filesystem::create_directories(keysPath.parent_path(), keysError);
        std::ofstream keysStream(keysPath, std::ios::binary | std::ios::trunc);
        keysStream.write(out.data(), static_cast<std::streamsize>(out.size()));

        std::size_t size = 0;
        check(vkGetPipelineCacheData(device.handle, cache, &size, nullptr), "vkGetPipelineCacheData");
        std::vector<char> bytes(size);
        check(vkGetPipelineCacheData(device.handle, cache, &size, bytes.data()), "vkGetPipelineCacheData");
        std::error_code error;
        std::filesystem::path path(cacheFile);
        std::filesystem::create_directories(path.parent_path(), error);
        std::ofstream stream(path, std::ios::binary | std::ios::trunc);
        stream.write(bytes.data(), static_cast<std::streamsize>(size));
    }

    VkPipeline Pipelines::create(u64 key, const VertexLayout &vertexLayout, RenderPassType &pass) {
        integrate();
        if (u64 found = pipelines.get(key)) return reinterpret_cast<VkPipeline>(found);
        {
            std::unique_lock guard(lock);
            if (pending.contains(key)) {
                compiled.wait(guard, [&] { return !pending.contains(key); });
            }
        }
        integrate();
        if (u64 found = pipelines.get(key)) return reinterpret_cast<VkPipeline>(found);
        pass.compatible();
        VkPipeline pipeline = compile(key, vertexLayout, pass);
        pipelines.put(key, reinterpret_cast<u64>(pipeline));
        std::lock_guard guard(lock);
        if (knownSet.insert(key).second) known.push_back(key);
        return pipeline;
    }

    VkPipeline Pipelines::compile(u64 key, const VertexLayout &vertexLayout, RenderPassType &pass) const {
        bool flat = key::has(key, key::FLAT) && !vertexLayout.terrain;
        u32 topology = key::field(key, key::TOPOLOGY_SHIFT, 3);

        VkSpecializationMapEntry entry{.constantID = 0, .offset = 0, .size = 4};
        u32 capacity = device.bindlessCapacity;
        VkSpecializationInfo specialization{
            .mapEntryCount = 1, .pMapEntries = &entry, .dataSize = 4, .pData = &capacity
        };

        VkPipelineShaderStageCreateInfo stages[2]{};
        stages[0].sType = VK_STRUCTURE_TYPE_PIPELINE_SHADER_STAGE_CREATE_INFO;
        stages[0].stage = VK_SHADER_STAGE_VERTEX_BIT;
        stages[0].module = vertexLayout.terrain
                               ? shaders.terrainVertex()
                               : shaders.vertex(vertexLayout.shaderMask, flat);
        stages[0].pName = "main";
        stages[1].sType = VK_STRUCTURE_TYPE_PIPELINE_SHADER_STAGE_CREATE_INFO;
        stages[1].stage = VK_SHADER_STAGE_FRAGMENT_BIT;
        stages[1].module = shaders.fragment(flat, device.bindless);
        stages[1].pName = "main";
        if (device.bindless) stages[1].pSpecializationInfo = &specialization;

        VkVertexInputBindingDescription bindings[2]{};
        bool secondBinding = vertexLayout.split && vertexLayout.attributeStride > 0;
        bindings[0].binding = 0;
        bindings[0].stride = vertexLayout.split ? VertexLayout::POSITION_SIZE : vertexLayout.stride;
        bindings[0].inputRate = VK_VERTEX_INPUT_RATE_VERTEX;
        bindings[1].binding = 1;
        bindings[1].stride = vertexLayout.attributeStride;
        bindings[1].inputRate = VK_VERTEX_INPUT_RATE_VERTEX;
        std::vector<VkVertexInputAttributeDescription> attributes;
        attributes.reserve(vertexLayout.attributes.size());
        for (const auto &[location, binding, format, offset]: vertexLayout.attributes) {
            attributes.push_back({.location = location, .binding = binding, .format = format, .offset = offset});
        }
        VkPipelineVertexInputStateCreateInfo vertexInput{
            .sType = VK_STRUCTURE_TYPE_PIPELINE_VERTEX_INPUT_STATE_CREATE_INFO
        };
        vertexInput.vertexBindingDescriptionCount = secondBinding ? 2 : 1;
        vertexInput.pVertexBindingDescriptions = bindings;
        vertexInput.vertexAttributeDescriptionCount = static_cast<uint32_t>(attributes.size());
        vertexInput.pVertexAttributeDescriptions = attributes.data();

        VkPipelineInputAssemblyStateCreateInfo inputAssembly{
            .sType = VK_STRUCTURE_TYPE_PIPELINE_INPUT_ASSEMBLY_STATE_CREATE_INFO
        };
        inputAssembly.topology = static_cast<VkPrimitiveTopology>(topology);

        VkPipelineViewportStateCreateInfo viewport{.sType = VK_STRUCTURE_TYPE_PIPELINE_VIEWPORT_STATE_CREATE_INFO};
        viewport.viewportCount = 1;
        viewport.scissorCount = 1;

        bool lines = topology == VK_PRIMITIVE_TOPOLOGY_LINE_LIST || topology == VK_PRIMITIVE_TOPOLOGY_LINE_STRIP;
        bool triangles = topology >= VK_PRIMITIVE_TOPOLOGY_TRIANGLE_LIST;
        bool offset = (triangles && key::has(key, key::OFFSET_FILL)) || (lines && key::has(key, key::OFFSET_LINE));
        VkPipelineRasterizationStateCreateInfo rasterization{
            .sType = VK_STRUCTURE_TYPE_PIPELINE_RASTERIZATION_STATE_CREATE_INFO
        };
        rasterization.polygonMode = VK_POLYGON_MODE_FILL;
        rasterization.cullMode = key::field(key, key::CULL_SHIFT, 2);
        rasterization.frontFace = pass.flipY() ? VK_FRONT_FACE_COUNTER_CLOCKWISE : VK_FRONT_FACE_CLOCKWISE;
        rasterization.depthBiasEnable = offset;
        rasterization.lineWidth = 1.0f;

        VkPipelineMultisampleStateCreateInfo multisample{
            .sType = VK_STRUCTURE_TYPE_PIPELINE_MULTISAMPLE_STATE_CREATE_INFO
        };
        multisample.rasterizationSamples = VK_SAMPLE_COUNT_1_BIT;

        VkPipelineDepthStencilStateCreateInfo depth{
            .sType = VK_STRUCTURE_TYPE_PIPELINE_DEPTH_STENCIL_STATE_CREATE_INFO
        };
        depth.depthTestEnable = pass.hasDepth() && key::has(key, key::DEPTH_TEST);
        depth.depthWriteEnable = pass.hasDepth() && key::has(key, key::DEPTH_WRITE);
        depth.depthCompareOp = static_cast<VkCompareOp>(key::field(key, key::COMPARE_SHIFT, 3));

        bool logicOp = device.logicOp && key::has(key, key::LOGIC_OP);
        VkPipelineColorBlendAttachmentState attachment{};
        attachment.blendEnable = !logicOp && key::has(key, key::BLEND);
        attachment.srcColorBlendFactor = static_cast<VkBlendFactor>(key::field(key, key::SOURCE_COLOR_SHIFT, 5));
        attachment.dstColorBlendFactor = static_cast<VkBlendFactor>(key::field(key, key::DESTINATION_COLOR_SHIFT, 5));
        attachment.colorBlendOp = VK_BLEND_OP_ADD;
        attachment.srcAlphaBlendFactor = static_cast<VkBlendFactor>(key::field(key, key::SOURCE_ALPHA_SHIFT, 5));
        attachment.dstAlphaBlendFactor = static_cast<VkBlendFactor>(key::field(key, key::DESTINATION_ALPHA_SHIFT, 5));
        attachment.alphaBlendOp = VK_BLEND_OP_ADD;
        attachment.colorWriteMask = key::field(key, key::COLOR_MASK_SHIFT, 4);
        VkPipelineColorBlendStateCreateInfo colorBlend{
            .sType = VK_STRUCTURE_TYPE_PIPELINE_COLOR_BLEND_STATE_CREATE_INFO
        };
        colorBlend.logicOpEnable = logicOp;
        colorBlend.logicOp = static_cast<VkLogicOp>(key::field(key, key::LOGIC_OP_SHIFT, 4));
        colorBlend.attachmentCount = 1;
        colorBlend.pAttachments = &attachment;

        VkDynamicState dynamicStates[] = {
            VK_DYNAMIC_STATE_VIEWPORT, VK_DYNAMIC_STATE_SCISSOR, VK_DYNAMIC_STATE_LINE_WIDTH,
            VK_DYNAMIC_STATE_DEPTH_BIAS
        };
        VkPipelineDynamicStateCreateInfo dynamic{.sType = VK_STRUCTURE_TYPE_PIPELINE_DYNAMIC_STATE_CREATE_INFO};
        dynamic.dynamicStateCount = 4;
        dynamic.pDynamicStates = dynamicStates;

        VkGraphicsPipelineCreateInfo info{.sType = VK_STRUCTURE_TYPE_GRAPHICS_PIPELINE_CREATE_INFO};
        info.stageCount = 2;
        info.pStages = stages;
        info.pVertexInputState = &vertexInput;
        info.pInputAssemblyState = &inputAssembly;
        info.pViewportState = &viewport;
        info.pRasterizationState = &rasterization;
        info.pMultisampleState = &multisample;
        info.pDepthStencilState = &depth;
        info.pColorBlendState = &colorBlend;
        info.pDynamicState = &dynamic;
        info.layout = layout;
        info.renderPass = pass.compatible();
        info.subpass = 0;
        VkPipeline pipeline;
        check(vkCreateGraphicsPipelines(device.handle, cache, 1, &info, nullptr, &pipeline),
              "vkCreateGraphicsPipelines");
        return pipeline;
    }
} // namespace at
