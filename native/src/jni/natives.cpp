#include "jni/jvm.h"

#include "core/error.h"
#include "render/commands.h"
#include "render/geometry.h"
#include "render/renderer.h"
#include "render/targets.h"
#include "render/texture.h"
#include "vulkan/device.h"

#include <algorithm>

using namespace at;

#define RENDERER()            \
    Renderer& renderer = Renderer::get(); \
    renderer.drain()

AT_EXPORT(void, initialize)(JNIEnv *env, jclass, jlong window, jlong getInstanceExtensions, jlong createSurface,
                            jlong destroySurface,
                            jlong getInstanceProcAddr, jlong windowSize, jlong nativeWindow, jboolean validation,
                            jstring cacheFile) {
    jvm::handled(env, [&] {
        WindowSystem system{};
        system.window = pointer<void>(window);
        system.getInstanceExtensions = reinterpret_cast<decltype(system.getInstanceExtensions)>(getInstanceExtensions);
        system.createSurface = reinterpret_cast<decltype(system.createSurface)>(createSurface);
        system.destroySurface = reinterpret_cast<decltype(system.destroySurface)>(destroySurface);
        system.getInstanceProcAddr = reinterpret_cast<decltype(system.getInstanceProcAddr)>(getInstanceProcAddr);
        system.windowSize = reinterpret_cast<WindowSizeFunction>(windowSize);
        system.nativeWindow = pointer<void>(nativeWindow);
        Renderer::initialize(system, validation, jvm::string(env, cacheFile));
    });
}

AT_EXPORT(void, shutdown)(JNIEnv *env, jclass) {
    jvm::handled(env, [&] { Renderer::shutdown(); });
}

AT_EXPORT(jlong, streamAddress)(JNIEnv *
                                ,
                                jclass) {
    return handle(Renderer::get().streamCell());
}

AT_EXPORT(void, flush)(JNIEnv *env, jclass) {
    jvm::handled(env, [&] {
        if (Renderer::ready()) Renderer::get().publish();
    });
}

AT_EXPORT(void, present)(JNIEnv *env, jclass) {
    jvm::handled(env, [&] {
        Renderer &renderer = Renderer::get();
        renderer.command(op::PRESENT, op::Flag{0, 0});
        renderer.publish();
    });
}

AT_EXPORT(void, setVsync)(JNIEnv *env, jclass, jboolean enabled) {
    jvm::handled(env, [&] { Renderer::get().command(op::SET_VSYNC, op::Flag{enabled ? 1u : 0u, 0}); });
}

AT_EXPORT(jstring, deviceName)(JNIEnv *env
                               ,
                               jclass) {
    return env->NewStringUTF(Renderer::get().context().device->name.c_str());
}

AT_EXPORT(jint, vendorId)(JNIEnv *
                          ,
                          jclass) {
    return static_cast<jint>(Renderer::get().context().device->vendorId);
}

AT_EXPORT(jint, apiVersion)(JNIEnv *
                            ,
                            jclass) {
    return static_cast<jint>(Renderer::get().context().device->apiVersion);
}

AT_EXPORT(jint, maxTextureSize)(JNIEnv *
                                ,
                                jclass) {
    return static_cast<jint>(Renderer::get().context().device->maxImageDimension);
}

AT_EXPORT(jint, driverVersion)(JNIEnv *, jclass) {
    return static_cast<jint>(Renderer::get().context().device->driverVersion);
}

AT_EXPORT(jboolean, gpuTimestamps)(JNIEnv *, jclass) {
    return Renderer::get().context().device->timestampMask != 0;
}

AT_EXPORT(jlong, gpuTime)(JNIEnv *, jclass) {
    return static_cast<jlong>(Renderer::get().context().device->gpuNanos);
}

AT_EXPORT(jint, registerLayout)(JNIEnv *env
                                ,
                                jclass,
                                jlong elements, jint count, jint vertexSize
) {
    return jvm::handled(env, [&]() -> jint {
        return static_cast<jint>(Renderer::get().layouts.registerFormat(pointer<const i32>(elements),
                                                                        static_cast<u32>(count),
                                                                        static_cast<u32>(vertexSize)));
    });
}

AT_EXPORT(void, draw)(JNIEnv *env, jclass, jint layout, jint mode, jlong source, jint count) {
    jvm::handled(env, [&] {
        Renderer &renderer = Renderer::get();
        u32 bytes = static_cast<u32>(count) * renderer.layouts[static_cast<u32>(layout)].stride;
        renderer.command(op::DRAW, op::Draw{static_cast<u32>(layout), mode, static_cast<u32>(count), bytes},
                         pointer<const u8>(source), bytes);
    });
}

AT_EXPORT(void, drawMesh)(JNIEnv *env, jclass, jlong mesh, jint mode) {
    jvm::handled(env, [&] {
        Renderer::get().command(op::DRAW_MESH, op::DrawMesh{static_cast<u64>(mesh), mode, 0});
    });
}

AT_EXPORT(jlong, uploadMesh)(JNIEnv *env
                             ,
                             jclass,
                             jint layout, jlong source, jint count
) {
    return jvm::handled(env, [&]() -> jlong {
        RENDERER();
        return handle(renderer.uploadMesh(static_cast<u32>(layout), pointer<const u8>(source),
                                          static_cast<u32>(count)));
    });
}

AT_EXPORT(jlong, textureCreate)(JNIEnv *
                                ,
                                jclass) {
    return handle(new Texture());
}

AT_EXPORT(void, textureAllocate)(JNIEnv *env, jclass, jlong texture, jint width, jint height) {
    jvm::handled(env, [&] {
        if (!texture) return;
        Renderer::get().command(op::TEXTURE_ALLOCATE, op::TextureAllocate{
                                    static_cast<u64>(texture), static_cast<u32>(width), static_cast<u32>(height)
                                });
    });
}

AT_EXPORT(void, textureUpload)(JNIEnv *env, jclass, jlong texture, jint level, jint x, jint y, jint width, jint height,
                               jlong source) {
    jvm::handled(env, [&] {
        if (!texture) return;
        u32 bytes = static_cast<u32>(width) * static_cast<u32>(height) * 4;
        Renderer::get().command(op::TEXTURE_UPLOAD, op::TextureUpload{
                                    static_cast<u64>(texture), static_cast<u32>(level), x, y, static_cast<u32>(width),
                                    static_cast<u32>(height), 0
                                }, pointer<const u8>(source), bytes);
    });
}

AT_EXPORT(void, textureAllocateActive)(JNIEnv *env, jclass, jint width, jint height) {
    jvm::handled(env, [&] {
        Renderer::get().command(op::TEXTURE_ALLOCATE, op::TextureAllocate{
                                    0, static_cast<u32>(width), static_cast<u32>(height)
                                });
    });
}

AT_EXPORT(void, textureUploadActive)(JNIEnv *env, jclass, jint level, jint x, jint y, jint width, jint height,
                                     jlong source) {
    jvm::handled(env, [&] {
        u32 bytes = static_cast<u32>(width) * static_cast<u32>(height) * 4;
        Renderer::get().command(op::TEXTURE_UPLOAD, op::TextureUpload{
                                    0, static_cast<u32>(level), x, y, static_cast<u32>(width),
                                    static_cast<u32>(height), 0
                                }, pointer<const u8>(source), bytes);
    });
}

AT_EXPORT(void, textureDetach)(JNIEnv *env, jclass, jlong texture) {
    jvm::handled(env, [&] {
        if (!Renderer::ready()) return;
        Renderer::get().command(op::TEXTURE_DETACH, op::Handle{static_cast<u64>(texture)});
    });
}

AT_EXPORT(jint, textureWidth)(JNIEnv *
                              ,
                              jclass,
                              jlong texture
) {
    return static_cast<jint>(pointer<Texture>(texture)->width());
}

AT_EXPORT(jint, textureHeight)(JNIEnv *
                               ,
                               jclass,
                               jlong texture
) {
    return static_cast<jint>(pointer<Texture>(texture)->height());
}

AT_EXPORT(jlong, activeTexture)(JNIEnv *env
                                ,
                                jclass) {
    return jvm::handled(env, [&]() -> jlong {
        RENDERER();
        return handle(renderer.state.activeBoundTexture());
    });
}

AT_EXPORT(void, readTexture)(JNIEnv *env, jclass, jlong texture, jlong destination) {
    jvm::handled(env, [&] {
        RENDERER();
        renderer.read(pointer<Texture>(texture), pointer<u8>(destination));
    });
}

AT_EXPORT(void, copyFromTarget)(JNIEnv *env, jclass, jint level, jint destinationX, jint destinationY, jint x, jint y,
                                jint width,
                                jint height) {
    jvm::handled(env, [&] {
        Renderer::get().command(op::COPY_FROM_TARGET, op::CopyFromTarget{
                                    static_cast<u32>(level), destinationX, destinationY, x, y,
                                    static_cast<u32>(width), static_cast<u32>(height), 0
                                });
    });
}

AT_EXPORT(jlong, framebufferCreate)(JNIEnv *env
                                    ,
                                    jclass,
                                    jint width, jint height, jboolean depth
) {
    return jvm::handled(env, [&]() -> jlong {
        RENDERER();
        return handle(renderer.createFramebuffer(static_cast<u32>(width), static_cast<u32>(height), depth));
    });
}

AT_EXPORT(void, framebufferRelease)(JNIEnv *env, jclass, jlong framebuffer) {
    jvm::handled(env, [&] {
        if (!Renderer::ready()) return;
        Renderer::get().command(op::FRAMEBUFFER_RELEASE, op::Handle{static_cast<u64>(framebuffer)});
    });
}

AT_EXPORT(jboolean, framebufferBlit)(JNIEnv *env, jclass, jlong framebuffer, jint width, jint height) {
    return jvm::handled(env, [&]() -> jboolean {
        Renderer &renderer = Renderer::get();
        if (!framebuffer || !renderer.canBlitSurface()) return static_cast<jboolean>(false);
        renderer.command(op::FRAMEBUFFER_BLIT, op::Blit{
                             static_cast<u64>(framebuffer), static_cast<u32>(width), static_cast<u32>(height)
                         });
        return static_cast<jboolean>(true);
    });
}

AT_EXPORT(jlong, framebufferTexture)(JNIEnv *
                                     ,
                                     jclass,
                                     jlong framebuffer
) {
    return handle(&pointer<Framebuffer>(framebuffer)->texture);
}

AT_EXPORT(void, getMatrix)(JNIEnv *env, jclass, jint name, jlong destination) {
    jvm::handled(env, [&] {
        RENDERER();
        RenderState &state = renderer.state;
        const float *matrix = nullptr;
        switch (name) {
            case 0x0BA6: matrix = state.modelView.top();
                break;
            case 0x0BA7: matrix = state.projection.top();
                break;
            case 0x0BA8: matrix = state.texture(state.activeUnit).top();
                break;
            default: return;
        }
        std::memcpy(pointer<void>(destination), matrix, 64);
    });
}

AT_EXPORT(void, getViewport)(JNIEnv *env, jclass, jlong destination) {
    jvm::handled(env, [&] {
        RENDERER();
        RenderState &state = renderer.state;
        i32 values[4] = {state.viewportX, state.viewportY, state.viewportWidth, state.viewportHeight};
        std::memcpy(pointer<void>(destination), values, sizeof(values));
    });
}
