package yt.ren.astatine.backend;

import com.mojang.renderpearl.api.buffers.GpuBuffer;
import com.mojang.renderpearl.api.buffers.GpuBufferSlice;
import org.lwjgl.system.MemoryUtil;

import java.util.function.Supplier;

public class AstatineBuffer implements GpuBuffer {
	public static final int USAGE_STORAGE = 1 << 20;
	private final long handle;
	private final long size;
	private final int usage;
	private final long address;
	private final boolean releasable;
	private boolean closed;

	private AstatineBuffer(long handle, long size, int usage, long address, boolean releasable) {
		this.handle = handle;
		this.size = size;
		this.usage = usage;
		this.address = address;
		this.releasable = releasable;
	}

	public static AstatineBuffer create(Supplier<String> label, int usage, long size, boolean hostVisible) {
		long handle = BackendNatives.bufferCreate(usage, size, hostVisible, label == null ? null : label.get());
		return new AstatineBuffer(handle, size, usage, BackendNatives.bufferAddress(handle), true);
	}

	public static AstatineBuffer adopt(long handle, long size, int usage) {
		return new AstatineBuffer(handle, size, usage, BackendNatives.bufferAddress(handle), true);
	}

	public static AstatineBuffer wrap(long vulkanBuffer, long size, int usage) {
		return new AstatineBuffer(BackendNatives.bufferWrap(vulkanBuffer, size, usage), size, usage, 0L, true);
	}

	public static AstatineBuffer borrowed(long handle, long size, int usage) {
		return new AstatineBuffer(handle, size, usage, BackendNatives.bufferAddress(handle), false);
	}

	public long handle() {
		return this.handle;
	}

	public long mappedAddress() {
		return this.address;
	}

	@Override
	public long size() {
		return this.size;
	}

	@Override
	public int usage() {
		return this.usage;
	}

	@Override
	public boolean isClosed() {
		return this.closed;
	}

	@Override
	public GpuBufferSlice.MappedView map(long offset, long length, boolean read, boolean write) {
		if (this.address == 0L) {
			throw new IllegalStateException("buffer is not host visible");
		}

		return new GpuBufferSlice.MappedView(this.slice(offset, length), MemoryUtil.memByteBuffer(this.address + offset,
			Math.toIntExact(length)), () -> {
		});
	}

	@Override
	public void close() {
		if (this.closed) {
			return;
		}

		this.closed = true;

		if (this.releasable) {
			BackendNatives.bufferRelease(this.handle);
		}
	}
}
