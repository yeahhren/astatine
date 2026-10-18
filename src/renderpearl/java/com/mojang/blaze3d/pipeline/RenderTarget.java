package com.mojang.blaze3d.pipeline;

import com.mojang.renderpearl.api.textures.GpuTexture;
import com.mojang.renderpearl.api.textures.GpuTextureView;

public class RenderTarget {
	public int width;
	public int height;
	private GpuTexture colorTexture;
	private GpuTextureView colorTextureView;
	private GpuTexture depthTexture;
	private GpuTextureView depthTextureView;
	private int colorVersion;
	private int depthVersion;

	public void update(int width, int height, GpuTexture colorTexture, GpuTextureView colorTextureView, GpuTexture depthTexture,
		GpuTextureView depthTextureView) {
		this.width = width;
		this.height = height;

		if (colorTexture != this.colorTexture) {
			this.colorVersion++;
		}

		if (depthTexture != this.depthTexture) {
			this.depthVersion++;
		}

		this.colorTexture = colorTexture;
		this.colorTextureView = colorTextureView;
		this.depthTexture = depthTexture;
		this.depthTextureView = depthTextureView;
	}

	public GpuTexture getColorTexture() {
		return this.colorTexture;
	}

	public GpuTextureView getColorTextureView() {
		return this.colorTextureView;
	}

	public GpuTexture getDepthTexture() {
		return this.depthTexture;
	}

	public GpuTextureView getDepthTextureView() {
		return this.depthTextureView;
	}

	public int iris$getDepthBufferVersion() {
		return this.depthVersion;
	}

	public int iris$getColorBufferVersion() {
		return this.colorVersion;
	}
}
