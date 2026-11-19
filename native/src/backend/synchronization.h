#pragma once

#include "core/common.h"
#include "core/vk.h"

#include <vector>

namespace at::backend {
    struct SyncResource {
        bool image;
        u64 handle;

        bool operator==(const SyncResource &other) const { return image == other.image && handle == other.handle; }
    };

    struct SyncAccess {
        SyncResource resource;
        VkPipelineStageFlags stages;
        VkAccessFlags access;
        bool write;
    };

    struct AccessRecord {
        u64 handle;
        u32 kind;
        u32 access;
        u32 write;
        u32 stages;
    };

    namespace access_kind {
        constexpr u32 TEXTURE = 0;
        constexpr u32 BUFFER = 1;
        constexpr u32 ANY_TEXTURE = 2;
        constexpr u32 ANY_BUFFER = 3;
    }

    class Synchronization {
    public:
        void before(VkCommandBuffer commands, const SyncAccess *accesses, std::size_t count);

        void before(VkCommandBuffer commands, const std::vector<SyncAccess> &accesses) {
            before(commands, accesses.data(), accesses.size());
        }

        void afterWorldPass(VkCommandBuffer commands);

        void after(VkCommandBuffer commands);

        void reset();

        static VkPipelineStageFlags shaderStages(u32 stages);

        static VkPipelineStageFlags defaultStages(u32 stage);

        static SyncAccess decode(const AccessRecord &record, u32 defaultStage);

        static SyncAccess shaderImage(VkImage image, VkPipelineStageFlags stages, bool write);

    private:
        struct State {
            SyncResource resource;
            VkPipelineStageFlags writerStages = 0;
            VkAccessFlags writerAccess = 0;
            VkPipelineStageFlags readerStages = 0;
            VkPipelineStageFlags visibleStages = 0;
            VkAccessFlags visibleAccess = 0;
        };

        std::vector<State> states;
        bool started = false;
    };
} // namespace at::backend
