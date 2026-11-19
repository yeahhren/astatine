#pragma once

#include "core/common.h"

namespace at {
    struct alignas(16) EnvironmentBlock {
        float projection[16];
        float textureMatrix0[16];
        float textureMatrix1[16];
        float lightPosition[2][4];
        float lightDiffuse[2][4];
        float sceneAmbient[4];
        float materialAmbient[4];
        float materialDiffuse[4];
        float materialEmission[4];
        float fogColor[4];
        float fogParams[4];
        float overlayColor[4];
        float texGenPlanes[4][4];
        i32 flags[4];
        float alphaReference[4];
    };

    static_assert(sizeof(EnvironmentBlock) == 464);

    struct alignas(16) DrawRecord {
        float modelView[16];
        float normalMatrix[3][4];
        float color[4];
        float normal[4];
        float texCoord0[4];
        float texCoord1[4];
        i32 textures[4];
        i32 ids[4];
        i32 pbr[4];
        i32 blend[4];
    };

    static_assert(sizeof(DrawRecord) == 240);

    struct SectionDraw {
        float offset[3];
        u32 record;
    };

    static_assert(sizeof(SectionDraw) == 16);

    namespace ff {
        constexpr i32 LIGHTING = 1 << 0;
        constexpr i32 LIGHT0 = 1 << 1;
        constexpr i32 COLOR_MATERIAL = 1 << 3;
        constexpr i32 COLOR_MATERIAL_SHIFT = 4;
        constexpr i32 FOG = 1 << 7;
        constexpr i32 FOG_RADIAL = 1 << 8;
        constexpr i32 ALPHA_TEST = 1 << 9;
        constexpr i32 OVERLAY = 1 << 10;
        constexpr i32 NORMALIZE = 1 << 11;
        constexpr i32 TEXGEN_S = 1 << 12;

        constexpr i32 TEXTURE0 = 1 << 0;
        constexpr i32 TEXTURE1 = 1 << 1;
        constexpr i32 TEXTURE0_CLAMP_SHIFT = 2;
        constexpr i32 TEXTURE1_CLAMP_SHIFT = 4;
    } // namespace ff
} // namespace at
