#pragma once

#include "core/common.h"
#include "core/vk.h"

#include <functional>
#include <memory>
#include <mutex>
#include <string>

namespace at {
    class Renderer;
    class Device;
    class MemoryAllocator;

    namespace shaderpack {
        class ShaderCompiler;
    }
}

namespace at::backend {
    class RenderPassCache;
    class Synchronization;
    class TransientMemory;
    class ShaderCache;
    class ColorMipmaps;
    class DepthMipmaps;
    class ProgramState;
    struct Texture;
    struct TextureView;

    class Backend {
    public:
        static Backend &get();

        static void shutdown();

        Backend();

        ~Backend();

        Backend(const Backend &) = delete;

        Backend &operator=(const Backend &) = delete;

        VkCommandBuffer commands();

        VkCommandBuffer setupCommands();

        void defer(std::function<void()> release);

        void waitIdle();

        void setName(VkObjectType type, u64 handle, const char *name) const;

        [[nodiscard]] bool hasFeature(const char *name) const;

        [[nodiscard]] bool formatFeatures(VkFormat format, VkFormatFeatureFlags required) const;

        void releaseTexture(Texture *texture);

        void releaseView(TextureView *view);

        Renderer &renderer;
        const Device &device;
        MemoryAllocator &allocator;
        VkPhysicalDeviceProperties properties{};
        u32 maxPushDescriptors = 0;
        bool subgroupQuads = false;
        bool anisotropy = false;
        bool demote = false;
        bool geometryShaders = false;
        bool tessellationShaders = false;
        bool updateAfterBind = false;

        std::unique_ptr<RenderPassCache> passes;
        std::unique_ptr<Synchronization> sync;
        std::unique_ptr<TransientMemory> transient;
        std::unique_ptr<ShaderCache> shaders;
        std::unique_ptr<ColorMipmaps> colorMipmaps;
        std::unique_ptr<DepthMipmaps> depthMipmaps;
        std::unique_ptr<ProgramState> programs;

    private:
        static std::unique_ptr<Backend> instance;
        static std::mutex creation;
    };

    void memoryBarrier(VkCommandBuffer commands);

    void barrier(VkCommandBuffer commands, VkPipelineStageFlags sourceStages, VkAccessFlags sourceAccess,
                 VkPipelineStageFlags destinationStages, VkAccessFlags destinationAccess);
} // namespace at::backend
