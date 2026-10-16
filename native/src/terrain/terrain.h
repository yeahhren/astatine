#pragma once

#include "core/common.h"
#include "core/vk.h"
#include "memory/range_allocator.h"
#include "terrain/culling.h"
#include "terrain/mesher.h"
#include "terrain/occlusion.h"
#include "terrain/snapshot.h"
#include "terrain/tables.h"
#include "vulkan/staging_pool.h"
#include "vulkan/memory.h"

#include <atomic>
#include <condition_variable>
#include <deque>
#include <thread>
#include <unordered_map>
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

            explicit VertexArena(Renderer &renderer, u64 elementSize = sizeof(Vertex), u64 capacity = BUFFER_VERTICES,
                                 VkBufferUsageFlags usage = VK_BUFFER_USAGE_VERTEX_BUFFER_BIT)
                : renderer(renderer), elementSize(elementSize), capacity(capacity), usage(usage) {
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
                RangeAllocator ranges;

                explicit Block(u64 capacity) : ranges(capacity) {
                }
            };

            Renderer &renderer;
            u64 elementSize;
            u64 capacity;
            VkBufferUsageFlags usage;
            std::vector<std::unique_ptr<Block> > buffers;
        };

        struct SectionLayer {
            u32 buffer = 0;
            u32 first = 0;
            u32 vertices = 0;
            u32 bucketQuads[BUCKETS] = {};
            u32 indexBuffer = 0;
            u32 indexFirst = 0;
            u32 indexCount = 0;
            std::shared_ptr<const std::vector<float> > centroids;
            float sortedX = 0, sortedY = 0, sortedZ = 0;
            SortMode sortMode = SortMode::DISTANCE;
            std::vector<float> planes[3];
            u32 signature = 0;
            u32 sortToken = 0;
            bool sorting = false;
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
            u32 drawnFrame = 0;
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

            void configure(i32 distance);

            void clear();

            void markDirty(i32 minX, i32 minY, i32 minZ, i32 maxX, i32 maxY, i32 maxZ, bool important);

            void setup(const SetupParameters &parameters);

            u32 schedule(i32 *out, u32 max);

            void abort(i32 x, i32 y, i32 z, u32 version);

            u32 upload(u64 budget, u64 budgetNanos);

            u32 draw(u32 layerIndex);

            u32 sortTranslucent(u32 limit);

            std::string debug() const;

            const i32 *visibleSections(u32 &count) const {
                count = static_cast<u32>(visibleCoordinates.size() / 3);
                return visibleCoordinates.data();
            }

            Snapshot *acquireSnapshot();

            void releaseSnapshot(Snapshot *snapshot);

            void build(Snapshot *snapshot);

            u32 attachClones(Snapshot *snapshot, i32 x, i32 y, i32 z, u32 present);

            void commitClones(Snapshot *snapshot, u32 copied, u32 sky);

        private:
            Section *at(i32 x, i32 y, i32 z);

            Section *slot(i32 x, i32 y, i32 z);

            void recenter(double x, double z);

            void reset(Section &section, i32 x, i32 y, i32 z);

            std::size_t slotIndex(const Section &section) const;

            void submitCull(i32 originX, i32 originY, i32 originZ, bool smart);

            bool acceptCull(std::unique_ptr<CullResult> result);

            const std::vector<u32> *candidates(i32 originX, i32 originY, i32 originZ) const;

            void releaseGeometry(Section &section) const;

            bool frustumVisible(const Section &section) const;

            void computeFrustum();

            void apply(MeshResult &result);

            Renderer &renderer;
            std::unique_ptr<VertexArena> arena;
            std::unique_ptr<StagingPool> staging;
            std::unique_ptr<VertexArena> indexArena;

            struct SortJob {
                i32 x, y, z;
                u32 token;
                std::shared_ptr<const std::vector<float> > centroids;
                float cameraX, cameraY, cameraZ;
            };

            struct SortResult {
                i32 x, y, z;
                u32 token;
                u32 indexCount;
                StagedData indices;
            };

            void sortWork();

            void applySorts();

            std::mutex sortLock;
            std::condition_variable sortWake;
            std::deque<SortJob> sortJobs;
            std::vector<SortResult> sortResults;
            bool sortStopping = false;
            u32 sortGeneration = 0;
            std::thread sortThread;

            struct CloneEntry {
                std::shared_ptr<const SectionClone> data;
                u64 serial;
            };

            static constexpr std::size_t MAX_CLONES = 4096;
            std::unordered_map<u64, CloneEntry> clones;
            std::deque<std::pair<u64, u64> > cloneOrder;
            u64 cloneSerial = 0;
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
            std::vector<Section *> layerVisible[LAYERS];
            std::vector<i32> visibleCoordinates;
            std::vector<GraphNode> graphNodes;
            u64 graphVersion = 1;
            u64 submittedVersion = 0;
            i32 submittedX = INT32_MIN, submittedY = INT32_MIN, submittedZ = INT32_MIN;
            bool submittedSmart = true;
            bool listDirty = true;
            std::unique_ptr<CullResult> cullResult;
            struct DrawRange {
                u32 buffer;
                u32 first;
                u32 count;
            };
            static constexpr u32 BRIDGE_VERTICES = 1024;
            std::vector<DrawRange> drawRanges;
            std::atomic<i32> buildSize{0};
            const std::vector<u32> *activeList = nullptr;
            float lastClip[16] = {};
            SetupParameters lastParameters{};

            std::mutex resultsLock;
            std::vector<std::unique_ptr<MeshResult> > results;
            std::mutex snapshotsLock;
            std::vector<Snapshot *> snapshots;
            std::mutex cameraLock;
            float buildCamera[3] = {};

            u32 drawnLastFrame = 0;
            u64 uploadedBytes = 0;
            Culler culler;
        };
    } // namespace terrain

    using terrain::Terrain;
} // namespace at
