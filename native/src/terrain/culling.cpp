#include "terrain/culling.h"

#include <algorithm>
#include <cstdlib>

#ifdef _WIN32
#include <windows.h>
#endif

namespace at::terrain {
    static constexpr u32 ALL_DIRECTIONS = 0x3F;

    static AT_INLINE i32 floorMod(i32 value, i32 divisor) {
        i32 remainder = value % divisor;
        return remainder < 0 ? remainder + divisor : remainder;
    }

    static AT_INLINE u64 straightBit(int from, int to) {
        return 1ull << (from + to * 6);
    }

    static constexpr u64 UP_DOWN = (1ull << (0 + 1 * 6)) | (1ull << (1 + 0 * 6));
    static constexpr u64 NORTH_SOUTH = (1ull << (2 + 3 * 6)) | (1ull << (3 + 2 * 6));
    static constexpr u64 WEST_EAST = (1ull << (4 + 5 * 6)) | (1ull << (5 + 4 * 6));

    static AT_INLINE u64 angleMask(i32 dx, i32 dy, i32 dz, i32 width) {
        i32 margin = 32 * width - 16;
        u64 occluded = 0;
        if (dx > dy + margin || dz > dy + margin) occluded |= UP_DOWN;
        if (dx > dz + margin || dy > dz + margin) occluded |= NORTH_SOUTH;
        if (dy > dx + margin || dz > dx + margin) occluded |= WEST_EAST;
        return ~occluded;
    }

    static AT_INLINE u32 connections(Visibility visibility, u32 incoming) {
        u32 outgoing = 0;
        for (int from = 0; from < 6; from++) {
            if (!((incoming >> from) & 1)) continue;
            for (int to = 0; to < 6; to++) {
                if (visibility & straightBit(from, to)) outgoing |= 1u << to;
            }
        }
        return outgoing;
    }

    static AT_INLINE u32 outward(i32 x, i32 y, i32 z, i32 ox, i32 oy, i32 oz, i32 width) {
        u32 directions = 0;
        if (y <= oy + width) directions |= 1u << 0;
        if (y >= oy - width) directions |= 1u << 1;
        if (z <= oz + width) directions |= 1u << 2;
        if (z >= oz - width) directions |= 1u << 3;
        if (x <= ox + width) directions |= 1u << 4;
        if (x >= ox - width) directions |= 1u << 5;
        return directions;
    }

    Culler::Culler() : worker([this] { run(); }) {
#ifdef _WIN32
        SetThreadPriority(worker.native_handle(), THREAD_PRIORITY_BELOW_NORMAL);
#endif
    }

    Culler::~Culler() {
        {
            std::lock_guard guard(lock);
            stopping = true;
        }
        wake.notify_all();
        if (worker.joinable()) worker.join();
    }

    bool Culler::busy() const {
        std::lock_guard guard(lock);
        return running || pending != nullptr;
    }

    void Culler::submit(std::unique_ptr<CullInput> input) {
        {
            std::lock_guard guard(lock);
            pending = std::move(input);
        }
        wake.notify_one();
    }

    std::unique_ptr<CullResult> Culler::take(bool wait) {
        std::unique_lock guard(lock);
        if (wait) finished.wait(guard, [this] { return done != nullptr || (!running && !pending); });
        return std::move(done);
    }

    void Culler::cancel() {
        std::unique_lock guard(lock);
        pending.reset();
        generation++;
        finished.wait(guard, [this] { return !running; });
        done.reset();
    }

    void Culler::recycle(std::unique_ptr<CullResult> result) {
        std::lock_guard guard(lock);
        if (!spare) spare = std::move(result);
    }

    void Culler::run() {
        std::unique_lock guard(lock);
        while (true) {
            wake.wait(guard, [this] { return stopping || pending != nullptr; });
            if (stopping) return;
            std::unique_ptr<CullInput> input = std::move(pending);
            std::unique_ptr<CullResult> result = std::move(spare);
            if (!result) result = std::make_unique<CullResult>();
            u64 started = generation;
            running = true;
            guard.unlock();
            cull(*input, *result);
            guard.lock();
            running = false;
            if (started == generation) done = std::move(result);
            finished.notify_all();
        }
    }

    void Culler::cull(const CullInput &input, CullResult &result) {
        const std::vector<GraphNode> &nodes = input.nodes;
        std::size_t count = nodes.size();
        if (visited.size() != count) {
            visited.assign(count, 0);
            incomingWide.assign(count, 0);
            incomingRegular.assign(count, 0);
            token = 0;
        }
        if (++token == 0) {
            std::ranges::fill(visited, 0u);
            token = 1;
        }
        result.originX = input.originX;
        result.originY = input.originY;
        result.originZ = input.originZ;
        result.graphVersion = input.graphVersion;
        result.regular.clear();
        result.wide.clear();
        result.member.assign(count, 0);
        current.clear();
        next.clear();
        if (count == 0 || input.size == 0) return;

        const i32 size = input.size;
        const i32 ox = input.originX, oy = input.originY, oz = input.originZ;
        const i32 range = input.viewDistance;
        auto slotOf = [&](i32 x, i32 y, i32 z) -> i32 {
            if (y < 0 || y > 15) return -1;
            i32 slot = (floorMod(z, size) * size + floorMod(x, size)) * 16 + y;
            if (const GraphNode &node = nodes[static_cast<std::size_t>(slot)]; !(node.state & graph::VALID) || node.x != x || node.z != z) return -1;
            return slot;
        };
        auto enqueue = [&](i32 slot, u32 wide, u32 regular) {
            auto index = static_cast<std::size_t>(slot);
            if (visited[index] != token) {
                visited[index] = token;
                incomingWide[index] = 0;
                incomingRegular[index] = 0;
                next.push_back(static_cast<u32>(slot));
            }
            incomingWide[index] |= static_cast<u8>(wide);
            incomingRegular[index] |= static_cast<u8>(regular);
        };

        i32 originSlot = slotOf(ox, oy, oz);
        if (originSlot >= 0) {
            enqueue(originSlot, ALL_DIRECTIONS, ALL_DIRECTIONS);
        } else {
            i32 layer = oy > 15 ? 15 : oy < 0 ? 0 : oy;
            u32 incoming = oy > 15 ? 1u << 1 : oy < 0 ? 1u << 0 : ALL_DIRECTIONS;
            for (i32 dz = -range; dz <= range; dz++) {
                for (i32 dx = -range; dx <= range; dx++) {
                    i32 slot = slotOf(ox + dx, layer, oz + dz);
                    if (slot >= 0) enqueue(slot, incoming, incoming);
                }
            }
        }

        while (!next.empty()) {
            current.swap(next);
            next.clear();
            for (u32 slot: current) {
                const auto &[visibility, x, z, y, state] = nodes[slot];
                const i32 sx = x, sy = y, sz = z;
                u32 inWide = incomingWide[slot];
                u32 inRegular = incomingRegular[slot];
                result.wide.push_back(slot);
                result.member[slot] |= 2;
                if (inRegular) {
                    result.regular.push_back(slot);
                    result.member[slot] |= 1;
                }
                bool compiled = state & graph::COMPILED;
                if (input.smartCull && !compiled) continue;

                u32 outWide, outRegular;
                if (static_cast<i32>(slot) == originSlot) {
                    outWide = ALL_DIRECTIONS;
                    outRegular = ALL_DIRECTIONS;
                } else if (input.smartCull) {
                    i32 dx = std::abs(sx - ox) * 16, dy = std::abs(sy - oy) * 16, dz = std::abs(sz - oz) * 16;
                    outWide = connections(visibility & angleMask(dx, dy, dz, 2), inWide);
                    outRegular = inRegular ? connections(visibility & angleMask(dx, dy, dz, 1), inRegular) : 0;
                } else {
                    outWide = ALL_DIRECTIONS;
                    outRegular = inRegular ? ALL_DIRECTIONS : 0;
                }
                outWide &= outward(sx, sy, sz, ox, oy, oz, 1);
                outRegular &= outward(sx, sy, sz, ox, oy, oz, 0);
                u32 any = outWide | outRegular;
                if (!any) continue;
                for (int direction = 0; direction < 6; direction++) {
                    if (!((any >> direction) & 1)) continue;
                    i32 nx = sx + OFFSETS[direction][0], ny = sy + OFFSETS[direction][1], nz = sz + OFFSETS[direction][2];
                    if (std::abs(nx - ox) > range || std::abs(nz - oz) > range) continue;
                    i32 neighbor = slotOf(nx, ny, nz);
                    if (neighbor < 0) continue;
                    u32 entry = 1u << opposite(direction);
                    enqueue(neighbor, ((outWide >> direction) & 1) ? entry : 0, ((outRegular >> direction) & 1) ? entry : 0);
                }
            }
        }
    }
} // namespace at::terrain
