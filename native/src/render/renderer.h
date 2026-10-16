#pragma once

#include "core/common.h"
#include "core/vk.h"
#include "render/blocks.h"
#include "render/primitives.h"
#include "render/state.h"
#include "render/vertex_layout.h"
#include "vulkan/presenter.h"

#include <functional>
#include <memory>
#include <string>
#include <unordered_set>
#include <vector>

namespace at {
    class Instance;
    class Device;
    class MemoryAllocator;
    class ShaderLibrary;
    class Pipelines;
    class Samplers;
    class TextureTable;
    class GeometryArena;
    class FrameResources;
    class FrameStream;
    class GpuBuffer;
    class GpuImage;
    class RenderPassType;
    class Swapchain;
    class DrawTarget;
    class Framebuffer;
    class SurfaceTarget;

    namespace terrain {
        class Terrain;
    }

    struct Mesh;
    struct GeometryPool;

    struct Context {
        std::unique_ptr<Device> device;
        std::unique_ptr<MemoryAllocator> allocator;
        std::unique_ptr<ShaderLibrary> shaders;
        std::unique_ptr<Pipelines> pipelines;
        std::unique_ptr<Samplers> samplers;
        std::unique_ptr<TextureTable> textures;
        std::unique_ptr<GeometryArena> geometry;

        Context();

        ~Context();
    };

    using WindowSizeFunction = bool (*)(void *window, int *width, int *height);

    struct WindowSystem {
        void *window;

        const char *const* (*getInstanceExtensions)(u32 *count);

        bool (*createSurface)(void *window, VkInstance instance, const VkAllocationCallbacks *allocator,
                              VkSurfaceKHR *surface);

        void (*destroySurface)(VkInstance instance, VkSurfaceKHR surface, const VkAllocationCallbacks *allocator);

        PFN_vkGetInstanceProcAddr (*getInstanceProcAddr)();

        WindowSizeFunction windowSize;
        void *nativeWindow;
    };

    class Renderer {
    public:
        static constexpr u32 FRAMES = 2;
        static constexpr u64 STREAM_SIZE = 32ull << 20;
        static constexpr u64 RECORDS_PER_FRAME = 1ull << 16;
        static constexpr u64 SECTIONS_PER_FRAME = 1ull << 19;
        static constexpr u32 MAX_QUADS = 1u << 16;
        static constexpr u32 BATCH_SIZE = 4096;
        static constexpr u32 INDIRECT_STRIDE = 20;

        static Renderer &get() { return *instance; }
        static bool ready() { return instance != nullptr; }

        static void initialize(const WindowSystem &system, bool validation, std::string cacheFile);

        static void shutdown();

        Renderer(VkSurfaceKHR surfaceHandle, const WindowSystem &system, std::string cacheFile);

        ~Renderer();

        Context &context() const { return *contextValue; }

        void defer(std::function<void()> release);

        void track(class Texture *texture);

        void trackDepth(GpuImage *image);

        void clear(i32 mask);

        void draw(u32 layout, i32 mode, const u8 *source, u32 count);

        void drawMesh(Mesh *mesh, i32 mode);

        Mesh *uploadMesh(u32 layout, const u8 *source, u32 count);

        void releaseMesh(Mesh *mesh);

        void allocateTexture(Texture *texture, u32 levels, u32 width, u32 height);

        void uploadTexture(Texture *texture, u32 level, i32 x, i32 y, u32 width, u32 height, const u8 *source,
                           u32 rowLength);

        void copyFromTarget(Texture *texture, u32 level, i32 destinationX, i32 destinationY, i32 x, i32 y, u32 width,
                            u32 height);

        void read(const Texture *texture, u8 *destination);

        Framebuffer *createFramebuffer(u32 width, u32 height, bool depth);

        void releaseFramebuffer(Framebuffer *framebuffer);

        void bindTarget(DrawTarget *next);

        void bindSurface();

        void present();

        bool blitToSurface(Framebuffer *source, u32 width, u32 height);

        void flush();

        void setVsync(bool enabled);

        void drain();

        u8 *streamBase() const { return commandBuffer; }

        bool prepare(u64 bytes, u64 sectionBytes = 0);

        void bindState(const VertexLayout &layout, u32 topology);

        u32 record();

        void flushBatch();

        void bindQuadIndices(bool flat);

        void bindIndexBuffer(VkBuffer buffer) { bindIndices(buffer, 0); }

        void setVertexBuffers(VkBuffer first, u64 firstOffset, VkBuffer second, u64 secondOffset);

        u8 *stage(u64 bytes, VkBuffer &buffer, u64 &offset);

        VkCommandBuffer setupCommands();

        VkCommandBuffer transferCommands();

        void markSetupTransfers() { setupTransfers = true; }
        VkCommandBuffer commands() const { return main; }
        FrameStream &sectionStream() const { return *sections; }
        FrameStream &dataStream() const { return *stream; }
        DrawTarget &currentTarget() const { return *target; }

        void ensureRecording();

        static void imageBarrier(VkCommandBuffer commands, VkImage image, VkImageAspectFlags aspect, u32 levels,
                                 VkImageLayout oldLayout,
                                 VkImageLayout newLayout);

        RenderState state;
        VertexLayouts layouts;
        std::unordered_set<class Texture *> textures;
        std::unordered_set<Framebuffer *> framebuffers;
        u64 serial = 0;
        std::unique_ptr<terrain::Terrain> terrain;

    private:
        static constexpr u32 CLEAR_COLOR = 1;
        static constexpr u32 CLEAR_DEPTH = 2;
        static constexpr u64 COMMAND_BUFFER_SIZE = 1ull << 20;

        static Renderer *instance;

        void beginRecording();

        void submit(const SubmitSync &sync);

        void finishSetup();

        void reserve(u64 bytes, u64 sectionBytes = 0);

        bool beginPass();

        void endPass();

        void clearAttachments(u32 attachments) const;

        void clearMasked();

        u64 writeIndices(u64 bytes) const;

        void resolveTextures();

        void bindEnvironment(u64 offset);

        void bindPipeline(VkPipeline pipeline);

        void bindMesh(const GeometryPool *pool);

        void bindIndices(VkBuffer buffer, u64 offset);

        void issue(u32 record, u32 firstVertex, VkBuffer indexBuffer, u64 indexOffset);

        void copyStaged(Texture *texture, u32 level, i32 x, i32 y, u32 width, u32 height, VkBuffer buffer, u64 offset);

        void copyFromSurface(const GpuImage *image, u32 level, i32 destinationX, i32 destinationY, i32 x, i32 y, u32 width,
                             u32 height) const;

        void uploadQuadIndices();

        void createFrameSet();

        void writeEnvironment(EnvironmentBlock *block, bool flipY);

        void writeRecord(DrawRecord *drawRecord, u32 flags, i32 texture0, i32 texture1);

        void execute(const u8 *cursor, const u8 *end);

        std::unique_ptr<Context> contextValue;
        std::unique_ptr<RenderPassType> offscreenDepthPass;
        std::unique_ptr<RenderPassType> offscreenColorPass;
        std::unique_ptr<RenderPassType> surfacePass;
        std::unique_ptr<Presenter> presenter;
        std::unique_ptr<SurfaceTarget> surface;
        std::vector<std::unique_ptr<FrameResources> > frames;
        std::unique_ptr<FrameStream> stream;
        std::unique_ptr<FrameStream> records;
        std::unique_ptr<FrameStream> sections;
        std::unique_ptr<GpuBuffer> quadIndices;
        std::unique_ptr<class Texture> fallback;
        u8 *commandBuffer = nullptr;

        FrameResources *frame = nullptr;
        VkCommandBuffer setup = VK_NULL_HANDLE;
        VkCommandBuffer main = VK_NULL_HANDLE;
        DrawTarget *target = nullptr;
        VkDescriptorPool framePool = VK_NULL_HANDLE;
        VkDescriptorSet frameSet = VK_NULL_HANDLE;
        u32 frameIndex = 0;
        bool recording = false;
        bool setupTransfers = false;
        std::vector<class Texture *> pendingTextures;
        std::vector<GpuImage *> pendingDepth;

        bool passActive = false;
        u32 pendingClear = 0;
        float clearColorValue[4] = {};
        float clearDepthValue = 1.0f;

        PrimitivePlan plan;
        VkPipeline boundPipeline = VK_NULL_HANDLE;
        i64 recordIndex = -1;
        u32 textureFlags = 0;
        u64 textureHandles[2] = {~0ull, ~0ull};
        u64 boundSets[2] = {};
        VkBuffer boundVertexBuffers[2] = {};
        u64 boundVertexOffsets[2] = {};
        VkBuffer boundIndexBuffer = VK_NULL_HANDLE;
        u64 boundIndexOffset = ~0ull;

        u32 batchCount = 0;
        GeometryPool *batchPool = nullptr;
        bool batchFlat = false;
        u32 batchIndexCounts[BATCH_SIZE];
        u32 batchVertexOffsets[BATCH_SIZE];
        u32 batchRecords[BATCH_SIZE];

        VkBuffer stagingBuffer = VK_NULL_HANDLE;
        u64 stagingOffset = 0;
    };
} // namespace at
