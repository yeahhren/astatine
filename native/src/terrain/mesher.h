#pragma once

#include "core/common.h"
#include "terrain/occlusion.h"
#include "terrain/tables.h"
#include "terrain/vertex.h"
#include "vulkan/staging_pool.h"

#include <memory>
#include <vector>

namespace at::terrain {
    struct Snapshot;

    enum class SortMode : u8 { DISTANCE, NONE, PLANES };

    struct LayerMesh {
        std::vector<Vertex> vertices;
        u32 bucketQuads[BUCKETS] = {};
        std::vector<float> centroids;
        SortMode sortMode = SortMode::DISTANCE;
        std::vector<float> planes[3];
        StagedData staged;
        u32 count = 0;
        StagedData indices;
        u32 indexCount = 0;
    };

    u32 planeSignature(const std::vector<float> (&planes)[3], float x, float y, float z);

    struct MeshResult {
        i32 x = 0, y = 0, z = 0;
        u32 version = 0;
        LayerMesh layers[LAYERS];
        Visibility visibility = 0;
        OpaqueMask opaque{};
        bool empty = true;
        bool urgent = false;
        u64 sequence = 0;
        u32 units = 1;
    };

    std::unique_ptr<MeshResult> mesh(const Tables &tables, const Snapshot &snapshot, float cameraX, float cameraY,
                                     float cameraZ);

    void sortQuads(std::vector<Vertex> &vertices, std::vector<float> &centroids, float cameraX, float cameraY,
                   float cameraZ, u32 units);
} // namespace at::terrain
