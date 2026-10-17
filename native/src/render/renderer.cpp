#include "render/renderer.h"

#include "core/error.h"
#include "core/log.h"
#include "render/geometry.h"
#include "render/pipeline_key.h"
#include "render/pipelines.h"
#include "render/targets.h"
#include "render/texture.h"
#include "terrain/terrain.h"
#include "vulkan/descriptors.h"
#include "vulkan/device.h"
#include "vulkan/frame.h"
#include "vulkan/instance.h"
#include "vulkan/memory.h"
#include "vulkan/render_pass.h"
#include "vulkan/dxgi_presenter.h"
#include "vulkan/swapchain.h"

#include <algorithm>

namespace at {
    Renderer *Renderer::instance = nullptr;
    static std::unique_ptr<Instance> vulkanInstance;
    static VkSurfaceKHR windowSurface = VK_NULL_HANDLE;
    static WindowSystem windowSystem;

    Context::Context() = default;

    Context::~Context() = default;

    void Renderer::initialize(const WindowSystem &system, bool validation, std::string cacheFile) {
        if (instance) fail("Renderer is already initialized");
        windowSystem = system;
        PFN_vkGetInstanceProcAddr getInstanceProcAddr = system.getInstanceProcAddr();
        if (!getInstanceProcAddr) fail("SDL has no Vulkan loader");
        u32 count = 0;
        const char *const*names = system.getInstanceExtensions(&count);
        if (!names) fail("SDL has no Vulkan support");
        std::vector<std::string> extensions(names, names + count);
        vulkanInstance = std::make_unique<Instance>(getInstanceProcAddr, extensions, validation);
        if (!system.createSurface(system.window, vulkanInstance->handle, nullptr, &windowSurface))
            fail(
                "Unable to create a Vulkan surface");
        new Renderer(windowSurface, system, std::move(cacheFile));
        instance->startThread();
    }

    void Renderer::shutdown() {
        if (instance) {
            instance->stopThread();
            delete instance;
            instance = nullptr;
        }
        if (vulkanInstance) {
            if (windowSurface) windowSystem.destroySurface(vulkanInstance->handle, windowSurface, nullptr);
            windowSurface = VK_NULL_HANDLE;
            vulkanInstance.reset();
        }
    }

    static std::unique_ptr<Presenter> createPresenter(const Device &device, VkSurfaceKHR surface,
                                                      const WindowSystem &system) {
#ifdef _WIN32
        if (device.interop && system.nativeWindow) {
            int width = 0, height = 0;
            system.windowSize(system.window, &width, &height);
            try {
                auto presenter = std::make_unique<DxgiPresenter>(device, system.nativeWindow,
                                                                 static_cast<u32>(std::max(width, 1)),
                                                                 static_cast<u32>(std::max(height, 1)));
                log::info("Presenting through a DXGI swapchain");
                return presenter;
            } catch (const std::exception &error) {
                log::warn("DXGI presentation is unavailable, using the Vulkan swapchain: %s", error.what());
            }
        }
#endif
        return std::make_unique<Swapchain>(device, surface);
    }

    Renderer::Renderer(VkSurfaceKHR surfaceHandle, const WindowSystem &system, std::string cacheFile) {
        instance = this;
        contextValue = std::make_unique<Context>();
        Context &context = *contextValue;
        context.device = std::make_unique<Device>(*vulkanInstance, surfaceHandle);
        Device &device = *context.device;
        context.allocator = std::make_unique<MemoryAllocator>(device);
        context.shaders = std::make_unique<ShaderLibrary>(device);
        context.pipelines = std::make_unique<Pipelines>(device, *context.shaders, std::move(cacheFile));
        context.samplers = std::make_unique<Samplers>(device);
        if (device.bindless) {
            context.textures = std::make_unique<BindlessTextureTable>(device, context.pipelines->textureSetLayout);
        } else {
            context.textures = std::make_unique<BoundTextureTable>(device, context.pipelines->textureSetLayout);
        }
        context.geometry = std::make_unique<GeometryArena>(device, *context.allocator);

        for (u32 i = 0; i < FRAMES; i++) frames.push_back(std::make_unique<FrameResources>(device));
        stream = std::make_unique<FrameStream>(device, *context.allocator, STREAM_SIZE, FRAMES,
                                               VK_BUFFER_USAGE_VERTEX_BUFFER_BIT | VK_BUFFER_USAGE_INDEX_BUFFER_BIT |
                                               VK_BUFFER_USAGE_INDIRECT_BUFFER_BIT
                                               |
                                               VK_BUFFER_USAGE_TRANSFER_SRC_BIT);
        records = std::make_unique<FrameStream>(device, *context.allocator, RECORDS_PER_FRAME * sizeof(DrawRecord),
                                                FRAMES,
                                                VK_BUFFER_USAGE_STORAGE_BUFFER_BIT);
        sections = std::make_unique<FrameStream>(device, *context.allocator, SECTIONS_PER_FRAME * sizeof(SectionDraw),
                                                 FRAMES,
                                                 VK_BUFFER_USAGE_STORAGE_BUFFER_BIT);
        environments = std::make_unique<FrameStream>(device, *context.allocator,
                                                     ENVIRONMENTS_PER_FRAME * sizeof(EnvironmentBlock), FRAMES,
                                                     VK_BUFFER_USAGE_STORAGE_BUFFER_BIT);
        offscreenDepthPass = std::make_unique<RenderPassType>(device, RenderPassType::Kind::Offscreen, 0,
                                                              VK_FORMAT_B8G8R8A8_UNORM,
                                                              device.depthFormat);
        offscreenColorPass = std::make_unique<RenderPassType>(device, RenderPassType::Kind::Offscreen, 1,
                                                              VK_FORMAT_B8G8R8A8_UNORM,
                                                              VK_FORMAT_UNDEFINED);
        presenter = createPresenter(device, surfaceHandle, system);
        surfacePass = std::make_unique<RenderPassType>(device, RenderPassType::Kind::Surface, 2, presenter->format,
                                                       device.depthFormat, presenter->layout);
        surface = std::make_unique<SurfaceTarget>(device, *context.allocator, system.window, system.windowSize,
                                                  *surfacePass, *presenter);
        target = surface.get();
        RenderPassType *passes[3] = {offscreenDepthPass.get(), offscreenColorPass.get(), surfacePass.get()};
        context.pipelines->prewarm(layouts, passes);
        createFrameSet();
        writing.data = static_cast<u8 *>(std::calloc(1, CHUNK_SIZE));
        writing.capacity = CHUNK_SIZE;
        streamCellValue = static_cast<u64>(reinterpret_cast<std::uintptr_t>(writing.data));
        quadIndices = std::make_unique<GpuBuffer>(device, *context.allocator, u64(MAX_QUADS) * 6 * 4 * 2,
                                                  VK_BUFFER_USAGE_INDEX_BUFFER_BIT | VK_BUFFER_USAGE_TRANSFER_DST_BIT,
                                                  VK_MEMORY_PROPERTY_DEVICE_LOCAL_BIT);
        uploadQuadIndices();
        fallback = std::make_unique<Texture>();
        allocateTexture(fallback.get(), 1, 1, 1);
        u32 white = 0xFFFFFFFFu;
        uploadTexture(fallback.get(), 0, 0, 0, 1, 1, reinterpret_cast<const u8 *>(&white), 1);
        terrain = std::make_unique<Terrain>(*this);
    }

    Renderer::~Renderer() {
        Context &context = *contextValue;
        context.pipelines->stop();
        context.device->waitIdle();
        try {
            context.pipelines->save(layouts);
        } catch (const std::exception &error) {
            log::warn("Unable to save the pipeline cache: %s", error.what());
        }
        for (auto &resources: frames) resources->await();
        terrain.reset();
        fallback.reset();
        std::vector<Framebuffer *> liveFramebuffers(framebuffers.begin(), framebuffers.end());
        for (Framebuffer *framebuffer: liveFramebuffers) delete framebuffer;
        framebuffers.clear();
        std::vector<Texture *> liveTextures;
        {
            std::lock_guard guard(texturesLock);
            liveTextures.assign(textures.begin(), textures.end());
        }
        for (Texture *texture: liveTextures) texture->detach();
        for (auto &resources: frames) resources->await();
        frames.clear();
        quadIndices.reset();
        stream.reset();
        records.reset();
        sections.reset();
        environments.reset();
        vkDestroyDescriptorPool(context.device->handle, framePool, nullptr);
        surface.reset();
        presenter.reset();
        offscreenDepthPass.reset();
        offscreenColorPass.reset();
        surfacePass.reset();
        context.geometry.reset();
        context.textures.reset();
        context.samplers.reset();
        context.pipelines.reset();
        context.shaders.reset();
        context.allocator.reset();
        context.device.reset();
        std::free(writing.data);
    }

    void Renderer::defer(std::function<void()> release) {
        FrameResources *owner = recording ? frame : frames[(frameIndex + FRAMES - 1) % FRAMES].get();
        owner->defer(std::move(release));
    }

    void Renderer::track(Texture *texture) {
        ensureRecording();
        if (texture->pendingSerial == serial) return;
        texture->pendingSerial = serial;
        pendingTextures.push_back(texture);
    }

    void Renderer::trackDepth(GpuImage *image) {
        ensureRecording();
        pendingDepth.push_back(image);
    }

    void Renderer::bindTarget(DrawTarget *next) {
        if (next == target) return;
        ensureRecording();
        endPass();
        if (next->pass.flipY() != target->pass.flipY()) state.dirty |= RenderState::ENVIRONMENT;
        target = next;
        state.dirty |= RenderState::VIEWPORT;
    }

    void Renderer::bindSurface() {
        bindTarget(surface.get());
    }

    Framebuffer *Renderer::createFramebuffer(u32 width, u32 height, bool depth) {
        Context &context = *contextValue;
        auto *framebuffer = new Framebuffer(*context.device, *context.allocator,
                                            depth ? *offscreenDepthPass : *offscreenColorPass, width, height);
        framebuffers.insert(framebuffer);
        return framebuffer;
    }

    void Renderer::releaseFramebuffer(Framebuffer *framebuffer) {
        if (target == framebuffer) {
            endPass();
            pendingClear = 0;
            bindTarget(surface.get());
        }
        framebuffer->texture.detach();
        framebuffers.erase(framebuffer);
        defer([framebuffer] { delete framebuffer; });
    }

    void Renderer::setVsync(bool enabled) const {
        surface->setVsync(enabled);
    }

    void Renderer::clear(i32 mask) {
        ensureRecording();
        flushBatch();
        u32 colorMask = state.colorMaskValue;
        u32 attachments = 0;
        bool masked = false;
        if ((mask & gl::COLOR_BUFFER_BIT) && colorMask != 0) {
            if (colorMask == 0xF) attachments |= CLEAR_COLOR;
            else masked = true;
        }
        if ((mask & gl::DEPTH_BUFFER_BIT) && state.depthMaskValue && target->pass.hasDepth())
            attachments |= CLEAR_DEPTH;
        if (attachments != 0) {
            std::memcpy(clearColorValue, state.clearColorValue, sizeof(clearColorValue));
            clearDepthValue = state.clearDepthValue;
            if (passActive) clearAttachments(attachments);
            else pendingClear |= attachments;
        }
        if (masked) clearMasked();
    }

    bool Renderer::drawBatched(const VertexLayout &layout, const u8 *source) {
        u32 vertices = plan.vertexCount;
        if (vertices == 0 || vertices > REGION_MAX_VERTICES) return false;
        ensureRecording();
        reserve(0);
        ImmediateRegion *region = nullptr;
        u32 grown = REGION_MIN_VERTICES;
        for (ImmediateRegion &candidate: regions) {
            if (candidate.layout != layout.id || candidate.serial != serial) continue;
            if (candidate.capacity - candidate.used >= vertices) {
                region = &candidate;
                break;
            }
            grown = std::max(grown, std::min(candidate.capacity * 2, REGION_MAX_VERTICES));
        }
        if (!region) {
            u32 capacity = std::max(grown, vertices);
            u64 positionBytes = layout.positionBytes(capacity);
            u64 attributeBytes = layout.attributeBytes(capacity);
            reserve(positionBytes + attributeBytes + 64);
            u32 victim = 0;
            for (u32 slot = 0; slot < REGION_SLOTS; slot++) {
                if (regions[slot].serial != serial) {
                    victim = slot;
                    break;
                }
                if (regionUses[slot] < regionUses[victim]) victim = slot;
            }
            region = &regions[victim];
            if (!capturing && batchSource == region) flushBatch();
            u64 positions = positionBytes > 0 ? stream->allocate(positionBytes, 16) : 0;
            u64 attributes = attributeBytes > 0 ? stream->allocate(attributeBytes, 16) : 0;
            if (positions == FrameStream::FAILED || attributes == FrameStream::FAILED) {
                *region = ImmediateRegion{};
                return false;
            }
            *region = ImmediateRegion{layout.id, serial, positions, attributes, capacity, 0};
        }
        regionUses[region - regions] = ++regionClock;
        if (!beginPass()) return true;
        u32 first = region->used;
        u8 *positionAddress = layout.split
                                  ? stream->address(region->positions) + static_cast<u64>(first) * VertexLayout::POSITION_SIZE
                                  : nullptr;
        u8 *attributeAddress = region->attributes || !layout.split
                                   ? stream->address(region->attributes) + static_cast<u64>(first) * layout.attributeStride
                                   : nullptr;
        layout.write(source, positionAddress, attributeAddress, vertices);
        region->used += vertices;

        bool flat = state.flatShading;
        if (capturing) {
            VkBuffer buffer = stream->handle();
            if (layout.split) {
                capture(layout, plan.topology, buffer, region->positions,
                        layout.attributeStride > 0 ? buffer : VK_NULL_HANDLE, region->attributes, first, vertices,
                        flat);
            } else {
                capture(layout, plan.topology, buffer, region->attributes, VK_NULL_HANDLE, 0, first, vertices, flat);
            }
            return true;
        }
        bindState(layout, plan.topology);
        u32 drawRecord = record();
        if (batchSource != region || batchFlat != flat) {
            flushBatch();
            batchSource = region;
            batchFlat = flat;
        }
        VkBuffer buffer = stream->handle();
        if (layout.split) {
            setVertexBuffers(buffer, region->positions, layout.attributeStride > 0 ? buffer : VK_NULL_HANDLE,
                             region->attributes);
        } else {
            setVertexBuffers(buffer, region->attributes, VK_NULL_HANDLE, 0);
        }
        u32 quads = vertices / 4;
        for (u32 quad = 0; quad < quads;) {
            u32 run = std::min(MAX_QUADS, quads - quad);
            if (batchCount == BATCH_SIZE) flushBatch();
            batchIndexCounts[batchCount] = run * 6;
            batchVertexOffsets[batchCount] = first + quad * 4;
            batchRecords[batchCount] = drawRecord;
            batchCount++;
            quad += run;
        }
        return true;
    }

    void Renderer::draw(u32 layoutId, i32 mode, const u8 *source, u32 count) {
        const VertexLayout &layout = layouts[layoutId];
        if (!plan.plan(mode, count, state.flatShading)) return;
        if (plan.indexing == PrimitivePlan::QUADS && drawBatched(layout, source)) return;
        u32 vertices = plan.vertexCount;
        u64 positionBytes = layout.positionBytes(vertices);
        u64 attributeBytes = layout.attributeBytes(vertices);
        u64 indexBytes = plan.indexing == PrimitivePlan::GENERATED ? u64(plan.indexCount) * 4 : 0;
        if (!prepare(positionBytes + attributeBytes + indexBytes + 64)) return;
        u64 positions = positionBytes > 0 ? stream->allocate(positionBytes, 16) : 0;
        u64 attributes = attributeBytes > 0 ? stream->allocate(attributeBytes, 16) : 0;
        layout.write(source, stream->address(positions), stream->address(attributes), vertices);
        u64 indices = writeIndices(indexBytes);
        bindState(layout, plan.topology);
        u32 drawRecord = record();
        VkBuffer buffer = stream->handle();
        if (layout.split) {
            setVertexBuffers(buffer, positions, attributeBytes > 0 ? buffer : VK_NULL_HANDLE, attributes);
        } else {
            setVertexBuffers(buffer, attributes, VK_NULL_HANDLE, 0);
        }
        issue(drawRecord, 0, buffer, indices);
    }

    void Renderer::drawMesh(const Mesh *mesh, i32 mode) {
        bool flat = state.flatShading;
        if (!plan.plan(mode, mesh->count, flat)) return;
        u64 indexBytes = plan.indexing == PrimitivePlan::GENERATED ? u64(plan.indexCount) * 4 : 0;
        if (plan.indexing != PrimitivePlan::QUADS) {
            if (!prepare(indexBytes)) return;
            u64 indices = writeIndices(indexBytes);
            bindState(*mesh->layout, plan.topology);
            u32 drawRecord = record();
            bindMesh(mesh->pool);
            issue(drawRecord, mesh->firstVertex, stream->handle(), indices);
            return;
        }
        ensureRecording();
        reserve(0);
        if (!beginPass()) return;
        if (capturing) {
            const GeometryPool *pool = mesh->pool;
            if (pool->positions) {
                capture(*mesh->layout, plan.topology, pool->positions->handle, 0,
                        pool->attributes ? pool->attributes->handle : VK_NULL_HANDLE, 0, mesh->firstVertex,
                        plan.vertexCount, flat);
            } else {
                capture(*mesh->layout, plan.topology, pool->attributes->handle, 0, VK_NULL_HANDLE, 0,
                        mesh->firstVertex, plan.vertexCount, flat);
            }
            return;
        }
        bindState(*mesh->layout, plan.topology);
        u32 drawRecord = record();
        if (batchSource != mesh->pool || batchFlat != flat) {
            flushBatch();
            batchSource = mesh->pool;
            batchFlat = flat;
        }
        bindMesh(mesh->pool);
        u32 quads = plan.vertexCount / 4;
        for (u32 quad = 0; quad < quads;) {
            u32 run = std::min(MAX_QUADS, quads - quad);
            if (batchCount == BATCH_SIZE) flushBatch();
            batchIndexCounts[batchCount] = run * 6;
            batchVertexOffsets[batchCount] = mesh->firstVertex + quad * 4;
            batchRecords[batchCount] = drawRecord;
            batchCount++;
            quad += run;
        }
    }

    Mesh *Renderer::uploadMesh(u32 layoutId, const u8 *source, u32 count) {
        const VertexLayout &layout = layouts[layoutId];
        Mesh *mesh = contextValue->geometry->allocate(layout, count);
        u64 positionBytes = layout.positionBytes(count);
        u64 attributeBytes = layout.attributeBytes(count);
        VkBuffer buffer;
        u64 offset;
        u8 *address = stage(positionBytes + attributeBytes, buffer, offset);
        layout.write(source, address, address + positionBytes, count);
        GeometryPool *pool = mesh->pool;
        if (pool->positions && positionBytes > 0) {
            VkBufferCopy region{.srcOffset = offset, .dstOffset = static_cast<u64>(mesh->firstVertex) * VertexLayout::POSITION_SIZE, .size = positionBytes};
            vkCmdCopyBuffer(setup, buffer, pool->positions->handle, 1, &region);
        }
        if (pool->attributes && attributeBytes > 0) {
            VkBufferCopy region{offset + positionBytes, u64(mesh->firstVertex) * pool->attributeStride, attributeBytes};
            vkCmdCopyBuffer(setup, buffer, pool->attributes->handle, 1, &region);
        }
        setupTransfers = true;
        return mesh;
    }

    void Renderer::releaseMesh(Mesh *mesh) {
        GeometryArena *geometry = contextValue->geometry.get();
        defer([geometry, mesh] { GeometryArena::release(mesh); });
    }

    void Renderer::allocateTexture(Texture *texture, u32 levels, u32 width, u32 height) {
        Context &context = *contextValue;
        auto *image = new GpuImage(*context.device, *context.allocator, std::max(width, 1u), std::max(height, 1u),
                                   std::max(levels, 1u),
                                   VK_FORMAT_B8G8R8A8_UNORM, VK_IMAGE_ASPECT_COLOR_BIT,
                                   VK_IMAGE_USAGE_SAMPLED_BIT | VK_IMAGE_USAGE_TRANSFER_DST_BIT |
                                   VK_IMAGE_USAGE_TRANSFER_SRC_BIT);
        texture->attach(image, false);
        state.texturesChanged();
    }

    void Renderer::uploadTexture(Texture *texture, u32 level, i32 x, i32 y, u32 width, u32 height, const u8 *source,
                                 u32) {
        if (!texture->image || width == 0 || height == 0) return;
        u64 bytes = u64(width) * height * 4;
        VkBuffer buffer;
        u64 offset;
        u8 *address = stage(bytes, buffer, offset);
        std::memcpy(address, source, bytes);
        copyStaged(texture, level, x, y, width, height, buffer, offset);
    }

    void Renderer::copyStaged(Texture *texture, u32 level, i32 x, i32 y, u32 width, u32 height, VkBuffer buffer,
                              u64 offset) {
        GpuImage *image = texture->image;
        if (!image) return;
        bool inlineCopy = texture->usedSerial == serial;
        if (inlineCopy) endPass();
        VkCommandBuffer commands = inlineCopy ? main : setup;
        if (VkImageLayout oldLayout = inlineCopy ? VK_IMAGE_LAYOUT_SHADER_READ_ONLY_OPTIMAL : texture->layout; oldLayout != VK_IMAGE_LAYOUT_TRANSFER_DST_OPTIMAL) {
            imageBarrier(commands, image->handle, VK_IMAGE_ASPECT_COLOR_BIT, image->levels, oldLayout,
                         VK_IMAGE_LAYOUT_TRANSFER_DST_OPTIMAL);
        }
        VkBufferImageCopy region{};
        region.bufferOffset = offset;
        region.imageSubresource = {VK_IMAGE_ASPECT_COLOR_BIT, level, 0, 1};
        region.imageOffset = {x, y, 0};
        region.imageExtent = {width, height, 1};
        vkCmdCopyBufferToImage(commands, buffer, image->handle, VK_IMAGE_LAYOUT_TRANSFER_DST_OPTIMAL, 1, &region);
        if (inlineCopy) {
            imageBarrier(main, image->handle, VK_IMAGE_ASPECT_COLOR_BIT, image->levels,
                         VK_IMAGE_LAYOUT_TRANSFER_DST_OPTIMAL,
                         VK_IMAGE_LAYOUT_SHADER_READ_ONLY_OPTIMAL);
        } else {
            texture->layout = VK_IMAGE_LAYOUT_TRANSFER_DST_OPTIMAL;
            track(texture);
        }
    }

    void Renderer::copyFromTarget(Texture *texture, u32 level, i32 destinationX, i32 destinationY, i32 x, i32 y,
                                  u32 width, u32 height) {
        GpuImage *image = texture->image;
        if (!image) return;
        ensureRecording();
        endPass();
        if (target == surface.get()) {
            copyFromSurface(image, level, destinationX, destinationY, x, y, width, height);
            texture->usedSerial = serial;
            return;
        }
        auto *source = static_cast<Framebuffer *>(target);
        imageBarrier(main, source->color->handle, VK_IMAGE_ASPECT_COLOR_BIT, 1,
                     VK_IMAGE_LAYOUT_SHADER_READ_ONLY_OPTIMAL,
                     VK_IMAGE_LAYOUT_TRANSFER_SRC_OPTIMAL);
        imageBarrier(main, image->handle, VK_IMAGE_ASPECT_COLOR_BIT, image->levels,
                     VK_IMAGE_LAYOUT_SHADER_READ_ONLY_OPTIMAL,
                     VK_IMAGE_LAYOUT_TRANSFER_DST_OPTIMAL);
        VkImageCopy region{};
        region.srcSubresource = {.aspectMask = VK_IMAGE_ASPECT_COLOR_BIT, .mipLevel = 0, .baseArrayLayer = 0, .layerCount = 1};
        region.srcOffset = {.x = x, .y = y, .z = 0};
        region.dstSubresource = {.aspectMask = VK_IMAGE_ASPECT_COLOR_BIT, .mipLevel = level, .baseArrayLayer = 0, .layerCount = 1};
        region.dstOffset = {.x = destinationX, .y = destinationY, .z = 0};
        region.extent = {.width = width, .height = height, .depth = 1};
        vkCmdCopyImage(main, source->color->handle, VK_IMAGE_LAYOUT_TRANSFER_SRC_OPTIMAL, image->handle,
                       VK_IMAGE_LAYOUT_TRANSFER_DST_OPTIMAL,
                       1, &region);
        imageBarrier(main, source->color->handle, VK_IMAGE_ASPECT_COLOR_BIT, 1, VK_IMAGE_LAYOUT_TRANSFER_SRC_OPTIMAL,
                     VK_IMAGE_LAYOUT_SHADER_READ_ONLY_OPTIMAL);
        imageBarrier(main, image->handle, VK_IMAGE_ASPECT_COLOR_BIT, image->levels,
                     VK_IMAGE_LAYOUT_TRANSFER_DST_OPTIMAL,
                     VK_IMAGE_LAYOUT_SHADER_READ_ONLY_OPTIMAL);
        texture->usedSerial = serial;
    }

    void Renderer::copyFromSurface(const GpuImage *image, u32 level, i32 destinationX, i32 destinationY, i32 x, i32 y,
                                   u32 width, u32 height) const {
        if (surface->imageIndex < 0) return;
        VkImage source = surface->image();
        i32 top = static_cast<i32>(surface->height()) - y;
        imageBarrier(main, source, VK_IMAGE_ASPECT_COLOR_BIT, 1, surface->layout(),
                     VK_IMAGE_LAYOUT_TRANSFER_SRC_OPTIMAL);
        imageBarrier(main, image->handle, VK_IMAGE_ASPECT_COLOR_BIT, image->levels,
                     VK_IMAGE_LAYOUT_SHADER_READ_ONLY_OPTIMAL,
                     VK_IMAGE_LAYOUT_TRANSFER_DST_OPTIMAL);
        VkImageBlit region{};
        region.srcSubresource = {.aspectMask = VK_IMAGE_ASPECT_COLOR_BIT, .mipLevel = 0, .baseArrayLayer = 0, .layerCount = 1};
        region.srcOffsets[0] = {.x = x, .y = top, .z = 0};
        region.srcOffsets[1] = {.x = x + static_cast<i32>(width), .y = top - static_cast<i32>(height), .z = 1};
        region.dstSubresource = {.aspectMask = VK_IMAGE_ASPECT_COLOR_BIT, .mipLevel = level, .baseArrayLayer = 0, .layerCount = 1};
        region.dstOffsets[0] = {.x = destinationX, .y = destinationY, .z = 0};
        region.dstOffsets[1] = {.x = destinationX + static_cast<i32>(width), .y = destinationY + static_cast<i32>(height), .z = 1};
        vkCmdBlitImage(main, source, VK_IMAGE_LAYOUT_TRANSFER_SRC_OPTIMAL, image->handle,
                       VK_IMAGE_LAYOUT_TRANSFER_DST_OPTIMAL, 1, &region,
                       VK_FILTER_NEAREST);
        imageBarrier(main, source, VK_IMAGE_ASPECT_COLOR_BIT, 1, VK_IMAGE_LAYOUT_TRANSFER_SRC_OPTIMAL,
                     surface->layout());
        imageBarrier(main, image->handle, VK_IMAGE_ASPECT_COLOR_BIT, image->levels,
                     VK_IMAGE_LAYOUT_TRANSFER_DST_OPTIMAL,
                     VK_IMAGE_LAYOUT_SHADER_READ_ONLY_OPTIMAL);
    }

    void Renderer::read(const Texture *texture, u8 *destination) {
        GpuImage *image = texture->image;
        if (!image) return;
        Context &context = *contextValue;
        u64 bytes = static_cast<u64>(image->width) * image->height * 4;
        GpuBuffer readback(*context.device, *context.allocator, bytes, VK_BUFFER_USAGE_TRANSFER_DST_BIT,
                           VK_MEMORY_PROPERTY_HOST_VISIBLE_BIT | VK_MEMORY_PROPERTY_HOST_COHERENT_BIT,
                           VK_MEMORY_PROPERTY_HOST_CACHED_BIT);
        ensureRecording();
        endPass();
        imageBarrier(main, image->handle, VK_IMAGE_ASPECT_COLOR_BIT, image->levels,
                     VK_IMAGE_LAYOUT_SHADER_READ_ONLY_OPTIMAL,
                     VK_IMAGE_LAYOUT_TRANSFER_SRC_OPTIMAL);
        VkBufferImageCopy region{};
        region.imageSubresource = {.aspectMask = VK_IMAGE_ASPECT_COLOR_BIT, .mipLevel = 0, .baseArrayLayer = 0, .layerCount = 1};
        region.imageExtent = {.width = image->width, .height = image->height, .depth = 1};
        vkCmdCopyImageToBuffer(main, image->handle, VK_IMAGE_LAYOUT_TRANSFER_SRC_OPTIMAL, readback.handle, 1, &region);
        imageBarrier(main, image->handle, VK_IMAGE_ASPECT_COLOR_BIT, image->levels,
                     VK_IMAGE_LAYOUT_TRANSFER_SRC_OPTIMAL,
                     VK_IMAGE_LAYOUT_SHADER_READ_ONLY_OPTIMAL);
        flush();
        std::memcpy(destination, readback.mapped(), bytes);
    }

    bool Renderer::blitToSurface(Framebuffer *source, u32 width, u32 height) {
        if (target != surface.get() || !source || !source->color || !surface->blitTarget()) return false;
        ensureRecording();
        endPass();
        if (!surface->acquireImage()) return true;
        width = surface->width();
        height = surface->height();
        VkImage destination = surface->image();
        VkImage image = source->color->handle;
        imageBarrier(main, image, VK_IMAGE_ASPECT_COLOR_BIT, 1, VK_IMAGE_LAYOUT_SHADER_READ_ONLY_OPTIMAL,
                     VK_IMAGE_LAYOUT_TRANSFER_SRC_OPTIMAL);
        imageBarrier(main, destination, VK_IMAGE_ASPECT_COLOR_BIT, 1, VK_IMAGE_LAYOUT_UNDEFINED,
                     VK_IMAGE_LAYOUT_TRANSFER_DST_OPTIMAL);
        auto sourceWidth = static_cast<i32>(source->width()), sourceHeight = static_cast<i32>(source->height());
        VkImageBlit region{};
        region.srcSubresource = {.aspectMask = VK_IMAGE_ASPECT_COLOR_BIT, .mipLevel = 0, .baseArrayLayer = 0, .layerCount = 1};
        region.srcOffsets[0] = {.x = 0, .y = 0, .z = 0};
        region.srcOffsets[1] = {.x = sourceWidth, .y = sourceHeight, .z = 1};
        region.dstSubresource = {.aspectMask = VK_IMAGE_ASPECT_COLOR_BIT, .mipLevel = 0, .baseArrayLayer = 0, .layerCount = 1};
        region.dstOffsets[0] = {.x = 0, .y = static_cast<i32>(height), .z = 0};
        region.dstOffsets[1] = {.x = static_cast<i32>(width), .y = 0, .z = 1};
        bool scaled = source->width() != width || source->height() != height;
        vkCmdBlitImage(main, image, VK_IMAGE_LAYOUT_TRANSFER_SRC_OPTIMAL, destination,
                       VK_IMAGE_LAYOUT_TRANSFER_DST_OPTIMAL, 1, &region, scaled ? VK_FILTER_LINEAR : VK_FILTER_NEAREST);
        imageBarrier(main, image, VK_IMAGE_ASPECT_COLOR_BIT, 1, VK_IMAGE_LAYOUT_TRANSFER_SRC_OPTIMAL,
                     VK_IMAGE_LAYOUT_SHADER_READ_ONLY_OPTIMAL);
        imageBarrier(main, destination, VK_IMAGE_ASPECT_COLOR_BIT, 1, VK_IMAGE_LAYOUT_TRANSFER_DST_OPTIMAL,
                     surface->layout());
        surface->markWritten();
        return true;
    }

    void Renderer::present() {
        ensureRecording();
        endPass();
        bool acquired = surface->imageIndex >= 0;
        SubmitSync sync;
        surface->sync(sync, main, true);
        submit(sync);
        if (acquired) surface->present();
        surface->endFrame();
        frameIndex = (frameIndex + 1) % FRAMES;
    }

    void Renderer::flush() {
        if (!recording) return;
        SubmitSync sync;
        surface->sync(sync, main, false);
        submit(sync);
        frame->await();
        beginRecording();
    }

    void Renderer::ensureRecording() {
        if (!recording) beginRecording();
    }

    VkCommandBuffer Renderer::setupCommands() {
        ensureRecording();
        return setup;
    }

    VkCommandBuffer Renderer::transferCommands() {
        ensureRecording();
        if (!frame->transfer) {
            markSetupTransfers();
            return setup;
        }
        if (!frame->transferRecording) {
            VkCommandBufferBeginInfo info{VK_STRUCTURE_TYPE_COMMAND_BUFFER_BEGIN_INFO};
            info.flags = VK_COMMAND_BUFFER_USAGE_ONE_TIME_SUBMIT_BIT;
            check(vkBeginCommandBuffer(frame->transfer, &info), "vkBeginCommandBuffer");
            frame->transferRecording = true;
        }
        return frame->transfer;
    }

    void Renderer::beginRecording() {
        frame = frames[frameIndex].get();
        frame->await();
        frame->reset();
        stream->begin(frameIndex);
        records->begin(frameIndex);
        sections->begin(frameIndex);
        environments->begin(frameIndex);
        setup = frame->setup;
        main = frame->main;
        surface->acquireSemaphore = frame->imageAvailable;
        VkCommandBufferBeginInfo info{VK_STRUCTURE_TYPE_COMMAND_BUFFER_BEGIN_INFO};
        info.flags = VK_COMMAND_BUFFER_USAGE_ONE_TIME_SUBMIT_BIT;
        check(vkBeginCommandBuffer(setup, &info), "vkBeginCommandBuffer");
        check(vkBeginCommandBuffer(main, &info), "vkBeginCommandBuffer");
        if (frame->timestamps) {
            vkCmdResetQueryPool(setup, frame->timestamps, 0, 2);
            vkCmdWriteTimestamp(setup, VK_PIPELINE_STAGE_TOP_OF_PIPE_BIT, frame->timestamps, 0);
        }
        recording = true;
        serial++;
        boundPipeline = VK_NULL_HANDLE;
        recordIndex = -1;
        boundSets[0] = boundSets[1] = 0;
        boundVertexBuffers[0] = boundVertexBuffers[1] = VK_NULL_HANDLE;
        boundIndexBuffer = VK_NULL_HANDLE;
        boundIndexOffset = ~0ull;
        batchCount = 0;
        batchSource = nullptr;
        state.dirty = RenderState::ALL;
        Context &context = *contextValue;
        vkCmdBindDescriptorSets(main, VK_PIPELINE_BIND_POINT_GRAPHICS, context.pipelines->layout, 0, 1, &frameSet, 0,
                                nullptr);
        if (context.textures->bindless()) {
            VkDescriptorSet set = static_cast<BindlessTextureTable *>(context.textures.get())->set;
            vkCmdBindDescriptorSets(main, VK_PIPELINE_BIND_POINT_GRAPHICS, context.pipelines->layout, 1, 1, &set, 0,
                                    nullptr);
        }
    }

    void Renderer::submit(const SubmitSync &submitted) {
        endPass();
        finishSetup();
        SubmitSync sync = submitted;
        if (frame->transferRecording) {
            check(vkEndCommandBuffer(frame->transfer), "vkEndCommandBuffer");
            VkSubmitInfo transferInfo{VK_STRUCTURE_TYPE_SUBMIT_INFO};
            transferInfo.commandBufferCount = 1;
            transferInfo.pCommandBuffers = &frame->transfer;
            transferInfo.signalSemaphoreCount = 1;
            transferInfo.pSignalSemaphores = &frame->transferDone;
            check(vkQueueSubmit(contextValue->device->transferQueue, 1, &transferInfo, VK_NULL_HANDLE),
                  "vkQueueSubmit");
            frame->transferRecording = false;
            sync.wait(frame->transferDone, VK_PIPELINE_STAGE_VERTEX_INPUT_BIT);
        }
        if (frame->timestamps) {
            vkCmdWriteTimestamp(main, VK_PIPELINE_STAGE_BOTTOM_OF_PIPE_BIT, frame->timestamps, 1);
            frame->timed = true;
        }
        check(vkEndCommandBuffer(setup), "vkEndCommandBuffer");
        check(vkEndCommandBuffer(main), "vkEndCommandBuffer");
        VkCommandBuffer buffers[2] = {setup, main};
        VkSubmitInfo info{VK_STRUCTURE_TYPE_SUBMIT_INFO};
        info.commandBufferCount = 2;
        info.pCommandBuffers = buffers;
        info.waitSemaphoreCount = sync.waitCount;
        info.pWaitSemaphores = sync.waits;
        info.pWaitDstStageMask = sync.waitStages;
        info.signalSemaphoreCount = sync.signalCount;
        info.pSignalSemaphores = sync.signals;
        VkTimelineSemaphoreSubmitInfoKHR timeline{.sType = VK_STRUCTURE_TYPE_TIMELINE_SEMAPHORE_SUBMIT_INFO_KHR};
        if (sync.timeline) {
            timeline.waitSemaphoreValueCount = sync.waitCount;
            timeline.pWaitSemaphoreValues = sync.waitValues;
            timeline.signalSemaphoreValueCount = sync.signalCount;
            timeline.pSignalSemaphoreValues = sync.signalValues;
            info.pNext = &timeline;
        }
        check(vkQueueSubmit(contextValue->device->queue, 1, &info, frame->fence), "vkQueueSubmit");
        recording = false;
    }

    void Renderer::finishSetup() {
        for (Texture *texture: pendingTextures) {
            GpuImage *image = texture->image;
            if (!image) continue;
            imageBarrier(setup, image->handle, VK_IMAGE_ASPECT_COLOR_BIT, image->levels, texture->layout,
                         VK_IMAGE_LAYOUT_SHADER_READ_ONLY_OPTIMAL);
            texture->layout = VK_IMAGE_LAYOUT_SHADER_READ_ONLY_OPTIMAL;
        }
        pendingTextures.clear();
        for (GpuImage *image: pendingDepth) {
            imageBarrier(setup, image->handle, image->aspect, 1, VK_IMAGE_LAYOUT_UNDEFINED,
                         VK_IMAGE_LAYOUT_DEPTH_STENCIL_ATTACHMENT_OPTIMAL);
        }
        pendingDepth.clear();
        if (setupTransfers) {
            VkMemoryBarrier barrier{.sType = VK_STRUCTURE_TYPE_MEMORY_BARRIER};
            barrier.srcAccessMask = VK_ACCESS_TRANSFER_WRITE_BIT;
            barrier.dstAccessMask = VK_ACCESS_VERTEX_ATTRIBUTE_READ_BIT | VK_ACCESS_INDEX_READ_BIT;
            vkCmdPipelineBarrier(setup, VK_PIPELINE_STAGE_TRANSFER_BIT, VK_PIPELINE_STAGE_VERTEX_INPUT_BIT, 0, 1,
                                 &barrier, 0, nullptr, 0, nullptr);
            setupTransfers = false;
        }
    }

    void Renderer::reserve(u64 bytes, u64 sectionBytes) {
        ensureRecording();
        u64 room = bytes + 64ull * INDIRECT_STRIDE * 4 + 64;
        if (stream->remaining(16) < room || records->remaining(sizeof(DrawRecord)) < sizeof(DrawRecord) * 2 ||
            sections->remaining(sizeof(SectionDraw)) < sectionBytes ||
            environments->remaining(sizeof(EnvironmentBlock)) < sizeof(EnvironmentBlock) * 2) {
            flush();
        }
    }

    bool Renderer::prepare(u64 bytes, u64 sectionBytes) {
        reserve(bytes, sectionBytes);
        flushBatch();
        return beginPass();
    }

    u8 *Renderer::stage(u64 bytes, VkBuffer &buffer, u64 &offset) {
        ensureRecording();
        if (bytes <= STREAM_SIZE / 2) {
            if (stream->remaining(16) < bytes) flush();
            offset = stream->allocate(bytes, 16);
            buffer = stream->handle();
            stagingBuffer = buffer;
            stagingOffset = offset;
            return stream->address(offset);
        }
        Context &context = *contextValue;
        auto *temporary = new GpuBuffer(*context.device, *context.allocator, bytes, VK_BUFFER_USAGE_TRANSFER_SRC_BIT,
                                        VK_MEMORY_PROPERTY_HOST_VISIBLE_BIT | VK_MEMORY_PROPERTY_HOST_COHERENT_BIT);
        defer([temporary] { delete temporary; });
        buffer = temporary->handle;
        offset = 0;
        stagingBuffer = buffer;
        stagingOffset = 0;
        return temporary->mapped();
    }

    bool Renderer::beginPass() {
        if (passActive) return true;
        VkFramebuffer framebuffer = target->framebuffer();
        if (!framebuffer) {
            pendingClear = 0;
            return false;
        }
        VkAttachmentLoadOp colorLoad = (pendingClear & CLEAR_COLOR) ? VK_ATTACHMENT_LOAD_OP_CLEAR : target->loadOp();
        VkAttachmentLoadOp depthLoad = (pendingClear & CLEAR_DEPTH) ? VK_ATTACHMENT_LOAD_OP_CLEAR : target->depthLoadOp();
        VkClearValue clearValues[2]{};
        std::memcpy(clearValues[0].color.float32, clearColorValue, sizeof(clearColorValue));
        clearValues[1].depthStencil = {clearDepthValue, 0};
        VkRenderPassBeginInfo info{.sType = VK_STRUCTURE_TYPE_RENDER_PASS_BEGIN_INFO};
        info.renderPass = target->pass.handle(colorLoad, depthLoad);
        info.framebuffer = framebuffer;
        info.renderArea.extent = {.width = target->width(), .height = target->height()};
        info.clearValueCount = 2;
        info.pClearValues = clearValues;
        vkCmdBeginRenderPass(main, &info, VK_SUBPASS_CONTENTS_INLINE);
        target->passStarted();
        pendingClear = 0;
        passActive = true;
        VkRect2D scissor{.offset = {.x = 0, .y = 0}, .extent = {.width = target->width(), .height = target->height()}};
        vkCmdSetScissor(main, 0, 1, &scissor);
        state.dirty |= RenderState::VIEWPORT;
        return true;
    }

    void Renderer::endPass() {
        if (!passActive && pendingClear != 0) beginPass();
        if (!passActive) return;
        flushBatch();
        vkCmdEndRenderPass(main);
        passActive = false;
    }

    void Renderer::clearAttachments(u32 attachments) const {
        VkClearAttachment clears[2]{};
        u32 count = 0;
        if (attachments & CLEAR_COLOR) {
            clears[count].aspectMask = VK_IMAGE_ASPECT_COLOR_BIT;
            clears[count].colorAttachment = 0;
            std::memcpy(clears[count].clearValue.color.float32, clearColorValue, sizeof(clearColorValue));
            count++;
        }
        if (attachments & CLEAR_DEPTH) {
            clears[count].aspectMask = VK_IMAGE_ASPECT_DEPTH_BIT;
            clears[count].clearValue.depthStencil = {.depth = clearDepthValue, .stencil = 0};
            count++;
        }
        VkClearRect rect{
            .rect = {.offset = {.x = 0, .y = 0}, .extent = {.width = target->width(), .height = target->height()}},
            .baseArrayLayer = 0, .layerCount = 1
        };
        vkCmdClearAttachments(main, count, clears, 1, &rect);
    }

    void Renderer::clearMasked() {
        const VertexLayout &layout = layouts.position();
        if (!prepare(48)) return;
        u64 positions = stream->allocate(48, 16);
        constexpr float corners[12] = {-1, -1, 0, 1, -1, 0, -1, 1, 0, 1, 1, 0};
        std::memcpy(stream->address(positions), corners, sizeof(corners));
        EnvironmentBlock *block;
        i32 environment = allocateEnvironment(block);
        std::memset(block, 0, sizeof(EnvironmentBlock));
        constexpr float identity[16] = {1, 0, 0, 0, 0, 1, 0, 0, 0, 0, 0.5f, 0, 0, 0, 0.5f, 1};
        std::memcpy(block->projection, identity, sizeof(identity));
        u64 recordOffset = records->allocate(sizeof(DrawRecord), sizeof(DrawRecord));
        auto *drawRecord = reinterpret_cast<DrawRecord *>(records->address(recordOffset));
        std::memset(drawRecord, 0, sizeof(DrawRecord));
        mat4::identity(drawRecord->modelView);
        std::memcpy(drawRecord->color, clearColorValue, sizeof(clearColorValue));
        drawRecord->textures[3] = environment;
        u64 pipelineKey = key::draw(
            (static_cast<u64>(VK_COMPARE_OP_ALWAYS) << key::COMPARE_SHIFT) | (
                static_cast<u64>(state.colorMaskValue) << key::COLOR_MASK_SHIFT),
            VK_PRIMITIVE_TOPOLOGY_TRIANGLE_STRIP, target->pass.id, layout.id);
        resolveTextures();
        bindPipeline(contextValue->pipelines->get(pipelineKey, layout, target->pass));
        VkViewport viewport{0, 0, static_cast<float>(target->width()), static_cast<float>(target->height()), 0, 1};
        vkCmdSetViewport(main, 0, 1, &viewport);
        setVertexBuffers(stream->handle(), positions, VK_NULL_HANDLE, 0);
        vkCmdDraw(main, 4, 1, 0, static_cast<u32>(recordOffset / sizeof(DrawRecord)));
        state.dirty |= RenderState::VIEWPORT | RenderState::LINE_WIDTH |
                RenderState::DEPTH_BIAS;
    }

    u64 Renderer::writeIndices(u64 bytes) const {
        if (bytes == 0) return 0;
        u64 offset = stream->allocate(bytes, 4);
        plan.generate(reinterpret_cast<u32 *>(stream->address(offset)));
        return offset;
    }

    void Renderer::bindState(const VertexLayout &layout, u32 topology) {
        Context &context = *contextValue;
        if (state.dirty & RenderState::TEXTURES) resolveTextures();
        if (state.dirty & RenderState::ENVIRONMENT) {
            EnvironmentBlock *block;
            currentEnvironment = allocateEnvironment(block);
            writeEnvironment(block, target->pass.flipY());
            recordIndex = -1;
            state.dirty &= ~RenderState::ENVIRONMENT;
        }
        u64 pipelineKey = key::draw(state.pipelineState(), topology, target->pass.id, layout.id);
        bindPipeline(context.pipelines->get(pipelineKey, layout, target->pass));
        if (state.dirty & RenderState::VIEWPORT) {
            flushBatch();
            i32 width = std::max(state.viewportWidth, 1);
            i32 height = std::max(state.viewportHeight, 1);
            i32 y = target->pass.flipY()
                        ? static_cast<i32>(target->height()) - (state.viewportY + height)
                        : state.viewportY;
            VkViewport viewport{
                static_cast<float>(state.viewportX), static_cast<float>(y), static_cast<float>(width),
                static_cast<float>(height), 0.0f, 1.0f
            };
            vkCmdSetViewport(main, 0, 1, &viewport);
        }
        if (state.dirty & RenderState::LINE_WIDTH) {
            flushBatch();
            Device &device = *context.device;
            vkCmdSetLineWidth(main, device.wideLines ? clamp(state.lineWidthValue, 1.0f, device.lineWidthMax) : 1.0f);
        }
        if (state.dirty & RenderState::DEPTH_BIAS) {
            flushBatch();
            vkCmdSetDepthBias(main, state.offsetUnits, 0.0f, state.offsetFactor);
        }
        state.dirty &= ~(RenderState::VIEWPORT | RenderState::LINE_WIDTH | RenderState::DEPTH_BIAS |
                         RenderState::PIPELINE);
    }

    void Renderer::resolveTextures() {
        Context &context = *contextValue;
        u32 flags = 0;
        for (int unit = 0; unit < 2; unit++) {
            if (Texture *texture = state.boundTexture(unit);
                texture && state.textureEnabled(unit) && texture->complete()) {
                texture->usedSerial = serial;
                flags |= (1u << unit) | (texture->clampFlags() << (2 + unit * 2));
                textureHandles[unit] = texture->handle();
            } else {
                textureHandles[unit] = fallback->handle();
            }
        }
        textureFlags = flags;
        recordIndex = -1;
        state.dirty &= ~RenderState::TEXTURES;
        if (!context.textures->bindless() && (boundSets[0] != textureHandles[0] || boundSets[1] != textureHandles[1])) {
            flushBatch();
            boundSets[0] = textureHandles[0];
            boundSets[1] = textureHandles[1];
            VkDescriptorSet sets[2] = {
                reinterpret_cast<VkDescriptorSet>(textureHandles[0]),
                reinterpret_cast<VkDescriptorSet>(textureHandles[1])
            };
            vkCmdBindDescriptorSets(main, VK_PIPELINE_BIND_POINT_GRAPHICS, context.pipelines->layout, 1, 2, sets, 0,
                                    nullptr);
        }
    }

    u32 Renderer::record() {
        if (recordIndex >= 0 && !(state.dirty & RenderState::TRANSFORM)) return static_cast<u32>(recordIndex);
        u64 offset = records->allocate(sizeof(DrawRecord), sizeof(DrawRecord));
        bool bindless = contextValue->textures->bindless();
        writeRecord(reinterpret_cast<DrawRecord *>(records->address(offset)), textureFlags,
                    bindless ? static_cast<i32>(textureHandles[0]) : 0,
                    bindless ? static_cast<i32>(textureHandles[1]) : 0);
        recordIndex = static_cast<i64>(offset / sizeof(DrawRecord));
        state.dirty &= ~RenderState::TRANSFORM;
        return static_cast<u32>(recordIndex);
    }

    i32 Renderer::allocateEnvironment(EnvironmentBlock *&block) {
        u64 offset = environments->allocate(sizeof(EnvironmentBlock), sizeof(EnvironmentBlock));
        block = reinterpret_cast<EnvironmentBlock *>(environments->address(offset));
        return static_cast<i32>(offset / sizeof(EnvironmentBlock));
    }

    void Renderer::bindPipeline(VkPipeline pipeline) {
        if (pipeline == boundPipeline) return;
        flushBatch();
        vkCmdBindPipeline(main, VK_PIPELINE_BIND_POINT_GRAPHICS, pipeline);
        boundPipeline = pipeline;
    }

    void Renderer::bindMesh(const GeometryPool *pool) {
        if (pool->positions) {
            setVertexBuffers(pool->positions->handle, 0, pool->attributes ? pool->attributes->handle : VK_NULL_HANDLE,
                             0);
        } else {
            setVertexBuffers(pool->attributes->handle, 0, VK_NULL_HANDLE, 0);
        }
    }

    void Renderer::setVertexBuffers(VkBuffer first, u64 firstOffset, VkBuffer second, u64 secondOffset) {
        u32 count = second ? 2 : 1;
        if (boundVertexBuffers[0] == first && boundVertexOffsets[0] == firstOffset &&
            (count == 1 || (boundVertexBuffers[1] == second && boundVertexOffsets[1] == secondOffset))) {
            return;
        }
        flushBatch();
        VkBuffer buffers[2] = {first, second};
        VkDeviceSize offsets[2] = {firstOffset, secondOffset};
        vkCmdBindVertexBuffers(main, 0, count, buffers, offsets);
        boundVertexBuffers[0] = first;
        boundVertexOffsets[0] = firstOffset;
        boundVertexBuffers[1] = second;
        boundVertexOffsets[1] = secondOffset;
    }

    void Renderer::bindIndices(VkBuffer buffer, u64 offset) {
        if (boundIndexBuffer == buffer && boundIndexOffset == offset) return;
        vkCmdBindIndexBuffer(main, buffer, offset, VK_INDEX_TYPE_UINT32);
        boundIndexBuffer = buffer;
        boundIndexOffset = offset;
    }

    void Renderer::bindQuadIndices(bool flat) {
        bindIndices(quadIndices->handle, flat ? u64(MAX_QUADS) * 6 * 4 : 0);
    }

    void Renderer::issue(u32 drawRecord, u32 firstVertex, VkBuffer indexBuffer, u64 indexOffset) {
        switch (plan.indexing) {
            case PrimitivePlan::DIRECT:
                vkCmdDraw(main, plan.vertexCount, 1, firstVertex, drawRecord);
                break;
            case PrimitivePlan::GENERATED:
                bindIndices(indexBuffer, indexOffset);
                vkCmdDrawIndexed(main, plan.indexCount, 1, 0, static_cast<i32>(firstVertex), drawRecord);
                break;
            default: {
                bindQuadIndices(state.flatShading);
                u32 quads = plan.vertexCount / 4;
                for (u32 quad = 0; quad < quads;) {
                    u32 run = std::min(MAX_QUADS, quads - quad);
                    vkCmdDrawIndexed(main, run * 6, 1, 0, static_cast<i32>(firstVertex + quad * 4), drawRecord);
                    quad += run;
                }
                break;
            }
        }
    }

    void Renderer::beginCapture() {
        ensureRecording();
        flushBatch();
        capturing = contextValue->textures->bindless();
    }

    void Renderer::endCapture() {
        if (!capturing) return;
        flushBatch();
        capturing = false;
    }

    Renderer::DynamicState Renderer::currentDynamicState() const {
        DynamicState dynamic{};
        i32 width = std::max(state.viewportWidth, 1);
        i32 height = std::max(state.viewportHeight, 1);
        i32 y = target->pass.flipY() ? static_cast<i32>(target->height()) - (state.viewportY + height) : state.viewportY;
        dynamic.viewport = {
            static_cast<float>(state.viewportX), static_cast<float>(y), static_cast<float>(width),
            static_cast<float>(height), 0.0f, 1.0f
        };
        Device &device = *contextValue->device;
        dynamic.lineWidth = device.wideLines ? clamp(state.lineWidthValue, 1.0f, device.lineWidthMax) : 1.0f;
        dynamic.biasUnits = state.offsetUnits;
        dynamic.biasFactor = state.offsetFactor;
        return dynamic;
    }

    void Renderer::capture(const VertexLayout &layout, u32 topology, VkBuffer first, u64 firstOffset, VkBuffer second,
                           u64 secondOffset, u32 firstVertex, u32 vertices, bool flat) {
        Context &context = *contextValue;
        if (state.dirty & RenderState::TEXTURES) resolveTextures();
        if (state.dirty & RenderState::ENVIRONMENT) {
            EnvironmentBlock *block;
            currentEnvironment = allocateEnvironment(block);
            writeEnvironment(block, target->pass.flipY());
            recordIndex = -1;
            state.dirty &= ~RenderState::ENVIRONMENT;
        }
        u64 pipelineState = state.pipelineState();
        VkPipeline pipeline = context.pipelines->get(key::draw(pipelineState, topology, target->pass.id, layout.id),
                                                     layout, target->pass);
        constexpr u32 DYNAMIC = RenderState::VIEWPORT | RenderState::LINE_WIDTH | RenderState::DEPTH_BIAS;
        if (dynamicStates.empty() || (state.dirty & DYNAMIC)) {
            DynamicState dynamic = currentDynamicState();
            if (dynamicStates.empty() || std::memcmp(&dynamicStates.back(), &dynamic, sizeof(DynamicState)) != 0) {
                dynamicStates.push_back(dynamic);
            }
        }
        state.dirty &= ~(DYNAMIC | RenderState::PIPELINE);
        u32 drawRecord = record();
        u32 compare = static_cast<u32>(pipelineState >> key::COMPARE_SHIFT) & 7;
        bool ordered = (pipelineState & (key::BLEND | key::LOGIC_OP)) != 0 ||
                       (pipelineState & key::DEPTH_TEST) == 0 || (pipelineState & key::DEPTH_WRITE) == 0 ||
                       compare == static_cast<u32>(0x0202 - gl::NEVER);
        u32 dynamicIndex = static_cast<u32>(dynamicStates.size() - 1);
        u32 quads = vertices / 4;
        for (u32 quad = 0; quad < quads;) {
            u32 run = std::min(MAX_QUADS, quads - quad);
            packets.push_back({
                pipeline, {first, second}, {firstOffset, second ? secondOffset : 0}, run * 6, firstVertex + quad * 4,
                drawRecord, dynamicIndex, flat, ordered
            });
            quad += run;
        }
    }

    void Renderer::emitPackets() {
        emitting = true;
        submitBatch();
        u32 count = static_cast<u32>(packets.size());
        packetOrder.clear();
        for (u32 i = 0; i < count; i++) {
            if (!packets[i].ordered) packetOrder.push_back(i);
        }
        std::stable_sort(packetOrder.begin(), packetOrder.end(), [this](u32 left, u32 right) {
            const Packet &a = packets[left];
            const Packet &b = packets[right];
            auto handle = [](auto value) { return reinterpret_cast<std::uintptr_t>(value); };
            if (a.pipeline != b.pipeline) return handle(a.pipeline) < handle(b.pipeline);
            if (a.dynamic != b.dynamic) return a.dynamic < b.dynamic;
            if (a.buffers[0] != b.buffers[0]) return handle(a.buffers[0]) < handle(b.buffers[0]);
            if (a.offsets[0] != b.offsets[0]) return a.offsets[0] < b.offsets[0];
            if (a.buffers[1] != b.buffers[1]) return handle(a.buffers[1]) < handle(b.buffers[1]);
            if (a.offsets[1] != b.offsets[1]) return a.offsets[1] < b.offsets[1];
            return a.flat < b.flat;
        });
        for (u32 i = 0; i < count; i++) {
            if (packets[i].ordered) packetOrder.push_back(i);
        }
        u32 boundDynamic = ~0u;
        for (u32 index: packetOrder) {
            const Packet &packet = packets[index];
            if (packet.pipeline != boundPipeline) {
                submitBatch();
                vkCmdBindPipeline(main, VK_PIPELINE_BIND_POINT_GRAPHICS, packet.pipeline);
                boundPipeline = packet.pipeline;
            }
            if (packet.dynamic != boundDynamic) {
                submitBatch();
                const DynamicState &dynamic = dynamicStates[packet.dynamic];
                vkCmdSetViewport(main, 0, 1, &dynamic.viewport);
                vkCmdSetLineWidth(main, dynamic.lineWidth);
                vkCmdSetDepthBias(main, dynamic.biasUnits, 0.0f, dynamic.biasFactor);
                boundDynamic = packet.dynamic;
            }
            u32 bufferCount = packet.buffers[1] ? 2 : 1;
            if (boundVertexBuffers[0] != packet.buffers[0] || boundVertexOffsets[0] != packet.offsets[0] ||
                (bufferCount == 2 && (boundVertexBuffers[1] != packet.buffers[1] ||
                                      boundVertexOffsets[1] != packet.offsets[1]))) {
                submitBatch();
                VkDeviceSize offsets[2] = {packet.offsets[0], packet.offsets[1]};
                vkCmdBindVertexBuffers(main, 0, bufferCount, packet.buffers, offsets);
                boundVertexBuffers[0] = packet.buffers[0];
                boundVertexOffsets[0] = packet.offsets[0];
                boundVertexBuffers[1] = packet.buffers[1];
                boundVertexOffsets[1] = packet.offsets[1];
            }
            if (batchCount > 0 && batchFlat != packet.flat) submitBatch();
            if (batchCount == BATCH_SIZE) submitBatch();
            batchFlat = packet.flat;
            batchIndexCounts[batchCount] = packet.indexCount;
            batchVertexOffsets[batchCount] = packet.vertexOffset;
            batchRecords[batchCount] = packet.record;
            batchCount++;
        }
        submitBatch();
        packets.clear();
        dynamicStates.clear();
        batchSource = nullptr;
        state.dirty |= RenderState::VIEWPORT | RenderState::LINE_WIDTH | RenderState::DEPTH_BIAS;
        emitting = false;
    }

    void Renderer::flushBatch() {
        if (!packets.empty() && !emitting) emitPackets();
        submitBatch();
    }

    void Renderer::submitBatch() {
        u32 count = batchCount;
        if (count == 0) return;
        batchCount = 0;
        bindQuadIndices(batchFlat);
        Device &device = *contextValue->device;
        u64 offset = device.multiDrawIndirect && count > 1
                         ? stream->allocate(static_cast<u64>(count) * INDIRECT_STRIDE, 4)
                         : FrameStream::FAILED;
        if (offset == FrameStream::FAILED) {
            for (u32 i = 0; i < count; i++) {
                vkCmdDrawIndexed(main, batchIndexCounts[i], 1, 0, static_cast<i32>(batchVertexOffsets[i]),
                                 batchRecords[i]);
            }
            return;
        }
        auto *commands = reinterpret_cast<VkDrawIndexedIndirectCommand *>(stream->address(offset));
        for (u32 i = 0; i < count; i++) {
            commands[i] = {batchIndexCounts[i], 1, 0, static_cast<i32>(batchVertexOffsets[i]), batchRecords[i]};
        }
        for (u32 first = 0; first < count;) {
            u32 draws = std::min(count - first, device.maxDrawIndirectCount);
            vkCmdDrawIndexedIndirect(main, stream->handle(), offset + static_cast<u64>(first) * INDIRECT_STRIDE, draws,
                                     INDIRECT_STRIDE);
            first += draws;
        }
    }

    void Renderer::uploadQuadIndices() {
        u64 bytes = static_cast<u64>(MAX_QUADS) * 6 * 4;
        VkBuffer buffer;
        u64 offset;
        auto *indices = reinterpret_cast<u32 *>(stage(bytes * 2, buffer, offset));
        u32 *flat = indices + MAX_QUADS * 6;
        for (u32 quad = 0; quad < MAX_QUADS; quad++) {
            u32 base = quad * 4;
            u32 *smooth = indices + quad * 6;
            smooth[0] = base;
            smooth[1] = base + 1;
            smooth[2] = base + 2;
            smooth[3] = base;
            smooth[4] = base + 2;
            smooth[5] = base + 3;
            u32 *leading = flat + quad * 6;
            leading[0] = base + 3;
            leading[1] = base;
            leading[2] = base + 1;
            leading[3] = base + 3;
            leading[4] = base + 1;
            leading[5] = base + 2;
        }
        VkBufferCopy region{offset, 0, bytes * 2};
        vkCmdCopyBuffer(setup, buffer, quadIndices->handle, 1, &region);
        setupTransfers = true;
    }

    void Renderer::createFrameSet() {
        Context &context = *contextValue;
        VkDevice device = context.device->handle;
        VkDescriptorPoolSize sizes[1] = {
            {.type = VK_DESCRIPTOR_TYPE_STORAGE_BUFFER, .descriptorCount = 3}
        };
        VkDescriptorPoolCreateInfo poolInfo{.sType = VK_STRUCTURE_TYPE_DESCRIPTOR_POOL_CREATE_INFO};
        poolInfo.maxSets = 1;
        poolInfo.poolSizeCount = 1;
        poolInfo.pPoolSizes = sizes;
        check(vkCreateDescriptorPool(device, &poolInfo, nullptr, &framePool), "vkCreateDescriptorPool");
        VkDescriptorSetAllocateInfo allocateInfo{.sType = VK_STRUCTURE_TYPE_DESCRIPTOR_SET_ALLOCATE_INFO};
        allocateInfo.descriptorPool = framePool;
        allocateInfo.descriptorSetCount = 1;
        allocateInfo.pSetLayouts = &context.pipelines->frameSetLayout;
        check(vkAllocateDescriptorSets(device, &allocateInfo, &frameSet), "vkAllocateDescriptorSets");
        VkDescriptorBufferInfo environment{.buffer = environments->handle(), .offset = 0, .range = VK_WHOLE_SIZE};
        VkDescriptorBufferInfo draws{.buffer = records->handle(), .offset = 0, .range = VK_WHOLE_SIZE};
        VkDescriptorBufferInfo terrainSections{.buffer = sections->handle(), .offset = 0, .range = VK_WHOLE_SIZE};
        VkWriteDescriptorSet writes[3]{};
        for (u32 i = 0; i < 3; i++) {
            writes[i].sType = VK_STRUCTURE_TYPE_WRITE_DESCRIPTOR_SET;
            writes[i].dstSet = frameSet;
            writes[i].dstBinding = i;
            writes[i].descriptorCount = 1;
        }
        writes[0].descriptorType = VK_DESCRIPTOR_TYPE_STORAGE_BUFFER;
        writes[0].pBufferInfo = &environment;
        writes[1].descriptorType = VK_DESCRIPTOR_TYPE_STORAGE_BUFFER;
        writes[1].pBufferInfo = &draws;
        writes[2].descriptorType = VK_DESCRIPTOR_TYPE_STORAGE_BUFFER;
        writes[2].pBufferInfo = &terrainSections;
        vkUpdateDescriptorSets(device, 3, writes, 0, nullptr);
    }

    static VkAccessFlags access(VkImageLayout layout) {
        switch (layout) {
            case VK_IMAGE_LAYOUT_TRANSFER_DST_OPTIMAL: return VK_ACCESS_TRANSFER_WRITE_BIT;
            case VK_IMAGE_LAYOUT_TRANSFER_SRC_OPTIMAL: return VK_ACCESS_TRANSFER_READ_BIT;
            case VK_IMAGE_LAYOUT_SHADER_READ_ONLY_OPTIMAL: return VK_ACCESS_SHADER_READ_BIT;
            case VK_IMAGE_LAYOUT_DEPTH_STENCIL_ATTACHMENT_OPTIMAL:
                return VK_ACCESS_DEPTH_STENCIL_ATTACHMENT_READ_BIT | VK_ACCESS_DEPTH_STENCIL_ATTACHMENT_WRITE_BIT;
            case VK_IMAGE_LAYOUT_GENERAL:
            case VK_IMAGE_LAYOUT_PRESENT_SRC_KHR: return VK_ACCESS_COLOR_ATTACHMENT_WRITE_BIT;
            default: return 0;
        }
    }

    static VkPipelineStageFlags pipelineStage(VkImageLayout layout) {
        switch (layout) {
            case VK_IMAGE_LAYOUT_UNDEFINED: return VK_PIPELINE_STAGE_TOP_OF_PIPE_BIT;
            case VK_IMAGE_LAYOUT_TRANSFER_DST_OPTIMAL:
            case VK_IMAGE_LAYOUT_TRANSFER_SRC_OPTIMAL: return VK_PIPELINE_STAGE_TRANSFER_BIT;
            case VK_IMAGE_LAYOUT_SHADER_READ_ONLY_OPTIMAL: return VK_PIPELINE_STAGE_FRAGMENT_SHADER_BIT;
            case VK_IMAGE_LAYOUT_DEPTH_STENCIL_ATTACHMENT_OPTIMAL:
                return VK_PIPELINE_STAGE_EARLY_FRAGMENT_TESTS_BIT | VK_PIPELINE_STAGE_LATE_FRAGMENT_TESTS_BIT;
            case VK_IMAGE_LAYOUT_GENERAL:
            case VK_IMAGE_LAYOUT_PRESENT_SRC_KHR: return VK_PIPELINE_STAGE_COLOR_ATTACHMENT_OUTPUT_BIT;
            default: return VK_PIPELINE_STAGE_ALL_COMMANDS_BIT;
        }
    }

    void Renderer::imageBarrier(VkCommandBuffer commands, VkImage image, VkImageAspectFlags aspect, u32 levels,
                                VkImageLayout oldLayout,
                                VkImageLayout newLayout) {
        VkImageMemoryBarrier barrier{.sType = VK_STRUCTURE_TYPE_IMAGE_MEMORY_BARRIER};
        barrier.srcAccessMask = access(oldLayout);
        barrier.dstAccessMask = access(newLayout);
        barrier.oldLayout = oldLayout;
        barrier.newLayout = newLayout;
        barrier.srcQueueFamilyIndex = VK_QUEUE_FAMILY_IGNORED;
        barrier.dstQueueFamilyIndex = VK_QUEUE_FAMILY_IGNORED;
        barrier.image = image;
        barrier.subresourceRange = {
            .aspectMask = aspect, .baseMipLevel = 0, .levelCount = levels, .baseArrayLayer = 0, .layerCount = 1
        };
        vkCmdPipelineBarrier(commands, pipelineStage(oldLayout), pipelineStage(newLayout), 0, 0, nullptr, 0, nullptr, 1,
                             &barrier);
    }

    static i32 colorMaterialMode(i32 mode) {
        switch (mode) {
            case gl::AMBIENT: return 1;
            case gl::DIFFUSE: return 2;
            case gl::EMISSION: return 3;
            case gl::SPECULAR: return 4;
            default: return 0;
        }
    }

    void Renderer::writeEnvironment(EnvironmentBlock *block, bool flipY) {
        float matrix[16];
        std::memcpy(matrix, state.projection.top(), 64);
        for (int column = 0; column < 4; column++) {
            float *c = matrix + column * 4;
            if (flipY) c[1] = -c[1];
            c[2] = 0.5f * (c[2] + c[3]);
        }
        std::memcpy(block->projection, matrix, 64);
        std::memcpy(block->textureMatrix0, state.texture(0).top(), 64);
        std::memcpy(block->textureMatrix1, state.texture(1).top(), 64);
        for (int light = 0; light < 2; light++) {
            std::memcpy(block->lightPosition[light], state.lightPositions + light * 4, 16);
            std::memcpy(block->lightDiffuse[light], state.lightDiffuse + light * 4, 16);
        }
        float ambient[4];
        std::memcpy(ambient, state.lightModelAmbientValue, 16);
        for (int light = 0; light < 8; light++) {
            if (!state.lights[light]) continue;
            for (int i = 0; i < 3; i++) ambient[i] += state.lightAmbient[light * 4 + i];
        }
        std::memcpy(block->sceneAmbient, ambient, 16);
        constexpr float materialAmbient[4] = {0.2f, 0.2f, 0.2f, 1.0f};
        constexpr float materialDiffuse[4] = {0.8f, 0.8f, 0.8f, 1.0f};
        constexpr float materialEmission[4] = {0.0f, 0.0f, 0.0f, 1.0f};
        std::memcpy(block->materialAmbient, materialAmbient, 16);
        std::memcpy(block->materialDiffuse, materialDiffuse, 16);
        std::memcpy(block->materialEmission, materialEmission, 16);
        std::memcpy(block->fogColor, state.fogColorValue, 16);
        float range = state.fogEndValue - state.fogStartValue;
        block->fogParams[0] = state.fogStartValue;
        block->fogParams[1] = state.fogEndValue;
        block->fogParams[2] = state.fogDensityValue;
        block->fogParams[3] = range != 0.0f ? 1.0f / range : 0.0f;
        std::memcpy(block->overlayColor, state.overlayColor, 16);
        i32 modes = 0;
        for (int coordinate = 0; coordinate < 4; coordinate++) {
            bool eye = state.texGenModes[coordinate] == gl::EYE_LINEAR;
            std::memcpy(block->texGenPlanes[coordinate], (eye ? state.eyePlanes : state.objectPlanes) + coordinate * 4,
                        16);
            if (eye) modes |= 1 << (coordinate * 2);
        }
        i32 flags = 0;
        if (state.lighting) flags |= ff::LIGHTING;
        for (int light = 0; light < 2; light++) if (state.lights[light]) flags |= ff::LIGHT0 << light;
        if (state.colorMaterialEnabled) flags |= ff::COLOR_MATERIAL;
        flags |= colorMaterialMode(state.colorMaterialMode) << ff::COLOR_MATERIAL_SHIFT;
        if (state.fogEnabled) flags |= ff::FOG;
        if (state.fogRadialValue) flags |= ff::FOG_RADIAL;
        if (state.alphaTest) flags |= ff::ALPHA_TEST;
        if (state.overlayEnabled) flags |= ff::OVERLAY;
        if (state.normalizeEnabled) flags |= ff::NORMALIZE;
        for (int coordinate = 0; coordinate < 4; coordinate++)
            if (state.texGenEnabled[coordinate])
                flags |= ff::TEXGEN_S << coordinate;
        i32 fogMode = state.fogModeValue == gl::LINEAR ? 0 : (state.fogModeValue == gl::EXP ? 1 : 2);
        block->flags[0] = flags;
        block->flags[1] = state.alphaFunctionValue - gl::NEVER;
        block->flags[2] = fogMode;
        block->flags[3] = modes;
        block->alphaReference[0] = state.alphaReference;
        block->alphaReference[1] = 0.0f;
        block->alphaReference[2] = 0.0f;
        block->alphaReference[3] = 0.0f;
    }

    void Renderer::writeRecord(DrawRecord *drawRecord, u32 flags, i32 texture0, i32 texture1) {
        const float *modelView = state.modelView.top();
        std::memcpy(drawRecord->modelView, modelView, 64);
        if (state.lighting) {
            mat4::normalMatrix(modelView, state.rescaleNormal, &drawRecord->normalMatrix[0][0]);
        }
        std::memcpy(drawRecord->color, state.colorValue, 16);
        drawRecord->normal[0] = state.normalValue[0];
        drawRecord->normal[1] = state.normalValue[1];
        drawRecord->normal[2] = state.normalValue[2];
        drawRecord->normal[3] = 0.0f;
        std::memcpy(drawRecord->texCoord0, state.texCoords, 16);
        std::memcpy(drawRecord->texCoord1, state.texCoords + 4, 16);
        drawRecord->textures[0] = texture0;
        drawRecord->textures[1] = texture1;
        drawRecord->textures[2] = static_cast<i32>(flags);
        drawRecord->textures[3] = currentEnvironment;
    }
} // namespace at
