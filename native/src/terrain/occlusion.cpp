#include "terrain/occlusion.h"

namespace at::terrain {
    static u32 floodFill(OpaqueMask &closed, int start, u16 *queue) {
        u32 faces = 0;
        int head = 0, tail = 0;
        queue[tail++] = static_cast<u16>(start);
        closed[static_cast<std::size_t>(start >> 6)] |= 1ull << (start & 63);
        while (head < tail) {
            int index = queue[head++];
            int x = index & 15, z = (index >> 4) & 15, y = (index >> 8) & 15;
            if (x == 0) faces |= 1u << 4;
            else if (x == 15) faces |= 1u << 5;
            if (y == 0) faces |= 1u << 0;
            else if (y == 15) faces |= 1u << 1;
            if (z == 0) faces |= 1u << 2;
            else if (z == 15) faces |= 1u << 3;
            int neighbors[6] = {
                y > 0 ? index - 256 : -1, y < 15 ? index + 256 : -1,
                z > 0 ? index - 16 : -1, z < 15 ? index + 16 : -1,
                x > 0 ? index - 1 : -1, x < 15 ? index + 1 : -1,
            };
            for (int next: neighbors) {
                if (next < 0) continue;
                u64 &word = closed[static_cast<std::size_t>(next >> 6)];
                u64 bit = 1ull << (next & 63);
                if (word & bit) continue;
                word |= bit;
                queue[tail++] = static_cast<u16>(next);
            }
        }
        return faces;
    }

    static Visibility connect(Visibility visibility, u32 faces) {
        for (int from = 0; from < 6; from++) {
            if (!(faces & (1u << from))) continue;
            for (int to = 0; to < 6; to++) {
                if (faces & (1u << to)) visibility |= 1ull << (from + to * 6);
            }
        }
        return visibility;
    }

    Visibility resolveVisibility(const OpaqueMask &opaque, int opaqueCount) {
        if (opaqueCount < 256) return ALL_VISIBLE;
        if (opaqueCount == 4096) return 0;
        OpaqueMask closed = opaque;
        u16 queue[4096];
        Visibility visibility = 0;
        for (int x = 0; x < 16; x++) {
            for (int y = 0; y < 16; y++) {
                for (int z = 0; z < 16; z++) {
                    if (x != 0 && x != 15 && y != 0 && y != 15 && z != 0 && z != 15) continue;
                    int index = x | (y << 8) | (z << 4);
                    if (opaqueAt(closed, index)) continue;
                    visibility = connect(visibility, floodFill(closed, index, queue));
                }
            }
        }
        return visibility;
    }

    u32 openFaces(const OpaqueMask &opaque, int index) {
        OpaqueMask closed = opaque;
        u16 queue[4096];
        return floodFill(closed, index, queue);
    }
} // namespace at::terrain
