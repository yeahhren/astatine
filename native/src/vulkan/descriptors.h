#pragma once

#include "core/common.h"
#include "core/vk.h"

#include <unordered_map>
#include <vector>

namespace at {
    class Device;

    struct SamplerState {
        VkFilter magFilter;
        VkFilter minFilter;
        VkSamplerMipmapMode mipmapMode;
        VkSamplerAddressMode addressU;
        VkSamplerAddressMode addressV;
        float minLod;
        float maxLod;
        float lodBias;

        bool operator==(const SamplerState &other) const {
            return std::memcmp(this, &other, sizeof(SamplerState)) == 0;
        }
    };

    struct SamplerStateHash {
        std::size_t operator()(const SamplerState &state) const {
            u64 hash = 1469598103934665603ull;
            auto bytes = reinterpret_cast<const u8 *>(&state);
            for (std::size_t i = 0; i < sizeof(SamplerState); i++) hash = (hash ^ bytes[i]) * 1099511628211ull;
            return hash;
        }
    };

    class Samplers {
    public:
        explicit Samplers(const Device &device) : device(device) {
        }

        ~Samplers();

        VkSampler get(const SamplerState &state);

    private:
        const Device &device;
        std::unordered_map<SamplerState, VkSampler, SamplerStateHash> samplers;
    };

    class TextureTable {
    public:
        virtual ~TextureTable() = default;

        [[nodiscard]] virtual bool bindless() const = 0;

        virtual u64 acquire(VkImageView view, VkSampler sampler) = 0;

        virtual void release(u64 handle) = 0;
    };

    class BindlessTextureTable final : public TextureTable {
    public:
        BindlessTextureTable(const Device &device, VkDescriptorSetLayout layout);

        ~BindlessTextureTable() override;

        bool bindless() const override { return true; }

        u64 acquire(VkImageView view, VkSampler sampler) override;

        void release(u64 handle) override { free.push_back(static_cast<u32>(handle)); }

        VkDescriptorSet set = VK_NULL_HANDLE;

    private:
        const Device &device;
        VkDescriptorPool pool = VK_NULL_HANDLE;
        std::vector<u32> free;
    };

    class BoundTextureTable final : public TextureTable {
    public:
        BoundTextureTable(const Device &device, VkDescriptorSetLayout layout) : device(device), layout(layout) {
        }

        ~BoundTextureTable() override;

        bool bindless() const override { return false; }

        u64 acquire(VkImageView view, VkSampler sampler) override;

        void release(u64 handle) override;

    private:
        static constexpr u32 POOL_SIZE = 512;

        struct Pool {
            VkDescriptorPool handle;
            u32 live;
        };

        const Device &device;
        VkDescriptorSetLayout layout;
        std::vector<Pool> pools;
        std::unordered_map<u64, std::size_t> owners;
    };
} // namespace at
