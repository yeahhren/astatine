#include "terrain/terrain.h"

#include "core/error.h"
#include "core/log.h"
#include "render/blocks.h"
#include "render/renderer.h"
#include "terrain/snapshot.h"
#include "vulkan/device.h"
#include "vulkan/frame.h"
#include "vulkan/memory.h"

#include <algorithm>
#include <bit>
#include <cmath>
#include <new>

namespace at::terrain {
    static AT_INLINE i32 floorDiv(i32 value, i32 divisor) {
        i32 quotient = value / divisor;
        if ((value % divisor != 0) && ((value < 0) != (divisor < 0))) quotient--;
        return quotient;
    }

    static AT_INLINE i32 floorMod(i32 value, i32 divisor) {
        i32 remainder = value % divisor;
        return remainder < 0 ? remainder + divisor : remainder;
    }

    static AT_INLINE i32 floorToInt(double value) {
        return static_cast<i32>(std::floor(value));
    }

    VertexArena::~VertexArena() = default;

    bool VertexArena::allocate(u32 count, u32 &buffer, u32 &first) {
        for (u32 i = 0; i < buffers.size(); i++) {
            if (u64 offset = buffers[i]->ranges.allocate(count, 1); offset != RangeAllocator::FAILED) {
                buffer = i;
                first = static_cast<u32>(offset);
                return true;
            }
        }
        if (count > BUFFER_VERTICES) return false;
        Context &context = renderer.context();
        auto block = std::make_unique<Block>();
        block->buffer = std::make_unique<GpuBuffer>(*context.device, *context.allocator,
                                                    BUFFER_VERTICES * sizeof(Vertex),
                                                    VK_BUFFER_USAGE_VERTEX_BUFFER_BIT |
                                                    VK_BUFFER_USAGE_TRANSFER_DST_BIT,
                                                    VK_MEMORY_PROPERTY_DEVICE_LOCAL_BIT);
        u64 offset = block->ranges.allocate(count, 1);
        buffers.push_back(std::move(block));
        buffer = static_cast<u32>(buffers.size() - 1);
        first = static_cast<u32>(offset);
        return true;
    }

    void VertexArena::release(u32 buffer, u32 first, u32 count) const {
        buffers[buffer]->ranges.release(first, count);
    }

    VkBuffer VertexArena::handle(u32 buffer) const {
        return buffers[buffer]->buffer->handle;
    }

    u64 VertexArena::usedVertices() const {
        u64 used = 0;
        for (const auto &block: buffers) used += block->ranges.used;
        return used;
    }

    Terrain::Terrain(Renderer &renderer) : renderer(renderer), arena(std::make_unique<VertexArena>(renderer)) {
    }

    Terrain::~Terrain() {
        std::lock_guard guard(snapshotsLock);
        for (Snapshot *snapshot: snapshots) {
            snapshot->~Snapshot();
            ::operator delete(snapshot, static_cast<std::align_val_t>(alignof(Snapshot)));
        }
        snapshots.clear();
    }

    void Terrain::setTables(std::shared_ptr<const Tables> next) {
        std::lock_guard guard(tablesLock);
        tables = std::move(next);
    }

    void Terrain::configure(i32 distance) {
        clear();
        viewDistance = std::max(distance, 2);
        size = viewDistance * 2 + 1;
        sections.clear();
        sections.resize(static_cast<std::size_t>(size) * size * 16);
        centerX = INT32_MIN;
        centerZ = INT32_MIN;
    }

    void Terrain::clear() {
        for (Section &section: sections) {
            releaseGeometry(section);
            section = Section{};
        }
        visible.clear();
        visibleCoordinates.clear();
        centerX = INT32_MIN;
        centerZ = INT32_MIN;
        std::lock_guard guard(resultsLock);
        results.clear();
    }

    Section *Terrain::slot(i32 x, i32 y, i32 z) {
        if (size == 0 || y < 0 || y > 15) return nullptr;
        std::size_t index = (static_cast<std::size_t>(floorMod(z, size)) * size + floorMod(x, size)) * 16 + y;
        return &sections[index];
    }

    Section *Terrain::at(i32 x, i32 y, i32 z) {
        Section *section = slot(x, y, z);
        if (!section || !section->valid || section->x != x || section->z != z) return nullptr;
        return section;
    }

    void Terrain::releaseGeometry(Section &section) const {
        for (SectionLayer &layer: section.layers) {
            if (layer.vertices > 0) {
                VertexArena *target = arena.get();
                u32 buffer = layer.buffer, first = layer.first, count = layer.vertices;
                renderer.defer([target, buffer, first, count] { target->release(buffer, first, count); });
            }
            layer = SectionLayer{};
        }
    }

    void Terrain::reset(Section &section, i32 x, i32 y, i32 z) const {
        releaseGeometry(section);
        u32 version = section.version + 1;
        section = Section{};
        section.x = x;
        section.y = y;
        section.z = z;
        section.valid = true;
        section.version = version;
        section.dirty = true;
    }

    void Terrain::recenter(double x, double z) {
        i32 baseX = floorToInt(x) - 8;
        i32 baseZ = floorToInt(z) - 8;
        if (baseX == centerX && baseZ == centerZ) return;
        centerX = baseX;
        centerZ = baseZ;
        i32 span = size * 16;
        auto coordinate = [span](i32 base, i32 slotIndex) {
            i32 i = slotIndex * 16;
            i32 j = i - base + span / 2;
            if (j < 0) j -= span - 1;
            return i - j / span * span;
        };
        for (i32 sx = 0; sx < size; sx++) {
            i32 blockX = coordinate(baseX, sx);
            for (i32 sz = 0; sz < size; sz++) {
                i32 blockZ = coordinate(baseZ, sz);
                i32 sectionX = blockX >> 4, sectionZ = blockZ >> 4;
                for (i32 y = 0; y < 16; y++) {
                    Section *section = slot(sectionX, y, sectionZ);
                    if (!section->valid || section->x != sectionX || section->z != sectionZ)
                        reset(
                            *section, sectionX, y, sectionZ);
                }
            }
        }
    }

    void Terrain::markDirty(i32 minX, i32 minY, i32 minZ, i32 maxX, i32 maxY, i32 maxZ, bool important) {
        i32 fromX = floorDiv(minX, 16), toX = floorDiv(maxX, 16);
        i32 fromY = std::max(floorDiv(minY, 16), 0), toY = std::min(floorDiv(maxY, 16), 15);
        i32 fromZ = floorDiv(minZ, 16), toZ = floorDiv(maxZ, 16);
        for (i32 x = fromX; x <= toX; x++) {
            for (i32 z = fromZ; z <= toZ; z++) {
                for (i32 y = fromY; y <= toY; y++) {
                    Section *section = at(x, y, z);
                    if (!section) continue;
                    section->version++;
                    section->dirty = true;
                    section->important |= important;
                }
            }
        }
    }

    void Terrain::computeFrustum() {
        RenderState &state = renderer.state;
        float clip[16];
        std::memcpy(clip, state.projection.top(), 64);
        mat4::multiply(clip, state.modelView.top());
        auto row = [&](int index, int column) { return clip[column * 4 + index]; };
        for (int plane = 0; plane < 6; plane++) {
            int axis = plane / 2;
            float sign = (plane & 1) ? -1.0f : 1.0f;
            float length = 0.0f;
            for (int column = 0; column < 4; column++) {
                planes[plane][column] = row(3, column) + sign * row(axis, column);
            }
            length = std::sqrt(
                planes[plane][0] * planes[plane][0] + planes[plane][1] * planes[plane][1] + planes[plane][2] * planes[
                    plane][2]);
            if (length > 0.0f) {
                for (float &value: planes[plane]) value /= length;
            }
        }
    }

    bool Terrain::frustumVisible(const Section &section) const {
        auto minX = static_cast<float>(section.x * 16 - cameraX);
        auto minY = static_cast<float>(section.y * 16 - cameraY);
        auto minZ = static_cast<float>(section.z * 16 - cameraZ);
        float maxX = minX + 16.0f, maxY = minY + 16.0f, maxZ = minZ + 16.0f;
        for (const auto &plane: planes) {
            float x = plane[0] > 0 ? maxX : minX;
            float y = plane[1] > 0 ? maxY : minY;
            if (float z = plane[2] > 0 ? maxZ : minZ; plane[0] * x + plane[1] * y + plane[2] * z + plane[3] < 0.0f) return false;
        }
        return true;
    }

    void Terrain::setup(const SetupParameters &parameters) {
        cameraX = parameters.x;
        cameraY = parameters.y;
        cameraZ = parameters.z;
        if (size == 0) return;
        recenter(cameraX, cameraZ);
        computeFrustum();

        float inverse[16];
        eyeX = cameraX;
        eyeY = cameraY + parameters.eyeHeight;
        eyeZ = cameraZ;
        if (mat4::invert(renderer.state.modelView.top(), inverse)) {
            eyeX = cameraX + inverse[12];
            eyeY = cameraY + inverse[13];
            eyeZ = cameraZ + inverse[14];
        }
        {
            std::lock_guard guard(cameraLock);
            buildCamera[0] = static_cast<float>(eyeX);
            buildCamera[1] = static_cast<float>(eyeY);
            buildCamera[2] = static_cast<float>(eyeZ);
        }

        frame++;
        if (frame == 0) frame = 1;
        visible.clear();
        queue.clear();
        i32 blockX = floorToInt(cameraX), blockY = floorToInt(cameraY + parameters.eyeHeight), blockZ =
                floorToInt(cameraZ);
        i32 originX = floorToInt(cameraX / 16.0) * 16, originZ = floorToInt(cameraZ / 16.0) * 16;
        bool smart = parameters.smartCull;
        i32 range = viewDistance * 16;
        if (Section *start = at(floorDiv(blockX, 16), floorDiv(blockY, 16), floorDiv(blockZ, 16))) {
            u32 faces = 0x3F;
            if (start->opaque)
                faces = openFaces(*start->opaque,
                                  (blockX & 15) | ((blockZ & 15) << 4) | ((blockY & 15) << 8));
            if (std::popcount(faces) == 1 && parameters.lookFace >= 0) faces &= ~(1u << parameters.lookFace);
            if (faces == 0 && !parameters.spectator) {
                visible.push_back(start);
            } else {
                if (parameters.spectator && parameters.cameraInOpaque) smart = false;
                start->frame = frame;
                queue.push_back({start, -1, 0});
            }
        } else {
            i32 layer = blockY > 0 ? 15 : 0;
            for (Section &section: sections) {
                if (!section.valid || section.y != layer || !frustumVisible(section)) continue;
                section.frame = frame;
                queue.push_back({&section, -1, 0});
            }
        }

        for (std::size_t head = 0; head < queue.size(); head++) {
            Node node = queue[head];
            Section &section = *node.section;
            visible.push_back(&section);
            for (int direction = 0; direction < 6; direction++) {
                if (smart && (node.culling & (1u << opposite(direction)))) continue;
                if (smart && node.source >= 0 && !(section.compiled && terrain::visible(
                                                       section.visibility, opposite(node.source), direction)))
                    continue;
                i32 nx = section.x + OFFSETS[direction][0], ny = section.y + OFFSETS[direction][1], nz =
                        section.z + OFFSETS[direction][2];
                if (std::abs(originX - nx * 16) > range || ny < 0 || ny > 15 || std::abs(originZ - nz * 16) > range)
                    continue;
                Section *neighbor = at(nx, ny, nz);
                if (!neighbor || neighbor->frame == frame) continue;
                neighbor->frame = frame;
                if (!frustumVisible(*neighbor)) continue;
                queue.push_back({
                    neighbor, static_cast<i8>(direction), static_cast<u8>(node.culling | (1u << direction))
                });
            }
        }

        visibleCoordinates.resize(visible.size() * 3);
        for (std::size_t i = 0; i < visible.size(); i++) {
            visibleCoordinates[i * 3] = visible[i]->x;
            visibleCoordinates[i * 3 + 1] = visible[i]->y;
            visibleCoordinates[i * 3 + 2] = visible[i]->z;
        }
    }

    u32 Terrain::schedule(i32 *out, u32 max) {
        u32 count = 0;
        i32 cameraSectionX = floorDiv(floorToInt(cameraX), 16);
        i32 cameraSectionY = floorDiv(floorToInt(cameraY), 16);
        i32 cameraSectionZ = floorDiv(floorToInt(cameraZ), 16);
        auto take = [&](Section &section) {
            if (count >= max || !section.dirty || section.building) return;
            bool near = std::abs(section.x - cameraSectionX) <= 1 && std::abs(section.y - cameraSectionY) <= 1 &&
                        std::abs(section.z - cameraSectionZ) <= 1;
            bool urgent = near && section.important && section.compiled;
            i32 *entry = out + count * 5;
            entry[0] = section.x;
            entry[1] = section.y;
            entry[2] = section.z;
            entry[3] = static_cast<i32>(section.version);
            entry[4] = urgent ? 1 : 0;
            section.building = true;
            section.buildingVersion = section.version;
            section.important = false;
            count++;
        };
        for (Section *section: visible) take(*section);
        for (i32 radius = 1; radius <= 2 && count < max; radius++) {
            for (i32 dy = -radius; dy <= radius; dy++) {
                for (i32 dz = -radius; dz <= radius; dz++) {
                    for (i32 dx = -radius; dx <= radius; dx++) {
                        Section *section = at(cameraSectionX + dx, cameraSectionY + dy, cameraSectionZ + dz);
                        if (section) take(*section);
                    }
                }
            }
        }
        return count;
    }

    void Terrain::abort(i32 x, i32 y, i32 z, u32 version) {
        Section *section = at(x, y, z);
        if (section && section->building && section->buildingVersion == version) section->building = false;
    }

    Snapshot *Terrain::acquireSnapshot() {
        {
            std::lock_guard guard(snapshotsLock);
            if (!snapshots.empty()) {
                Snapshot *snapshot = snapshots.back();
                snapshots.pop_back();
                return snapshot;
            }
        }
        void *memory = ::operator new(sizeof(Snapshot), std::align_val_t(alignof(Snapshot)));
        return new(memory) Snapshot();
    }

    void Terrain::releaseSnapshot(Snapshot *snapshot) {
        std::lock_guard guard(snapshotsLock);
        snapshots.push_back(snapshot);
    }

    void Terrain::build(Snapshot *snapshot) {
        std::shared_ptr<const Tables> current;
        {
            std::lock_guard guard(tablesLock);
            current = tables;
        }
        float camera[3];
        {
            std::lock_guard guard(cameraLock);
            std::memcpy(camera, buildCamera, sizeof(camera));
        }
        std::unique_ptr<MeshResult> result;
        if (current) {
            result = mesh(*current, *snapshot, camera[0], camera[1], camera[2]);
        } else {
            result = std::make_unique<MeshResult>();
            result->x = snapshot->sectionX;
            result->y = snapshot->sectionY;
            result->z = snapshot->sectionZ;
            result->version = snapshot->version;
        }
        releaseSnapshot(snapshot);
        std::lock_guard guard(resultsLock);
        results.push_back(std::move(result));
    }

    void Terrain::apply(MeshResult &result) {
        Section *section = at(result.x, result.y, result.z);
        if (!section) return;
        if (section->building && section->buildingVersion == result.version) section->building = false;
        if (result.version < section->builtVersion) return;
        releaseGeometry(*section);
        VkCommandBuffer setup = renderer.setupCommands();
        for (u32 index = 0; index < LAYERS; index++) {
            LayerMesh &mesh = result.layers[index];
            if (mesh.vertices.empty()) continue;
            SectionLayer &layer = section->layers[index];
            u32 count = static_cast<u32>(mesh.vertices.size());
            u32 buffer, first;
            if (!arena->allocate(count, buffer, first)) {
                log::warn("Section %d %d %d does not fit in a terrain buffer", result.x, result.y, result.z);
                continue;
            }
            u64 bytes = u64(count) * sizeof(Vertex);
            VkBuffer staging;
            u64 offset;
            u8 *address = renderer.stage(bytes, staging, offset);
            std::memcpy(address, mesh.vertices.data(), bytes);
            VkBufferCopy region{offset, u64(first) * sizeof(Vertex), bytes};
            vkCmdCopyBuffer(setup, staging, arena->handle(buffer), 1, &region);
            uploadedBytes += bytes;
            layer.buffer = buffer;
            layer.first = first;
            layer.vertices = count;
            std::memcpy(layer.bucketQuads, mesh.bucketQuads, sizeof(layer.bucketQuads));
            if (index == TRANSLUCENT) {
                layer.cpu = std::move(mesh.vertices);
                layer.centroids = std::move(mesh.centroids);
                std::lock_guard guard(cameraLock);
                layer.sortedX = buildCamera[0];
                layer.sortedY = buildCamera[1];
                layer.sortedZ = buildCamera[2];
            }
        }
        renderer.markSetupTransfers();
        section->builtVersion = result.version;
        section->compiled = true;
        section->visibility = result.visibility;
        if (!section->opaque) section->opaque = std::make_unique<OpaqueMask>();
        *section->opaque = result.opaque;
        section->dirty = section->version != section->builtVersion;
    }

    u32 Terrain::upload(u64 budget) {
        std::vector<std::unique_ptr<MeshResult> > pending;
        {
            std::lock_guard guard(resultsLock);
            pending.swap(results);
        }
        u64 spent = 0;
        u32 applied = 0;
        std::size_t index = 0;
        for (; index < pending.size(); index++) {
            if (spent >= budget) break;
            MeshResult &result = *pending[index];
            for (const LayerMesh &layer: result.layers) spent += layer.vertices.size() * sizeof(Vertex);
            apply(result);
            applied++;
        }
        if (index < pending.size()) {
            std::lock_guard guard(resultsLock);
            results.insert(results.begin(),
                           std::make_move_iterator(pending.begin() + static_cast<std::ptrdiff_t>(index)),
                           std::make_move_iterator(pending.end()));
        }
        return applied;
    }

    u32 Terrain::sortTranslucent(u32 limit) const {
        u32 sorted = 0;
        VkCommandBuffer setup = VK_NULL_HANDLE;
        for (Section *section: visible) {
            if (sorted >= limit) break;
            SectionLayer &layer = section->layers[TRANSLUCENT];
            if (layer.vertices == 0 || layer.cpu.empty()) continue;
            auto x = static_cast<float>(eyeX), y = static_cast<float>(eyeY), z = static_cast<float>(eyeZ);
            float dy = y - layer.sortedY, dz = z - layer.sortedZ;
            if (float dx = x - layer.sortedX; dx * dx + dy * dy + dz * dz <= 1.0f) continue;
            sortQuads(layer.cpu, layer.centroids, x - static_cast<float>(section->x * 16),
                      y - static_cast<float>(section->y * 16),
                      z - static_cast<float>(section->z * 16));
            layer.sortedX = x;
            layer.sortedY = y;
            layer.sortedZ = z;
            u32 count = layer.vertices;
            u32 buffer, first;
            if (!arena->allocate(count, buffer, first)) continue;
            VertexArena *target = arena.get();
            u32 oldBuffer = layer.buffer, oldFirst = layer.first;
            renderer.defer([target, oldBuffer, oldFirst, count] { target->release(oldBuffer, oldFirst, count); });
            u64 bytes = static_cast<u64>(count) * sizeof(Vertex);
            VkBuffer staging;
            u64 offset;
            u8 *address = renderer.stage(bytes, staging, offset);
            std::memcpy(address, layer.cpu.data(), bytes);
            if (!setup) setup = renderer.setupCommands();
            VkBufferCopy region{.srcOffset = offset, .dstOffset = static_cast<u64>(first) * sizeof(Vertex), .size = bytes};
            vkCmdCopyBuffer(setup, staging, arena->handle(buffer), 1, &region);
            layer.buffer = buffer;
            layer.first = first;
            sorted++;
        }
        if (sorted > 0) renderer.markSetupTransfers();
        return sorted;
    }

    u32 Terrain::draw(u32 layerIndex) {
        if (visible.empty()) return 0;
        bool translucent = layerIndex == TRANSLUCENT;
        u32 sectionsWithLayer = 0;
        for (Section *section: visible) {
            if (section->layers[layerIndex].vertices > 0) sectionsWithLayer++;
        }
        if (sectionsWithLayer == 0) return 0;
        u64 commandBytes = u64(sectionsWithLayer) * BUCKETS * sizeof(VkDrawIndexedIndirectCommand);
        u64 sectionBytes = u64(sectionsWithLayer) * sizeof(SectionDraw);
        if (!renderer.prepare(commandBytes + 64, sectionBytes)) return 0;
        renderer.bindState(renderer.layouts.terrain(), VK_PRIMITIVE_TOPOLOGY_TRIANGLE_LIST);
        u32 record = renderer.record();
        bool facing = !translucent && renderer.state.cullsBack();

        FrameStream &data = renderer.dataStream();
        FrameStream &sectionStream = renderer.sectionStream();
        u64 sectionOffset = sectionStream.allocate(sectionBytes, sizeof(SectionDraw));
        u64 commandOffset = data.allocate(commandBytes, 4);
        if (sectionOffset == FrameStream::FAILED || commandOffset == FrameStream::FAILED) return 0;
        auto *draws = reinterpret_cast<SectionDraw *>(sectionStream.address(sectionOffset));
        auto *commands = reinterpret_cast<VkDrawIndexedIndirectCommand *>(data.address(commandOffset));
        u32 sectionBase = static_cast<u32>(sectionOffset / sizeof(SectionDraw));
        const Device &device = *renderer.context().device;
        VkCommandBuffer main = renderer.commands();
        renderer.bindQuadIndices(false);

        u32 drawn = 0;
        u32 commandCount = 0;
        u32 runStart = 0;
        u32 runBuffer = ~0u;
        auto flushRun = [&] {
            u32 runCount = commandCount - runStart;
            if (runCount == 0) return;
            renderer.setVertexBuffers(arena->handle(runBuffer), 0, VK_NULL_HANDLE, 0);
            if (device.multiDrawIndirect) {
                for (u32 first = 0; first < runCount;) {
                    u32 batch = std::min(runCount - first, device.maxDrawIndirectCount);
                    vkCmdDrawIndexedIndirect(main, data.handle(),
                                             commandOffset + u64(runStart + first) * sizeof(
                                                 VkDrawIndexedIndirectCommand),
                                             batch, sizeof(VkDrawIndexedIndirectCommand));
                    first += batch;
                }
            } else {
                for (u32 i = runStart; i < commandCount; i++) {
                    const VkDrawIndexedIndirectCommand &command = commands[i];
                    vkCmdDrawIndexed(main, command.indexCount, 1, 0, command.vertexOffset, command.firstInstance);
                }
            }
            runStart = commandCount;
        };

        auto emitSection = [&](const Section &section, u32 slot) {
            const SectionLayer &layer = section.layers[layerIndex];
            SectionDraw &entry = draws[slot];
            entry.offset[0] = static_cast<float>(section.x * 16 - cameraX);
            entry.offset[1] = static_cast<float>(section.y * 16 - cameraY);
            entry.offset[2] = static_cast<float>(section.z * 16 - cameraZ);
            entry.record = record;
            bool show[BUCKETS];
            double minX = section.x * 16.0, minY = section.y * 16.0, minZ = section.z * 16.0;
            show[0] = !facing || eyeY < minY + 18.0;
            show[1] = !facing || eyeY > minY - 2.0;
            show[2] = !facing || eyeZ < minZ + 18.0;
            show[3] = !facing || eyeZ > minZ - 2.0;
            show[4] = !facing || eyeX < minX + 18.0;
            show[5] = !facing || eyeX > minX - 2.0;
            show[6] = true;
            u32 quadStart = 0;
            u32 pendingStart = 0, pendingQuads = 0;
            auto push = [&] {
                if (pendingQuads == 0) return;
                for (u32 offset = 0; offset < pendingQuads;) {
                    u32 run = std::min(pendingQuads - offset, Renderer::MAX_QUADS);
                    commands[commandCount++] = {
                        run * 6, 1, 0, static_cast<i32>(layer.first + (pendingStart + offset) * 4), sectionBase + slot
                    };
                    offset += run;
                }
                pendingQuads = 0;
            };
            for (u32 bucket = 0; bucket < BUCKETS; bucket++) {
                u32 quads = layer.bucketQuads[bucket];
                if (quads == 0) continue;
                if (show[bucket]) {
                    if (pendingQuads == 0) pendingStart = quadStart;
                    if (pendingStart + pendingQuads == quadStart) {
                        pendingQuads += quads;
                    } else {
                        push();
                        pendingStart = quadStart;
                        pendingQuads = quads;
                    }
                } else {
                    push();
                }
                quadStart += quads;
            }
            push();
        };

        u32 slot = 0;
        if (translucent) {
            for (std::size_t i = visible.size(); i-- > 0;) {
                Section &section = *visible[i];
                const SectionLayer &layer = section.layers[layerIndex];
                if (layer.vertices == 0) continue;
                if (layer.buffer != runBuffer) {
                    flushRun();
                    runBuffer = layer.buffer;
                }
                emitSection(section, slot++);
                drawn++;
            }
            flushRun();
        } else {
            for (u32 buffer = 0; buffer < arena->bufferCount(); buffer++) {
                runBuffer = buffer;
                for (Section *section: visible) {
                    const SectionLayer &layer = section->layers[layerIndex];
                    if (layer.vertices == 0 || layer.buffer != buffer) continue;
                    emitSection(*section, slot++);
                    drawn++;
                }
                flushRun();
            }
        }
        drawnLastFrame = drawn;
        return drawn;
    }

    std::string Terrain::debug() const {
        u32 total = 0, compiled = 0, dirty = 0, building = 0;
        for (const Section &section: sections) {
            if (!section.valid) continue;
            total++;
            if (section.compiled) compiled++;
            if (section.dirty) dirty++;
            if (section.building) building++;
        }
        u32 rendered = 0;
        for (const Section *section: visible) {
            bool any = false;
            for (const SectionLayer &layer: section->layers) any |= layer.vertices > 0;
            if (any) rendered++;
        }
        return log::format("C: %u/%u D: %d, B: %u, Q: %u, M: %lluMB", rendered, total, viewDistance, building, dirty,
                           arena->usedVertices() * sizeof(Vertex) >> 20);
    }
} // namespace at::terrain
