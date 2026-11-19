#pragma once

#include "core/common.h"

#include <vector>

namespace at::backend {
    struct Buffer;

    class TransientMemory {
    public:
        static constexpr u64 CHUNK_SIZE = 4ull << 20;

        explicit TransientMemory(u32 frames);

        ~TransientMemory();

        TransientMemory(const TransientMemory &) = delete;

        TransientMemory &operator=(const TransientMemory &) = delete;

        struct Allocation {
            Buffer *buffer;
            u64 offset;
            u8 *address;
        };

        Allocation allocate(u64 size, u64 alignment);

    private:
        struct Arena {
            std::vector<Buffer *> chunks;
            std::size_t chunk = 0;
            u64 offset = 0;
        };

        void advance();

        std::vector<Arena> arenas;
        Arena *current;
        u64 frame = ~0ull;
    };
} // namespace at::backend
