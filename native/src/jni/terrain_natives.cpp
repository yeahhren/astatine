#include "jni/jvm.h"

#include "core/error.h"
#include "render/renderer.h"
#include "terrain/snapshot.h"
#include "terrain/tables.h"
#include "terrain/terrain.h"

using namespace at;
using namespace at::terrain;

static Terrain &mainTerrain() {
    Renderer &renderer = Renderer::get();
    renderer.drain();
    return *renderer.terrain;
}

AT_EXPORT(void, terrainTables)(JNIEnv *env, jclass, jlong blocks, jlong models, jint modelWords, jlong sprites,
                               jboolean ambientOcclusion,
                               jboolean alternatives, jboolean sky) {
    jvm::handled(env, [&] {
        auto tables = std::make_shared<Tables>();
        const auto *source = pointer<const BlockInfo>(blocks);
        tables->blocks.assign(source, source + STATES);
        tables->loadModels(pointer<const i32>(models), static_cast<std::size_t>(modelWords));
        const auto *uv = pointer<const float>(sprites);
        Sprite *targets[4] = {&tables->water[0], &tables->water[1], &tables->lava[0], &tables->lava[1]};
        for (int i = 0; i < 4; i++) *targets[i] = {
                                        .minU = uv[i * 4], .maxU = uv[i * 4 + 1], .minV = uv[i * 4 + 2],
                                        .maxV = uv[i * 4 + 3]
                                    };
        tables->ambientOcclusion = ambientOcclusion;
        tables->alternatives = alternatives;
        tables->sky = sky;
        Renderer::get().terrain->setTables(std::move(tables));
    });
}

AT_EXPORT(void, terrainConfigure)(JNIEnv *env, jclass, jint viewDistance) {
    jvm::handled(env, [&] { mainTerrain().configure(viewDistance); });
}

AT_EXPORT(void, terrainClear)(JNIEnv *env, jclass) {
    jvm::handled(env, [&] {
        if (Renderer::ready()) mainTerrain().clear();
    });
}

AT_EXPORT(void, terrainMarkDirty)(JNIEnv *env, jclass, jint minX, jint minY, jint minZ, jint maxX, jint maxY, jint maxZ,
                                  jboolean important) {
    jvm::handled(env, [&] { Renderer::get().terrain->markDirty(minX, minY, minZ, maxX, maxY, maxZ, important); });
}

AT_EXPORT(void, terrainSetup)(JNIEnv *env, jclass, jdouble x, jdouble y, jdouble z, jdouble eyeHeight, jint frame,
                              jint lookFace,
                              jboolean smartCull, jboolean spectator, jboolean cameraInOpaque) {
    jvm::handled(env, [&] {
        SetupParameters parameters{
            .x = x, .y = y, .z = z, .eyeHeight = eyeHeight, .frame = frame, .lookFace = lookFace,
            .smartCull = smartCull != 0, .spectator = spectator != 0, .cameraInOpaque = cameraInOpaque != 0
        };
        mainTerrain().setup(parameters);
    });
}

AT_EXPORT(jint, terrainVisibleCount)(JNIEnv *
                                     ,
                                     jclass) {
    u32 count = 0;
    Renderer::get().terrain->visibleSections(count);
    return static_cast<jint>(count);
}

AT_EXPORT(jlong, terrainVisibleAddress)(JNIEnv *
                                        ,
                                        jclass) {
    u32 count = 0;
    return handle(Renderer::get().terrain->visibleSections(count));
}

AT_EXPORT(jint, terrainSchedule)(JNIEnv *env
                                 ,
                                 jclass,
                                 jlong out, jint max
) {
    return jvm::handled(env, [&]() -> jint {
        return static_cast<jint>(Renderer::get().terrain->schedule(pointer<i32>(out), static_cast<u32>(max)));
    });
}

AT_EXPORT(void, terrainAbort)(JNIEnv *env, jclass, jint x, jint y, jint z, jint version) {
    jvm::handled(env, [&] { Renderer::get().terrain->abort(x, y, z, static_cast<u32>(version)); });
}

AT_EXPORT(jlong, terrainSnapshot)(JNIEnv *env
                                  ,
                                  jclass) {
    return jvm::handled(env, [&]() -> jlong { return handle(Renderer::get().terrain->acquireSnapshot()); });
}

AT_EXPORT(void, terrainRelease)(JNIEnv *env, jclass, jlong snapshot) {
    jvm::handled(env, [&] { Renderer::get().terrain->releaseSnapshot(pointer<Snapshot>(snapshot)); });
}

AT_EXPORT(void, terrainBuild)(JNIEnv *env, jclass, jlong snapshot) {
    jvm::handled(env, [&] { Renderer::get().terrain->build(pointer<Snapshot>(snapshot)); });
}

AT_EXPORT(jint, terrainUpload)(JNIEnv *env
                               ,
                               jclass,
                               jlong budget
) {
    return jvm::handled(env, [&]() -> jint { return static_cast<jint>(mainTerrain().upload(static_cast<u64>(budget))); });
}

AT_EXPORT(jint, terrainSort)(JNIEnv *env
                             ,
                             jclass,
                             jint limit
) {
    return jvm::handled(env, [&]() -> jint {
        return static_cast<jint>(mainTerrain().sortTranslucent(static_cast<u32>(limit)));
    });
}

AT_EXPORT(jint, terrainDraw)(JNIEnv *env
                             ,
                             jclass,
                             jint layer
) {
    return jvm::handled(env, [&]() -> jint {
        return static_cast<jint>(mainTerrain().draw(static_cast<u32>(layer) & 3));
    });
}

AT_EXPORT(jstring, terrainDebug)(JNIEnv *env
                                 ,
                                 jclass) {
    std::string text = Renderer::get().terrain->debug();
    return env->NewStringUTF(text.c_str());
}
