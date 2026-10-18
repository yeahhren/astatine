#include "backend/transient.h"

#include "render/renderer.h"
#include "backend/resources.h"
#include "vulkan/memory.h"

#include <algorithm>

namespace at::backend {
    TransientMemory::TransientMemory(u32 frames) : arenas(frames), current(&arenas[0]) {
    }

    TransientMemory::~TransientMemory() {
        for (Arena &arena: arenas) {
            for (Buffer *chunk: arena.chunks) {
                delete chunk->owned;
                delete chunk;
            }
        }
    }

    void TransientMemory::advance() {
        u64 presented = Renderer::get().presentedFrames();
        if (presented == frame) return;
        frame = presented;
        current = &arenas[presented % arenas.size()];
        current->chunk = 0;
        current->offset = 0;
    }

    TransientMemory::Allocation TransientMemory::allocate(u64 size, u64 alignment) {
        advance();
        size = std::max<u64>(size, 4);
        alignment = std::max<u64>(alignment, 4);
        Arena &arena = *current;
        while (true) {
            if (arena.chunk >= arena.chunks.size()) {
                arena.chunks.push_back(Buffer::create(buffer_usage::ALL, std::max(CHUNK_SIZE, size), true,
                                                      "Iris transient memory"));
            }
            Buffer *target = arena.chunks[arena.chunk];
            u64 start = alignUp(arena.offset, alignment);
            if (start + size <= target->size) {
                arena.offset = start + size;
                return {target, start, target->mapped + start};
            }
            if (size > target->size) {
                arena.chunks.insert(arena.chunks.begin() + static_cast<std::ptrdiff_t>(arena.chunk) + 1,
                                    Buffer::create(buffer_usage::ALL, size, true, "Iris transient memory"));
            }
            arena.chunk++;
            arena.offset = 0;
        }
    }
} // namespace at::backend
