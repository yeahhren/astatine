package com.mojang.renderpearl.api.buffers;

import com.mojang.renderpearl.util.UncheckedAutoCloseable;

import java.nio.ByteBuffer;

public record GpuBufferSlice(GpuBuffer buffer, long offset, long length) {
	public GpuBufferSlice slice(long offset, long length) {
		if (offset < 0L || length < 0L || offset + length > this.length) {
			throw new IllegalArgumentException("slice " + offset + "+" + length + " is outside slice of length " + this.length);
		}

		return new GpuBufferSlice(this.buffer, this.offset + offset, length);
	}

	public MappedView map(boolean read, boolean write) {
		return this.buffer.map(this.offset, this.length, read, write);
	}

	public record MappedView(GpuBufferSlice slice, ByteBuffer data, Runnable onClose) implements UncheckedAutoCloseable {
		@Override
		public void close() {
			this.onClose.run();
		}
	}
}
