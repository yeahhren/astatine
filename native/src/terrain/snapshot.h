#pragma once

#include "core/common.h"

#include <cstddef>
#include <algorithm>
#include <memory>

namespace at::terrain {
    struct SectionClone {
        u16 states[4096];
        u8 blockLight[2048];
        u8 skyLight[2048];
        bool sky;
    };

    struct alignas(64) Snapshot {
        static constexpr u32 SKY = 1;
        static constexpr u32 URGENT = 2;
        static constexpr u32 SECTIONS = 27;
        static constexpr u32 COLUMNS = 9;

        static constexpr u8 MODEL = 1;
        static constexpr u8 FACES = 2;
        static constexpr u8 TINT = 4;
        static constexpr u8 FLOW = 8;
        static constexpr u8 SKIP = 16;

        i32 sectionX;
        i32 sectionY;
        i32 sectionZ;
        u32 version;
        u32 flags;
        u32 presentMask;
        u32 skyMask;
        u32 loadedMask;
        u64 sequence;
        u8 header[24];

        u16 states[SECTIONS][4096];
        u8 blockLight[SECTIONS][2048];
        u8 skyLight[SECTIONS][2048];
        i32 heightMaps[COLUMNS][256];
        u8 overrides[4096];
        u8 faces[4096];
        u32 models[4096];
        u32 tints[4096];
        float flows[4096];
        std::shared_ptr<const SectionClone> clones[SECTIONS];

        void copyClones() {
            for (u32 slot = 0; slot < SECTIONS; slot++) {
                const std::shared_ptr<const SectionClone> &clone = clones[slot];
                if (!clone) continue;
                std::copy_n(clone->states, 4096, states[slot]);
                std::copy_n(clone->blockLight, 2048, blockLight[slot]);
                if (clone->sky) std::copy_n(clone->skyLight, 2048, skyLight[slot]);
                clones[slot].reset();
            }
        }

        void dropClones() {
            for (auto &clone: clones) clone.reset();
        }
    };

    static_assert(offsetof(Snapshot, states) == 64);
    static_assert(offsetof(Snapshot, blockLight) == 221248);
    static_assert(offsetof(Snapshot, skyLight) == 276544);
    static_assert(offsetof(Snapshot, heightMaps) == 331840);
    static_assert(offsetof(Snapshot, overrides) == 341056);
    static_assert(offsetof(Snapshot, faces) == 345152);
    static_assert(offsetof(Snapshot, models) == 349248);
    static_assert(offsetof(Snapshot, tints) == 365632);
    static_assert(offsetof(Snapshot, flows) == 382016);

} // namespace at::terrain
