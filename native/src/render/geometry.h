#pragma once

#include "core/common.h"
#include "memory/range_allocator.h"
#include "vulkan/memory.h"

#include <memory>
#include <vector>

namespace at {
    class Device;
    struct VertexLayout;

    struct GeometryPool {
        GeometryPool(const Device &device, MemoryAllocator &allocator, bool split, u32 attributeStride, u32 capacity);

        bool split;
        u32 attributeStride;
        u32 capacity;
        std::unique_ptr<GpuBuffer> positions;
        std::unique_ptr<GpuBuffer> attributes;
        RangeAllocator vertices;
    };

    struct Mesh {
        GeometryPool *pool;
        const VertexLayout *layout;
        u32 firstVertex;
        u32 count;
    };

    class GeometryArena {
    public:
        GeometryArena(const Device &device, MemoryAllocator &allocator) : device(device), allocator(allocator) {
        }

        Mesh *allocate(const VertexLayout &layout, u32 count);

        static void release(const Mesh *mesh);

    private:
        static constexpr u32 POOL_VERTICES = 1u << 20;

        const Device &device;
        MemoryAllocator &allocator;
        std::vector<std::unique_ptr<GeometryPool> > pools;
    };
} // namespace at
