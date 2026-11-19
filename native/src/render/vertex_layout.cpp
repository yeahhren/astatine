#include "render/vertex_layout.h"

#include "core/error.h"

#include <cmath>
#include <cstring>

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

    static i8 packComponent(float value) {
        return static_cast<i8>(static_cast<i32>(value * 127.0f));
    }

    // iris logic
    static u32 computeTangent(float normalX, float normalY, float normalZ, const float (*position)[3], const float (*uv)[2],
                              bool smooth) {
        float x0 = position[0][0], y0 = position[0][1], z0 = position[0][2];
        float x1 = position[1][0], y1 = position[1][1], z1 = position[1][2];
        float x2 = position[2][0], y2 = position[2][1], z2 = position[2][2];
        if (smooth) {
            float d0 = x0 * normalX + y0 * normalY + z0 * normalZ;
            float d1 = x1 * normalX + y1 * normalY + z1 * normalZ;
            float d2 = x2 * normalX + y2 * normalY + z2 * normalZ;
            x0 -= d0 * normalX;
            y0 -= d0 * normalY;
            z0 -= d0 * normalZ;
            x1 -= d1 * normalX;
            y1 -= d1 * normalY;
            z1 -= d1 * normalZ;
            x2 -= d2 * normalX;
            y2 -= d2 * normalY;
            z2 -= d2 * normalZ;
        }
        float edge1x = x1 - x0, edge1y = y1 - y0, edge1z = z1 - z0;
        float edge2x = x2 - x0, edge2y = y2 - y0, edge2z = z2 - z0;
        float deltaU1 = uv[1][0] - uv[0][0];
        float deltaV1 = uv[1][1] - uv[0][1];
        float deltaU2 = uv[2][0] - uv[0][0];
        float deltaV2 = uv[2][1] - uv[0][1];
        float fdenom = deltaU1 * deltaV2 - deltaU2 * deltaV1;
        float f = fdenom == 0.0f ? 1.0f : 1.0f / fdenom;
        float tangentx = f * (deltaV2 * edge1x - deltaV1 * edge2x);
        float tangenty = f * (deltaV2 * edge1y - deltaV1 * edge2y);
        float tangentz = f * (deltaV2 * edge1z - deltaV1 * edge2z);
        float tcoeff = 1.0f / std::sqrt(tangentx * tangentx + tangenty * tangenty + tangentz * tangentz);
        tangentx *= tcoeff;
        tangenty *= tcoeff;
        tangentz *= tcoeff;
        float bitangentx = f * (-deltaU2 * edge1x + deltaU1 * edge2x);
        float bitangenty = f * (-deltaU2 * edge1y + deltaU1 * edge2y);
        float bitangentz = f * (-deltaU2 * edge1z + deltaU1 * edge2z);
        float bitcoeff = 1.0f / std::sqrt(bitangentx * bitangentx + bitangenty * bitangenty + bitangentz * bitangentz);
        bitangentx *= bitcoeff;
        bitangenty *= bitcoeff;
        bitangentz *= bitcoeff;
        float pbitangentx = tangenty * normalZ - tangentz * normalY;
        float pbitangenty = tangentz * normalX - tangentx * normalZ;
        float pbitangentz = tangentx * normalY - tangenty * normalX;
        float dot = bitangentx * pbitangentx + bitangenty * pbitangenty + bitangentz * pbitangentz;
        float tangentW = dot < 0 ? -1.0f : 1.0f;
        i8 packed[4] = {packComponent(tangentx), packComponent(tangenty), packComponent(tangentz), packComponent(tangentW)};
        u32 result;
        std::memcpy(&result, packed, 4);
        return result;
    }

    void VertexLayout::writeTangents(const u8 *source, u8 *destination, u32 count, u32 corners) const {
        float position[4][3];
        float uv[4][2];
        u32 group = corners == 3 || corners == 4 ? corners : 1;
        for (u32 first = 0; first + group <= count; first += group) {
            float midU = 0, midV = 0;
            for (u32 k = 0; k < group; k++) {
                const u8 *vertex = source + static_cast<u64>(first + k) * stride;
                std::memcpy(position[k], vertex, 12);
                std::memcpy(uv[k], vertex + sourceUv, 8);
                midU += uv[k][0];
                midV += uv[k][1];
            }
            midU /= static_cast<float>(group);
            midV /= static_cast<float>(group);
            u32 tangent = 0x7F000000u;
            if (group == 4) {
                float dx0 = position[2][0] - position[0][0], dy0 = position[2][1] - position[0][1], dz0 = position[2][2] - position[0][2];
                float dx1 = position[3][0] - position[1][0], dy1 = position[3][1] - position[1][1], dz1 = position[3][2] - position[1][2];
                float faceX = dy0 * dz1 - dz0 * dy1;
                float faceY = dz0 * dx1 - dx0 * dz1;
                float faceZ = dx0 * dy1 - dy0 * dx1;
                float length = std::sqrt(faceX * faceX + faceY * faceY + faceZ * faceZ);
                if (length > 0) {
                    faceX /= length;
                    faceY /= length;
                    faceZ /= length;
                }
                tangent = computeTangent(faceX, faceY, faceZ, position, uv, false);
            }
            for (u32 k = 0; k < group; k++) {
                u8 *target = destination + static_cast<u64>(first + k) * attributeStride + tangentOffset;
                if (group == 3) {
                    const i8 *normal = reinterpret_cast<const i8 *>(source + static_cast<u64>(first + k) * stride + sourceNormal);
                    tangent = computeTangent(normal[0] / 127.0f, normal[1] / 127.0f, normal[2] / 127.0f, position, uv, true);
                }
                std::memcpy(target, &tangent, 4);
                std::memcpy(target + 4, &midU, 4);
                std::memcpy(target + 8, &midV, 4);
            }
        }
    }

    void VertexLayout::write(const u8 *source, u8 *positions, u8 *destination, u32 count, u32 corners) const {
        if (!split) {
            std::memcpy(destination, source, static_cast<u64>(count) * stride);
            return;
        }
        const u32 rest = stride - POSITION_SIZE;
        const u8 *start = source;
        u8 *attributesStart = destination;
        for (u32 i = 0; i < count; i++) {
            std::memcpy(positions, source, POSITION_SIZE);
            std::memcpy(destination, source + POSITION_SIZE, rest);
            source += stride;
            positions += POSITION_SIZE;
            destination += attributeStride;
        }
        if (tangents) writeTangents(start, attributesStart, count, corners);
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

        auto extendedLayout = std::make_unique<VertexLayout>();
        extendedLayout->id = static_cast<u32>(layouts.size());
        extendedLayout->stride = EXTENDED_TERRAIN_STRIDE;
        extendedLayout->attributeStride = EXTENDED_TERRAIN_STRIDE;
        extendedLayout->terrain = true;
        extendedLayout->attributes = {
            {.location = 0, .binding = 0, .format = VK_FORMAT_R32G32B32A32_UINT, .offset = 0},
        };
        extendedTerrainId = extendedLayout->id;
        layouts.push_back(std::move(extendedLayout));
        descriptors.push_back({EXTENDED_TERRAIN_DESCRIPTOR});

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
        if (layout->split && (layout->shaderMask & VertexLayout::TEXTURE0) && (layout->shaderMask & VertexLayout::NORMAL)) {
            for (u32 i = 0; i < elementCount; i++) {
                const i32 *element = elements + i * 5;
                if (location(element[0], element[3]) == 2) layout->sourceUv = static_cast<u32>(element[4]);
                if (location(element[0], element[3]) == 4) layout->sourceNormal = static_cast<u32>(element[4]);
            }
            layout->tangents = true;
            layout->tangentOffset = layout->attributeStride;
            layout->attributeStride += 12;
        }
        u32 id = layout->id;
        layouts.push_back(std::move(layout));
        descriptors.push_back(std::move(descriptor));
        return id;
    }
} // namespace at
