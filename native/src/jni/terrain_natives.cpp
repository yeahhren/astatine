#include "jni/jvm.h"

#include "core/error.h"
#include "render/commands.h"
#include "render/renderer.h"
#include "terrain/snapshot.h"
#include "terrain/tables.h"
#include "terrain/terrain.h"

#include <algorithm>
#include <cstring>

using namespace at;
using namespace at::terrain;

static constexpr u32 SCHEDULE_CAP = 64;

static Terrain &exclusiveTerrain() {
    Renderer &renderer = Renderer::get();
    renderer.waitIdle();
    return *renderer.terrain;
}

AT_EXPORT(void, terrainTables)(JNIEnv *env, jclass, jlong blocks, jlong models, jint modelWords, jlong sprites,
                               jboolean ambientOcclusion,
                               jboolean alternatives, jboolean sky, jlong packIds, jfloat ambientOcclusionLevel,
                               jboolean directionalShading, jboolean separateAo) {
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
        tables->ambientShade = 1.0f - std::clamp(static_cast<float>(ambientOcclusionLevel), 0.0f, 1.0f) * 0.8f;
        tables->directionalShading = directionalShading;
        tables->separateAo = separateAo;
        if (!directionalShading) tables->removeDirectionalShading();
        if (packIds) {
            const auto *ids = pointer<const i16>(packIds);
            tables->packIds.assign(ids, ids + STATES);
            tables->vertexUnits = EXTENDED_UNITS;
        }
        Renderer::get().terrain->setTables(std::move(tables));
    });
}

AT_EXPORT(void, terrainConfigure)(JNIEnv *env, jclass, jint viewDistance) {
    jvm::handled(env, [&] { exclusiveTerrain().configure(viewDistance); });
}

AT_EXPORT(void, terrainClear)(JNIEnv *env, jclass) {
    jvm::handled(env, [&] {
        if (Renderer::ready()) exclusiveTerrain().clear();
    });
}

AT_EXPORT(void, terrainMarkDirty)(JNIEnv *env, jclass, jint minX, jint minY, jint minZ, jint maxX, jint maxY, jint maxZ,
                                  jboolean important) {
    jvm::handled(env, [&] {
        Renderer &renderer = Renderer::get();
        renderer.terrain->invalidateClones(minX, minY, minZ, maxX, maxY, maxZ);
        u64 sequence = renderer.terrain->nextDirtySequence();
        renderer.command(op::TERRAIN_MARK_DIRTY, op::TerrainBox{
                             minX, minY, minZ, maxX, maxY, maxZ, important ? 1u : 0u, 0, sequence
                         });
    });
}

AT_EXPORT(void, terrainSetup)(JNIEnv *env, jclass, jdouble x, jdouble y, jdouble z, jdouble eyeHeight, jint frame,
                              jint lookFace,
                              jboolean smartCull, jboolean spectator, jboolean cameraInOpaque, jboolean frustumCull) {
    jvm::handled(env, [&] {
        u32 flags = (smartCull ? 1u : 0u) | (spectator ? 2u : 0u) | (cameraInOpaque ? 4u : 0u) | (frustumCull ? 0u : 8u);
        Renderer::get().command(op::TERRAIN_SETUP, op::TerrainSetup{
                                    x, y, z, eyeHeight, frame, lookFace, flags, SCHEDULE_CAP
                                });
    });
}

AT_EXPORT(jlong, terrainDirtySequence)(JNIEnv *, jclass) {
    return static_cast<jlong>(Renderer::get().terrain->dirtySequence());
}

AT_EXPORT(jint, terrainVisibleCount)(JNIEnv *
                                     ,
                                     jclass) {
    return static_cast<jint>(Renderer::get().terrain->takeVisible());
}

AT_EXPORT(jlong, terrainVisibleAddress)(JNIEnv *
                                        ,
                                        jclass) {
    return handle(Renderer::get().terrain->visibleData());
}

AT_EXPORT(jint, terrainSchedule)(JNIEnv *env
                                 ,
                                 jclass,
                                 jlong out, jint max
) {
    return jvm::handled(env, [&]() -> jint {
        return static_cast<jint>(Renderer::get().terrain->takeSchedule(pointer<i32>(out), static_cast<u32>(max)));
    });
}

AT_EXPORT(void, terrainAbort)(JNIEnv *env, jclass, jint x, jint y, jint z, jint version) {
    jvm::handled(env, [&] {
        Renderer::get().command(op::TERRAIN_ABORT, op::TerrainAbort{x, y, z, static_cast<u32>(version)});
    });
}

AT_EXPORT(jlong, terrainSnapshot)(JNIEnv *env
                                  ,
                                  jclass) {
    return jvm::handled(env, [&]() -> jlong { return handle(Renderer::get().terrain->acquireSnapshot()); });
}

AT_EXPORT(jint, terrainSnapshotClones)(JNIEnv *env, jclass, jlong snapshot, jint x, jint y, jint z, jint present) {
    return jvm::handled(env, [&]() -> jint {
        return static_cast<jint>(Renderer::get().terrain->attachClones(pointer<Snapshot>(snapshot), x, y, z,
                                                                       static_cast<u32>(present)));
    });
}

AT_EXPORT(void, terrainSnapshotCommit)(JNIEnv *env, jclass, jlong snapshot, jint copied, jint sky) {
    jvm::handled(env, [&] {
        Renderer::get().terrain->commitClones(pointer<Snapshot>(snapshot), static_cast<u32>(copied),
                                              static_cast<u32>(sky));
    });
}

AT_EXPORT(void, terrainSnapshotCopyClones)(JNIEnv *env, jclass, jlong snapshot) {
    jvm::handled(env, [&] { pointer<Snapshot>(snapshot)->copyClones(); });
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
                               jlong budget, jlong budgetNanos
) {
    return jvm::handled(env, [&]() -> jint {
        Renderer::get().command(op::TERRAIN_UPLOAD, op::TerrainUpload{
                                    static_cast<u64>(budget), static_cast<u64>(budgetNanos)
                                });
        return 0;
    });
}

AT_EXPORT(jint, terrainSort)(JNIEnv *env
                             ,
                             jclass,
                             jint limit
) {
    return jvm::handled(env, [&]() -> jint {
        Renderer::get().command(op::TERRAIN_SORT, op::Flag{static_cast<u32>(limit), 0});
        return 0;
    });
}

AT_EXPORT(jint, terrainDraw)(JNIEnv *env
                             ,
                             jclass,
                             jint layer
) {
    return jvm::handled(env, [&]() -> jint {
        Renderer &renderer = Renderer::get();
        u32 index = static_cast<u32>(layer) & 3;
        renderer.command(op::TERRAIN_DRAW, op::Flag{index, 0});
        return static_cast<jint>(renderer.terrain->drawn(index));
    });
}

AT_EXPORT(void, terrainShadowView)(JNIEnv *env, jclass, jlong view, jdouble x, jdouble y, jdouble z) {
    jvm::handled(env, [&] {
        op::TerrainShadowView command{};
        std::memcpy(command.modelView, pointer<const float>(view), sizeof(command.modelView));
        std::memcpy(command.projection, pointer<const float>(view) + 16, sizeof(command.projection));
        std::memcpy(&command.culling, pointer<const u8>(view) + 128, sizeof(command.culling));
        command.culling.planeCount = std::min<u32>(command.culling.planeCount, 13);
        command.camera[0] = x;
        command.camera[1] = y;
        command.camera[2] = z;
        Renderer::get().command(op::TERRAIN_SHADOW_VIEW, command);
    });
}

AT_EXPORT(void, terrainShadowViewEnd)(JNIEnv *env, jclass) {
    jvm::handled(env, [&] { Renderer::get().command(op::TERRAIN_SHADOW_VIEW_END, op::Flag{0, 0}); });
}

AT_EXPORT(jstring, terrainDebug)(JNIEnv *env
                                 ,
                                 jclass) {
    std::string text = Renderer::get().terrain->debugText();
    return env->NewStringUTF(text.c_str());
}
