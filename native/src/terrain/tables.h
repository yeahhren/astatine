#pragma once

#include "core/common.h"

#include <memory>
#include <vector>

namespace at::terrain {
    constexpr u32 STATES = 65536;
    constexpr u32 LAYERS = 4;
    constexpr u32 TRANSLUCENT = 3;
    constexpr u32 BUCKETS = 7;
    constexpr u32 UNASSIGNED = 6;

    enum BlockFlag : u32 {
        OPAQUE = 1,
        FULL_CUBE = 2,
        AO_BLOCKING = 4,
        TRANSLUCENT_BLOCK = 8,
        NEIGHBOR_LIGHT = 16,
        SLAB = 32,
        AIR = 64,
        SOLID_MATERIAL = 128,
    };

    enum RenderType : u8 { INVISIBLE = 0, LIQUID = 1, ENTITY = 2, MODEL = 3 };

    enum Liquid : u8 { NO_LIQUID = 0, WATER = 1, LAVA = 2 };

    struct BlockInfo {
        u32 flags;
        u32 model;
        u8 light;
        u8 layer;
        u8 renderType;
        u8 offsetType;
        u8 insetFaces;
        u8 liquid;
        u8 level;
        u8 pad;
    };

    static_assert(sizeof(BlockInfo) == 16);

    struct Quad {
        float position[4][3];
        float uv[4][2];
        u32 color[4];
        float min[3];
        float max[3];
        u8 face;
        i8 tint;
        u8 bucket;
        u8 pad;
    };

    struct Model {
        u32 begin[8];
        bool ambientOcclusion;
        bool weighted;
        u32 entryBegin;
        u32 entryCount;
        i32 totalWeight;
    };

    struct WeightedEntry {
        u32 model;
        i32 weight;
    };

    struct Sprite {
        float minU, maxU, minV, maxV;

        [[nodiscard]] float u(float delta) const { return minU + (maxU - minU) * delta / 16.0f; }
        [[nodiscard]] float v(float delta) const { return minV + (maxV - minV) * (delta / 16.0f); }
    };

    struct Tables {
        std::vector<BlockInfo> blocks;
        std::vector<Model> models;
        std::vector<Quad> quads;
        std::vector<WeightedEntry> entries;
        Sprite water[2];
        Sprite lava[2];
        bool ambientOcclusion = true;
        bool alternatives = true;
        bool sky = true;

        [[nodiscard]] const BlockInfo &block(u16 state) const { return blocks[state]; }

        void loadModels(const i32 *data, std::size_t words);
    };
} // namespace at::terrain
