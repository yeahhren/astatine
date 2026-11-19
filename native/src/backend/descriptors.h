#pragma once

#include "core/common.h"
#include "core/vk.h"

#include <unordered_map>
#include <vector>

namespace at {
    struct Allocation;
    class MemoryAllocator;
}

namespace at::backend {
    namespace descriptor_kind {
        constexpr u32 UNIFORM_BUFFER = 0;
        constexpr u32 STORAGE_BUFFER = 1;
        constexpr u32 IMAGE = 2;
    }

    struct SetDescriptor {
        u32 binding;
        u32 kind;
        u32 count;
        u32 stages;
        u32 uniformBlock;
    };

    struct BufferSlice {
        VkBuffer buffer;
        u64 offset;
        u64 length;

        bool operator==(const BufferSlice &other) const {
            return buffer == other.buffer && offset == other.offset && length == other.length;
        }
    };

    VkShaderStageFlags shaderStageFlags(u32 stages);

    class ResourceSets {
    public:
        ResourceSets(const SetDescriptor *descriptors, u32 count, u32 capacity, u32 setIndex, u64 uniformSize);

        ~ResourceSets();

        ResourceSets(const ResourceSets &) = delete;

        ResourceSets &operator=(const ResourceSets &) = delete;

        struct SharedSet;

        struct Snapshot {
            ResourceSets *owner;
            SharedSet *shared;
            std::vector<u32> offsets;

            void bind(VkCommandBuffer commands, VkPipelineLayout layout, VkPipelineBindPoint bindPoint) const;

            void release();
        };

        Snapshot *snapshot(const BufferSlice *buffers, u32 bufferCount, const VkImageView *views, u32 viewCount);

        Snapshot *persistent(const BufferSlice &buffer);

        void release();

        VkDescriptorSetLayout layout = VK_NULL_HANDLE;
        u32 setIndex;

        struct SharedSet {
            VkDescriptorSet handle;
            std::vector<BufferSlice> buffers;
            std::vector<VkImageView> views;
            u32 references = 1;
        };

    private:
        struct Key {
            std::vector<BufferSlice> buffers;
            std::vector<VkImageView> views;

            bool operator==(const Key &other) const { return buffers == other.buffers && views == other.views; }
        };

        struct KeyHash {
            std::size_t operator()(const Key &key) const;
        };

        [[nodiscard]] VkDescriptorType descriptorType(const SetDescriptor &descriptor) const;

        Snapshot *reuse(const BufferSlice *buffers, u32 bufferCount, const VkImageView *views, u32 viewCount);

        void retire(SharedSet *shared);

        VkDevice device;
        u32 capacity;
        u64 uniformSize;
        u64 alignment;
        u64 storageAlignment;
        u64 maxRange;
        u64 maxStorageRange;
        std::vector<SetDescriptor> blocks;
        std::vector<SetDescriptor> images;
        std::vector<u32> blockOrder;
        u32 bufferCount = 0;
        u32 viewCount = 0;
        u32 dynamicOffsetCount = 0;
        VkDescriptorPool pool = VK_NULL_HANDLE;
        std::unordered_map<Key, SharedSet *, KeyHash> cached;
        std::unordered_map<VkBuffer, Snapshot *> persistentSnapshots;
        SharedSet *previous = nullptr;
        u32 live = 0;
    };

    namespace heap_flags {
        constexpr u32 TEXEL = 1;
        constexpr u32 SHADOW = 2;
        constexpr u32 CUBE = 4;
        constexpr u32 ARRAY = 8;
        constexpr u32 MULTISAMPLED = 16;
        constexpr u32 ONE_DIMENSIONAL = 32;
        constexpr u32 THREE_DIMENSIONAL = 64;
        constexpr u32 SIGNED = 128;
        constexpr u32 UNSIGNED = 256;
    }

    struct HeapType {
        u32 binding;
        u32 count;
        u32 flags;
    };

    struct HeapEntry {
        u32 binding;
        u32 index;
        u32 texel;
        u32 layout;
        u64 view;
        u64 sampler;

        bool operator==(const HeapEntry &other) const {
            return binding == other.binding && index == other.index && texel == other.texel && layout == other.layout &&
                   view == other.view && sampler == other.sampler;
        }
    };

    class SamplerHeap {
    public:
        static constexpr u32 DESCRIPTOR_SET = 1;
        static constexpr u32 DRAW_DESCRIPTOR_SET = 3;

        SamplerHeap(const HeapType *heaps, u32 heapCount, const HeapType *draws, u32 drawCount, u32 poolCapacity);

        ~SamplerHeap();

        SamplerHeap(const SamplerHeap &) = delete;

        SamplerHeap &operator=(const SamplerHeap &) = delete;

        struct Snapshot {
            SamplerHeap *owner;
            Snapshot *parent;
            VkDescriptorPool pool;
            VkDescriptorSet set;
            std::vector<HeapEntry> draws;
            u32 references = 1;

            void bind(VkCommandBuffer commands, VkPipelineLayout layout, VkPipelineBindPoint bindPoint) const;

            Snapshot *retain() {
                references++;
                return this;
            }

            void release();
        };

        std::vector<VkDescriptorSetLayout> pipelineSetLayouts(VkDescriptorSetLayout uniforms, VkDescriptorSetLayout frame) const;

        VkSampler sampler(u32 description);

        Snapshot *snapshot(const HeapEntry *entries, u32 count);

        Snapshot *cachedSnapshot(const HeapEntry *entries, u32 count);

        Snapshot *withDraws(Snapshot *parent, const HeapEntry *draws, u32 count);

        void beginFrame();

        void release();

        [[nodiscard]] bool hasDraws() const { return !drawTypes.empty(); }

        VkDescriptorSetLayout setLayout = VK_NULL_HANDLE;
        VkDescriptorSetLayout drawSetLayout = VK_NULL_HANDLE;
        VkDescriptorSetLayout emptySetLayout = VK_NULL_HANDLE;

    private:
        struct Dummy {
            bool texel = false;
            VkImage image = VK_NULL_HANDLE;
            VkBuffer buffer = VK_NULL_HANDLE;
            VkImageView view = VK_NULL_HANDLE;
            VkBufferView bufferView = VK_NULL_HANDLE;
            VkSampler sampler = VK_NULL_HANDLE;
            Allocation *memory = nullptr;
        };

        struct EntryListHash {
            std::size_t operator()(const std::vector<HeapEntry> &entries) const;
        };

        static VkDescriptorType descriptorType(u32 flags);

        VkDescriptorPool createPool();

        void validateLimits() const;

        Dummy createDummy(u32 flags);

        void destroyDummy(const Dummy &dummy) const;

        void write(VkDescriptorSet set, const std::vector<HeapType> &types, const HeapEntry *entries, u32 count,
                   bool push, VkCommandBuffer commands, VkPipelineLayout layout, VkPipelineBindPoint bindPoint);

        VkDevice device;
        MemoryAllocator *allocator;
        std::vector<HeapType> heapTypes;
        std::vector<HeapType> drawTypes;
        u32 poolCapacity;
        u32 descriptorCount = 0;
        std::vector<std::pair<VkDescriptorPool, u32> > pools;
        VkDescriptorPool currentPool = VK_NULL_HANDLE;
        std::unordered_map<u32, Dummy> dummies;
        std::unordered_map<u32, VkSampler> samplers;
        std::unordered_map<std::vector<HeapEntry>, Snapshot *, EntryListHash> frameSnapshots;
        u32 liveSnapshots = 0;
    };
} // namespace at::backend
