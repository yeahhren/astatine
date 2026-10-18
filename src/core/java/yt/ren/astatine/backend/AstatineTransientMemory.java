package yt.ren.astatine.backend;

import com.mojang.renderpearl.api.buffers.GpuBuffer;
import com.mojang.renderpearl.api.buffers.GpuBufferSlice;
import com.mojang.renderpearl.api.buffers.TransientMemory;
import yt.ren.astatine.memory.Memory;

import java.nio.ByteBuffer;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class AstatineTransientMemory implements TransientMemory {
	private static final int USAGE = GpuBuffer.USAGE_UNIFORM | GpuBuffer.USAGE_VERTEX | GpuBuffer.USAGE_INDEX | GpuBuffer.USAGE_COPY_SRC
		| GpuBuffer.USAGE_COPY_DST | GpuBuffer.USAGE_UNIFORM_TEXEL_BUFFER | GpuBuffer.USAGE_INDIRECT_PARAMETERS | AstatineBuffer.USAGE_STORAGE;
	private final Map<Long, AstatineBuffer> chunks = new HashMap<>();
	private final Scratch out = new Scratch(64);

	public record Allocation(AstatineBuffer buffer, long offset, long address, long size) {
		public GpuBufferSlice slice() {
			return this.buffer.slice(this.offset, this.size);
		}
	}

	public Allocation allocate(long size, long alignment) {
		long bytes = Math.max(size, 4L);
		long address = this.out.address();
		BackendNatives.transientAllocate(bytes, Math.max(alignment, 4L), address);
		long handle = Memory.INSTANCE.getLong(address);
		long offset = Memory.INSTANCE.getLong(address + 8);
		long mapped = Memory.INSTANCE.getLong(address + 16);
		long chunkSize = Memory.INSTANCE.getLong(address + 24);
		var buffer = this.chunks.computeIfAbsent(handle, key -> AstatineBuffer.borrowed(key, chunkSize, USAGE));
		return new Allocation(buffer, offset, mapped, bytes);
	}

	@Override
	public GpuBufferSlice allocateGpu(long size, long alignment, int usage, long minimumAllocation, long elementSize) {
		return this.allocate(Math.max(size, minimumAllocation), alignment).slice();
	}

	@Override
	public GpuBufferSlice uploadGpu(List<ByteBuffer> data, long alignment, int usage, long minimumAllocation, long elementSize) {
		long total = 0;

		for (ByteBuffer buffer : data) {
			total += buffer.remaining();
		}

		var allocation = this.allocate(Math.max(total, minimumAllocation), alignment);
		long address = allocation.address();

		for (ByteBuffer buffer : data) {
			copy(buffer, address);
			address += buffer.remaining();
		}

		return allocation.slice();
	}

	static void copy(ByteBuffer source, long destination) {
		int count = source.remaining();

		if (count == 0) {
			return;
		}

		if (source.isDirect()) {
			Memory.INSTANCE.copy(Memory.INSTANCE.address(source) + source.position(), destination, count);
		} else {
			var bytes = new byte[count];
			source.duplicate().get(bytes);
			Memory.INSTANCE.copy(bytes, destination);
		}
	}
}
