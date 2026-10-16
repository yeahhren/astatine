#pragma once

#include "core/common.h"
#include "core/vk.h"

#include <memory>
#include <vector>

namespace at {
    struct VertexAttribute {
        u32 location;
        u32 binding;
        VkFormat format;
        u32 offset;
    };

    struct VertexLayout {
        static constexpr u32 POSITION_SIZE = 12;
        static constexpr u32 COLOR = 1;
        static constexpr u32 TEXTURE0 = 2;
        static constexpr u32 TEXTURE1 = 4;
        static constexpr u32 NORMAL = 8;

        u32 id = 0;
        u32 stride = 0;
        u32 shaderMask = 0;
        bool split = false;
        bool terrain = false;
        u32 attributeStride = 0;
        std::vector<VertexAttribute> attributes;

        u64 positionBytes(u32 count) const { return split ? u64(count) * POSITION_SIZE : 0; }
        u64 attributeBytes(u32 count) const { return u64(count) * attributeStride; }
        u32 bufferStride() const { return split ? attributeStride : stride; }

        void write(const u8 *source, u8 *positions, u8 *destination, u32 count) const;
    };

    class VertexLayouts {
    public:
        VertexLayouts();

        u32 registerFormat(const i32 *elements, u32 elementCount, u32 vertexSize);

        const VertexLayout &operator[](u32 id) const { return *layouts[id]; }
        [[nodiscard]] const std::vector<i32> &descriptor(u32 id) const { return descriptors[id]; }
        [[nodiscard]] u32 count() const { return static_cast<u32>(layouts.size()); }
        [[nodiscard]] u32 terrainLayout() const { return terrainId; }
        [[nodiscard]] const VertexLayout &terrain() const { return *layouts[terrainId]; }
        [[nodiscard]] const VertexLayout &position() const { return *layouts[positionId]; }

    private:
        std::vector<std::unique_ptr<VertexLayout> > layouts;
        std::vector<std::vector<i32> > descriptors;
        u32 terrainId = 0;
        u32 positionId = 0;
    };
} // namespace at
