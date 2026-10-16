#pragma once

#include "core/common.h"

#include <vector>

namespace at {
    class RangeAllocator {
    public:
        explicit RangeAllocator(const u64 capacity) : capacity(capacity) {
            free.push_back({.offset = 0, .size = capacity});
        }

        static constexpr u64 FAILED = ~0ull;

        u64 allocate(const u64 alloc_size, const u64 alignment) {
            for (std::size_t i = 0; i < free.size(); i++) {
                auto &[offset, size] = free[i];
                const u64 aligned = alignUp(offset, alignment);
                const u64 end = aligned + alloc_size;
                const u64 freeEnd = offset + size;
                if (end > freeEnd) continue;
                if (end == freeEnd) {
                    if (aligned == offset) {
                        free.erase(free.begin() + static_cast<std::ptrdiff_t>(i));
                    } else {
                        size = aligned - offset;
                    }
                } else if (aligned == offset) {
                    offset = end;
                    size = freeEnd - end;
                } else {
                    size = aligned - offset;
                    free.insert(free.begin() + static_cast<std::ptrdiff_t>(i) + 1,
                                Range{.offset = end, .size = freeEnd - end});
                }
                used += alloc_size;
                return aligned;
            }
            return FAILED;
        }

        void release(const u64 offset, const u64 size) {
            used -= size;
            std::size_t index = 0;
            std::size_t low = 0, high = free.size();
            while (low < high) {
                if (const std::size_t middle = (low + high) / 2; free[middle].offset < offset) low = middle + 1;
                else high = middle;
            }
            index = low;
            const bool joinsPrevious = index > 0 && free[index - 1].offset + free[index - 1].size == offset;
            if (const bool joinsNext = index < free.size() && offset + size == free[index].offset;
                joinsPrevious && joinsNext) {
                free[index - 1].size += size + free[index].size;
                free.erase(free.begin() + static_cast<std::ptrdiff_t>(index));
            } else if (joinsPrevious) {
                free[index - 1].size += size;
            } else if (joinsNext) {
                free[index].offset = offset;
                free[index].size += size;
            } else {
                free.insert(free.begin() + static_cast<std::ptrdiff_t>(index), Range{.offset = offset, .size = size});
            }
        }

        u64 capacity;
        u64 used = 0;

    private:
        struct Range {
            u64 offset;
            u64 size;
        };

        std::vector<Range> free;
    };
} // namespace at
