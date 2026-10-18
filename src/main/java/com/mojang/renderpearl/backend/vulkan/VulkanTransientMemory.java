package com.mojang.renderpearl.backend.vulkan;

import com.mojang.renderpearl.api.buffers.GpuBuffer;
import com.mojang.renderpearl.api.buffers.GpuBufferSlice;
import com.mojang.renderpearl.api.buffers.TransientMemory;
import org.lwjgl.system.MemoryUtil;
import org.lwjgl.util.vma.Vma;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;

public class VulkanTransientMemory implements TransientMemory {
	private static final long CHUNK_SIZE = 4L << 20;
	private static final int USAGE = GpuBuffer.USAGE_UNIFORM | GpuBuffer.USAGE_VERTEX | GpuBuffer.USAGE_INDEX | GpuBuffer.USAGE_COPY_SRC
		| GpuBuffer.USAGE_COPY_DST | GpuBuffer.USAGE_UNIFORM_TEXEL_BUFFER | GpuBuffer.USAGE_INDIRECT_PARAMETERS | VulkanGpuBuffer.USAGE_STORAGE;
	private final VulkanDevice device;
	private final Arena[] arenas;
	private Arena current;

	public VulkanTransientMemory(VulkanDevice device, int frames) {
		this.device = device;
		this.arenas = new Arena[frames];

		for (int i = 0; i < frames; i++) {
			this.arenas[i] = new Arena();
		}

		this.current = this.arenas[0];
	}

	public void beginFrame(long frame) {
		this.current = this.arenas[(int) Math.floorMod(frame, (long) this.arenas.length)];
		this.current.reset();
	}

	@Override
	public GpuBufferSlice allocateGpu(long size, long alignment, int usage, long minimumAllocation, long elementSize) {
		return this.current.allocate(Math.max(size, minimumAllocation), Math.max(alignment, 4)).slice();
	}

	@Override
	public GpuBufferSlice uploadGpu(List<ByteBuffer> data, long alignment, int usage, long minimumAllocation, long elementSize) {
		long total = 0;

		for (ByteBuffer buffer : data) {
			total += buffer.remaining();
		}

		var allocation = this.current.allocate(Math.max(Math.max(total, minimumAllocation), 4), Math.max(alignment, 4));
		long address = allocation.address();

		for (ByteBuffer buffer : data) {
			MemoryUtil.memCopy(MemoryUtil.memAddress(buffer), address, buffer.remaining());
			address += buffer.remaining();
		}

		allocation.flush();
		return allocation.slice();
	}

	public Allocation allocateMapped(long size, long alignment) {
		return this.current.allocate(Math.max(size, 4), Math.max(alignment, 4));
	}

	public record Allocation(VulkanDevice device, VulkanGpuBuffer.Direct buffer, long allocationHandle, long offset, long size) {
		public long address() {
			return this.buffer.mappedAddress() + this.offset;
		}

		public GpuBufferSlice slice() {
			return this.buffer.slice(this.offset, this.size);
		}

		public void flush() {
			Vma.vmaFlushAllocation(this.device.vma(), this.allocationHandle, this.offset, this.size);
		}
	}

	private final class Arena {
		private final List<Chunk> chunks = new ArrayList<>();
		private int chunk;
		private long offset;

		void reset() {
			this.chunk = 0;
			this.offset = 0;
		}

		Allocation allocate(long size, long alignment) {
			while (true) {
				if (this.chunk >= this.chunks.size()) {
					this.chunks.add(new Chunk(Math.max(CHUNK_SIZE, size)));
				}

				var target = this.chunks.get(this.chunk);
				long start = (this.offset + alignment - 1) / alignment * alignment;

				if (start + size <= target.buffer.size()) {
					this.offset = start + size;
					return new Allocation(VulkanTransientMemory.this.device, target.buffer, target.buffer.allocation(), start, size);
				}

				if (size > target.buffer.size()) {
					this.chunks.add(this.chunk + 1, new Chunk(size));
				}

				this.chunk++;
				this.offset = 0;
			}
		}
	}

	private final class Chunk {
		private final VulkanGpuBuffer.Direct buffer;

		Chunk(long size) {
			this.buffer = new VulkanGpuBuffer.Direct(VulkanTransientMemory.this.device, () -> "Iris transient memory", USAGE, size, true);
		}
	}
}
