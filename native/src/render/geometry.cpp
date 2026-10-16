#include "render/geometry.h"

#include "render/vertex_layout.h"

#include <algorithm>

namespace at {
    static constexpr VkBufferUsageFlags POOL_USAGE =
            VK_BUFFER_USAGE_VERTEX_BUFFER_BIT | VK_BUFFER_USAGE_TRANSFER_DST_BIT;

    GeometryPool::GeometryPool(const Device &device, MemoryAllocator &allocator, bool split, u32 attributeStride,
                               u32 capacity)
        : split(split), attributeStride(attributeStride), capacity(capacity), vertices(capacity) {
        if (split) {
            positions = std::make_unique<GpuBuffer>(device, allocator,
                                                    static_cast<u64>(capacity) * VertexLayout::POSITION_SIZE,
                                                    POOL_USAGE,
                                                    VK_MEMORY_PROPERTY_DEVICE_LOCAL_BIT);
        }
        if (attributeStride > 0) {
            attributes = std::make_unique<GpuBuffer>(device, allocator, static_cast<u64>(capacity) * attributeStride,
                                                     POOL_USAGE,
                                                     VK_MEMORY_PROPERTY_DEVICE_LOCAL_BIT);
        }
    }

    Mesh *GeometryArena::allocate(const VertexLayout &layout, u32 count) {
        u32 stride = layout.bufferStride();
        for (auto &pool: pools) {
            if (pool->attributeStride != stride || pool->split != layout.split) continue;
            if (u64 first = pool->vertices.allocate(count, 1); first != RangeAllocator::FAILED) return new Mesh{
                .pool = pool.get(), .layout = &layout, .firstVertex = static_cast<u32>(first), .count = count
            };
        }
        auto &pool = pools.emplace_back(
            std::make_unique<GeometryPool>(device, allocator, layout.split, stride, std::max(
                                               POOL_VERTICES, count)));
        u64 first = pool->vertices.allocate(count, 1);
        return new Mesh{.pool = pool.get(), .layout = &layout, .firstVertex = static_cast<u32>(first), .count = count};
    }

    void GeometryArena::release(const Mesh *mesh) {
        mesh->pool->vertices.release(mesh->firstVertex, mesh->count);
        delete mesh;
    }
} // namespace at
