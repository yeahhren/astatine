#pragma once

#include "core/common.h"

namespace at::key {
    constexpr u64 BLEND = 1ull;
    constexpr u32 SOURCE_COLOR_SHIFT = 1;
    constexpr u32 DESTINATION_COLOR_SHIFT = 6;
    constexpr u32 SOURCE_ALPHA_SHIFT = 11;
    constexpr u32 DESTINATION_ALPHA_SHIFT = 16;
    constexpr u64 DEPTH_TEST = 1ull << 21;
    constexpr u64 DEPTH_WRITE = 1ull << 22;
    constexpr u32 COMPARE_SHIFT = 23;
    constexpr u32 CULL_SHIFT = 26;
    constexpr u32 COLOR_MASK_SHIFT = 28;
    constexpr u64 LOGIC_OP = 1ull << 32;
    constexpr u32 LOGIC_OP_SHIFT = 33;
    constexpr u64 FLAT = 1ull << 37;
    constexpr u64 OFFSET_FILL = 1ull << 38;
    constexpr u64 OFFSET_LINE = 1ull << 39;
    constexpr u32 TOPOLOGY_SHIFT = 40;
    constexpr u32 PASS_SHIFT = 43;
    constexpr u32 LAYOUT_SHIFT = 45;

    constexpr u64 draw(u64 state, u32 topology, u32 pass, u32 layout) {
        return state | (static_cast<u64>(topology) << TOPOLOGY_SHIFT) | (static_cast<u64>(pass) << PASS_SHIFT) | (
                   static_cast<u64>(layout) << LAYOUT_SHIFT);
    }

    constexpr u32 field(u64 key, u32 shift, u32 bits) {
        return static_cast<u32>((key >> shift) & ((1ull << bits) - 1));
    }

    constexpr bool has(u64 key, u64 flag) {
        return (key & flag) != 0;
    }
} // namespace at::key
