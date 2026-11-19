#include "render/primitives.h"

#include "core/vk.h"
#include "render/state.h"

namespace at {
    bool PrimitivePlan::plan(i32 drawMode, u32 count, bool flatShading, bool triangleFans) {
        mode = drawMode;
        flat = flatShading;
        indexing = flat ? GENERATED : DIRECT;
        switch (mode) {
            case gl::POINTS:
                topology = VK_PRIMITIVE_TOPOLOGY_POINT_LIST;
                vertexCount = count;
                indexing = DIRECT;
                break;
            case gl::LINES:
                topology = VK_PRIMITIVE_TOPOLOGY_LINE_LIST;
                vertexCount = count - count % 2;
                indexCount = vertexCount;
                break;
            case gl::LINE_LOOP:
                vertexCount = count < 2 ? 0 : count;
                topology = flat ? VK_PRIMITIVE_TOPOLOGY_LINE_LIST : VK_PRIMITIVE_TOPOLOGY_LINE_STRIP;
                indexCount = flat ? vertexCount * 2 : vertexCount + 1;
                indexing = GENERATED;
                break;
            case gl::LINE_STRIP:
                vertexCount = count < 2 ? 0 : count;
                topology = flat ? VK_PRIMITIVE_TOPOLOGY_LINE_LIST : VK_PRIMITIVE_TOPOLOGY_LINE_STRIP;
                indexCount = vertexCount > 0 ? (vertexCount - 1) * 2 : 0;
                break;
            case gl::TRIANGLES:
                topology = VK_PRIMITIVE_TOPOLOGY_TRIANGLE_LIST;
                vertexCount = count - count % 3;
                indexCount = vertexCount;
                break;
            case gl::TRIANGLE_STRIP:
                vertexCount = count < 3 ? 0 : count;
                topology = flat ? VK_PRIMITIVE_TOPOLOGY_TRIANGLE_LIST : VK_PRIMITIVE_TOPOLOGY_TRIANGLE_STRIP;
                indexCount = vertexCount > 0 ? (vertexCount - 2) * 3 : 0;
                break;
            case gl::TRIANGLE_FAN:
            case gl::POLYGON:
                vertexCount = count < 3 ? 0 : count;
                if (!triangleFans) indexing = GENERATED;
                topology = indexing == GENERATED ? VK_PRIMITIVE_TOPOLOGY_TRIANGLE_LIST : VK_PRIMITIVE_TOPOLOGY_TRIANGLE_FAN;
                indexCount = vertexCount > 0 ? (vertexCount - 2) * 3 : 0;
                break;
            case gl::QUADS:
                topology = VK_PRIMITIVE_TOPOLOGY_TRIANGLE_LIST;
                vertexCount = count - count % 4;
                indexCount = vertexCount / 4 * 6;
                indexing = QUADS;
                break;
            case gl::QUAD_STRIP:
                vertexCount = count < 4 ? 0 : count - count % 2;
                topology = flat ? VK_PRIMITIVE_TOPOLOGY_TRIANGLE_LIST : VK_PRIMITIVE_TOPOLOGY_TRIANGLE_STRIP;
                indexCount = vertexCount > 0 ? (vertexCount / 2 - 1) * 6 : 0;
                break;
            default:
                vertexCount = 0;
                break;
        }
        return vertexCount > 0;
    }

    void PrimitivePlan::generate(u32 *out) const {
        u32 n = vertexCount;
        switch (mode) {
            case gl::LINES:
                for (u32 i = 0; i < n; i += 2) {
                    *out++ = i + 1;
                    *out++ = i;
                }
                break;
            case gl::LINE_LOOP:
                if (flat) {
                    for (u32 i = 0; i + 1 < n; i++) {
                        *out++ = i + 1;
                        *out++ = i;
                    }
                    *out++ = 0;
                    *out++ = n - 1;
                } else {
                    for (u32 i = 0; i < n; i++) *out++ = i;
                    *out++ = 0;
                }
                break;
            case gl::LINE_STRIP:
                for (u32 i = 0; i + 1 < n; i++) {
                    *out++ = i + 1;
                    *out++ = i;
                }
                break;
            case gl::TRIANGLES:
                for (u32 i = 0; i < n; i += 3) {
                    *out++ = i + 2;
                    *out++ = i;
                    *out++ = i + 1;
                }
                break;
            case gl::TRIANGLE_STRIP:
                for (u32 i = 0; i + 2 < n; i++) {
                    *out++ = i + 2;
                    if (i % 2 == 0) {
                        *out++ = i;
                        *out++ = i + 1;
                    } else {
                        *out++ = i + 1;
                        *out++ = i;
                    }
                }
                break;
            case gl::TRIANGLE_FAN:
                for (u32 i = 0; i + 2 < n; i++) {
                    *out++ = i + 2;
                    *out++ = 0;
                    *out++ = i + 1;
                }
                break;
            case gl::POLYGON:
                for (u32 i = 0; i + 2 < n; i++) {
                    *out++ = 0;
                    *out++ = i + 1;
                    *out++ = i + 2;
                }
                break;
            case gl::QUAD_STRIP:
                for (u32 j = 0; j + 1 < n / 2; j++) {
                    u32 base = j * 2;
                    *out++ = base + 3;
                    *out++ = base;
                    *out++ = base + 1;
                    *out++ = base + 3;
                    *out++ = base + 2;
                    *out++ = base;
                }
                break;
            default:
                break;
        }
    }
} // namespace at
