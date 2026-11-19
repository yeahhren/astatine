#pragma once

#include "core/common.h"

namespace at {
    struct PrimitivePlan {
        enum Indexing { DIRECT = 0, QUADS = 1, GENERATED = 2 };

        u32 topology = 0;
        Indexing indexing = DIRECT;
        u32 vertexCount = 0;
        u32 indexCount = 0;
        i32 mode = 0;
        bool flat = false;

        bool plan(i32 drawMode, u32 count, bool flatShading, bool triangleFans);

        void generate(u32 *out) const;
    };
} // namespace at
