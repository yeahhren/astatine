#pragma once

#include "core/common.h"

namespace at::op {
    enum : u32 {
        ENABLE = 1,
        DISABLE = 2,
        ALPHA_FUNC = 3,
        COLOR_MATERIAL = 4,
        DEPTH_FUNC = 5,
        DEPTH_MASK = 6,
        BLEND_FUNC = 7,
        FOG_MODE = 8,
        FOG_DENSITY = 9,
        FOG_START = 10,
        FOG_END = 11,
        FOG_COLOR = 12,
        FOG_RADIAL = 13,
        CULL_FACE = 14,
        POLYGON_OFFSET = 15,
        LOGIC_OP = 16,
        COLOR_MASK = 17,
        TEXGEN_MODE = 18,
        TEXGEN_PLANE = 19,
        ACTIVE_TEXTURE = 20,
        BIND_TEXTURE = 21,
        SHADE_MODEL = 22,
        VIEWPORT = 23,
        LINE_WIDTH = 24,
        CLEAR_COLOR = 25,
        CLEAR_DEPTH = 26,
        COLOR = 27,
        NORMAL = 28,
        TEXCOORD = 29,
        OVERLAY = 30,
        CLEAR_OVERLAY = 31,
        PUSH_ATTRIB = 32,
        POP_ATTRIB = 33,
        MATRIX_MODE = 34,
        LOAD_IDENTITY = 35,
        PUSH_MATRIX = 36,
        POP_MATRIX = 37,
        ORTHO = 38,
        ROTATE = 39,
        SCALE = 40,
        TRANSLATE = 41,
        MULT_MATRIX = 42,
        PERSPECTIVE = 43,
        LIGHT = 44,
        LIGHT_MODEL_AMBIENT = 45,
        TEXTURE_PARAMETER = 46,
        CLEAR = 47,
        BIND_FRAMEBUFFER = 48,
        RELEASE_MESH = 49,

        DRAW = 100,
        DRAW_MESH = 101,
        TEXTURE_ALLOCATE = 102,
        TEXTURE_UPLOAD = 103,
        TEXTURE_DETACH = 104,
        COPY_FROM_TARGET = 105,
        FRAMEBUFFER_RELEASE = 106,
        FRAMEBUFFER_BLIT = 107,
        PRESENT = 108,
        SET_VSYNC = 109,
    };

    constexpr u32 HEADER = 16;
    constexpr u32 FIRST_NATIVE = 100;

    struct Draw {
        u32 layout;
        i32 mode;
        u32 count;
        u32 bytes;
    };

    struct DrawMesh {
        u64 mesh;
        i32 mode;
        u32 pad;
    };

    struct TextureAllocate {
        u64 texture;
        u32 width;
        u32 height;
    };

    struct TextureUpload {
        u64 texture;
        u32 level;
        i32 x;
        i32 y;
        u32 width;
        u32 height;
        u32 pad;
    };

    struct Handle {
        u64 value;
    };

    struct CopyFromTarget {
        u32 level;
        i32 destinationX;
        i32 destinationY;
        i32 x;
        i32 y;
        u32 width;
        u32 height;
        u32 pad;
    };

    struct Blit {
        u64 framebuffer;
        u32 width;
        u32 height;
    };

    struct Flag {
        u32 value;
        u32 pad;
    };
} // namespace at::op
