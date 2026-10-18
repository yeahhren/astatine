package com.mojang.renderpearl.backend.vulkan;

import com.mojang.renderpearl.api.buffers.GpuBuffer;
import com.mojang.renderpearl.api.buffers.GpuBufferSlice;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.system.MemoryUtil;
import org.lwjgl.util.vma.Vma;
import org.lwjgl.util.vma.VmaAllocationCreateInfo;
import org.lwjgl.util.vma.VmaAllocationInfo;
import org.lwjgl.vulkan.VK10;
import org.lwjgl.vulkan.VkBufferCreateInfo;

import java.util.function.Supplier;

public abstract class VulkanGpuBuffer implements GpuBuffer, Destroyable {
	private final long vkBuffer;
	private final int usage;
	private final long size;

	public VulkanGpuBuffer(long vkBuffer, int usage, long size) {
		this.vkBuffer = vkBuffer;
		this.usage = usage;
		this.size = size;
	}

	public long vkBuffer() {
		return this.vkBuffer;
	}

	@Override
	public long size() {
		return this.size;
	}

	@Override
	public int usage() {
		return this.usage;
	}

	public static class Direct extends VulkanGpuBuffer {
		private final VulkanDevice device;
		private final long allocation;
		private final long mapped;
		private boolean closed;

		private Direct(VulkanDevice device, long[] created, int usage, long size) {
			super(created[0], usage, size);
			this.device = device;
			this.allocation = created[1];
			this.mapped = created[2];
		}

		public Direct(VulkanDevice device, Supplier<String> label, int usage, long size, boolean hostVisible) {
			this(device, create(device, label, usage, size, hostVisible), usage, size);
		}

		private static long[] create(VulkanDevice device, Supplier<String> label, int usage, long size, boolean hostVisible) {
			try (var stack = MemoryStack.stackPush()) {
				int vkUsage = VulkanConst.bufferUsageToVk(usage) | VK10.VK_BUFFER_USAGE_TRANSFER_DST_BIT | VK10.VK_BUFFER_USAGE_TRANSFER_SRC_BIT;

				if ((usage & VulkanGpuBuffer.USAGE_STORAGE) != 0) {
					vkUsage |= VK10.VK_BUFFER_USAGE_STORAGE_BUFFER_BIT;
				}

				var info = VkBufferCreateInfo.calloc(stack).sType$Default().size(Math.max(4, size)).usage(vkUsage)
					.sharingMode(VK10.VK_SHARING_MODE_EXCLUSIVE);
				boolean mapped = hostVisible || (usage & (GpuBuffer.USAGE_MAP_READ | GpuBuffer.USAGE_MAP_WRITE)) != 0;
				var allocationInfo = VmaAllocationCreateInfo.calloc(stack).usage(Vma.VMA_MEMORY_USAGE_AUTO);

				if (mapped) {
					allocationInfo.flags(Vma.VMA_ALLOCATION_CREATE_MAPPED_BIT | ((usage & GpuBuffer.USAGE_MAP_READ) != 0
						? Vma.VMA_ALLOCATION_CREATE_HOST_ACCESS_RANDOM_BIT : Vma.VMA_ALLOCATION_CREATE_HOST_ACCESS_SEQUENTIAL_WRITE_BIT));
				}

				var buffer = stack.mallocLong(1);
				var allocation = stack.mallocPointer(1);
				var result = VmaAllocationInfo.calloc(stack);
				VulkanUtils.check(Vma.vmaCreateBuffer(device.vma(), info, allocationInfo, buffer, allocation, result), "create buffer");

				if (label != null) {
					device.instance().debug().setObjectName(device.vkDevice(), VK10.VK_OBJECT_TYPE_BUFFER, buffer.get(0), label.get());
				}

				return new long[]{buffer.get(0), allocation.get(0), mapped ? result.pMappedData() : 0};
			}
		}

		@Override
		public boolean isClosed() {
			return this.closed;
		}

		@Override
		public void close() {
			if (!this.closed) {
				this.closed = true;
				this.device.createCommandEncoder().queueForDestroy(this);
			}
		}

		@Override
		public void destroy() {
			Vma.vmaDestroyBuffer(this.device.vma(), this.vkBuffer(), this.allocation);
		}

		public long allocation() {
			return this.allocation;
		}

		public long mappedAddress() {
			return this.mapped;
		}

		@Override
		public GpuBufferSlice.MappedView map(long offset, long length, boolean read, boolean write) {
			if (this.mapped == 0) {
				throw new IllegalStateException("buffer is not host visible");
			}

			if (read) {
				Vma.vmaInvalidateAllocation(this.device.vma(), this.allocation, offset, length);
			}

			return new GpuBufferSlice.MappedView(this.slice(offset, length), MemoryUtil.memByteBuffer(this.mapped + offset, Math.toIntExact(length)),
				() -> {
					if (write) {
						Vma.vmaFlushAllocation(this.device.vma(), this.allocation, offset, length);
					}
				});
		}
	}

	public static final int USAGE_STORAGE = 1 << 20;

	public static class Foreign extends VulkanGpuBuffer {
		private boolean closed;

		public Foreign(long vkBuffer, int usage, long size) {
			super(vkBuffer, usage, size);
		}

		@Override
		public boolean isClosed() {
			return this.closed;
		}

		@Override
		public void close() {
			this.closed = true;
		}

		@Override
		public void destroy() {
		}

		@Override
		public GpuBufferSlice.MappedView map(long offset, long length, boolean read, boolean write) {
			throw new UnsupportedOperationException("native buffers cannot be mapped from Java");
		}
	}
}
