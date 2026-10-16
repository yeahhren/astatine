#pragma once

#include "core/common.h"
#include "core/vk.h"
#include "memory/range_allocator.h"
#include "terrain/mesher.h"
#include "terrain/occlusion.h"
#include "terrain/tables.h"
#include "vulkan/memory.h"

#include <memory>
#include <mutex>
#include <string>
#include <vector>

namespace at {
    class Renderer;

    namespace terrain {
        struct Snapshot;

        class VertexArena {
        public:
            static constexpr u64 BUFFER_VERTICES = 4ull << 20;

            explicit VertexArena(Renderer &renderer) : renderer(renderer) {
            }

            ~VertexArena();

            bool allocate(u32 count, u32 &buffer, u32 &first);

            void release(u32 buffer, u32 first, u32 count) const;

            VkBuffer handle(u32 buffer) const;

            u32 bufferCount() const { return static_cast<u32>(buffers.size()); }

            u64 usedVertices() const;

        private:
            struct Block {
                std::unique_ptr<GpuBuffer> buffer;
                RangeAllocator ranges{BUFFER_VERTICES};
            };

            Renderer &renderer;
            std::vector<std::unique_ptr<Block> > buffers;
        };

        struct SectionLayer {
            u32 buffer = 0;
            u32 first = 0;
            u32 vertices = 0;
            u32 bucketQuads[BUCKETS] = {};
            std::vector<Vertex> cpu;
            std::vector<float> centroids;
            float sortedX = 0, sortedY = 0, sortedZ = 0;
        };

        struct Section {
            i32 x = 0, y = 0, z = 0;
            bool valid = false;
            u32 version = 1;
            u32 builtVersion = 0;
            u32 buildingVersion = 0;
            bool building = false;
            bool dirty = true;
            bool important = false;
            bool compiled = false;
            Visibility visibility = 0;
            std::unique_ptr<OpaqueMask> opaque;
            SectionLayer layers[LAYERS];
            u32 frame = 0;
        };

        struct SetupParameters {
            double x, y, z;
            double eyeHeight;
            i32 frame;
            i32 lookFace;
            bool smartCull;
            bool spectator;
            bool cameraInOpaque;
        };

        class Terrain {
        public:
            explicit Terrain(Renderer &renderer);

            ~Terrain();

            void setTables(std::shared_ptr<const Tables> next);

            void configure(i32 viewDistance);

            void clear();

            void markDirty(i32 minX, i32 minY, i32 minZ, i32 maxX, i32 maxY, i32 maxZ, bool important);

            void setup(const SetupParameters &parameters);

            u32 schedule(i32 *out, u32 max);

            void abort(i32 x, i32 y, i32 z, u32 version);

            u32 upload(u64 budget);

            u32 draw(u32 layer);

            u32 sortTranslucent(u32 limit) const;

            std::string debug() const;

            const i32 *visibleSections(u32 &count) const {
                count = static_cast<u32>(visibleCoordinates.size() / 3);
                return visibleCoordinates.data();
            }

            Snapshot *acquireSnapshot();

            void releaseSnapshot(Snapshot *snapshot);

            void build(Snapshot *snapshot);

        private:
            struct Node {
                Section *section;
                i8 source;
                u8 culling;
            };

            Section *at(i32 x, i32 y, i32 z);

            Section *slot(i32 x, i32 y, i32 z);

            void recenter(double x, double z);

            void reset(Section &section, i32 x, i32 y, i32 z) const;

            void releaseGeometry(Section &section) const;

            bool frustumVisible(const Section &section) const;

            void computeFrustum();

            void apply(MeshResult &result);

            Renderer &renderer;
            std::unique_ptr<VertexArena> arena;
            std::shared_ptr<const Tables> tables;
            mutable std::mutex tablesLock;

            i32 viewDistance = 0;
            i32 size = 0;
            std::vector<Section> sections;
            i32 centerX = INT32_MIN, centerZ = INT32_MIN;

            double cameraX = 0, cameraY = 0, cameraZ = 0;
            double eyeX = 0, eyeY = 0, eyeZ = 0;
            float planes[6][4] = {};
            u32 frame = 0;

            std::vector<Section *> visible;
            std::vector<i32> visibleCoordinates;
            std::vector<Node> queue;

            std::mutex resultsLock;
            std::vector<std::unique_ptr<MeshResult> > results;
            std::mutex snapshotsLock;
            std::vector<Snapshot *> snapshots;
            std::mutex cameraLock;
            float buildCamera[3] = {};

            u32 drawnLastFrame = 0;
            u64 uploadedBytes = 0;
        };
    } // namespace terrain

    using terrain::Terrain;
} // namespace at
