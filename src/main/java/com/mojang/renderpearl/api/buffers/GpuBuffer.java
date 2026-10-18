package com.mojang.renderpearl.api.buffers;

import com.mojang.renderpearl.util.UncheckedAutoCloseable;

public interface GpuBuffer extends UncheckedAutoCloseable {
	int USAGE_MAP_READ = 1;
	int USAGE_MAP_WRITE = 2;
	int USAGE_HINT_CLIENT_STORAGE = 4;
	int USAGE_COPY_DST = 8;
	int USAGE_COPY_SRC = 16;
	int USAGE_VERTEX = 32;
	int USAGE_INDEX = 64;
	int USAGE_UNIFORM = 128;
	int USAGE_UNIFORM_TEXEL_BUFFER = 256;
	int USAGE_INDIRECT_PARAMETERS = 512;

	long size();

	int usage();

	boolean isClosed();

	default GpuBufferSlice slice(long offset, long length) {
		if (offset < 0L || length < 0L || offset + length > this.size()) {
			throw new IllegalArgumentException("slice " + offset + "+" + length + " is outside buffer of size " + this.size());
		}

		return new GpuBufferSlice(this, offset, length);
	}

	default GpuBufferSlice slice() {
		return new GpuBufferSlice(this, 0L, this.size());
	}

	default GpuBufferSlice.MappedView map(boolean read, boolean write) {
		return this.map(0L, this.size(), read, write);
	}

	GpuBufferSlice.MappedView map(long offset, long length, boolean read, boolean write);
}
