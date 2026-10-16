#pragma once

#include "core/common.h"

#include <cmath>

namespace at::terrain {
    struct Vertex {
        u16 x, y, z, flags;
        u32 color;
        u16 u, v;
        u8 blockLight;
        u8 skyLight;
        u16 pad;
    };

    static_assert(sizeof(Vertex) == 20);

    AT_INLINE u16 encodePosition(float value) {
        float scaled = (value + 8.0f) * 2048.0f + 0.5f;
        return static_cast<u16>(clamp(scaled, 0.0f, 65535.0f));
    }

    AT_INLINE u16 encodeUv(float value) {
        return static_cast<u16>(clamp(value * 65535.0f + 0.5f, 0.0f, 65535.0f));
    }
} // namespace at::terrain
