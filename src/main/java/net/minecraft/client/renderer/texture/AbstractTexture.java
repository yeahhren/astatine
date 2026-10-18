package net.minecraft.client.renderer.texture;

import com.mojang.renderpearl.api.textures.GpuSampler;
import com.mojang.renderpearl.api.textures.GpuTexture;
import com.mojang.renderpearl.api.textures.GpuTextureView;

public abstract class AbstractTexture implements AutoCloseable {
	protected GpuTexture texture;
	protected GpuTextureView textureView;
	protected GpuSampler sampler;

	public GpuTexture getTexture() {
		return this.texture;
	}

	public GpuTextureView getTextureView() {
		return this.textureView;
	}

	public GpuSampler getSampler() {
		return this.sampler;
	}

	@Override
	public void close() {
		if (this.textureView != null) {
			this.textureView.close();
			this.textureView = null;
		}

		if (this.texture != null) {
			this.texture.close();
			this.texture = null;
		}
	}
}
