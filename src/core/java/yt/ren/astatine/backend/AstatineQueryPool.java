package yt.ren.astatine.backend;

import com.mojang.renderpearl.api.commands.GpuQueryPool;

import java.util.OptionalLong;

public final class AstatineQueryPool implements GpuQueryPool {
	private final int size;
	private long handle;

	public AstatineQueryPool(int size) {
		if (size < 1) {
			throw new IllegalArgumentException("query pools need at least one query");
		}

		this.size = size;
		this.handle = BackendNatives.queryPoolCreate(size);
	}

	public long handle() {
		if (this.handle == 0L) {
			throw new IllegalStateException("query pool is closed");
		}

		return this.handle;
	}

	@Override
	public int size() {
		return this.size;
	}

	@Override
	public OptionalLong getValue(int index) {
		long value = BackendNatives.queryValue(this.handle(), index);
		return value < 0 ? OptionalLong.empty() : OptionalLong.of(value);
	}

	@Override
	public void close() {
		if (this.handle != 0L) {
			BackendNatives.queryPoolRelease(this.handle);
			this.handle = 0L;
		}
	}
}
