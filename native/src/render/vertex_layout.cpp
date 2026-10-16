#include "render/vertex_layout.h"

#include "core/error.h"

namespace at {
    namespace {
        enum Usage : i32 { USAGE_POSITION = 0, USAGE_COLOR = 1, USAGE_UV = 2, USAGE_NORMAL = 3, USAGE_OTHER = 4 };

        enum Type : i32 {
            TYPE_FLOAT = 0, TYPE_UBYTE = 1, TYPE_BYTE = 2, TYPE_USHORT = 3, TYPE_SHORT = 4, TYPE_UINT = 5, TYPE_INT = 6
        };
    }

    static bool isPosition(i32 usage, i32 type, i32 count) {
        return usage == USAGE_POSITION && type == TYPE_FLOAT && count == 3;
    }

    static int location(i32 usage, i32 index) {
        switch (usage) {
            case USAGE_POSITION: return 0;
            case USAGE_COLOR: return 1;
            case USAGE_UV: return index == 0 ? 2 : (index == 1 ? 3 : -1);
            case USAGE_NORMAL: return 4;
            default: return -1;
        }
    }

    static VkFormat vulkanFormat(i32 usage, i32 type, i32 count, i32 index) {
        switch (usage) {
            case USAGE_POSITION:
                return type == TYPE_FLOAT && count == 3 ? VK_FORMAT_R32G32B32_SFLOAT : VK_FORMAT_UNDEFINED;
            case USAGE_COLOR:
                if (type == TYPE_UBYTE && count == 4) return VK_FORMAT_R8G8B8A8_UNORM;
                if (type == TYPE_FLOAT && count == 4) return VK_FORMAT_R32G32B32A32_SFLOAT;
                return VK_FORMAT_UNDEFINED;
            case USAGE_UV:
                if (index == 0 && type == TYPE_FLOAT && count == 2) return VK_FORMAT_R32G32_SFLOAT;
                if (index == 1 && type == TYPE_SHORT && count == 2) return VK_FORMAT_R16G16_SINT;
                return VK_FORMAT_UNDEFINED;
            case USAGE_NORMAL:
                return type == TYPE_BYTE && count == 3 ? VK_FORMAT_R8G8B8A8_SNORM : VK_FORMAT_UNDEFINED;
            default:
                return VK_FORMAT_UNDEFINED;
        }
    }

    void VertexLayout::write(const u8 *source, u8 *positions, u8 *destination, u32 count) const {
        if (!split) {
            std::memcpy(destination, source, static_cast<u64>(count) * stride);
            return;
        }
        const u32 rest = attributeStride;
        for (u32 i = 0; i < count; i++) {
            std::memcpy(positions, source, POSITION_SIZE);
            std::memcpy(destination, source + POSITION_SIZE, rest);
            source += stride;
            positions += POSITION_SIZE;
            destination += rest;
        }
    }

    VertexLayouts::VertexLayouts() {
        auto terrainLayout = std::make_unique<VertexLayout>();
        terrainLayout->id = 0;
        terrainLayout->stride = 16;
        terrainLayout->attributeStride = 16;
        terrainLayout->terrain = true;
        terrainLayout->attributes = {
            {.location = 0, .binding = 0, .format = VK_FORMAT_R32G32B32A32_UINT, .offset = 0},
        };
        layouts.push_back(std::move(terrainLayout));
        descriptors.emplace_back();
        terrainId = 0;

        constexpr i32 position[] = {USAGE_POSITION, TYPE_FLOAT, 3, 0, 0};
        positionId = registerFormat(position, 1, 12);
    }

    u32 VertexLayouts::registerFormat(const i32 *elements, u32 elementCount, u32 vertexSize) {
        std::vector<i32> descriptor(elements, elements + elementCount * 5);
        descriptor.push_back(static_cast<i32>(vertexSize));
        for (std::size_t i = 0; i < descriptors.size(); i++) {
            if (descriptors[i] == descriptor) return static_cast<u32>(i);
        }
        if (layouts.size() >= (1u << 19)) fail("Too many vertex formats");

        auto layout = std::make_unique<VertexLayout>();
        layout->id = static_cast<u32>(layouts.size());
        layout->stride = vertexSize;
        layout->split = elementCount > 0 && elements[4] == 0 && isPosition(elements[0], elements[1], elements[2]);
        for (u32 i = 0; i < elementCount; i++) {
            const i32 *element = elements + i * 5;
            i32 usage = element[0], type = element[1], count = element[2], index = element[3], offset = element[4];
            int at = location(usage, index);
            VkFormat format = vulkanFormat(usage, type, count, index);
            if (at < 0 || format == VK_FORMAT_UNDEFINED) continue;
            if (at == 0 && !isPosition(usage, type, count)) continue;
            bool secondary = layout->split && at != 0;
            u32 shifted = secondary ? static_cast<u32>(offset) - VertexLayout::POSITION_SIZE : static_cast<u32>(offset);
            layout->attributes.push_back({static_cast<u32>(at), secondary ? 1u : 0u, format, shifted});
            switch (at) {
                case 1: layout->shaderMask |= VertexLayout::COLOR;
                    break;
                case 2: layout->shaderMask |= VertexLayout::TEXTURE0;
                    break;
                case 3: layout->shaderMask |= VertexLayout::TEXTURE1;
                    break;
                case 4: layout->shaderMask |= VertexLayout::NORMAL;
                    break;
                default: break;
            }
        }
        layout->attributeStride = layout->split ? vertexSize - VertexLayout::POSITION_SIZE : vertexSize;
        u32 id = layout->id;
        layouts.push_back(std::move(layout));
        descriptors.push_back(std::move(descriptor));
        return id;
    }
} // namespace at
