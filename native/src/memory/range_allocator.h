#pragma once

#include "core/common.h"

#include <bit>
#include <unordered_map>
#include <vector>

namespace at {
    class RangeAllocator {
    public:
        explicit RangeAllocator(const u64 capacity) : capacity(capacity) {
            for (u32 &head: heads) head = NONE;
            if (capacity > 0) insert(0, capacity);
        }

        static constexpr u64 FAILED = ~0ull;

        u64 allocate(const u64 alloc_size, const u64 alignment) {
            if (alloc_size == 0) return FAILED;
            const u64 padding = alignment > 1 ? alignment - 1 : 0;
            u32 node = find(alloc_size + padding);
            if (node == NONE && padding > 0) node = scan(alloc_size, alignment);
            if (node == NONE) return FAILED;
            const u64 offset = nodes[node].offset;
            const u64 size = nodes[node].size;
            remove(node);
            const u64 aligned = alignUp(offset, alignment);
            const u64 end = aligned + alloc_size;
            if (aligned > offset) insert(offset, aligned - offset);
            if (offset + size > end) insert(end, offset + size - end);
            used += alloc_size;
            return aligned;
        }

        void release(const u64 offset, const u64 size) {
            used -= size;
            u64 start = offset;
            u64 end = offset + size;
            if (auto before = byEnd.find(start); before != byEnd.end()) {
                u32 node = before->second;
                start = nodes[node].offset;
                remove(node);
            }
            if (auto after = byStart.find(end); after != byStart.end()) {
                u32 node = after->second;
                end = nodes[node].offset + nodes[node].size;
                remove(node);
            }
            insert(start, end - start);
        }

        u64 capacity;
        u64 used = 0;

    private:
        static constexpr u32 NONE = ~0u;
        static constexpr u32 SL_BITS = 4;
        static constexpr u32 SL_COUNT = 1u << SL_BITS;
        static constexpr u32 FL_COUNT = 64;

        struct Node {
            u64 offset;
            u64 size;
            u32 previous;
            u32 next;
        };

        static void classify(const u64 size, u32 &fl, u32 &sl) {
            if (size < SL_COUNT) {
                fl = 0;
                sl = static_cast<u32>(size);
                return;
            }
            const u32 top = static_cast<u32>(std::bit_width(size)) - 1;
            sl = static_cast<u32>(size >> (top - SL_BITS)) - SL_COUNT;
            fl = top - SL_BITS + 1;
        }

        u32 find(const u64 request) const {
            u64 size = request;
            if (size >= SL_COUNT) {
                const u32 top = static_cast<u32>(std::bit_width(size)) - 1;
                size += (1ull << (top - SL_BITS)) - 1;
            }
            u32 fl, sl;
            classify(size, fl, sl);
            if (fl >= FL_COUNT) return NONE;
            u32 secondMap = secondLevel[fl] & (~0u << sl);
            if (secondMap == 0) {
                const u64 firstMap = fl + 1 < FL_COUNT ? firstLevel & (~0ull << (fl + 1)) : 0;
                if (firstMap == 0) return scanClass(request);
                fl = static_cast<u32>(std::countr_zero(firstMap));
                secondMap = secondLevel[fl];
            }
            sl = static_cast<u32>(std::countr_zero(secondMap));
            return heads[fl * SL_COUNT + sl];
        }

        u32 scanClass(const u64 size) const {
            u32 fl, sl;
            classify(size, fl, sl);
            for (u32 node = heads[fl * SL_COUNT + sl]; node != NONE; node = nodes[node].next) {
                if (nodes[node].size >= size) return node;
            }
            return NONE;
        }

        u32 scan(const u64 size, const u64 alignment) const {
            u32 fl, sl;
            classify(size, fl, sl);
            for (u32 index = fl * SL_COUNT + sl; index < FL_COUNT * SL_COUNT; index++) {
                for (u32 node = heads[index]; node != NONE; node = nodes[node].next) {
                    const Node &candidate = nodes[node];
                    if (alignUp(candidate.offset, alignment) + size <= candidate.offset + candidate.size) return node;
                }
            }
            return NONE;
        }

        void insert(const u64 offset, const u64 size) {
            u32 node;
            if (!spare.empty()) {
                node = spare.back();
                spare.pop_back();
            } else {
                node = static_cast<u32>(nodes.size());
                nodes.push_back({});
            }
            u32 fl, sl;
            classify(size, fl, sl);
            const u32 index = fl * SL_COUNT + sl;
            nodes[node] = {offset, size, NONE, heads[index]};
            if (heads[index] != NONE) nodes[heads[index]].previous = node;
            heads[index] = node;
            firstLevel |= 1ull << fl;
            secondLevel[fl] |= 1u << sl;
            byStart[offset] = node;
            byEnd[offset + size] = node;
        }

        void remove(const u32 node) {
            const Node &entry = nodes[node];
            u32 fl, sl;
            classify(entry.size, fl, sl);
            const u32 index = fl * SL_COUNT + sl;
            if (entry.previous != NONE) nodes[entry.previous].next = entry.next;
            else heads[index] = entry.next;
            if (entry.next != NONE) nodes[entry.next].previous = entry.previous;
            if (heads[index] == NONE) {
                secondLevel[fl] &= ~(1u << sl);
                if (secondLevel[fl] == 0) firstLevel &= ~(1ull << fl);
            }
            byStart.erase(entry.offset);
            byEnd.erase(entry.offset + entry.size);
            spare.push_back(node);
        }

        u64 firstLevel = 0;
        u32 secondLevel[FL_COUNT] = {};
        u32 heads[FL_COUNT * SL_COUNT];
        std::vector<Node> nodes;
        std::vector<u32> spare;
        std::unordered_map<u64, u32> byStart;
        std::unordered_map<u64, u32> byEnd;
    };
} // namespace at
