package com.mojang.renderpearl.api.commands;

import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.renderpearl.api.buffers.GpuBufferSlice;
import com.mojang.renderpearl.api.buffers.TransientMemory;
import com.mojang.renderpearl.api.textures.GpuTexture;
import com.mojang.renderpearl.api.textures.GpuTextureView;
import org.joml.Vector4fc;

import java.nio.ByteBuffer;
import java.util.Optional;
import java.util.OptionalDouble;
import java.util.function.Supplier;

public interface CommandEncoder {
	void submit();

	TransientMemory transientMemory();

	default RenderPass createRenderPass(Supplier<String> label, GpuTextureView colorTexture, Optional<Vector4fc> clearColor) {
		return this.createRenderPass(label, colorTexture, clearColor, null, OptionalDouble.empty());
	}

	default RenderPass createRenderPass(Supplier<String> label, GpuTextureView colorTexture, Optional<Vector4fc> clearColor,
		GpuTextureView depthTexture, OptionalDouble clearDepth) {
		return this.createRenderPass(label, colorTexture, clearColor, depthTexture, clearDepth,
			new RenderPass.RenderArea(0, 0, colorTexture.getWidth(0), colorTexture.getHeight(0)));
	}

	default RenderPass createRenderPass(Supplier<String> label, GpuTextureView colorTexture, Optional<Vector4fc> clearColor,
		GpuTextureView depthTexture, OptionalDouble clearDepth, RenderPass.RenderArea renderArea) {
		var descriptor = RenderPassDescriptor.builder(label).withColorAttachment(colorTexture, clearColor);

		if (depthTexture != null) {
			descriptor.withDepthAttachment(depthTexture, clearDepth);
		}

		return this.createRenderPass(descriptor.withRenderArea(renderArea).build());
	}

	RenderPass createRenderPass(RenderPassDescriptor descriptor);

	void clearColorTexture(GpuTexture texture, Vector4fc color);

	void clearDepthTexture(GpuTexture texture, double depth);

	default void clearColorAndDepthTextures(GpuTexture colorTexture, Vector4fc color, GpuTexture depthTexture, double depth) {
		this.clearColorTexture(colorTexture, color);
		this.clearDepthTexture(depthTexture, depth);
	}

	void writeToBuffer(GpuBufferSlice destination, ByteBuffer data);

	default void writeToTexture(GpuTexture destination, NativeImage image) {
		this.writeToTexture(destination, image.toRgba(), 0, 0, 0, 0, image.getWidth(), image.getHeight());
	}

	void writeToTexture(GpuTexture destination, ByteBuffer source, int mipLevel, int depthOrLayer, int x, int y, int width, int height);

	void copyTextureToTexture(GpuTexture source, GpuTexture destination, int mipLevel, int destinationX, int destinationY, int sourceX,
		int sourceY, int width, int height);

	void copyToBuffer(GpuBufferSlice source, GpuBufferSlice destination);

	void copyTextureToBuffer(GpuTexture source, GpuBufferSlice destination, int mipLevel, int x, int y, int width, int height);
}
