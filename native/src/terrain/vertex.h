#pragma once

#include "core/common.h"

#include <cmath>

namespace at::terrain {
    struct Vertex {
        u32 a, b, c, d;
    };

    static_assert(sizeof(Vertex) == 16);

    AT_INLINE u32 quantizePosition(float value) {
        return static_cast<u32>(clamp((value + 8.0f) * 1024.0f + 0.5f, 0.0f, 32767.0f));
    }

    AT_INLINE u32 quantizeUv(float value, float center) {
        auto quantized = static_cast<i32>(value * 16384.0f + 0.5f);
        bool high = value >= center;
        quantized += high ? -1 : 1;
        quantized = quantized < 0 ? 0 : quantized > 16383 ? 16383 : quantized;
        return static_cast<u32>(quantized) | (high ? 1u << 14 : 0u);
    }

    AT_INLINE u32 quantizeLight(u32 value) {
        return std::min((value + 2) >> 2, 63u);
    }

    AT_INLINE Vertex packVertex(float x, float y, float z, float u, float v, float centerU, float centerV, u32 color,
                                u32 blockLight, u32 skyLight) {
        Vertex vertex{};
        vertex.a = quantizePosition(x) | quantizePosition(y) << 15;
        vertex.b = quantizePosition(z) | quantizeUv(u, centerU) << 15;
        vertex.c = quantizeUv(v, centerV) | quantizeLight(blockLight) << 15 | quantizeLight(skyLight) << 21;
        vertex.d = color & 0xFFFFFFu;
        return vertex;
    }

    AT_INLINE void setSlot(Vertex &vertex, u32 slot) {
        vertex.a = (vertex.a & 0x3FFFFFFFu) | (slot & 3u) << 30;
        vertex.b = (vertex.b & 0x3FFFFFFFu) | ((slot >> 2) & 3u) << 30;
        vertex.c = (vertex.c & 0x07FFFFFFu) | ((slot >> 4) & 31u) << 27;
        vertex.d = (vertex.d & 0x00FFFFFFu) | ((slot >> 9) & 0xFFu) << 24;
    }
} // namespace at::terrain
