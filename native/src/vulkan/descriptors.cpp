#include "vulkan/descriptors.h"

#include <ranges>

#include "core/error.h"
#include "vulkan/device.h"

namespace at {
    Samplers::~Samplers() {
        for (auto &sampler: samplers | std::views::values) vkDestroySampler(device.handle, sampler, nullptr);
    }

    VkSampler Samplers::get(const SamplerState &state) {
        if (auto found = samplers.find(state); found != samplers.end()) return found->second;
        VkSamplerCreateInfo info{.sType = VK_STRUCTURE_TYPE_SAMPLER_CREATE_INFO};
        info.magFilter = state.magFilter;
        info.minFilter = state.minFilter;
        info.mipmapMode = state.mipmapMode;
        info.addressModeU = state.addressU;
        info.addressModeV = state.addressV;
        info.addressModeW = state.addressU;
        info.mipLodBias = state.lodBias;
        info.compareOp = VK_COMPARE_OP_ALWAYS;
        info.minLod = state.minLod;
        info.maxLod = state.maxLod;
        info.borderColor = VK_BORDER_COLOR_FLOAT_TRANSPARENT_BLACK;
        VkSampler sampler;
        check(vkCreateSampler(device.handle, &info, nullptr, &sampler), "vkCreateSampler");
        samplers.emplace(state, sampler);
        return sampler;
    }

    BindlessTextureTable::BindlessTextureTable(const Device &device, VkDescriptorSetLayout layout) : device(device) {
        VkDescriptorPoolSize size{VK_DESCRIPTOR_TYPE_COMBINED_IMAGE_SAMPLER, device.bindlessCapacity};
        VkDescriptorPoolCreateInfo poolInfo{VK_STRUCTURE_TYPE_DESCRIPTOR_POOL_CREATE_INFO};
        poolInfo.flags = VK_DESCRIPTOR_POOL_CREATE_UPDATE_AFTER_BIND_BIT_EXT;
        poolInfo.maxSets = 1;
        poolInfo.poolSizeCount = 1;
        poolInfo.pPoolSizes = &size;
        check(vkCreateDescriptorPool(device.handle, &poolInfo, nullptr, &pool), "vkCreateDescriptorPool");
        VkDescriptorSetAllocateInfo info{VK_STRUCTURE_TYPE_DESCRIPTOR_SET_ALLOCATE_INFO};
        info.descriptorPool = pool;
        info.descriptorSetCount = 1;
        info.pSetLayouts = &layout;
        check(vkAllocateDescriptorSets(device.handle, &info, &set), "vkAllocateDescriptorSets");
        free.reserve(device.bindlessCapacity);
        for (u32 i = device.bindlessCapacity; i > 0; i--) free.push_back(i - 1);
    }

    BindlessTextureTable::~BindlessTextureTable() {
        vkDestroyDescriptorPool(device.handle, pool, nullptr);
    }

    u64 BindlessTextureTable::acquire(VkImageView view, VkSampler sampler) {
        if (free.empty()) fail("Bindless texture table is full");
        u32 slot = free.back();
        free.pop_back();
        VkDescriptorImageInfo image{.sampler = sampler, .imageView = view, .imageLayout = VK_IMAGE_LAYOUT_SHADER_READ_ONLY_OPTIMAL};
        VkWriteDescriptorSet write{.sType = VK_STRUCTURE_TYPE_WRITE_DESCRIPTOR_SET};
        write.dstSet = set;
        write.dstBinding = 0;
        write.dstArrayElement = slot;
        write.descriptorCount = 1;
        write.descriptorType = VK_DESCRIPTOR_TYPE_COMBINED_IMAGE_SAMPLER;
        write.pImageInfo = &image;
        vkUpdateDescriptorSets(device.handle, 1, &write, 0, nullptr);
        return slot;
    }

    BoundTextureTable::~BoundTextureTable() {
        for (auto &[handle, live]: pools) vkDestroyDescriptorPool(device.handle, handle, nullptr);
    }

    u64 BoundTextureTable::acquire(VkImageView view, VkSampler sampler) {
        std::size_t owner = pools.size();
        for (std::size_t i = 0; i < pools.size(); i++) {
            if (pools[i].live < POOL_SIZE) {
                owner = i;
                break;
            }
        }
        if (owner == pools.size()) {
            VkDescriptorPoolSize size{VK_DESCRIPTOR_TYPE_COMBINED_IMAGE_SAMPLER, POOL_SIZE};
            VkDescriptorPoolCreateInfo info{VK_STRUCTURE_TYPE_DESCRIPTOR_POOL_CREATE_INFO};
            info.flags = VK_DESCRIPTOR_POOL_CREATE_FREE_DESCRIPTOR_SET_BIT;
            info.maxSets = POOL_SIZE;
            info.poolSizeCount = 1;
            info.pPoolSizes = &size;
            VkDescriptorPool created;
            check(vkCreateDescriptorPool(device.handle, &info, nullptr, &created), "vkCreateDescriptorPool");
            pools.push_back({.handle = created, .live = 0});
        }
        VkDescriptorSetAllocateInfo allocateInfo{.sType = VK_STRUCTURE_TYPE_DESCRIPTOR_SET_ALLOCATE_INFO};
        allocateInfo.descriptorPool = pools[owner].handle;
        allocateInfo.descriptorSetCount = 1;
        allocateInfo.pSetLayouts = &layout;
        VkDescriptorSet set;
        check(vkAllocateDescriptorSets(device.handle, &allocateInfo, &set), "vkAllocateDescriptorSets");
        pools[owner].live++;
        u64 handle = reinterpret_cast<u64>(set);
        owners[handle] = owner;

        VkDescriptorImageInfo image{sampler, view, VK_IMAGE_LAYOUT_SHADER_READ_ONLY_OPTIMAL};
        VkWriteDescriptorSet write{VK_STRUCTURE_TYPE_WRITE_DESCRIPTOR_SET};
        write.dstSet = set;
        write.dstBinding = 0;
        write.descriptorCount = 1;
        write.descriptorType = VK_DESCRIPTOR_TYPE_COMBINED_IMAGE_SAMPLER;
        write.pImageInfo = &image;
        vkUpdateDescriptorSets(device.handle, 1, &write, 0, nullptr);
        return handle;
    }

    void BoundTextureTable::release(u64 handle) {
        auto found = owners.find(handle);
        if (found == owners.end()) return;
        Pool &pool = pools[found->second];
        owners.erase(found);
        pool.live--;
        auto set = reinterpret_cast<VkDescriptorSet>(handle);
        vkFreeDescriptorSets(device.handle, pool.handle, 1, &set);
    }
} // namespace at
