package yt.ren.astatine.backend;

import com.mojang.renderpearl.api.textures.GpuTextureView;

public class AstatineTextureView implements GpuTextureView {
	private final AstatineTexture texture;
	private final long handle;
	private final int baseMipLevel;
	private final int mipLevels;
	private final boolean wrapper;
	private boolean closed;

	private AstatineTextureView(AstatineTexture texture, long handle, int baseMipLevel, int mipLevels, boolean wrapper) {
		this.texture = texture;
		this.handle = handle;
		this.baseMipLevel = baseMipLevel;
		this.mipLevels = mipLevels;
		this.wrapper = wrapper;
	}

	public static AstatineTextureView create(AstatineTexture texture, int baseMipLevel, int mipLevels) {
		return new AstatineTextureView(texture, BackendNatives.textureViewCreate(texture.handle(), baseMipLevel, mipLevels), baseMipLevel,
			mipLevels, false);
	}

	public static AstatineTextureView wrap(long view, int usage, String label) {
		var texture = AstatineTexture.wrap(BackendNatives.textureViewTexture(view), usage, label);
		return new AstatineTextureView(texture, view, 0, texture.getMipLevels(), true);
	}

	public long handle() {
		return this.handle;
	}

	@Override
	public boolean isClosed() {
		return this.closed;
	}

	@Override
	public AstatineTexture texture() {
		return this.texture;
	}

	@Override
	public int baseMipLevel() {
		return this.baseMipLevel;
	}

	@Override
	public int mipLevels() {
		return this.mipLevels;
	}

	@Override
	public int getWidth(int mipLevel) {
		return this.texture.getWidth(mipLevel + this.baseMipLevel);
	}

	@Override
	public int getHeight(int mipLevel) {
		return this.texture.getHeight(mipLevel + this.baseMipLevel);
	}

	@Override
	public void close() {
		if (this.closed) {
			return;
		}

		this.closed = true;

		if (this.wrapper) {
			this.texture.close();
			BackendNatives.wrapperRelease(this.handle);
		} else {
			BackendNatives.textureViewRelease(this.handle);
		}
	}
}
