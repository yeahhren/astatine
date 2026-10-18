#include "terrain/tables.h"

#include "core/error.h"

#include <algorithm>
#include <cmath>

namespace at::terrain {
    static u8 bucketOf(const Quad &quad) {
        const float *a = quad.position[0];
        const float *b = quad.position[1];
        const float *c = quad.position[2];
        float e1[3] = {b[0] - a[0], b[1] - a[1], b[2] - a[2]};
        float e2[3] = {c[0] - a[0], c[1] - a[1], c[2] - a[2]};
        float n[3] = {e1[1] * e2[2] - e1[2] * e2[1], e1[2] * e2[0] - e1[0] * e2[2], e1[0] * e2[1] - e1[1] * e2[0]};
        float length = std::sqrt(n[0] * n[0] + n[1] * n[1] + n[2] * n[2]);
        if (length < 1e-6f) return UNASSIGNED;
        for (float &value: n) value /= length;
        const float *d = quad.position[3];
        if (float offset = (d[0] - a[0]) * n[0] + (d[1] - a[1]) * n[1] + (d[2] - a[2]) * n[2]; std::fabs(offset) > 1e-4f) return UNASSIGNED;
        constexpr float axis = 0.9999f;
        if (n[1] <= -axis) return 0;
        if (n[1] >= axis) return 1;
        if (n[2] <= -axis) return 2;
        if (n[2] >= axis) return 3;
        if (n[0] <= -axis) return 4;
        if (n[0] >= axis) return 5;
        return UNASSIGNED;
    }

    void Tables::loadModels(const i32 *data, std::size_t words) {
        std::size_t at = 0;
        auto next = [&]() -> i32 {
            if (at >= words) fail("Model stream ended early");
            return data[at++];
        };
        u32 count = static_cast<u32>(next());
        models.assign(count, Model{});
        quads.clear();
        entries.clear();
        for (u32 index = 0; index < count; index++) {
            auto &[begin, ambientOcclusion, weighted, entryBegin, entryCount, totalWeight] = models[index];
            i32 flags = next();
            ambientOcclusion = flags & 1;
            weighted = flags & 2;
            if (weighted) {
                entryBegin = static_cast<u32>(entries.size());
                entryCount = static_cast<u32>(next());
                totalWeight = 0;
                for (u32 i = 0; i < entryCount; i++) {
                    u32 child = static_cast<u32>(next());
                    i32 weight = next();
                    entries.push_back({child, weight});
                    totalWeight += weight;
                }
            }
            u32 counts[7];
            for (u32 &value: counts) value = static_cast<u32>(next());
            for (u32 face = 0; face < 7; face++) {
                begin[face] = static_cast<u32>(quads.size());
                for (u32 i = 0; i < counts[face]; i++) {
                    Quad &quad = quads.emplace_back();
                    for (int vertex = 0; vertex < 4; vertex++) {
                        i32 raw[7];
                        for (i32 &value: raw) value = next();
                        std::memcpy(&quad.position[vertex][0], &raw[0], 4);
                        std::memcpy(&quad.position[vertex][1], &raw[1], 4);
                        std::memcpy(&quad.position[vertex][2], &raw[2], 4);
                        quad.color[vertex] = static_cast<u32>(raw[3]);
                        std::memcpy(&quad.uv[vertex][0], &raw[4], 4);
                        std::memcpy(&quad.uv[vertex][1], &raw[5], 4);
                    }
                    quad.face = static_cast<u8>(next());
                    quad.tint = static_cast<i8>(next());
                    for (int axis = 0; axis < 3; axis++) {
                        float low = 32.0f, high = -32.0f;
                        for (auto & vertex : quad.position) {
                            low = std::min(low, vertex[axis]);
                            high = std::max(high, vertex[axis]);
                        }
                        quad.min[axis] = low;
                        quad.max[axis] = high;
                    }
                    quad.bucket = bucketOf(quad);
                }
            }
            begin[7] = static_cast<u32>(quads.size());
        }
        for (const auto &[model, weight]: entries) {
            if (model >= count) fail("Weighted model points past the model table");
        }
    }
    void Tables::removeDirectionalShading() {
        constexpr float SHADES[6] = {0.5f, 1.0f, 0.8f, 0.8f, 0.6f, 0.6f};
        for (Quad &quad: quads) {
            if (quad.face >= 6) continue;
            u32 gray = static_cast<u32>(std::clamp(static_cast<int>(SHADES[quad.face] * 255.0f), 0, 255));
            u32 shaded = 0xFF000000u | gray << 16 | gray << 8 | gray;
            bool uniform = true;
            for (u32 color: quad.color) uniform &= color == shaded;
            if (!uniform) continue;
            for (u32 &color: quad.color) color = 0xFFFFFFFFu;
        }
    }
} // namespace at::terrain
