package com.mojang.renderpearl.api.buffers;

import java.nio.ByteBuffer;
import java.util.List;

public interface TransientMemory {
	default GpuBufferSlice allocateGpu(long size, long alignment, int usage) {
		return this.allocateGpu(size, alignment, usage, size, 1L);
	}

	GpuBufferSlice allocateGpu(long size, long alignment, int usage, long minimumAllocation, long elementSize);

	default GpuBufferSlice uploadGpu(ByteBuffer data, long alignment, int usage) {
		return this.uploadGpu(data, alignment, usage, data.remaining(), 1L);
	}

	default GpuBufferSlice uploadGpu(ByteBuffer data, long alignment, int usage, long minimumAllocation, long elementSize) {
		return this.uploadGpu(List.of(data), alignment, usage, minimumAllocation, elementSize);
	}

	default GpuBufferSlice uploadGpu(List<ByteBuffer> data, long alignment, int usage) {
		long total = 0L;

		for (ByteBuffer buffer : data) {
			total += buffer.remaining();
		}

		return this.uploadGpu(data, alignment, usage, total, 1L);
	}

	GpuBufferSlice uploadGpu(List<ByteBuffer> data, long alignment, int usage, long minimumAllocation, long elementSize);
}
