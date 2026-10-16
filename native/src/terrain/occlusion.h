#pragma once

#include "core/common.h"

#include <array>

namespace at::terrain {
    constexpr int OFFSETS[6][3] = {{0, -1, 0}, {0, 1, 0}, {0, 0, -1}, {0, 0, 1}, {-1, 0, 0}, {1, 0, 0}};

    constexpr int opposite(int direction) {
        return direction ^ 1;
    }

    using OpaqueMask = std::array<u64, 64>;

    AT_INLINE bool opaqueAt(const OpaqueMask &mask, int index) {
        return (mask[static_cast<std::size_t>(index >> 6)] >> (index & 63)) & 1;
    }

    using Visibility = u64;

    constexpr Visibility ALL_VISIBLE = (1ull << 36) - 1;

    AT_INLINE bool visible(Visibility visibility, int from, int to) {
        return (visibility >> (from + to * 6)) & 1;
    }

    Visibility resolveVisibility(const OpaqueMask &opaque, int opaqueCount);

    u32 openFaces(const OpaqueMask &opaque, int index);
} // namespace at::terrain
