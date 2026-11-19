package yt.ren.astatine.backend;

import com.mojang.renderpearl.api.GpuFormat;
import com.mojang.renderpearl.api.textures.GpuTexture;

public class AstatineTexture implements GpuTexture {
	public static final int USAGE_STORAGE = 1 << 20;
	private final long handle;
	private final int usage;
	private final String label;
	private final GpuFormat format;
	private final int width;
	private final int height;
	private final int depthOrLayers;
	private final int mipLevels;
	private final boolean owned;
	private boolean closed;

	private AstatineTexture(long handle, int usage, String label, GpuFormat format, int width, int height, int depthOrLayers, int mipLevels,
		boolean owned) {
		this.handle = handle;
		this.usage = usage;
		this.label = label;
		this.format = format;
		this.width = width;
		this.height = height;
		this.depthOrLayers = depthOrLayers;
		this.mipLevels = mipLevels;
		this.owned = owned;
	}

	public static AstatineTexture create(int usage, String label, GpuFormat format, int width, int height, int depthOrLayers, int mipLevels) {
		long handle = BackendNatives.textureCreate(usage, format.ordinal(), width, height, depthOrLayers, mipLevels, label);
		return new AstatineTexture(handle, usage, label, format, width, height, depthOrLayers, mipLevels, true);
	}

	public static AstatineTexture wrap(long handle, int usage, String label) {
		int[] info = BackendNatives.textureInfo(handle);

		if (info[0] < 0) {
			throw new IllegalStateException("native texture has no renderpearl format: " + label);
		}

		return new AstatineTexture(handle, usage, label, GpuFormat.values()[info[0]], info[1], info[2], info[3], info[4], false);
	}

	public long handle() {
		return this.handle;
	}

	@Override
	public int getWidth(int mipLevel) {
		return Math.max(1, this.width >> mipLevel);
	}

	@Override
	public int getHeight(int mipLevel) {
		return Math.max(1, this.height >> mipLevel);
	}

	@Override
	public int getDepthOrLayers() {
		return this.depthOrLayers;
	}

	@Override
	public int getMipLevels() {
		return this.mipLevels;
	}

	@Override
	public GpuFormat getFormat() {
		return this.format;
	}

	@Override
	public int usage() {
		return this.usage;
	}

	@Override
	public String getLabel() {
		return this.label;
	}

	@Override
	public boolean isClosed() {
		return this.closed;
	}

	@Override
	public void close() {
		if (this.closed) {
			return;
		}

		this.closed = true;

		if (this.owned) {
			BackendNatives.textureRelease(this.handle);
		}
	}
}
