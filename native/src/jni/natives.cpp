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
    return handle(Renderer::get().streamBase());
}

AT_EXPORT(void, flush)(JNIEnv *env, jclass) {
    jvm::handled(env, [&] {
        if (Renderer::ready()) Renderer::get().drain();
    });
}

AT_EXPORT(void, present)(JNIEnv *env, jclass) {
    jvm::handled(env, [&] {
        RENDERER();
        renderer.present();
    });
}

AT_EXPORT(void, setVsync)(JNIEnv *env, jclass, jboolean enabled) {
    jvm::handled(env, [&] { Renderer::get().setVsync(enabled); });
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
        RENDERER();
        renderer.draw(static_cast<u32>(layout), mode, pointer<const u8>(source), static_cast<u32>(count));
    });
}

AT_EXPORT(void, drawMesh)(JNIEnv *env, jclass, jlong mesh, jint mode) {
    jvm::handled(env, [&] {
        RENDERER();
        renderer.drawMesh(pointer<Mesh>(mesh), mode);
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
        RENDERER();
        auto *target = pointer<Texture>(texture);
        u32 levels = static_cast<u32>(std::max(target->maxLevel, 0)) + 1;
        renderer.allocateTexture(target, std::min(levels, 17u), static_cast<u32>(width), static_cast<u32>(height));
    });
}

AT_EXPORT(void, textureUpload)(JNIEnv *env, jclass, jlong texture, jint level, jint x, jint y, jint width, jint height,
                               jlong source) {
    jvm::handled(env, [&] {
        RENDERER();
        renderer.uploadTexture(pointer<Texture>(texture), static_cast<u32>(level), x, y, static_cast<u32>(width),
                               static_cast<u32>(height),
                               pointer<const u8>(source), static_cast<u32>(width));
    });
}

AT_EXPORT(void, textureAllocateActive)(JNIEnv *env, jclass, jint width, jint height) {
    jvm::handled(env, [&] {
        RENDERER();
        Texture *target = renderer.state.activeBoundTexture();
        if (!target) return;
        u32 levels = static_cast<u32>(std::max(target->maxLevel, 0)) + 1;
        renderer.allocateTexture(target, std::min(levels, 17u), static_cast<u32>(width), static_cast<u32>(height));
    });
}

AT_EXPORT(void, textureUploadActive)(JNIEnv *env, jclass, jint level, jint x, jint y, jint width, jint height,
                                     jlong source) {
    jvm::handled(env, [&] {
        RENDERER();
        Texture *target = renderer.state.activeBoundTexture();
        if (!target) return;
        renderer.uploadTexture(target, static_cast<u32>(level), x, y, static_cast<u32>(width), static_cast<u32>(height),
                               pointer<const u8>(source), static_cast<u32>(width));
    });
}

AT_EXPORT(void, textureDetach)(JNIEnv *env, jclass, jlong texture) {
    jvm::handled(env, [&] {
        if (!Renderer::ready()) return;
        RENDERER();
        pointer<Texture>(texture)->detach();
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
        RENDERER();
        if (Texture *texture = renderer.state.activeBoundTexture()) {
            renderer.copyFromTarget(texture, static_cast<u32>(level), destinationX, destinationY, x, y,
                                    static_cast<u32>(width),
                                    static_cast<u32>(height));
        }
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
        RENDERER();
        renderer.releaseFramebuffer(pointer<Framebuffer>(framebuffer));
    });
}

AT_EXPORT(jboolean, framebufferBlit)(JNIEnv *env, jclass, jlong framebuffer, jint width, jint height) {
    return jvm::handled(env, [&]() -> jboolean {
        RENDERER();
        return renderer.blitToSurface(pointer<Framebuffer>(framebuffer), static_cast<u32>(width),
                                      static_cast<u32>(height));
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
