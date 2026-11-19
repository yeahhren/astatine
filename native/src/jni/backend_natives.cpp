#include "jni/jvm.h"

#include "core/error.h"
#include "render/renderer.h"
#include "render/targets.h"
#include "render/texture.h"
#include "backend/backend.h"
#include "backend/descriptors.h"
#include "backend/encoder.h"
#include "backend/mipmaps.h"
#include "backend/pipelines.h"
#include "backend/programs.h"
#include "backend/resources.h"
#include "backend/synchronization.h"
#include "backend/transient.h"
#include "vulkan/device.h"
#include "vulkan/memory.h"

#include <cmath>
#include <iterator>
#include <vector>

using namespace at;
using namespace at::backend;

#define AT_GPU(type, name) extern "C" JNIEXPORT type JNICALL Java_yt_ren_astatine_backend_BackendNatives_##name

namespace {
    struct ColorRecord {
        u64 view;
        u32 clear;
        u32 padding;
        float color[4];
    };

    struct DepthRecord {
        u64 view;
        u32 clear;
        float depth;
    };

    std::string text(JNIEnv *env, jstring value) {
        return jvm::string(env, value);
    }

    VkRect2D rect(jint x, jint y, jint width, jint height) {
        return {{x, y}, {static_cast<u32>(width), static_cast<u32>(height)}};
    }

    jlong object(const void *value) {
        return static_cast<jlong>(reinterpret_cast<std::uintptr_t>(value));
    }

    VkCommandBuffer drawCommands() {
        Backend &backend = Backend::get();
        VkCommandBuffer commands = backend.commands();
        backend.programs->prepare(commands);
        return commands;
    }

    struct QueryPool {
        VkQueryPool handle;
        u32 size;
    };
} // namespace

AT_GPU(jintArray, deviceProperties)(JNIEnv *env, jclass) {
    return jvm::handled(env, [&]() -> jintArray {
        Backend &backend = Backend::get();
        const VkPhysicalDeviceLimits &limits = backend.properties.limits;
        float period = limits.timestampPeriod;
        jint periodBits;
        std::memcpy(&periodBits, &period, sizeof(periodBits));
        jint values[] = {
            static_cast<jint>(std::max(1.0f, limits.maxSamplerAnisotropy)),
            static_cast<jint>(std::max<u64>(16, limits.minUniformBufferOffsetAlignment)),
            static_cast<jint>(limits.maxImageDimension2D), static_cast<jint>(limits.maxColorAttachments),
            backend.demote ? 1 : 0, backend.geometryShaders ? 1 : 0, backend.tessellationShaders ? 1 : 0, periodBits,
            static_cast<jint>(backend.device.vendorId), static_cast<jint>(backend.device.driverVersion)
        };
        jintArray array = env->NewIntArray(static_cast<jsize>(std::size(values)));
        env->SetIntArrayRegion(array, 0, static_cast<jsize>(std::size(values)), values);
        return array;
    });
}

AT_GPU(jlong, bufferCreate)(JNIEnv *env, jclass, jint usage, jlong size, jboolean hostVisible, jstring label) {
    return jvm::handled(env, [&]() -> jlong {
        std::string name = text(env, label);
        return object(Buffer::create(static_cast<u32>(usage), static_cast<u64>(size), hostVisible, name.c_str()));
    });
}

AT_GPU(jlong, bufferWrap)(JNIEnv *env, jclass, jlong handle, jlong size, jint usage) {
    return jvm::handled(env, [&]() -> jlong {
        return object(Buffer::wrap(reinterpret_cast<VkBuffer>(handle), static_cast<u64>(size), static_cast<u32>(usage)));
    });
}

AT_GPU(jlong, bufferAddress)(JNIEnv *, jclass, jlong buffer) {
    return object(pointer<Buffer>(buffer)->mapped);
}

AT_GPU(void, bufferRelease)(JNIEnv *env, jclass, jlong buffer) {
    jvm::handled(env, [&] { pointer<Buffer>(buffer)->release(); });
}

AT_GPU(jlong, textureCreate)(JNIEnv *env, jclass, jint usage, jint format, jint width, jint height, jint layers, jint levels,
                             jstring label) {
    return jvm::handled(env, [&]() -> jlong {
        std::string name = text(env, label);
        return object(backend::Texture::create(static_cast<u32>(usage), vulkanFormat(static_cast<u32>(format)), static_cast<u32>(width),
                                      static_cast<u32>(height), static_cast<u32>(layers), static_cast<u32>(levels),
                                      name.c_str()));
    });
}

AT_GPU(jintArray, textureInfo)(JNIEnv *env, jclass, jlong handle) {
    return jvm::handled(env, [&]() -> jintArray {
        backend::Texture *texture = pointer<backend::Texture>(handle);
        jint values[] = {
            gpuFormat(texture->format), static_cast<jint>(texture->width), static_cast<jint>(texture->height),
            static_cast<jint>(texture->layers), static_cast<jint>(texture->levels)
        };
        jintArray array = env->NewIntArray(5);
        env->SetIntArrayRegion(array, 0, 5, values);
        return array;
    });
}

AT_GPU(void, textureRelease)(JNIEnv *env, jclass, jlong texture) {
    jvm::handled(env, [&] { pointer<backend::Texture>(texture)->release(); });
}

AT_GPU(jlong, textureViewCreate)(JNIEnv *env, jclass, jlong texture, jint baseLevel, jint levels) {
    return jvm::handled(env, [&]() -> jlong {
        return object(TextureView::create(pointer<backend::Texture>(texture), static_cast<u32>(baseLevel), static_cast<u32>(levels)));
    });
}

AT_GPU(jlong, textureViewTexture)(JNIEnv *, jclass, jlong view) {
    return object(pointer<TextureView>(view)->texture);
}

AT_GPU(void, textureViewRelease)(JNIEnv *env, jclass, jlong view) {
    jvm::handled(env, [&] { pointer<TextureView>(view)->release(); });
}

AT_GPU(jlong, framebufferColorView)(JNIEnv *env, jclass, jlong framebuffer) {
    return jvm::handled(env, [&]() -> jlong {
        Backend::get().waitIdle();
        auto *target = pointer<Framebuffer>(framebuffer);
        if (!target || !target->color) fail("the main framebuffer has no color image");
        GpuImage &color = *target->color;
        u32 usage = texture_usage::RENDER_ATTACHMENT | texture_usage::TEXTURE_BINDING | texture_usage::COPY_SRC |
                    texture_usage::COPY_DST;
        backend::Texture *texture = backend::Texture::wrap(color.handle, color.format, color.width, color.height, color.levels, usage);
        return object(TextureView::wrap(texture, color.view, 0, 1, VK_IMAGE_LAYOUT_GENERAL));
    });
}

AT_GPU(jlong, nativeTextureView)(JNIEnv *env, jclass, jlong handle) {
    return jvm::handled(env, [&]() -> jlong {
        Backend::get().waitIdle();
        auto *source = pointer<at::Texture>(handle);
        if (!source || !source->image) return 0;
        GpuImage &image = *source->image;
        u32 levels = source->sampledLevels();
        backend::Texture *texture = backend::Texture::wrap(image.handle, image.format, image.width, image.height, levels,
                                         texture_usage::TEXTURE_BINDING);
        return object(TextureView::wrap(texture, source->sampledView(), 0, levels, VK_IMAGE_LAYOUT_SHADER_READ_ONLY_OPTIMAL));
    });
}

AT_GPU(void, wrapperRelease)(JNIEnv *env, jclass, jlong view) {
    jvm::handled(env, [&] {
        auto *wrapped = pointer<TextureView>(view);
        backend::Texture *texture = wrapped->texture;
        Backend &backend = Backend::get();
        backend.releaseView(wrapped);
        backend.defer([wrapped, texture] {
            delete wrapped;
            delete texture;
        });
    });
}

AT_GPU(jlong, imageCreate)(JNIEnv *env, jclass, jint dimension, jint format, jint width, jint height, jint depth,
                           jboolean storage, jstring label) {
    return jvm::handled(env, [&]() -> jlong {
        std::string name = text(env, label);
        VkImageType type = dimension == 1 ? VK_IMAGE_TYPE_1D : dimension == 3 ? VK_IMAGE_TYPE_3D : VK_IMAGE_TYPE_2D;
        backend::Texture *texture = backend::Texture::createImage(type, vulkanFormat(static_cast<u32>(format)), static_cast<u32>(width),
                                                static_cast<u32>(height), static_cast<u32>(depth), storage, name.c_str());
        if (storage) encoder::clearImage(texture);
        return object(texture);
    });
}

AT_GPU(jlong, imageView)(JNIEnv *env, jclass, jlong handle) {
    return jvm::handled(env, [&]() -> jlong {
        backend::Texture *texture = pointer<backend::Texture>(handle);
        return object(TextureView::wrap(texture, texture->owned->view, 0, 1, VK_IMAGE_LAYOUT_GENERAL));
    });
}

AT_GPU(void, imageUpload)(JNIEnv *env, jclass, jlong texture, jlong source, jlong size) {
    jvm::handled(env, [&] { encoder::uploadImage(pointer<backend::Texture>(texture), pointer<u8>(source), static_cast<u64>(size)); });
}

AT_GPU(void, imageClear)(JNIEnv *env, jclass, jlong texture) {
    jvm::handled(env, [&] { encoder::clearImage(pointer<backend::Texture>(texture)); });
}

AT_GPU(jlong, samplerCreate)(JNIEnv *env, jclass, jboolean clampU, jboolean clampV, jboolean linearMin, jboolean linearMag,
                             jint maxAnisotropy, jfloat maxLod) {
    return jvm::handled(env, [&]() -> jlong {
        return object(Sampler::create(clampU, clampV, linearMin, linearMag, static_cast<u32>(maxAnisotropy), maxLod));
    });
}

AT_GPU(jlong, samplerWrap)(JNIEnv *env, jclass, jlong handle) {
    return jvm::handled(env, [&]() -> jlong { return object(Sampler::wrap(reinterpret_cast<VkSampler>(handle))); });
}

AT_GPU(void, samplerRelease)(JNIEnv *env, jclass, jlong sampler) {
    jvm::handled(env, [&] { pointer<Sampler>(sampler)->release(); });
}

AT_GPU(void, transientAllocate)(JNIEnv *env, jclass, jlong size, jlong alignment, jlong out) {
    jvm::handled(env, [&] {
        TransientMemory::Allocation allocation = Backend::get().transient->allocate(static_cast<u64>(size),
                                                                                     static_cast<u64>(alignment));
        u64 values[4] = {
            static_cast<u64>(object(allocation.buffer)), allocation.offset, static_cast<u64>(object(allocation.address)),
            allocation.buffer->size
        };
        std::memcpy(pointer<void>(out), values, sizeof(values));
    });
}

AT_GPU(void, clearColorTexture)(JNIEnv *env, jclass, jlong texture, jfloat red, jfloat green, jfloat blue, jfloat alpha) {
    jvm::handled(env, [&] {
        float color[4] = {red, green, blue, alpha};
        encoder::clearColorTexture(pointer<backend::Texture>(texture), color);
    });
}

AT_GPU(void, clearDepthTexture)(JNIEnv *env, jclass, jlong texture, jdouble depth) {
    jvm::handled(env, [&] { encoder::clearDepthTexture(pointer<backend::Texture>(texture), static_cast<float>(depth)); });
}

AT_GPU(void, writeBuffer)(JNIEnv *env, jclass, jlong buffer, jlong offset, jlong source, jlong size) {
    jvm::handled(env, [&] {
        encoder::writeBuffer(pointer<Buffer>(buffer), static_cast<u64>(offset), pointer<u8>(source), static_cast<u64>(size));
    });
}

AT_GPU(void, writeTexture)(JNIEnv *env, jclass, jlong texture, jlong source, jlong size, jint level, jint layer, jint x, jint y,
                           jint width, jint height) {
    jvm::handled(env, [&] {
        encoder::writeTexture(pointer<backend::Texture>(texture), pointer<u8>(source), static_cast<u64>(size), static_cast<u32>(level),
                              static_cast<u32>(layer), x, y, static_cast<u32>(width), static_cast<u32>(height));
    });
}

AT_GPU(void, copyTexture)(JNIEnv *env, jclass, jlong source, jlong destination, jint level, jint destinationX,
                          jint destinationY, jint sourceX, jint sourceY, jint width, jint height) {
    jvm::handled(env, [&] {
        encoder::copyTexture(pointer<backend::Texture>(source), pointer<backend::Texture>(destination), static_cast<u32>(level), destinationX,
                             destinationY, sourceX, sourceY, static_cast<u32>(width), static_cast<u32>(height));
    });
}

AT_GPU(void, renderPassBegin)(JNIEnv *env, jclass, jlong attachments, jint colorCount, jboolean hasDepth, jint x, jint y,
                              jint width, jint height) {
    jvm::handled(env, [&] {
        std::vector<ColorAttachment> colors(static_cast<std::size_t>(colorCount));
        const u8 *cursor = pointer<const u8>(attachments);
        for (jint i = 0; i < colorCount; i++) {
            auto record = load<ColorRecord>(cursor);
            cursor += sizeof(ColorRecord);
            colors[i].view = pointer<TextureView>(static_cast<i64>(record.view));
            colors[i].clear = record.clear != 0;
            std::memcpy(colors[i].color, record.color, sizeof(record.color));
        }
        DepthAttachment depth{};
        if (hasDepth) {
            auto record = load<DepthRecord>(cursor);
            depth.view = pointer<TextureView>(static_cast<i64>(record.view));
            depth.clear = record.clear != 0;
            depth.depth = record.depth;
        }
        encoder::beginPass(colors.data(), static_cast<u32>(colorCount), hasDepth ? &depth : nullptr, rect(x, y, width, height));
    });
}

AT_GPU(void, renderPassEnd)(JNIEnv *env, jclass, jboolean world) {
    jvm::handled(env, [&] { encoder::endPass(world); });
}

AT_GPU(void, renderPassAttachments)(JNIEnv *env, jclass, jlong views, jint colorCount, jlong depth, jint x, jint y, jint width,
                                    jint height) {
    jvm::handled(env, [&] {
        std::vector<TextureView *> colors(static_cast<std::size_t>(colorCount));
        for (jint i = 0; i < colorCount; i++) colors[i] = pointer<TextureView>(load<i64>(pointer<u8>(views) + i * 8));
        encoder::switchAttachments(colors.data(), static_cast<u32>(colorCount), pointer<TextureView>(depth),
                                   rect(x, y, width, height));
    });
}

AT_GPU(void, setViewport)(JNIEnv *env, jclass, jfloat x, jfloat y, jfloat width, jfloat height) {
    jvm::handled(env, [&] {
        VkViewport viewport{x, y, width, height, 0.0f, 1.0f};
        vkCmdSetViewport(Backend::get().commands(), 0, 1, &viewport);
    });
}

AT_GPU(void, setScissor)(JNIEnv *env, jclass, jint x, jint y, jint width, jint height) {
    jvm::handled(env, [&] {
        VkRect2D scissor = rect(std::max(0, x), std::max(0, y), std::max(0, width), std::max(0, height));
        vkCmdSetScissor(Backend::get().commands(), 0, 1, &scissor);
    });
}

AT_GPU(void, setVertexBuffer)(JNIEnv *env, jclass, jint slot, jlong buffer, jlong offset) {
    jvm::handled(env, [&] {
        VkBuffer handle = pointer<Buffer>(buffer)->handle;
        VkDeviceSize start = static_cast<VkDeviceSize>(offset);
        vkCmdBindVertexBuffers(Backend::get().commands(), static_cast<u32>(slot), 1, &handle, &start);
    });
}

AT_GPU(void, setIndexBuffer)(JNIEnv *env, jclass, jlong buffer, jlong offset, jboolean wide) {
    jvm::handled(env, [&] {
        vkCmdBindIndexBuffer(Backend::get().commands(), pointer<Buffer>(buffer)->handle, static_cast<VkDeviceSize>(offset),
                             wide ? VK_INDEX_TYPE_UINT32 : VK_INDEX_TYPE_UINT16);
    });
}

AT_GPU(void, draw)(JNIEnv *env, jclass, jint count, jint instances, jint first, jint firstInstance) {
    jvm::handled(env, [&] {
        VkCommandBuffer commands = drawCommands();
        vkCmdDraw(commands, static_cast<u32>(count), static_cast<u32>(instances), static_cast<u32>(first),
                  static_cast<u32>(firstInstance));
    });
}

AT_GPU(void, drawIndexed)(JNIEnv *env, jclass, jint count, jint instances, jint first, jint vertexOffset, jint firstInstance) {
    jvm::handled(env, [&] {
        VkCommandBuffer commands = drawCommands();
        vkCmdDrawIndexed(commands, static_cast<u32>(count), static_cast<u32>(instances), static_cast<u32>(first),
                         vertexOffset, static_cast<u32>(firstInstance));
    });
}

AT_GPU(void, drawIndirect)(JNIEnv *env, jclass, jlong buffer, jlong offset, jint count, jboolean indexed) {
    jvm::handled(env, [&] {
        VkCommandBuffer commands = drawCommands();
        VkBuffer handle = pointer<Buffer>(buffer)->handle;
        if (indexed) {
            vkCmdDrawIndexedIndirect(commands, handle, static_cast<VkDeviceSize>(offset), static_cast<u32>(count),
                                     sizeof(VkDrawIndexedIndirectCommand));
        } else {
            vkCmdDrawIndirect(commands, handle, static_cast<VkDeviceSize>(offset), static_cast<u32>(count),
                              sizeof(VkDrawIndirectCommand));
        }
    });
}

AT_GPU(void, multiDraw)(JNIEnv *env, jclass, jlong parameters, jint count, jint instances, jint firstInstance, jboolean indexed) {
    jvm::handled(env, [&] {
        VkCommandBuffer commands = drawCommands();
        const u8 *data = pointer<const u8>(parameters);
        if (indexed) {
            if (vkCmdDrawMultiIndexedEXT) {
                vkCmdDrawMultiIndexedEXT(commands, static_cast<u32>(count), reinterpret_cast<const VkMultiDrawIndexedInfoEXT *>(data),
                                         static_cast<u32>(instances), static_cast<u32>(firstInstance),
                                         sizeof(VkMultiDrawIndexedInfoEXT), nullptr);
                return;
            }
            for (jint i = 0; i < count; i++) {
                auto info = load<VkMultiDrawIndexedInfoEXT>(data + i * sizeof(VkMultiDrawIndexedInfoEXT));
                vkCmdDrawIndexed(commands, info.indexCount, static_cast<u32>(instances), info.firstIndex, info.vertexOffset,
                                 static_cast<u32>(firstInstance));
            }
        } else {
            if (vkCmdDrawMultiEXT) {
                vkCmdDrawMultiEXT(commands, static_cast<u32>(count), reinterpret_cast<const VkMultiDrawInfoEXT *>(data),
                                  static_cast<u32>(instances), static_cast<u32>(firstInstance), sizeof(VkMultiDrawInfoEXT));
                return;
            }
            for (jint i = 0; i < count; i++) {
                auto info = load<VkMultiDrawInfoEXT>(data + i * sizeof(VkMultiDrawInfoEXT));
                vkCmdDraw(commands, info.vertexCount, static_cast<u32>(instances), info.firstVertex, static_cast<u32>(firstInstance));
            }
        }
    });
}

AT_GPU(void, memoryBarrier)(JNIEnv *env, jclass) {
    jvm::handled(env, [&] { backend::memoryBarrier(Backend::get().commands()); });
}

AT_GPU(void, mainTargetAccess)(JNIEnv *env, jclass, jlong texture, jboolean general) {
    jvm::handled(env, [&] { encoder::mainTargetAccess(pointer<backend::Texture>(texture), general); });
}

AT_GPU(void, synchronize)(JNIEnv *env, jclass, jlong records, jint count, jint defaultStage) {
    jvm::handled(env, [&] {
        Backend &backend = Backend::get();
        std::vector<SyncAccess> accesses(static_cast<std::size_t>(count));
        const u8 *cursor = pointer<const u8>(records);
        for (jint i = 0; i < count; i++) {
            accesses[i] = Synchronization::decode(load<AccessRecord>(cursor + i * sizeof(AccessRecord)),
                                                  static_cast<u32>(defaultStage));
        }
        backend.sync->before(backend.commands(), accesses);
    });
}

AT_GPU(jlong, resourceSetsCreate)(JNIEnv *env, jclass, jlong descriptors, jint count, jint capacity, jint setIndex,
                                  jlong uniformSize) {
    return jvm::handled(env, [&]() -> jlong {
        std::vector<SetDescriptor> list(static_cast<std::size_t>(count));
        std::memcpy(list.data(), pointer<void>(descriptors), list.size() * sizeof(SetDescriptor));
        return object(new ResourceSets(list.data(), static_cast<u32>(count), static_cast<u32>(capacity),
                                       static_cast<u32>(setIndex), static_cast<u64>(uniformSize)));
    });
}

AT_GPU(void, resourceSetsRelease)(JNIEnv *env, jclass, jlong sets) {
    jvm::handled(env, [&] { pointer<ResourceSets>(sets)->release(); });
}

AT_GPU(jlong, samplerHeapCreate)(JNIEnv *env, jclass, jlong types, jint heapCount, jint drawCount, jint capacity) {
    return jvm::handled(env, [&]() -> jlong {
        const auto *list = pointer<const HeapType>(types);
        return object(new SamplerHeap(list, static_cast<u32>(heapCount), list + heapCount, static_cast<u32>(drawCount),
                                      static_cast<u32>(capacity)));
    });
}

AT_GPU(void, samplerHeapBeginFrame)(JNIEnv *env, jclass, jlong heap) {
    jvm::handled(env, [&] { pointer<SamplerHeap>(heap)->beginFrame(); });
}

AT_GPU(void, samplerHeapRelease)(JNIEnv *env, jclass, jlong heap) {
    jvm::handled(env, [&] { pointer<SamplerHeap>(heap)->release(); });
}

AT_GPU(jlong, shaderCompile)(JNIEnv *env, jclass, jint stage, jstring name, jstring source) {
    return jvm::handled(env, [&]() -> jlong {
        std::string label = text(env, name);
        std::string code = text(env, source);
        return object(Backend::get().shaders->compile(static_cast<u32>(stage), label, code));
    });
}

AT_GPU(void, shaderRelease)(JNIEnv *env, jclass, jlong code) {
    jvm::handled(env, [&] { delete pointer<ShaderCode>(code); });
}

AT_GPU(jlong, graphicsPipelineCreate)(JNIEnv *env, jclass, jlong description) {
    return jvm::handled(env, [&]() -> jlong { return object(new GraphicsPipeline(pointer<const u8>(description))); });
}

AT_GPU(void, graphicsPipelineBind)(JNIEnv *env, jclass, jlong pipeline, jlong heap, jlong bindings) {
    jvm::handled(env, [&] {
        BindingData data;
        data.read(pointer<const u8>(bindings), pointer<SamplerHeap>(heap));
        VkCommandBuffer commands = Backend::get().commands();
        pointer<GraphicsPipeline>(pipeline)->bind(commands, data);
    });
}

AT_GPU(void, graphicsPipelinePushConstants)(JNIEnv *env, jclass, jlong pipeline, jlong data, jint size) {
    jvm::handled(env, [&] {
        pointer<GraphicsPipeline>(pipeline)->pushConstants(Backend::get().commands(), pointer<void>(data), static_cast<u32>(size));
    });
}

AT_GPU(void, graphicsPipelineRelease)(JNIEnv *env, jclass, jlong pipeline) {
    jvm::handled(env, [&] { pointer<GraphicsPipeline>(pipeline)->release(); });
}

AT_GPU(jlong, computePipelineCreate)(JNIEnv *env, jclass, jlong heap, jlong sets, jlong frameSets, jlong code) {
    return jvm::handled(env, [&]() -> jlong {
        return object(new ComputePipeline(pointer<SamplerHeap>(heap), pointer<ResourceSets>(sets),
                                          pointer<ResourceSets>(frameSets), *pointer<ShaderCode>(code)));
    });
}

AT_GPU(jint, computeLocalSize)(JNIEnv *, jclass, jlong pipeline, jint axis) {
    return static_cast<jint>(pointer<ComputePipeline>(pipeline)->localSize[axis]);
}

AT_GPU(void, computeDispatch)(JNIEnv *env, jclass, jlong pipeline, jlong heap, jlong bindings, jint x, jint y, jint z,
                              jlong indirect, jlong indirectOffset) {
    jvm::handled(env, [&] {
        BindingData data;
        data.read(pointer<const u8>(bindings), pointer<SamplerHeap>(heap));
        VkCommandBuffer commands = Backend::get().commands();
        VkBuffer indirectBuffer = indirect ? pointer<Buffer>(indirect)->handle : VK_NULL_HANDLE;
        pointer<ComputePipeline>(pipeline)->dispatch(commands, data, static_cast<u32>(x), static_cast<u32>(y),
                                                     static_cast<u32>(z), indirectBuffer, static_cast<u64>(indirectOffset));
    });
}

AT_GPU(void, computePipelineRelease)(JNIEnv *env, jclass, jlong pipeline) {
    jvm::handled(env, [&] { pointer<ComputePipeline>(pipeline)->release(); });
}

AT_GPU(jlong, texelBufferView)(JNIEnv *env, jclass, jlong buffer, jlong offset, jlong length, jint format) {
    return jvm::handled(env, [&]() -> jlong {
        return reinterpret_cast<jlong>(encoder::texelView(pointer<Buffer>(buffer), static_cast<u64>(offset),
                                                          static_cast<u64>(length), vulkanFormat(static_cast<u32>(format))));
    });
}

AT_GPU(void, clearColors)(JNIEnv *env, jclass, jlong views, jint count, jfloat red, jfloat green, jfloat blue, jfloat alpha) {
    jvm::handled(env, [&] {
        std::vector<TextureView *> list(static_cast<std::size_t>(count));
        for (jint i = 0; i < count; i++) list[i] = pointer<TextureView>(load<i64>(pointer<u8>(views) + i * 8));
        float color[4] = {red, green, blue, alpha};
        encoder::clearColors(list.data(), static_cast<u32>(count), color);
    });
}

AT_GPU(void, copyTextureSynced)(JNIEnv *env, jclass, jlong source, jlong destination, jint width, jint height) {
    jvm::handled(env, [&] {
        encoder::copyTextureSynced(pointer<backend::Texture>(source), pointer<backend::Texture>(destination), static_cast<u32>(width),
                                   static_cast<u32>(height));
    });
}

AT_GPU(void, generateMipmaps)(JNIEnv *env, jclass, jlong handle, jboolean linear) {
    jvm::handled(env, [&] {
        backend::Texture *texture = pointer<backend::Texture>(handle);
        if (!linear || !Backend::get().colorMipmaps->generate(texture)) blitMipmaps(texture, linear);
    });
}

AT_GPU(void, generateDepthMipmaps)(JNIEnv *env, jclass, jlong texture) {
    jvm::handled(env, [&] { Backend::get().depthMipmaps->generate(pointer<backend::Texture>(texture)); });
}

AT_GPU(jboolean, mipmapsSupported)(JNIEnv *env, jclass, jint format) {
    return jvm::handled(env, [&]() -> jboolean {
        return Backend::get().colorMipmaps->supports(vulkanFormat(static_cast<u32>(format))) ? JNI_TRUE : JNI_FALSE;
    });
}

AT_GPU(void, mipmapSources)(JNIEnv *env, jclass, jstring core, jstring spd, jstring spdMain, jstring area, jstring tail) {
    jvm::handled(env, [&] {
        Backend::get().colorMipmaps->setSources(text(env, core), text(env, spd), text(env, spdMain), text(env, area),
                                                text(env, tail));
    });
}

AT_GPU(jlong, storageBufferCreate)(JNIEnv *env, jclass, jstring name, jlong size, jlong content, jlong contentSize) {
    return jvm::handled(env, [&]() -> jlong {
        std::string label = text(env, name);
        return object(encoder::createStorageBuffer(label.c_str(), static_cast<u64>(size), pointer<const u8>(content),
                                                   static_cast<u64>(contentSize)));
    });
}

AT_GPU(jint, formatFromVulkan)(JNIEnv *, jclass, jint format) {
    return gpuFormat(static_cast<VkFormat>(format));
}

AT_GPU(jlong, programCreate)(JNIEnv *env, jclass, jlong description) {
    return jvm::handled(env, [&]() -> jlong { return object(new Program(pointer<const u8>(description))); });
}

AT_GPU(void, programRelease)(JNIEnv *env, jclass, jlong program) {
    jvm::handled(env, [&] { pointer<Program>(program)->release(); });
}

AT_GPU(void, passUseProgram)(JNIEnv *env, jclass, jlong program) {
    jvm::handled(env, [&] { Backend::get().programs->use(pointer<Program>(program)); });
}

AT_GPU(void, passSetSampler)(JNIEnv *env, jclass, jint index, jlong view, jlong sampler) {
    jvm::handled(env, [&] {
        ProgramState::Resource resource;
        resource.type = resource_type::SAMPLER;
        auto *textureView = pointer<TextureView>(view);
        resource.view = textureView->handle;
        resource.layout = textureView->layout;
        resource.sampler = pointer<Sampler>(sampler)->handle;
        Backend::get().programs->setResource(static_cast<u32>(index), resource);
    });
}

AT_GPU(void, passSetUniformBuffer)(JNIEnv *env, jclass, jint index, jlong buffer, jlong offset, jlong length) {
    jvm::handled(env, [&] {
        ProgramState::Resource resource;
        resource.type = resource_type::UNIFORM_BUFFER;
        resource.buffer = pointer<Buffer>(buffer)->handle;
        resource.offset = static_cast<u64>(offset);
        resource.length = static_cast<u64>(length);
        Backend::get().programs->setResource(static_cast<u32>(index), resource);
    });
}

AT_GPU(void, passSetTexelBuffer)(JNIEnv *env, jclass, jint index, jlong buffer, jlong offset, jlong length, jint format) {
    jvm::handled(env, [&] {
        ProgramState::Resource resource;
        resource.type = resource_type::TEXEL_BUFFER;
        resource.texel = encoder::texelView(pointer<Buffer>(buffer), static_cast<u64>(offset), static_cast<u64>(length),
                                            vulkanFormat(static_cast<u32>(format)));
        Backend::get().programs->setResource(static_cast<u32>(index), resource);
    });
}

AT_GPU(void, passPushConstants)(JNIEnv *env, jclass, jlong data, jint size) {
    jvm::handled(env, [&] {
        Backend &backend = Backend::get();
        backend.programs->pushConstants(backend.commands(), pointer<void>(data), static_cast<u32>(size));
    });
}

AT_GPU(void, copyBuffer)(JNIEnv *env, jclass, jlong source, jlong sourceOffset, jlong destination, jlong destinationOffset,
                         jlong size) {
    jvm::handled(env, [&] {
        encoder::copyBuffer(pointer<Buffer>(source), static_cast<u64>(sourceOffset), pointer<Buffer>(destination),
                            static_cast<u64>(destinationOffset), static_cast<u64>(size));
    });
}

AT_GPU(void, copyTextureToBuffer)(JNIEnv *env, jclass, jlong source, jlong destination, jlong offset, jint level, jint x, jint y,
                                  jint width, jint height) {
    jvm::handled(env, [&] {
        encoder::copyTextureToBuffer(pointer<backend::Texture>(source), pointer<Buffer>(destination), static_cast<u64>(offset),
                                     static_cast<u32>(level), x, y, static_cast<u32>(width), static_cast<u32>(height));
    });
}

AT_GPU(jlong, queryPoolCreate)(JNIEnv *env, jclass, jint size) {
    return jvm::handled(env, [&]() -> jlong {
        Backend &backend = Backend::get();
        if (backend.device.timestampMask == 0) fail("this device has no GPU timestamps");
        VkQueryPoolCreateInfo info{.sType = VK_STRUCTURE_TYPE_QUERY_POOL_CREATE_INFO};
        info.queryType = VK_QUERY_TYPE_TIMESTAMP;
        info.queryCount = static_cast<u32>(size);
        auto *pool = new QueryPool{VK_NULL_HANDLE, static_cast<u32>(size)};
        check(vkCreateQueryPool(backend.device.handle, &info, nullptr, &pool->handle), "vkCreateQueryPool");
        vkCmdResetQueryPool(backend.setupCommands(), pool->handle, 0, pool->size);
        return object(pool);
    });
}

AT_GPU(void, queryPoolRelease)(JNIEnv *env, jclass, jlong handle) {
    jvm::handled(env, [&] {
        auto *pool = pointer<QueryPool>(handle);
        VkDevice device = Backend::get().device.handle;
        Backend::get().defer([device, pool] {
            vkDestroyQueryPool(device, pool->handle, nullptr);
            delete pool;
        });
    });
}

AT_GPU(void, writeTimestamp)(JNIEnv *env, jclass, jlong handle, jint index) {
    jvm::handled(env, [&] {
        auto *pool = pointer<QueryPool>(handle);
        if (static_cast<u32>(index) >= pool->size) fail("timestamp query index out of range");
        vkCmdWriteTimestamp(Backend::get().commands(), VK_PIPELINE_STAGE_BOTTOM_OF_PIPE_BIT, pool->handle, static_cast<u32>(index));
    });
}

AT_GPU(jlong, queryValue)(JNIEnv *env, jclass, jlong handle, jint index) {
    return jvm::handled(env, [&]() -> jlong {
        Backend &backend = Backend::get();
        auto *pool = pointer<QueryPool>(handle);
        if (static_cast<u32>(index) >= pool->size) fail("timestamp query index out of range");
        u64 values[2] = {};
        VkResult result = vkGetQueryPoolResults(backend.device.handle, pool->handle, static_cast<u32>(index), 1, sizeof(values),
                                                values, sizeof(values), VK_QUERY_RESULT_64_BIT | VK_QUERY_RESULT_WITH_AVAILABILITY_BIT);
        if (result != VK_SUCCESS || values[1] == 0) return -1;
        vkCmdResetQueryPool(backend.setupCommands(), pool->handle, static_cast<u32>(index), 1);
        u64 ticks = values[0] & backend.device.timestampMask;
        return static_cast<jlong>(static_cast<double>(ticks) * backend.device.timestampPeriod);
    });
}
