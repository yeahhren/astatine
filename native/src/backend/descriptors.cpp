#include "backend/descriptors.h"

#include "core/error.h"
#include "backend/backend.h"
#include "backend/pipelines.h"
#include "backend/resources.h"
#include "vulkan/device.h"
#include "vulkan/memory.h"

#include <algorithm>
#include <map>

namespace at::backend {
    namespace {
        std::size_t mix(std::size_t hash, u64 value) {
            return (hash ^ static_cast<std::size_t>(value)) * 1099511628211ull;
        }

        bool lessEntry(const HeapEntry &a, const HeapEntry &b) {
            return a.binding != b.binding ? a.binding < b.binding : a.index < b.index;
        }
    } // namespace

    VkShaderStageFlags shaderStageFlags(u32 stages) {
        VkShaderStageFlags flags = 0;
        for (u32 stage = 0; stage < shader_stage::COUNT; stage++) {
            if (stages & (1u << stage)) flags |= shaderStageBit(stage);
        }
        return flags;
    }

    std::size_t ResourceSets::KeyHash::operator()(const Key &key) const {
        std::size_t hash = 1469598103934665603ull;
        for (const BufferSlice &slice: key.buffers) {
            hash = mix(hash, reinterpret_cast<u64>(slice.buffer));
            hash = mix(hash, slice.offset);
            hash = mix(hash, slice.length);
        }
        for (VkImageView view: key.views) hash = mix(hash, reinterpret_cast<u64>(view));
        return hash;
    }

    ResourceSets::ResourceSets(const SetDescriptor *descriptors, u32 count, u32 capacity, u32 setIndex, u64 uniformSize)
        : setIndex(setIndex), device(Backend::get().device.handle), capacity(capacity), uniformSize(uniformSize) {
        if (capacity < 1) fail("uniform sets need positive capacity");
        if (count == 0) fail("no resources in descriptor set " + std::to_string(setIndex));
        const VkPhysicalDeviceLimits &limits = Backend::get().properties.limits;
        alignment = limits.minUniformBufferOffsetAlignment;
        storageAlignment = limits.minStorageBufferOffsetAlignment;
        maxRange = limits.maxUniformBufferRange;
        maxStorageRange = limits.maxStorageBufferRange;
        if (uniformSize > maxRange) fail("uniform block exceeds maxUniformBufferRange");
        std::vector<VkDescriptorSetLayoutBinding> bindings;
        std::map<VkDescriptorType, u32> counts;
        std::vector<u32> seen;
        for (u32 i = 0; i < count; i++) {
            const SetDescriptor &descriptor = descriptors[i];
            if (descriptor.count < 1 || std::find(seen.begin(), seen.end(), descriptor.binding) != seen.end()) {
                fail("invalid program descriptor at binding " + std::to_string(descriptor.binding));
            }
            seen.push_back(descriptor.binding);
            if (descriptor.uniformBlock && uniformSize == 0) fail("empty generated uniform block");
            (descriptor.kind == descriptor_kind::IMAGE ? images : blocks).push_back(descriptor);
            VkDescriptorType type = descriptorType(descriptor);
            VkShaderStageFlags stages = shaderStageFlags(descriptor.stages);
            if (stages == 0) fail("descriptor has no shader stages at binding " + std::to_string(descriptor.binding));
            bindings.push_back({descriptor.binding, type, descriptor.count, stages, nullptr});
            counts[type] += descriptor.count;
        }
        for (const SetDescriptor &block: blocks) bufferCount += block.count;
        for (const SetDescriptor &image: images) viewCount += image.count;
        for (u32 i = 0; i < blocks.size(); i++) blockOrder.push_back(i);
        std::sort(blockOrder.begin(), blockOrder.end(), [&](u32 a, u32 b) { return blocks[a].binding < blocks[b].binding; });
        for (const SetDescriptor &block: blocks) {
            if (descriptorType(block) == VK_DESCRIPTOR_TYPE_UNIFORM_BUFFER_DYNAMIC) dynamicOffsetCount += block.count;
        }
        VkDescriptorSetLayoutCreateInfo layoutInfo{.sType = VK_STRUCTURE_TYPE_DESCRIPTOR_SET_LAYOUT_CREATE_INFO};
        layoutInfo.bindingCount = static_cast<u32>(bindings.size());
        layoutInfo.pBindings = bindings.data();
        check(vkCreateDescriptorSetLayout(device, &layoutInfo, nullptr, &layout), "vkCreateDescriptorSetLayout");
        std::vector<VkDescriptorPoolSize> sizes;
        for (auto &[type, total]: counts) sizes.push_back({type, total * capacity});
        VkDescriptorPoolCreateInfo poolInfo{.sType = VK_STRUCTURE_TYPE_DESCRIPTOR_POOL_CREATE_INFO};
        poolInfo.flags = VK_DESCRIPTOR_POOL_CREATE_FREE_DESCRIPTOR_SET_BIT;
        poolInfo.maxSets = capacity;
        poolInfo.poolSizeCount = static_cast<u32>(sizes.size());
        poolInfo.pPoolSizes = sizes.data();
        VkResult result = vkCreateDescriptorPool(device, &poolInfo, nullptr, &pool);
        if (result != VK_SUCCESS) {
            vkDestroyDescriptorSetLayout(device, layout, nullptr);
            check(result, "vkCreateDescriptorPool");
        }
    }

    ResourceSets::~ResourceSets() {
        for (auto &[buffer, snapshot]: persistentSnapshots) delete snapshot;
        for (auto &[key, shared]: cached) delete shared;
        if (pool) vkDestroyDescriptorPool(device, pool, nullptr);
        if (layout) vkDestroyDescriptorSetLayout(device, layout, nullptr);
    }

    VkDescriptorType ResourceSets::descriptorType(const SetDescriptor &descriptor) const {
        switch (descriptor.kind) {
            case descriptor_kind::UNIFORM_BUFFER:
                return setIndex == 0 ? VK_DESCRIPTOR_TYPE_UNIFORM_BUFFER_DYNAMIC : VK_DESCRIPTOR_TYPE_UNIFORM_BUFFER;
            case descriptor_kind::STORAGE_BUFFER:
                return VK_DESCRIPTOR_TYPE_STORAGE_BUFFER;
            default:
                return VK_DESCRIPTOR_TYPE_STORAGE_IMAGE;
        }
    }

    ResourceSets::Snapshot *ResourceSets::reuse(const BufferSlice *buffers, u32 count, const VkImageView *views,
                                                u32 viewTotal) {
        SharedSet *shared = previous;
        if (!shared || shared->views.size() != viewTotal || !std::equal(shared->views.begin(), shared->views.end(), views)) {
            return nullptr;
        }
        if (shared->buffers.size() != count) return nullptr;
        std::vector<u32> offsets(dynamicOffsetCount);
        u32 index = 0;
        std::vector<u32> starts(blocks.size());
        u32 cursor = 0;
        for (u32 i = 0; i < blocks.size(); i++) {
            starts[i] = cursor;
            cursor += blocks[i].count;
        }
        for (u32 block: blockOrder) {
            const SetDescriptor &descriptor = blocks[block];
            bool dynamic = descriptorType(descriptor) == VK_DESCRIPTOR_TYPE_UNIFORM_BUFFER_DYNAMIC;
            for (u32 i = 0; i < descriptor.count; i++) {
                const BufferSlice &slice = buffers[starts[block] + i];
                const BufferSlice &bound = shared->buffers[starts[block] + i];
                if (slice.buffer != bound.buffer || slice.length != bound.length) return nullptr;
                if (dynamic) {
                    if (slice.offset % alignment != 0 || slice.offset > 0xFFFFFFFFull) return nullptr;
                    offsets[index++] = static_cast<u32>(slice.offset);
                } else if (slice.offset != bound.offset) {
                    return nullptr;
                }
            }
        }
        shared->references++;
        return new Snapshot{this, shared, std::move(offsets)};
    }

    ResourceSets::Snapshot *ResourceSets::snapshot(const BufferSlice *buffers, u32 count, const VkImageView *views, u32 viewTotal) {
        if (count != bufferCount) fail("buffer bindings do not match shader blocks");
        if (viewTotal != viewCount) fail("image bindings do not match shader images");
        if (Snapshot *reused = reuse(buffers, count, views, viewTotal)) return reused;
        for (u32 i = 0; i < viewTotal; i++) {
            if (!views[i]) fail("invalid image views in descriptor set " + std::to_string(setIndex));
        }
        Key key;
        key.views.assign(views, views + viewTotal);
        key.buffers.resize(count);
        std::vector<u32> offsets;
        u32 cursor = 0;
        std::vector<u32> starts(blocks.size());
        for (u32 i = 0; i < blocks.size(); i++) {
            starts[i] = cursor;
            const SetDescriptor &descriptor = blocks[i];
            bool storage = descriptor.kind == descriptor_kind::STORAGE_BUFFER;
            for (u32 j = 0; j < descriptor.count; j++) {
                const BufferSlice &slice = buffers[cursor + j];
                if (!slice.buffer || slice.length == 0 || slice.offset % (storage ? storageAlignment : alignment) != 0 ||
                    slice.length > (storage ? maxStorageRange : maxRange) ||
                    (descriptor.uniformBlock && slice.length < uniformSize)) {
                    fail("invalid or unaligned buffer slice at binding " + std::to_string(descriptor.binding));
                }
                key.buffers[cursor + j] = slice;
            }
            cursor += descriptor.count;
        }
        for (u32 block: blockOrder) {
            const SetDescriptor &descriptor = blocks[block];
            if (descriptorType(descriptor) != VK_DESCRIPTOR_TYPE_UNIFORM_BUFFER_DYNAMIC) continue;
            for (u32 j = 0; j < descriptor.count; j++) {
                BufferSlice &slice = key.buffers[starts[block] + j];
                if (slice.offset > 0xFFFFFFFFull) fail("dynamic uniform offset exceeds uint32");
                offsets.push_back(static_cast<u32>(slice.offset));
                slice.offset = 0;
            }
        }
        auto found = cached.find(key);
        if (found != cached.end()) {
            found->second->references++;
            previous = found->second;
            return new Snapshot{this, found->second, std::move(offsets)};
        }
        if (live == capacity) fail("retire completed uniform sets before allocating another");
        VkDescriptorSetAllocateInfo allocateInfo{.sType = VK_STRUCTURE_TYPE_DESCRIPTOR_SET_ALLOCATE_INFO};
        allocateInfo.descriptorPool = pool;
        allocateInfo.descriptorSetCount = 1;
        allocateInfo.pSetLayouts = &layout;
        VkDescriptorSet set;
        check(vkAllocateDescriptorSets(device, &allocateInfo, &set), "vkAllocateDescriptorSets");
        std::vector<VkDescriptorBufferInfo> bufferInfos(count);
        std::vector<VkDescriptorImageInfo> imageInfos(viewTotal);
        std::vector<VkWriteDescriptorSet> writes;
        cursor = 0;
        for (const SetDescriptor &descriptor: blocks) {
            for (u32 j = 0; j < descriptor.count; j++) {
                const BufferSlice &slice = key.buffers[cursor + j];
                bufferInfos[cursor + j] = {slice.buffer, slice.offset, slice.length};
            }
            VkWriteDescriptorSet write{.sType = VK_STRUCTURE_TYPE_WRITE_DESCRIPTOR_SET};
            write.dstSet = set;
            write.dstBinding = descriptor.binding;
            write.descriptorCount = descriptor.count;
            write.descriptorType = descriptorType(descriptor);
            write.pBufferInfo = &bufferInfos[cursor];
            writes.push_back(write);
            cursor += descriptor.count;
        }
        cursor = 0;
        for (const SetDescriptor &descriptor: images) {
            for (u32 j = 0; j < descriptor.count; j++) {
                imageInfos[cursor + j] = {VK_NULL_HANDLE, views[cursor + j], VK_IMAGE_LAYOUT_GENERAL};
            }
            VkWriteDescriptorSet write{.sType = VK_STRUCTURE_TYPE_WRITE_DESCRIPTOR_SET};
            write.dstSet = set;
            write.dstBinding = descriptor.binding;
            write.descriptorCount = descriptor.count;
            write.descriptorType = VK_DESCRIPTOR_TYPE_STORAGE_IMAGE;
            write.pImageInfo = &imageInfos[cursor];
            writes.push_back(write);
            cursor += descriptor.count;
        }
        vkUpdateDescriptorSets(device, static_cast<u32>(writes.size()), writes.data(), 0, nullptr);
        live++;
        auto *shared = new SharedSet{set, key.buffers, key.views, 1};
        cached.emplace(std::move(key), shared);
        previous = shared;
        return new Snapshot{this, shared, std::move(offsets)};
    }

    ResourceSets::Snapshot *ResourceSets::persistent(const BufferSlice &buffer) {
        auto found = persistentSnapshots.find(buffer.buffer);
        if (found != persistentSnapshots.end()) return found->second;
        Snapshot *created = snapshot(&buffer, 1, nullptr, 0);
        persistentSnapshots.emplace(buffer.buffer, created);
        return created;
    }

    void ResourceSets::retire(SharedSet *shared) {
        if (--shared->references != 0) return;
        vkFreeDescriptorSets(device, pool, 1, &shared->handle);
        for (auto it = cached.begin(); it != cached.end(); ++it) {
            if (it->second == shared) {
                cached.erase(it);
                break;
            }
        }
        if (previous == shared) previous = nullptr;
        delete shared;
        live--;
    }

    void ResourceSets::Snapshot::bind(VkCommandBuffer commands, VkPipelineLayout pipelineLayout,
                                      VkPipelineBindPoint bindPoint) const {
        vkCmdBindDescriptorSets(commands, bindPoint, pipelineLayout, owner->setIndex, 1, &shared->handle,
                                static_cast<u32>(offsets.size()), offsets.empty() ? nullptr : offsets.data());
    }

    void ResourceSets::Snapshot::release() {
        owner->retire(shared);
        delete this;
    }

    void ResourceSets::release() {
        ResourceSets *self = this;
        Backend::get().defer([self] { delete self; });
    }

    std::size_t SamplerHeap::EntryListHash::operator()(const std::vector<HeapEntry> &entries) const {
        std::size_t hash = 1469598103934665603ull;
        for (const HeapEntry &entry: entries) {
            hash = mix(hash, entry.binding);
            hash = mix(hash, entry.index);
            hash = mix(hash, entry.view);
            hash = mix(hash, entry.sampler);
            hash = mix(hash, entry.layout);
        }
        return hash;
    }

    VkDescriptorType SamplerHeap::descriptorType(u32 flags) {
        return flags & heap_flags::TEXEL ? VK_DESCRIPTOR_TYPE_UNIFORM_TEXEL_BUFFER : VK_DESCRIPTOR_TYPE_COMBINED_IMAGE_SAMPLER;
    }

    SamplerHeap::SamplerHeap(const HeapType *heaps, u32 heapCount, const HeapType *draws, u32 drawCount, u32 poolCapacity)
        : device(Backend::get().device.handle), allocator(&Backend::get().allocator), heapTypes(heaps, heaps + heapCount), drawTypes(draws, draws + drawCount),
          poolCapacity(poolCapacity) {
        if (poolCapacity < 1) fail("heap must allow at least one descriptor generation");
        Backend &backend = Backend::get();
        for (const HeapType &type: heapTypes) descriptorCount += type.count;
        validateLimits();
        try {
            std::vector<VkDescriptorSetLayoutBinding> bindings;
            for (const HeapType &type: heapTypes) {
                bindings.push_back({type.binding, descriptorType(type.flags), type.count, VK_SHADER_STAGE_ALL, nullptr});
            }
            VkDescriptorSetLayoutCreateInfo info{.sType = VK_STRUCTURE_TYPE_DESCRIPTOR_SET_LAYOUT_CREATE_INFO};
            info.bindingCount = static_cast<u32>(bindings.size());
            info.pBindings = bindings.data();
            std::vector<VkDescriptorBindingFlags> flags(bindings.size(), VK_DESCRIPTOR_BINDING_UPDATE_AFTER_BIND_BIT);
            VkDescriptorSetLayoutBindingFlagsCreateInfo bindingFlags{
                .sType = VK_STRUCTURE_TYPE_DESCRIPTOR_SET_LAYOUT_BINDING_FLAGS_CREATE_INFO
            };
            if (backend.updateAfterBind) {
                bindingFlags.bindingCount = static_cast<u32>(flags.size());
                bindingFlags.pBindingFlags = flags.data();
                info.flags = VK_DESCRIPTOR_SET_LAYOUT_CREATE_UPDATE_AFTER_BIND_POOL_BIT;
                info.pNext = &bindingFlags;
            }
            if (vkGetDescriptorSetLayoutSupportKHR) {
                VkDescriptorSetLayoutSupport support{.sType = VK_STRUCTURE_TYPE_DESCRIPTOR_SET_LAYOUT_SUPPORT};
                vkGetDescriptorSetLayoutSupportKHR(device, &info, &support);
                if (!support.supported) fail("the device cannot support this sampler heap layout");
            }
            check(vkCreateDescriptorSetLayout(device, &info, nullptr, &setLayout), "vkCreateDescriptorSetLayout");
            if (!drawTypes.empty()) {
                u32 count = 0;
                for (const HeapType &type: drawTypes) count += type.count;
                if (count > backend.maxPushDescriptors) fail("too many per-draw samplers: " + std::to_string(count));
                if (!vkCmdPushDescriptorSetKHR) fail("per-draw samplers need VK_KHR_push_descriptor");
                std::vector<VkDescriptorSetLayoutBinding> drawBindings;
                for (const HeapType &type: drawTypes) {
                    drawBindings.push_back({type.binding, descriptorType(type.flags), type.count, VK_SHADER_STAGE_ALL, nullptr});
                }
                VkDescriptorSetLayoutCreateInfo drawInfo{.sType = VK_STRUCTURE_TYPE_DESCRIPTOR_SET_LAYOUT_CREATE_INFO};
                drawInfo.flags = VK_DESCRIPTOR_SET_LAYOUT_CREATE_PUSH_DESCRIPTOR_BIT_KHR;
                drawInfo.bindingCount = static_cast<u32>(drawBindings.size());
                drawInfo.pBindings = drawBindings.data();
                check(vkCreateDescriptorSetLayout(device, &drawInfo, nullptr, &drawSetLayout), "vkCreateDescriptorSetLayout");
                VkDescriptorSetLayoutCreateInfo emptyInfo{.sType = VK_STRUCTURE_TYPE_DESCRIPTOR_SET_LAYOUT_CREATE_INFO};
                check(vkCreateDescriptorSetLayout(device, &emptyInfo, nullptr, &emptySetLayout), "vkCreateDescriptorSetLayout");
            }
            currentPool = createPool();
            auto addDummy = [&](const HeapType &type) {
                if (dummies.contains(type.binding)) return;
                Dummy dummy = createDummy(type.flags);
                if (!dummy.texel) dummy.sampler = sampler((type.flags & heap_flags::SHADOW ? 8u : 0u) | 16u);
                dummies.emplace(type.binding, dummy);
            };
            for (const HeapType &type: heapTypes) addDummy(type);
            for (const HeapType &type: drawTypes) addDummy(type);
        } catch (...) {
            for (auto &[binding, dummy]: dummies) destroyDummy(dummy);
            for (auto &[description, handle]: samplers) vkDestroySampler(device, handle, nullptr);
            for (auto &[handle, live]: pools) vkDestroyDescriptorPool(device, handle, nullptr);
            if (drawSetLayout) vkDestroyDescriptorSetLayout(device, drawSetLayout, nullptr);
            if (emptySetLayout) vkDestroyDescriptorSetLayout(device, emptySetLayout, nullptr);
            if (setLayout) vkDestroyDescriptorSetLayout(device, setLayout, nullptr);
            throw;
        }
    }

    SamplerHeap::~SamplerHeap() {
        for (auto &[entries, snapshot]: frameSnapshots) delete snapshot;
        for (auto &[handle, live]: pools) vkDestroyDescriptorPool(device, handle, nullptr);
        for (auto &[binding, dummy]: dummies) destroyDummy(dummy);
        for (auto &[description, handle]: samplers) vkDestroySampler(device, handle, nullptr);
        if (drawSetLayout) vkDestroyDescriptorSetLayout(device, drawSetLayout, nullptr);
        if (emptySetLayout) vkDestroyDescriptorSetLayout(device, emptySetLayout, nullptr);
        if (setLayout) vkDestroyDescriptorSetLayout(device, setLayout, nullptr);
    }

    void SamplerHeap::validateLimits() const {
        Backend &backend = Backend::get();
        const VkPhysicalDeviceLimits &limits = backend.properties.limits;
        u64 count = 0;
        u64 samplerCount = 0;
        u64 drawCount = 0;
        u64 drawSamplers = 0;
        for (const HeapType &type: heapTypes) {
            count += type.count;
            if (!(type.flags & heap_flags::TEXEL)) samplerCount += type.count;
        }
        for (const HeapType &type: drawTypes) {
            count += type.count;
            drawCount += type.count;
            if (!(type.flags & heap_flags::TEXEL)) {
                samplerCount += type.count;
                drawSamplers += type.count;
            }
        }
        u64 regularCount = backend.updateAfterBind ? drawCount : count;
        u64 regularSamplers = backend.updateAfterBind ? drawSamplers : samplerCount;
        if (regularSamplers > limits.maxPerStageDescriptorSamplers || regularCount > limits.maxPerStageDescriptorSampledImages ||
            regularSamplers > limits.maxDescriptorSetSamplers || regularCount > limits.maxDescriptorSetSampledImages ||
            regularCount > limits.maxPerStageResources) {
            fail("sampler heap exceeds device descriptor limits: " + std::to_string(regularCount) + " descriptors");
        }
    }

    VkDescriptorPool SamplerHeap::createPool() {
        std::map<VkDescriptorType, u32> counts;
        for (const HeapType &type: heapTypes) counts[descriptorType(type.flags)] += type.count;
        std::vector<VkDescriptorPoolSize> sizes;
        for (auto &[type, count]: counts) sizes.push_back({type, count * poolCapacity});
        VkDescriptorPoolCreateInfo info{.sType = VK_STRUCTURE_TYPE_DESCRIPTOR_POOL_CREATE_INFO};
        info.flags = VK_DESCRIPTOR_POOL_CREATE_FREE_DESCRIPTOR_SET_BIT;
        if (Backend::get().updateAfterBind) info.flags |= VK_DESCRIPTOR_POOL_CREATE_UPDATE_AFTER_BIND_BIT;
        info.maxSets = poolCapacity;
        info.poolSizeCount = static_cast<u32>(sizes.size());
        info.pPoolSizes = sizes.empty() ? nullptr : sizes.data();
        VkDescriptorPool pool;
        check(vkCreateDescriptorPool(device, &info, nullptr, &pool), "vkCreateDescriptorPool");
        pools.emplace_back(pool, 0);
        return pool;
    }

    SamplerHeap::Dummy SamplerHeap::createDummy(u32 flags) {
        Backend &backend = Backend::get();
        Dummy dummy;
        dummy.texel = flags & heap_flags::TEXEL;
        VkCommandBuffer commands = backend.commands();
        if (dummy.texel) {
            VkFormat format = flags & heap_flags::SIGNED ? VK_FORMAT_R32_SINT : flags & heap_flags::UNSIGNED
                                                                                ? VK_FORMAT_R32_UINT
                                                                                : VK_FORMAT_R32_SFLOAT;
            VkBufferCreateInfo info{.sType = VK_STRUCTURE_TYPE_BUFFER_CREATE_INFO};
            info.size = 16;
            info.usage = VK_BUFFER_USAGE_UNIFORM_TEXEL_BUFFER_BIT | VK_BUFFER_USAGE_TRANSFER_DST_BIT;
            info.sharingMode = VK_SHARING_MODE_EXCLUSIVE;
            check(vkCreateBuffer(device, &info, nullptr, &dummy.buffer), "vkCreateBuffer");
            VkMemoryRequirements requirements;
            vkGetBufferMemoryRequirements(device, dummy.buffer, &requirements);
            dummy.memory = new Allocation(backend.allocator.allocate(requirements, VK_MEMORY_PROPERTY_DEVICE_LOCAL_BIT, 0, true));
            check(vkBindBufferMemory(device, dummy.buffer, dummy.memory->memory, dummy.memory->offset), "vkBindBufferMemory");
            VkBufferViewCreateInfo viewInfo{.sType = VK_STRUCTURE_TYPE_BUFFER_VIEW_CREATE_INFO};
            viewInfo.buffer = dummy.buffer;
            viewInfo.format = format;
            viewInfo.range = 16;
            check(vkCreateBufferView(device, &viewInfo, nullptr, &dummy.bufferView), "vkCreateBufferView");
            vkCmdFillBuffer(commands, dummy.buffer, 0, 16, 0);
            memoryBarrier(commands);
            return dummy;
        }
        bool shadow = flags & heap_flags::SHADOW;
        bool cube = flags & heap_flags::CUBE;
        bool array = flags & heap_flags::ARRAY;
        bool multisampled = flags & heap_flags::MULTISAMPLED;
        VkImageType dimension = flags & heap_flags::ONE_DIMENSIONAL ? VK_IMAGE_TYPE_1D : flags & heap_flags::THREE_DIMENSIONAL
                                                                                    ? VK_IMAGE_TYPE_3D
                                                                                    : VK_IMAGE_TYPE_2D;
        VkImageViewType viewType = cube
                                       ? (array ? VK_IMAGE_VIEW_TYPE_CUBE_ARRAY : VK_IMAGE_VIEW_TYPE_CUBE)
                                       : dimension == VK_IMAGE_TYPE_1D
                                             ? (array ? VK_IMAGE_VIEW_TYPE_1D_ARRAY : VK_IMAGE_VIEW_TYPE_1D)
                                             : dimension == VK_IMAGE_TYPE_3D
                                                   ? VK_IMAGE_VIEW_TYPE_3D
                                                   : (array ? VK_IMAGE_VIEW_TYPE_2D_ARRAY : VK_IMAGE_VIEW_TYPE_2D);
        VkFormat format = shadow ? VK_FORMAT_D16_UNORM : flags & heap_flags::SIGNED ? VK_FORMAT_R8G8B8A8_SINT
                                                       : flags & heap_flags::UNSIGNED ? VK_FORMAT_R8G8B8A8_UINT
                                                       : VK_FORMAT_R8G8B8A8_UNORM;
        VkImageAspectFlags aspect = shadow ? VK_IMAGE_ASPECT_DEPTH_BIT : VK_IMAGE_ASPECT_COLOR_BIT;
        u32 layers = cube ? 6 : 1;
        VkImageCreateFlags createFlags = cube ? VK_IMAGE_CREATE_CUBE_COMPATIBLE_BIT : 0;
        VkImageUsageFlags usage = VK_IMAGE_USAGE_SAMPLED_BIT | VK_IMAGE_USAGE_TRANSFER_DST_BIT;
        VkImageFormatProperties properties{};
        check(vkGetPhysicalDeviceImageFormatProperties(backend.device.physical, format, dimension, VK_IMAGE_TILING_OPTIMAL,
                                                       usage, createFlags, &properties),
              "vkGetPhysicalDeviceImageFormatProperties");
        VkSampleCountFlags samples = VK_SAMPLE_COUNT_1_BIT;
        if (multisampled) {
            VkSampleCountFlags available = properties.sampleCounts & ~static_cast<VkSampleCountFlags>(VK_SAMPLE_COUNT_1_BIT);
            samples = available & (~available + 1);
            if (samples == 0) fail("unsupported dummy sampler type");
        }
        VkImageCreateInfo info{.sType = VK_STRUCTURE_TYPE_IMAGE_CREATE_INFO};
        info.flags = createFlags;
        info.imageType = dimension;
        info.format = format;
        info.extent = {1, 1, 1};
        info.mipLevels = 1;
        info.arrayLayers = layers;
        info.samples = static_cast<VkSampleCountFlagBits>(samples);
        info.tiling = VK_IMAGE_TILING_OPTIMAL;
        info.usage = usage;
        info.sharingMode = VK_SHARING_MODE_EXCLUSIVE;
        info.initialLayout = VK_IMAGE_LAYOUT_UNDEFINED;
        check(vkCreateImage(device, &info, nullptr, &dummy.image), "vkCreateImage");
        VkMemoryRequirements requirements;
        vkGetImageMemoryRequirements(device, dummy.image, &requirements);
        dummy.memory = new Allocation(backend.allocator.allocate(requirements, 0, VK_MEMORY_PROPERTY_DEVICE_LOCAL_BIT, false));
        check(vkBindImageMemory(device, dummy.image, dummy.memory->memory, dummy.memory->offset), "vkBindImageMemory");
        VkImageViewCreateInfo viewInfo{.sType = VK_STRUCTURE_TYPE_IMAGE_VIEW_CREATE_INFO};
        viewInfo.image = dummy.image;
        viewInfo.viewType = viewType;
        viewInfo.format = format;
        viewInfo.subresourceRange = {aspect, 0, 1, 0, layers};
        check(vkCreateImageView(device, &viewInfo, nullptr, &dummy.view), "vkCreateImageView");
        VkImageMemoryBarrier barrier{.sType = VK_STRUCTURE_TYPE_IMAGE_MEMORY_BARRIER};
        barrier.oldLayout = VK_IMAGE_LAYOUT_UNDEFINED;
        barrier.newLayout = VK_IMAGE_LAYOUT_GENERAL;
        barrier.dstAccessMask = VK_ACCESS_TRANSFER_WRITE_BIT;
        barrier.srcQueueFamilyIndex = VK_QUEUE_FAMILY_IGNORED;
        barrier.dstQueueFamilyIndex = VK_QUEUE_FAMILY_IGNORED;
        barrier.image = dummy.image;
        barrier.subresourceRange = viewInfo.subresourceRange;
        vkCmdPipelineBarrier(commands, VK_PIPELINE_STAGE_TOP_OF_PIPE_BIT, VK_PIPELINE_STAGE_TRANSFER_BIT, 0, 0, nullptr, 0,
                             nullptr, 1, &barrier);
        if (!multisampled) {
            if (shadow) {
                VkClearDepthStencilValue clear{1.0f, 0};
                vkCmdClearDepthStencilImage(commands, dummy.image, VK_IMAGE_LAYOUT_GENERAL, &clear, 1,
                                            &barrier.subresourceRange);
            } else {
                VkClearColorValue clear{};
                vkCmdClearColorImage(commands, dummy.image, VK_IMAGE_LAYOUT_GENERAL, &clear, 1, &barrier.subresourceRange);
            }
        }
        memoryBarrier(commands);
        return dummy;
    }

    void SamplerHeap::destroyDummy(const Dummy &dummy) const {
        if (dummy.bufferView) vkDestroyBufferView(device, dummy.bufferView, nullptr);
        if (dummy.view) vkDestroyImageView(device, dummy.view, nullptr);
        if (dummy.buffer) vkDestroyBuffer(device, dummy.buffer, nullptr);
        if (dummy.image) vkDestroyImage(device, dummy.image, nullptr);
        if (dummy.memory) {
            allocator->release(*dummy.memory);
            delete dummy.memory;
        }
    }

    std::vector<VkDescriptorSetLayout> SamplerHeap::pipelineSetLayouts(VkDescriptorSetLayout uniforms,
                                                                       VkDescriptorSetLayout frame) const {
        if (drawSetLayout) return {uniforms, setLayout, frame ? frame : emptySetLayout, drawSetLayout};
        if (frame) return {uniforms, setLayout, frame};
        return {uniforms, setLayout};
    }

    VkSampler SamplerHeap::sampler(u32 description) {
        auto found = samplers.find(description);
        if (found != samplers.end()) return found->second;
        bool linear = description & 1;
        bool linearMips = description & 2;
        bool mipmapped = description & 4;
        bool hardwareShadow = description & 8;
        bool clamp = description & 16;
        VkSamplerCreateInfo info{.sType = VK_STRUCTURE_TYPE_SAMPLER_CREATE_INFO};
        VkFilter filter = linear ? VK_FILTER_LINEAR : VK_FILTER_NEAREST;
        VkSamplerAddressMode address = clamp ? VK_SAMPLER_ADDRESS_MODE_CLAMP_TO_EDGE : VK_SAMPLER_ADDRESS_MODE_REPEAT;
        info.minFilter = filter;
        info.magFilter = filter;
        info.mipmapMode = mipmapped && linearMips ? VK_SAMPLER_MIPMAP_MODE_LINEAR : VK_SAMPLER_MIPMAP_MODE_NEAREST;
        info.addressModeU = address;
        info.addressModeV = address;
        info.addressModeW = address;
        info.maxLod = mipmapped ? VK_LOD_CLAMP_NONE : 0.0f;
        info.compareEnable = hardwareShadow;
        info.compareOp = VK_COMPARE_OP_LESS_OR_EQUAL;
        VkSampler handle;
        check(vkCreateSampler(device, &info, nullptr, &handle), "vkCreateSampler");
        samplers.emplace(description, handle);
        return handle;
    }

    void SamplerHeap::write(VkDescriptorSet set, const std::vector<HeapType> &types, const HeapEntry *entries, u32 count,
                            bool push, VkCommandBuffer commands, VkPipelineLayout pipelineLayout,
                            VkPipelineBindPoint bindPoint) {
        std::vector<VkWriteDescriptorSet> writes;
        std::vector<std::vector<VkDescriptorImageInfo> > images(types.size());
        std::vector<std::vector<VkBufferView> > texels(types.size());
        for (std::size_t t = 0; t < types.size(); t++) {
            const HeapType &type = types[t];
            const Dummy &dummy = dummies.at(type.binding);
            bool texel = type.flags & heap_flags::TEXEL;
            if (texel) texels[t].assign(type.count, dummy.bufferView);
            else images[t].assign(type.count, {dummy.sampler, dummy.view, VK_IMAGE_LAYOUT_GENERAL});
            for (u32 i = 0; i < count; i++) {
                const HeapEntry &entry = entries[i];
                if (entry.binding != type.binding || entry.index >= type.count) continue;
                if (texel) {
                    texels[t][entry.index] = reinterpret_cast<VkBufferView>(entry.view);
                } else {
                    images[t][entry.index] = {
                        reinterpret_cast<VkSampler>(entry.sampler), reinterpret_cast<VkImageView>(entry.view),
                        static_cast<VkImageLayout>(entry.layout)
                    };
                }
            }
            VkWriteDescriptorSet write{.sType = VK_STRUCTURE_TYPE_WRITE_DESCRIPTOR_SET};
            write.dstSet = set;
            write.dstBinding = type.binding;
            write.descriptorCount = type.count;
            write.descriptorType = descriptorType(type.flags);
            if (texel) write.pTexelBufferView = texels[t].data();
            else write.pImageInfo = images[t].data();
            writes.push_back(write);
        }
        if (writes.empty()) return;
        if (push) {
            vkCmdPushDescriptorSetKHR(commands, bindPoint, pipelineLayout, DRAW_DESCRIPTOR_SET, static_cast<u32>(writes.size()),
                                      writes.data());
        } else {
            vkUpdateDescriptorSets(device, static_cast<u32>(writes.size()), writes.data(), 0, nullptr);
        }
    }

    SamplerHeap::Snapshot *SamplerHeap::snapshot(const HeapEntry *entries, u32 count) {
        for (u32 i = 0; i < count; i++) {
            bool known = false;
            for (const HeapType &type: heapTypes) {
                if (type.binding == entries[i].binding && entries[i].index < type.count &&
                    ((type.flags & heap_flags::TEXEL) != 0) == (entries[i].texel != 0)) {
                    known = true;
                }
            }
            if (!known) fail("descriptor does not match heap type at binding " + std::to_string(entries[i].binding));
        }
        u32 &used = std::find_if(pools.begin(), pools.end(), [&](const auto &pool) { return pool.first == currentPool; })->second;
        if (used == poolCapacity) {
            auto free = std::find_if(pools.begin(), pools.end(), [&](const auto &pool) { return pool.second < poolCapacity; });
            currentPool = free != pools.end() ? free->first : createPool();
        }
        VkDescriptorSetAllocateInfo info{.sType = VK_STRUCTURE_TYPE_DESCRIPTOR_SET_ALLOCATE_INFO};
        info.descriptorPool = currentPool;
        info.descriptorSetCount = 1;
        info.pSetLayouts = &setLayout;
        VkDescriptorSet set;
        check(vkAllocateDescriptorSets(device, &info, &set), "vkAllocateDescriptorSets");
        write(set, heapTypes, entries, count, false, VK_NULL_HANDLE, VK_NULL_HANDLE, VK_PIPELINE_BIND_POINT_GRAPHICS);
        liveSnapshots++;
        for (auto &pool: pools) {
            if (pool.first == currentPool) pool.second++;
        }
        return new Snapshot{this, nullptr, currentPool, set, {}, 1};
    }

    SamplerHeap::Snapshot *SamplerHeap::cachedSnapshot(const HeapEntry *entries, u32 count) {
        std::vector<HeapEntry> key(entries, entries + count);
        std::sort(key.begin(), key.end(), lessEntry);
        auto found = frameSnapshots.find(key);
        if (found != frameSnapshots.end()) return found->second;
        Snapshot *created = snapshot(key.data(), static_cast<u32>(key.size()));
        frameSnapshots.emplace(std::move(key), created);
        return created;
    }

    SamplerHeap::Snapshot *SamplerHeap::withDraws(Snapshot *parent, const HeapEntry *draws, u32 count) {
        if (count == 0) return parent->retain();
        for (u32 i = 0; i < count; i++) {
            bool known = false;
            for (const HeapType &type: drawTypes) {
                if (type.binding == draws[i].binding && draws[i].index < type.count &&
                    ((type.flags & heap_flags::TEXEL) != 0) == (draws[i].texel != 0)) {
                    known = true;
                }
            }
            if (!known) fail("invalid per-draw sampler at binding " + std::to_string(draws[i].binding));
        }
        return new Snapshot{this, parent->retain(), parent->pool, parent->set, std::vector<HeapEntry>(draws, draws + count), 1};
    }

    void SamplerHeap::Snapshot::bind(VkCommandBuffer commands, VkPipelineLayout pipelineLayout,
                                     VkPipelineBindPoint bindPoint) const {
        vkCmdBindDescriptorSets(commands, bindPoint, pipelineLayout, DESCRIPTOR_SET, 1, &set, 0, nullptr);
        if (owner->hasDraws()) {
            owner->write(VK_NULL_HANDLE, owner->drawTypes, draws.data(), static_cast<u32>(draws.size()), true, commands,
                         pipelineLayout, bindPoint);
        }
    }

    void SamplerHeap::Snapshot::release() {
        if (--references != 0) return;
        if (parent) {
            parent->release();
            delete this;
            return;
        }
        vkFreeDescriptorSets(owner->device, pool, 1, &set);
        for (auto &entry: owner->pools) {
            if (entry.first == pool) entry.second--;
        }
        owner->liveSnapshots--;
        delete this;
    }

    void SamplerHeap::beginFrame() {
        if (frameSnapshots.empty()) return;
        std::vector<Snapshot *> retired;
        for (auto &[entries, snapshot]: frameSnapshots) retired.push_back(snapshot);
        frameSnapshots.clear();
        Backend::get().defer([retired] {
            for (Snapshot *snapshot: retired) snapshot->release();
        });
    }

    void SamplerHeap::release() {
        beginFrame();
        SamplerHeap *self = this;
        Backend::get().defer([self] { delete self; });
    }
} // namespace at::backend
