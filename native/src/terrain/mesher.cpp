#include "terrain/mesher.h"

#include "terrain/snapshot.h"

#include <algorithm>
#include <cmath>
#include <numeric>

namespace at::terrain {
    namespace {
        constexpr int PAD = 2;
        constexpr int SIZE = 16 + PAD * 2;
        constexpr u32 UNKNOWN = 0xFFFFFFFFu;

        AT_INLINE int regionIndex(int x, int y, int z) {
            return ((y + PAD) * SIZE + (z + PAD)) * SIZE + (x + PAD);
        }

        AT_INLINE i64 positionHash(i32 x, i32 y, i32 z) {
            i64 l = static_cast<i64>(static_cast<i32>(static_cast<u32>(x) * 3129871u)) ^
                    static_cast<i64>(static_cast<u64>(static_cast<i64>(z)) * 116129781ull) ^ static_cast<i64>(y);
            u64 u = static_cast<u64>(l);
            return static_cast<i64>(u * u * 42317861ull + u * 11ull);
        }

        struct AdjacencyData {
            int corners[4];
            bool nonCubicWeight;
            int weights[4][8];
        };

        constexpr int D = 0, U = 1, N = 2, S = 3, W = 4, E = 5;
        constexpr int FD = 6, FU = 7, FN = 8, FS = 9, FW = 10, FE = 11;
        constexpr AdjacencyData ADJACENCY[6] = {
            {.corners = {W, E, N, S}, .nonCubicWeight = false, .weights = {}},
            {.corners = {E, W, N, S}, .nonCubicWeight = false, .weights = {}},
            {
                .corners = {U, D, E, W}, .nonCubicWeight = true,
                .weights = {
                    {U, FW, U, W, FU, W, FU, FW}, {U, FE, U, E, FU, E, FU, FE}, {D, FE, D, E, FD, E, FD, FE},
                    {D, FW, D, W, FD, W, FD, FW}
                }
            },
            {
                .corners = {W, E, D, U}, .nonCubicWeight = true,
                .weights = {
                    {U, FW, FU, FW, FU, W, U, W}, {D, FW, FD, FW, FD, W, D, W}, {D, FE, FD, FE, FD, E, D, E},
                    {U, FE, FU, FE, FU, E, U, E}
                }
            },
            {
                .corners = {U, D, N, S}, .nonCubicWeight = true,
                .weights = {
                    {U, S, U, FS, FU, FS, FU, S}, {U, N, U, FN, FU, FN, FU, N}, {D, N, D, FN, FD, FN, FD, N},
                    {D, S, D, FS, FD, FS, FD, S}
                }
            },
            {
                .corners = {D, U, N, S}, .nonCubicWeight = true,
                .weights = {
                    {FD, S, FD, FS, D, FS, D, S}, {FD, N, FD, FN, D, FN, D, N}, {FU, N, FU, FN, U, FN, U, N},
                    {FU, S, FU, FS, U, FS, U, S}
                }
            },
        };

        constexpr int REMAP[6][4] = {
            {0, 1, 2, 3}, {2, 3, 0, 1}, {3, 0, 1, 2}, {0, 1, 2, 3}, {3, 0, 1, 2}, {1, 2, 3, 0}
        };

        struct Scratch {
            u16 states[SIZE * SIZE * SIZE]{};
            u32 light[SIZE * SIZE * SIZE]{};
            std::vector<Vertex> buckets[LAYERS][BUCKETS];
            std::vector<float> centroids;
            std::vector<float> quadPlanes;
            std::vector<u8> quadAxes;
        };

        class Builder {
        public:
            Builder(const Tables &tables, const Snapshot &snapshot, Scratch &scratch)
                : tables(tables), snapshot(snapshot), scratch(scratch), baseY(snapshot.sectionY * 16),
                  sky(snapshot.flags & Snapshot::SKY) {
            }

            void fillStates() const {
                for (int y = -PAD; y < 16 + PAD; y++) {
                    int sy = y < 0 ? 0 : (y < 16 ? 1 : 2);
                    int ly = y & 15;
                    for (int z = -PAD; z < 16 + PAD; z++) {
                        int sz = z < 0 ? 0 : (z < 16 ? 1 : 2);
                        int lz = z & 15;
                        for (int x = -PAD; x < 16 + PAD; x++) {
                            int sx = x < 0 ? 0 : (x < 16 ? 1 : 2);
                            int section = sy * 9 + sz * 3 + sx;
                            u16 state = 0;
                            if (snapshot.presentMask & (1u << section))
                                state = snapshot.states[section][(ly << 8) | (lz << 4) | (x & 15)];
                            scratch.states[regionIndex(x, y, z)] = state;
                        }
                    }
                }
                std::ranges::fill(scratch.light, UNKNOWN);
            }

            [[nodiscard]] AT_INLINE u16 state(int x, int y, int z) const { return scratch.states[regionIndex(x, y, z)]; }
            [[nodiscard]] AT_INLINE const BlockInfo &info(int x, int y, int z) const { return tables.blocks[state(x, y, z)]; }
            [[nodiscard]] AT_INLINE bool has(int x, int y, int z, u32 flag) const { return info(x, y, z).flags & flag; }
            [[nodiscard]] AT_INLINE float ambientLight(int x, int y, int z) const { return has(x, y, z, AO_BLOCKING) ? 0.2f : 1.0f; }

            [[nodiscard]] int rawLight(bool skyType, int x, int y, int z) const {
                int worldY = baseY + y;
                int fallback = skyType ? 15 : 0;
                if (worldY < 0 || worldY >= 256) return fallback;
                int cx = x < 0 ? 0 : (x < 16 ? 1 : 2);
                int cz = z < 0 ? 0 : (z < 16 ? 1 : 2);
                int column = cz * 3 + cx;
                if (!(snapshot.loadedMask & (1u << column))) return fallback;
                int cy = y < 0 ? 0 : (y < 16 ? 1 : 2);
                int section = cy * 9 + column;
                int lx = x & 15, ly = y & 15, lz = z & 15;
                if (!(snapshot.presentMask & (1u << section))) {
                    bool open = worldY >= snapshot.heightMaps[column][(lz << 4) | lx];
                    return open ? fallback : 0;
                }
                int index = (ly << 8) | (lz << 4) | lx;
                const u8 *data;
                if (skyType) {
                    if (!sky || !(snapshot.skyMask & (1u << section))) return 0;
                    data = snapshot.skyLight[section];
                } else {
                    data = snapshot.blockLight[section];
                }
                u8 packed = data[index >> 1];
                return (index & 1) ? (packed >> 4) & 15 : packed & 15;
            }

            [[nodiscard]] int brightness(bool skyType, int x, int y, int z) const {
                if (skyType && !sky) return 0;
                int worldY = baseY + y;
                if (worldY < 0 || worldY >= 256) return skyType ? 15 : 0;
                if (has(x, y, z, NEIGHBOR_LIGHT)) {
                    int brightest = 0;
                    for (const auto &offset: OFFSETS) {
                        int light = rawLight(skyType, x + offset[0], y + offset[1], z + offset[2]);
                        if (light > brightest) brightest = light;
                        if (brightest >= 15) return brightest;
                    }
                    return brightest;
                }
                return rawLight(skyType, x, y, z);
            }

            [[nodiscard]] AT_INLINE u32 lightColor(int x, int y, int z, int minBlockLight) const {
                int skyLight = brightness(true, x, y, z);
                int blockLight = std::max(brightness(false, x, y, z), minBlockLight);
                return static_cast<u32>(skyLight << 20 | blockLight << 4);
            }

            [[nodiscard]] u32 blockLightColor(int x, int y, int z) const {
                u32 &cached = scratch.light[regionIndex(x, y, z)];
                if (cached != UNKNOWN) return cached;
                const BlockInfo &block = info(x, y, z);
                u32 color = lightColor(x, y, z, block.light);
                if (color == 0 && (block.flags & SLAB)) color = lightColor(x, y - 1, z, info(x, y - 1, z).light);
                cached = color;
                return color;
            }

            [[nodiscard]] u32 liquidLightColor(int x, int y, int z) const {
                u32 i = lightColor(x, y, z, 0);
                u32 j = lightColor(x, y + 1, z, 0);
                u32 low = std::max(i & 0xFF, j & 0xFF);
                u32 high = std::max((i >> 16) & 0xFF, (j >> 16) & 0xFF);
                return low | high << 16;
            }

            void emit(u32 layer, const float position[4][3], const u32 color[4], const float uv[4][2],
                      const u32 light[4], u8 bucket) {
                std::vector<Vertex> &out = scratch.buckets[layer][layer == TRANSLUCENT ? UNASSIGNED : bucket];
                float centerU = (uv[0][0] + uv[1][0] + uv[2][0] + uv[3][0]) * 0.25f;
                float centerV = (uv[0][1] + uv[1][1] + uv[2][1] + uv[3][1]) * 0.25f;
                for (int i = 0; i < 4; i++) {
                    out.push_back(packVertex(position[i][0], position[i][1], position[i][2], uv[i][0], uv[i][1],
                                             centerU, centerV, color[i], light[i] & 0xFFFF, (light[i] >> 16) & 0xFFFF));
                }
                if (layer == TRANSLUCENT) {
                    float ax = position[1][0] - position[0][0], ay = position[1][1] - position[0][1],
                            az = position[1][2] - position[0][2];
                    float bx = position[2][0] - position[0][0], by = position[2][1] - position[0][1],
                            bz = position[2][2] - position[0][2];
                    float normal[3] = {ay * bz - az * by, az * bx - ax * bz, ax * by - ay * bx};
                    float length = std::sqrt(normal[0] * normal[0] + normal[1] * normal[1] + normal[2] * normal[2]);
                    u8 axis = 3;
                    for (u8 candidate = 0; candidate < 3; candidate++) {
                        if (length > 0.0f && std::abs(normal[candidate]) >= length * 0.9999f) axis = candidate;
                    }
                    scratch.quadAxes.push_back(axis);
                    scratch.quadPlanes.push_back(axis < 3 ? position[0][axis] : 0.0f);
                    scratch.centroids.push_back(
                        (position[0][0] + position[1][0] + position[2][0] + position[3][0]) * 0.25f);
                    scratch.centroids.push_back(
                        (position[0][1] + position[1][1] + position[2][1] + position[3][1]) * 0.25f);
                    scratch.centroids.push_back(
                        (position[0][2] + position[1][2] + position[2][2] + position[3][2]) * 0.25f);
                }
                empty = false;
            }

            static AT_INLINE u32 multiply(u32 color, float r, float g, float b) {
                u32 red = static_cast<u32>(static_cast<i32>(static_cast<float>(color & 0xFF) * r)) & 0xFF;
                u32 green = static_cast<u32>(static_cast<i32>(static_cast<float>((color >> 8) & 0xFF) * g)) & 0xFF;
                u32 blue = static_cast<u32>(static_cast<i32>(static_cast<float>((color >> 16) & 0xFF) * b)) & 0xFF;
                return (color & 0xFF000000u) | blue << 16 | green << 8 | red;
            }

            struct FaceShape {
                float shape[12];
                bool flush;
                bool partial;
            };

            static FaceShape computeShape(const Quad &quad, bool cube) {
                FaceShape out{};
                float f = quad.min[0], g = quad.min[1], h = quad.min[2];
                float i = quad.max[0], j = quad.max[1], k = quad.max[2];
                out.shape[W] = f;
                out.shape[E] = i;
                out.shape[D] = g;
                out.shape[U] = j;
                out.shape[N] = h;
                out.shape[S] = k;
                out.shape[W + 6] = 1.0f - f;
                out.shape[E + 6] = 1.0f - i;
                out.shape[D + 6] = 1.0f - g;
                out.shape[U + 6] = 1.0f - j;
                out.shape[N + 6] = 1.0f - h;
                out.shape[S + 6] = 1.0f - k;
                switch (quad.face) {
                    case D:
                        out.partial = f >= 1.0E-4f || h >= 1.0E-4f || i <= 0.9999f || k <= 0.9999f;
                        out.flush = (g < 1.0E-4f || cube) && g == j;
                        break;
                    case U:
                        out.partial = f >= 1.0E-4f || h >= 1.0E-4f || i <= 0.9999f || k <= 0.9999f;
                        out.flush = (j > 0.9999f || cube) && g == j;
                        break;
                    case N:
                        out.partial = f >= 1.0E-4f || g >= 1.0E-4f || i <= 0.9999f || j <= 0.9999f;
                        out.flush = (h < 1.0E-4f || cube) && h == k;
                        break;
                    case S:
                        out.partial = f >= 1.0E-4f || g >= 1.0E-4f || i <= 0.9999f || j <= 0.9999f;
                        out.flush = (k > 0.9999f || cube) && h == k;
                        break;
                    case W:
                        out.partial = g >= 1.0E-4f || h >= 1.0E-4f || j <= 0.9999f || k <= 0.9999f;
                        out.flush = (f < 1.0E-4f || cube) && f == i;
                        break;
                    default:
                        out.partial = g >= 1.0E-4f || h >= 1.0E-4f || j <= 0.9999f || k <= 0.9999f;
                        out.flush = (i > 0.9999f || cube) && f == i;
                        break;
                }
                return out;
            }

            static AT_INLINE u32 blend(u32 i, u32 j, u32 k, u32 l) {
                if (i == 0) i = l;
                if (j == 0) j = l;
                if (k == 0) k = l;
                return ((i + j + k + l) >> 2) & 0xFF00FF;
            }

            static AT_INLINE u32 blend(u32 i, u32 j, u32 k, u32 l, float f, float g, float h, float m) {
                u32 high = static_cast<u32>(static_cast<i32>(
                               static_cast<float>((i >> 16) & 0xFF) * f + static_cast<float>((j >> 16) & 0xFF) * g +
                               static_cast<float>((k >> 16) & 0xFF) * h + static_cast<float>((l >> 16) & 0xFF) * m)) &
                           0xFF;
                u32 low = static_cast<u32>(static_cast<i32>(
                              static_cast<float>(i & 0xFF) * f + static_cast<float>(j & 0xFF) * g +
                              static_cast<float>(k & 0xFF) * h + static_cast<float>(l & 0xFF) * m)) &
                          0xFF;
                return high << 16 | low;
            }

            void ambientOcclusion(int x, int y, int z, int face, const FaceShape &shape, float brightness[4],
                                  u32 lightMap[4]) const {
                const int *offset = OFFSETS[face];
                int bx = x, by = y, bz = z;
                if (shape.flush) {
                    bx += offset[0];
                    by += offset[1];
                    bz += offset[2];
                }
                const AdjacencyData &adjacency = ADJACENCY[face];
                const int *c0 = OFFSETS[adjacency.corners[0]];
                const int *c1 = OFFSETS[adjacency.corners[1]];
                const int *c2 = OFFSETS[adjacency.corners[2]];
                const int *c3 = OFFSETS[adjacency.corners[3]];
                int p2[3] = {bx + c0[0], by + c0[1], bz + c0[2]};
                int p3[3] = {bx + c1[0], by + c1[1], bz + c1[2]};
                int p4[3] = {bx + c2[0], by + c2[1], bz + c2[2]};
                int p5[3] = {bx + c3[0], by + c3[1], bz + c3[2]};
                u32 i = blockLightColor(p2[0], p2[1], p2[2]);
                u32 j = blockLightColor(p3[0], p3[1], p3[2]);
                u32 k = blockLightColor(p4[0], p4[1], p4[2]);
                u32 l = blockLightColor(p5[0], p5[1], p5[2]);
                float f = ambientLight(p2[0], p2[1], p2[2]);
                float g = ambientLight(p3[0], p3[1], p3[2]);
                float h = ambientLight(p4[0], p4[1], p4[2]);
                float m = ambientLight(p5[0], p5[1], p5[2]);
                bool bl = has(p2[0] + offset[0], p2[1] + offset[1], p2[2] + offset[2], TRANSLUCENT_BLOCK);
                bool bl2 = has(p3[0] + offset[0], p3[1] + offset[1], p3[2] + offset[2], TRANSLUCENT_BLOCK);
                bool bl3 = has(p4[0] + offset[0], p4[1] + offset[1], p4[2] + offset[2], TRANSLUCENT_BLOCK);
                bool bl4 = has(p5[0] + offset[0], p5[1] + offset[1], p5[2] + offset[2], TRANSLUCENT_BLOCK);
                float n, p, r, t;
                u32 o, q, s, u;
                if (!bl3 && !bl) {
                    n = f;
                    o = i;
                } else {
                    int corner[3] = {p2[0] + c2[0], p2[1] + c2[1], p2[2] + c2[2]};
                    n = ambientLight(corner[0], corner[1], corner[2]);
                    o = blockLightColor(corner[0], corner[1], corner[2]);
                }
                if (!bl4 && !bl) {
                    p = f;
                    q = i;
                } else {
                    int corner[3] = {p2[0] + c3[0], p2[1] + c3[1], p2[2] + c3[2]};
                    p = ambientLight(corner[0], corner[1], corner[2]);
                    q = blockLightColor(corner[0], corner[1], corner[2]);
                }
                if (!bl3 && !bl2) {
                    r = g;
                    s = j;
                } else {
                    int corner[3] = {p3[0] + c2[0], p3[1] + c2[1], p3[2] + c2[2]};
                    r = ambientLight(corner[0], corner[1], corner[2]);
                    s = blockLightColor(corner[0], corner[1], corner[2]);
                }
                if (!bl4 && !bl2) {
                    t = g;
                    u = j;
                } else {
                    int corner[3] = {p3[0] + c3[0], p3[1] + c3[1], p3[2] + c3[2]};
                    t = ambientLight(corner[0], corner[1], corner[2]);
                    u = blockLightColor(corner[0], corner[1], corner[2]);
                }
                u32 v = blockLightColor(x, y, z);
                if (shape.flush || !has(x + offset[0], y + offset[1], z + offset[2], OPAQUE)) {
                    v = blockLightColor(x + offset[0], y + offset[1], z + offset[2]);
                }
                float w = shape.flush ? ambientLight(bx, by, bz) : ambientLight(x, y, z);
                const int *remap = REMAP[face];
                float a0 = (m + f + p + w) * 0.25f;
                float a1 = (h + f + n + w) * 0.25f;
                float a2 = (h + g + r + w) * 0.25f;
                float a3 = (m + g + t + w) * 0.25f;
                if (shape.partial && adjacency.nonCubicWeight) {
                    const float *sh = shape.shape;
                    float weights[4][4];
                    for (int vertex = 0; vertex < 4; vertex++) {
                        const int *entry = adjacency.weights[vertex];
                        for (int pair = 0; pair < 4; pair++)
                            weights[vertex][pair] = sh[entry[pair * 2]] * sh[entry[pair * 2 + 1]];
                    }
                    for (int vertex = 0; vertex < 4; vertex++) {
                        const float *wv = weights[vertex];
                        brightness[remap[vertex]] = a0 * wv[0] + a1 * wv[1] + a2 * wv[2] + a3 * wv[3];
                    }
                    u32 ar = blend(i, v, q, l);
                    u32 as = blend(k, i, o, v);
                    u32 at = blend(k, j, s, v);
                    u32 au = blend(l, j, u, v);
                    for (int vertex = 0; vertex < 4; vertex++) {
                        const float *wv = weights[vertex];
                        lightMap[remap[vertex]] = blend(ar, as, at, au, wv[0], wv[1], wv[2], wv[3]);
                    }
                } else {
                    lightMap[remap[0]] = blend(l, i, q, v);
                    lightMap[remap[1]] = blend(k, i, o, v);
                    lightMap[remap[2]] = blend(k, j, s, v);
                    lightMap[remap[3]] = blend(l, j, u, v);
                    brightness[remap[0]] = a0;
                    brightness[remap[1]] = a1;
                    brightness[remap[2]] = a2;
                    brightness[remap[3]] = a3;
                }
            }

            [[nodiscard]] bool faceVisible(int x, int y, int z, int index, const BlockInfo &block, int face) const {
                if (snapshot.overrides[index] & Snapshot::FACES) return (snapshot.faces[index] >> face) & 1;
                if ((block.insetFaces >> face) & 1) return true;
                const int *offset = OFFSETS[face];
                return !has(x + offset[0], y + offset[1], z + offset[2], OPAQUE);
            }

            void renderModel(int x, int y, int z, int index, const BlockInfo &block) {
                u32 modelId = (snapshot.overrides[index] & Snapshot::MODEL) ? snapshot.models[index] : block.model;
                if (modelId >= tables.models.size()) return;
                const Model *model = &tables.models[modelId];
                i32 worldX = snapshot.sectionX * 16 + x, worldY = baseY + y, worldZ = snapshot.sectionZ * 16 + z;
                if (model->weighted && model->entryCount > 0) {
                    u32 child;
                    if (tables.alternatives && model->totalWeight > 0) {
                        i32 seed = static_cast<i32>(positionHash(worldX, worldY, worldZ)) >> 16;
                        i32 remaining = std::abs(seed) % model->totalWeight;
                        child = tables.entries[model->entryBegin].model;
                        for (u32 i = 0; i < model->entryCount; i++) {
                            const WeightedEntry &entry = tables.entries[model->entryBegin + i];
                            remaining -= entry.weight;
                            if (remaining < 0) {
                                child = entry.model;
                                break;
                            }
                        }
                    } else {
                        child = tables.entries[model->entryBegin].model;
                    }
                    model = &tables.models[child];
                }
                bool ao = tables.ambientOcclusion && block.light == 0 && model->ambientOcclusion;
                bool tinted = snapshot.overrides[index] & Snapshot::TINT;
                u32 tintColor = tinted ? snapshot.tints[index] : 0xFFFFFFu;
                float tintR = static_cast<float>((tintColor >> 16) & 0xFF) / 255.0f;
                float tintG = static_cast<float>((tintColor >> 8) & 0xFF) / 255.0f;
                float tintB = static_cast<float>(tintColor & 0xFF) / 255.0f;

                double offsetX = 0.0, offsetY = 0.0, offsetZ = 0.0;
                if (block.offsetType != 0) {
                    i64 hash = ao ? positionHash(worldX, worldY, worldZ) : positionHash(worldX, 0, worldZ);
                    offsetX = (static_cast<float>((hash >> 16) & 15) / 15.0f - 0.5) * 0.5;
                    offsetZ = (static_cast<float>((hash >> 24) & 15) / 15.0f - 0.5) * 0.5;
                    if (block.offsetType == 2) offsetY = (static_cast<float>((hash >> 20) & 15) / 15.0f - 1.0) * 0.2;
                }
                auto originX = static_cast<float>(static_cast<double>(x) + offsetX);
                auto originY = static_cast<float>(static_cast<double>(y) + offsetY);
                auto originZ = static_cast<float>(static_cast<double>(z) + offsetZ);
                u32 layer = block.layer;
                bool cube = block.flags & FULL_CUBE;

                for (int face = 0; face < 7; face++) {
                    u32 begin = model->begin[face], end = model->begin[face + 1];
                    if (begin == end) continue;
                    if (face < 6 && !faceVisible(x, y, z, index, block, face)) continue;
                    u32 flatLight = 0;
                    if (!ao && face < 6) {
                        const int *offset = OFFSETS[face];
                        flatLight = blockLightColor(x + offset[0], y + offset[1], z + offset[2]);
                    }
                    for (u32 q = begin; q < end; q++) {
                        const Quad &quad = tables.quads[q];
                        float position[4][3];
                        for (int vertex = 0; vertex < 4; vertex++) {
                            position[vertex][0] = originX + quad.position[vertex][0];
                            position[vertex][1] = originY + quad.position[vertex][1];
                            position[vertex][2] = originZ + quad.position[vertex][2];
                        }
                        u32 color[4];
                        u32 light[4];
                        bool hasTint = quad.tint != -1;
                        if (ao) {
                            FaceShape shape = computeShape(quad, cube);
                            float brightness[4];
                            ambientOcclusion(x, y, z, quad.face, shape, brightness, light);
                            for (int vertex = 0; vertex < 4; vertex++) {
                                float b = brightness[vertex];
                                color[vertex] = hasTint
                                                    ? multiply(quad.color[vertex], b * tintR, b * tintG, b * tintB)
                                                    : multiply(quad.color[vertex], b, b, b);
                            }
                        } else {
                            u32 value = flatLight;
                            if (face == 6) {
                                FaceShape shape = computeShape(quad, cube);
                                const int *offset = OFFSETS[quad.face];
                                value = shape.flush
                                            ? blockLightColor(x + offset[0], y + offset[1], z + offset[2])
                                            : blockLightColor(x, y, z);
                            }
                            for (int vertex = 0; vertex < 4; vertex++) {
                                light[vertex] = value;
                                color[vertex] = hasTint
                                                    ? multiply(quad.color[vertex], tintR, tintG, tintB)
                                                    : quad.color[vertex];
                            }
                        }
                        emit(layer, position, color, quad.uv, light, quad.bucket);
                    }
                }
            }

            [[nodiscard]] float liquidHeight(int x, int y, int z, u8 liquid) const {
                int count = 0;
                float total = 0.0f;
                for (int j = 0; j < 4; j++) {
                    int px = x - (j & 1), pz = z - ((j >> 1) & 1);
                    if (info(px, y + 1, pz).liquid == liquid) return 1.0f;
                    const BlockInfo &neighbor = info(px, y, pz);
                    if (neighbor.liquid == liquid) {
                        int level = neighbor.level;
                        auto heightLoss = [](int value) {
                            if (value >= 8) value = 0;
                            return static_cast<float>(value + 1) / 9.0f;
                        };
                        if (level >= 8 || level == 0) {
                            total += heightLoss(level) * 10.0f;
                            count += 10;
                        }
                        total += heightLoss(level);
                        count++;
                    } else if (!(neighbor.flags & SOLID_MATERIAL)) {
                        total += 1.0f;
                        count++;
                    }
                }
                return 1.0f - total / static_cast<float>(count);
            }

            static AT_INLINE u32 packColor(float r, float g, float b) {
                u32 red = static_cast<u32>(static_cast<i32>(r * 255.0f)) & 0xFF;
                u32 green = static_cast<u32>(static_cast<i32>(g * 255.0f)) & 0xFF;
                u32 blue = static_cast<u32>(static_cast<i32>(b * 255.0f)) & 0xFF;
                return 0xFF000000u | blue << 16 | green << 8 | red;
            }

            static u8 bucketOf(const float position[4][3]) {
                const float *a = position[0];
                const float *b = position[1];
                const float *c = position[2];
                float e1[3] = {b[0] - a[0], b[1] - a[1], b[2] - a[2]};
                float e2[3] = {c[0] - a[0], c[1] - a[1], c[2] - a[2]};
                float n[3] = {
                    e1[1] * e2[2] - e1[2] * e2[1], e1[2] * e2[0] - e1[0] * e2[2], e1[0] * e2[1] - e1[1] * e2[0]
                };
                float length = std::sqrt(n[0] * n[0] + n[1] * n[1] + n[2] * n[2]);
                if (length < 1e-6f) return UNASSIGNED;
                for (float &value: n) value /= length;
                const float *d = position[3];
                float offset = (d[0] - a[0]) * n[0] + (d[1] - a[1]) * n[1] + (d[2] - a[2]) * n[2];
                if (std::fabs(offset) > 1e-4f) return UNASSIGNED;
                constexpr float axis = 0.9999f;
                if (n[1] <= -axis) return 0;
                if (n[1] >= axis) return 1;
                if (n[2] <= -axis) return 2;
                if (n[2] >= axis) return 3;
                if (n[0] <= -axis) return 4;
                if (n[0] >= axis) return 5;
                return UNASSIGNED;
            }

            void quad(u32 layer, const float (&position)[4][3], u32 color, const float (&uv)[4][2], u32 light) {
                const u32 colors[4] = {color, color, color, color};
                const u32 lights[4] = {light, light, light, light};
                emit(layer, position, colors, uv, lights, bucketOf(position));
            }

            void renderLiquid(int x, int y, int z, int index, const BlockInfo &block) {
                const Sprite *sprites = block.liquid == LAVA ? tables.lava : tables.water;
                u32 tint = (snapshot.overrides[index] & Snapshot::TINT) ? snapshot.tints[index] : 0xFFFFFFu;
                float f = static_cast<float>((tint >> 16) & 0xFF) / 255.0f;
                float g = static_cast<float>((tint >> 8) & 0xFF) / 255.0f;
                float h = static_cast<float>(tint & 0xFF) / 255.0f;
                u8 faces = (snapshot.overrides[index] & Snapshot::FACES) ? snapshot.faces[index] : 0;
                bool up = faces & (1 << U);
                bool down = faces & (1 << D);
                bool sides[4] = {
                    (faces & (1 << N)) != 0, (faces & (1 << S)) != 0, (faces & (1 << W)) != 0, (faces & (1 << E)) != 0
                };
                bool gap = faces & (1 << 6);
                if (!up && !down && !sides[0] && !sides[1] && !sides[2] && !sides[3]) return;
                const float j = 0.5f, k = 1.0f, l = 0.8f, m = 0.6f;
                float n = liquidHeight(x, y, z, block.liquid);
                float o = liquidHeight(x, y, z + 1, block.liquid);
                float p = liquidHeight(x + 1, y, z + 1, block.liquid);
                float q = liquidHeight(x + 1, y, z, block.liquid);
                const auto d = static_cast<float>(x), e = static_cast<float>(y), r = static_cast<float>(z);
                constexpr auto s = 0.001f;
                u32 layer = block.layer;
                if (up) {
                    const Sprite *sprite = &sprites[0];
                    float t = (snapshot.overrides[index] & Snapshot::FLOW) ? snapshot.flows[index] : -1000.0f;
                    if (t > -999.0f) sprite = &sprites[1];
                    n -= s;
                    o -= s;
                    p -= s;
                    q -= s;
                    float u, w, yy, aa, v, xx, z2, ab;
                    if (t < -999.0f) {
                        u = sprite->u(0.0f);
                        v = sprite->v(0.0f);
                        w = u;
                        xx = sprite->v(16.0f);
                        yy = sprite->u(16.0f);
                        z2 = xx;
                        aa = yy;
                        ab = v;
                    } else {
                        float ac = std::sin(t) * 0.25f;
                        float ad = std::cos(t) * 0.25f;
                        u = sprite->u(8.0f + (-ad - ac) * 16.0f);
                        v = sprite->v(8.0f + (-ad + ac) * 16.0f);
                        w = sprite->u(8.0f + (-ad + ac) * 16.0f);
                        xx = sprite->v(8.0f + (ad + ac) * 16.0f);
                        yy = sprite->u(8.0f + (ad + ac) * 16.0f);
                        z2 = sprite->v(8.0f + (ad - ac) * 16.0f);
                        aa = sprite->u(8.0f + (ad - ac) * 16.0f);
                        ab = sprite->v(8.0f + (-ad - ac) * 16.0f);
                    }
                    u32 light = liquidLightColor(x, y, z);
                    u32 color = packColor(k * f, k * g, k * h);
                    const float top[4][3] = {
                        {d, e + n, r}, {d, e + o, r + 1.0f}, {d + 1.0f, e + p, r + 1.0f}, {d + 1.0f, e + q, r}
                    };
                    const float topUv[4][2] = {{u, v}, {w, xx}, {yy, z2}, {aa, ab}};
                    quad(layer, top, color, topUv, light);
                    if (gap) {
                        const float under[4][3] = {
                            {d, e + n, r}, {d + 1.0f, e + q, r}, {d + 1.0f, e + p, r + 1.0f}, {d, e + o, r + 1.0f}
                        };
                        const float underUv[4][2] = {{u, v}, {aa, ab}, {yy, z2}, {w, xx}};
                        quad(layer, under, color, underUv, light);
                    }
                }
                if (down) {
                    const auto &[minU, maxU, minV, maxV] = sprites[0];
                    float t = minU, u = maxU, w = minV, yy = maxV;
                    u32 light = liquidLightColor(x, y - 1, z);
                    u32 color = packColor(j, j, j);
                    const float bottom[4][3] = {{d, e, r + 1.0f}, {d, e, r}, {d + 1.0f, e, r}, {d + 1.0f, e, r + 1.0f}};
                    const float bottomUv[4][2] = {{t, yy}, {t, w}, {u, w}, {u, yy}};
                    quad(layer, bottom, color, bottomUv, light);
                }
                for (int side = 0; side < 4; side++) {
                    if (!sides[side]) continue;
                    int ap = side == 2 ? -1 : (side == 3 ? 1 : 0);
                    int aq = side == 0 ? -1 : (side == 1 ? 1 : 0);
                    const Sprite &sprite = sprites[1];
                    float aa, v, ar, at, as, au;
                    if (side == 0) {
                        aa = n;
                        v = q;
                        ar = d;
                        as = d + 1.0f;
                        at = r + s;
                        au = r + s;
                    } else if (side == 1) {
                        aa = p;
                        v = o;
                        ar = d + 1.0f;
                        as = d;
                        at = r + 1.0f - s;
                        au = r + 1.0f - s;
                    } else if (side == 2) {
                        aa = o;
                        v = n;
                        ar = d + s;
                        as = d + s;
                        at = r + 1.0f;
                        au = r;
                    } else {
                        aa = q;
                        v = p;
                        ar = d + 1.0f - s;
                        as = d + 1.0f - s;
                        at = r;
                        au = r + 1.0f;
                    }
                    float ak = sprite.u(0.0f);
                    float av = sprite.u(8.0f);
                    float aw = sprite.v((1.0f - aa) * 16.0f * 0.5f);
                    float ax = sprite.v((1.0f - v) * 16.0f * 0.5f);
                    float ay = sprite.v(8.0f);
                    u32 light = liquidLightColor(x + ap, y, z + aq);
                    float bc = side < 2 ? l : m;
                    u32 color = packColor(k * bc * f, k * bc * g, k * bc * h);
                    const float front[4][3] = {{ar, e + aa, at}, {as, e + v, au}, {as, e, au}, {ar, e, at}};
                    const float frontUv[4][2] = {{ak, aw}, {av, ax}, {av, ay}, {ak, ay}};
                    quad(layer, front, color, frontUv, light);
                    const float back[4][3] = {{ar, e, at}, {as, e, au}, {as, e + v, au}, {ar, e + aa, at}};
                    const float backUv[4][2] = {{ak, ay}, {av, ay}, {av, ax}, {ak, aw}};
                    quad(layer, back, color, backUv, light);
                }
            }

            void build(MeshResult &result) {
                fillStates();
                int opaqueCount = 0;
                for (int y = 0; y < 16; y++) {
                    for (int z = 0; z < 16; z++) {
                        for (int x = 0; x < 16; x++) {
                            int index = (y << 8) | (z << 4) | x;
                            u16 raw = state(x, y, z);
                            if (raw == 0) continue;
                            const BlockInfo &block = tables.blocks[raw];
                            if (block.flags & OPAQUE) {
                                result.opaque[static_cast<std::size_t>(index >> 6)] |= 1ull << (index & 63);
                                opaqueCount++;
                            }
                            if (snapshot.overrides[index] & Snapshot::SKIP) continue;
                            switch (block.renderType) {
                                case MODEL: renderModel(x, y, z, index, block);
                                    break;
                                case LIQUID: renderLiquid(x, y, z, index, block);
                                    break;
                                default: break;
                            }
                        }
                    }
                }
                result.visibility = resolveVisibility(result.opaque, opaqueCount);
            }

            bool empty = true;

        private:
            const Tables &tables;
            const Snapshot &snapshot;
            Scratch &scratch;
            const int baseY;
            const bool sky;
        };
    } // namespace

    void sortQuads(std::vector<Vertex> &vertices, std::vector<float> &centroids, float cameraX, float cameraY,
                   float cameraZ) {
        std::size_t quads = vertices.size() / 4;
        thread_local std::vector<u32> order;
        thread_local std::vector<float> distances;
        thread_local std::vector<Vertex> sortedVertices;
        thread_local std::vector<float> sortedCentroids;
        order.resize(quads);
        distances.resize(quads);
        for (std::size_t i = 0; i < quads; i++) {
            float dx = centroids[i * 3] - cameraX;
            float dy = centroids[i * 3 + 1] - cameraY;
            float dz = centroids[i * 3 + 2] - cameraZ;
            distances[i] = dx * dx + dy * dy + dz * dz;
        }
        std::iota(order.begin(), order.end(), 0u);
        std::ranges::stable_sort(order, [](u32 a, u32 b) { return distances[a] > distances[b]; });
        sortedVertices.resize(vertices.size());
        sortedCentroids.resize(centroids.size());
        for (std::size_t i = 0; i < quads; i++) {
            std::memcpy(&sortedVertices[i * 4], &vertices[order[i] * 4], sizeof(Vertex) * 4);
            std::memcpy(&sortedCentroids[i * 3], &centroids[order[i] * 3], sizeof(float) * 3);
        }
        vertices.swap(sortedVertices);
        centroids.swap(sortedCentroids);
    }

    u32 planeSignature(const std::vector<float> (&planes)[3], float x, float y, float z) {
        float camera[3] = {x, y, z};
        u32 signature = 0;
        for (int axis = 0; axis < 3; axis++) {
            u32 below = 0;
            for (float plane: planes[axis]) below += plane < camera[axis] ? 1u : 0u;
            signature |= std::min(below, 1023u) << (axis * 10);
        }
        return signature;
    }

    std::unique_ptr<MeshResult> mesh(const Tables &tables, const Snapshot &snapshot, float cameraX, float cameraY,
                                     float cameraZ) {
        thread_local std::unique_ptr<Scratch> scratchStorage;
        if (!scratchStorage) scratchStorage = std::make_unique<Scratch>();
        Scratch &scratch = *scratchStorage;
        for (auto &layer: scratch.buckets) {
            for (auto &bucket: layer) bucket.clear();
        }
        scratch.centroids.clear();
        scratch.quadPlanes.clear();
        scratch.quadAxes.clear();

        auto result = std::make_unique<MeshResult>();
        result->x = snapshot.sectionX;
        result->y = snapshot.sectionY;
        result->z = snapshot.sectionZ;
        result->version = snapshot.version;
        Builder builder(tables, snapshot, scratch);
        builder.build(*result);
        result->empty = builder.empty;

        for (u32 layer = 0; layer < LAYERS; layer++) {
            auto &[vertices, bucketQuads, centroids, sortMode, planes, staged, count, indices, indexCount] =
                    result->layers[layer];
            std::size_t total = 0;
            for (auto &bucket: scratch.buckets[layer]) total += bucket.size();
            if (total == 0) continue;
            vertices.reserve(total);
            for (u32 bucket = 0; bucket < BUCKETS; bucket++) {
                auto &source = scratch.buckets[layer][bucket];
                bucketQuads[bucket] = static_cast<u32>(source.size() / 4);
                vertices.insert(vertices.end(), source.begin(), source.end());
            }
            if (layer == TRANSLUCENT) {
                centroids = scratch.centroids;
                bool aligned = true;
                bool coplanar = true;
                for (std::size_t quad = 0; quad < scratch.quadAxes.size(); quad++) {
                    u8 axis = scratch.quadAxes[quad];
                    if (axis > 2) {
                        aligned = false;
                        coplanar = false;
                        break;
                    }
                    float plane = scratch.quadPlanes[quad];
                    if (axis != scratch.quadAxes[0] || std::abs(plane - scratch.quadPlanes[0]) > 1e-4f) coplanar = false;
                    std::vector<float> &list = planes[axis];
                    bool known = false;
                    for (float existing: list) known |= std::abs(existing - plane) <= 1e-4f;
                    if (!known) list.push_back(plane);
                }
                for (auto &list: planes) std::sort(list.begin(), list.end());
                sortMode = coplanar ? SortMode::NONE : aligned ? SortMode::PLANES : SortMode::DISTANCE;
                if (!aligned) {
                    for (auto &list: planes) list.clear();
                }
                sortQuads(vertices, centroids, cameraX - static_cast<float>(snapshot.sectionX * 16),
                          cameraY - static_cast<float>(snapshot.sectionY * 16),
                          cameraZ - static_cast<float>(snapshot.sectionZ * 16));
            }
        }
        return result;
    }
} // namespace at::terrain
