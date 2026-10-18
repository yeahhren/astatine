package com.mojang.renderpearl.backend.api;

import com.mojang.renderpearl.api.buffers.GpuBufferSlice;
import com.mojang.renderpearl.api.buffers.TransientMemory;
import com.mojang.renderpearl.api.commands.RenderPassDescriptor;
import com.mojang.renderpearl.api.textures.GpuTexture;
import org.joml.Vector4fc;

import java.nio.ByteBuffer;

public interface CommandEncoderBackend {
	TransientMemory transientMemory();

	RenderPassBackend createRenderPass(RenderPassDescriptor descriptor);

	void submitRenderPass();

	boolean isInRenderPass();

	void clearColorTexture(GpuTexture texture, Vector4fc color);

	void clearDepthTexture(GpuTexture texture, double depth);

	void writeToBuffer(GpuBufferSlice destination, ByteBuffer data);

	void writeToTexture(GpuTexture destination, ByteBuffer source, int mipLevel, int depthOrLayer, int x, int y, int width, int height);

	void copyTextureToTexture(GpuTexture source, GpuTexture destination, int mipLevel, int destinationX, int destinationY, int sourceX,
		int sourceY, int width, int height);

	void copyToBuffer(GpuBufferSlice source, GpuBufferSlice destination);

	void copyTextureToBuffer(GpuTexture source, GpuBufferSlice destination, int mipLevel, int x, int y, int width, int height);
}
