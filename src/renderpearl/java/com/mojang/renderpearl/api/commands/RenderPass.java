package com.mojang.renderpearl.api.commands;

import com.mojang.renderpearl.api.buffers.GpuBuffer;
import com.mojang.renderpearl.api.buffers.GpuBufferSlice;
import com.mojang.renderpearl.api.pipeline.IndexType;
import com.mojang.renderpearl.api.pipeline.RenderPipeline;
import com.mojang.renderpearl.api.textures.GpuSampler;
import com.mojang.renderpearl.api.textures.GpuTextureView;
import com.mojang.renderpearl.util.UncheckedAutoCloseable;

import java.nio.ByteBuffer;
import java.util.function.Supplier;

public interface RenderPass extends UncheckedAutoCloseable {
	int MAX_VERTEX_BUFFERS = 16;

	void pushDebugGroup(Supplier<String> label);

	void popDebugGroup();

	void setPipeline(RenderPipeline pipeline);

	void bindTexture(String name, GpuTextureView texture, GpuSampler sampler);

	void setUniform(String name, GpuBufferSlice buffer);

	default void setUniform(String name, GpuBuffer buffer) {
		this.setUniform(name, buffer.slice());
	}

	void pushConstants(ByteBuffer data);

	void writeTimestamp(GpuQueryPool pool, int index);

	void enableScissor(int x, int y, int width, int height);

	void disableScissor();

	void setVertexBuffer(int slot, GpuBufferSlice buffer);

	void setIndexBuffer(GpuBuffer buffer, IndexType type);

	void draw(int count, int instances, int first, int firstInstance);

	void drawIndexed(int count, int instances, int first, int offset, int firstInstance);

	void drawIndirect(GpuBufferSlice commands, int count);

	void drawIndexedIndirect(GpuBufferSlice commands, int count);

	record RenderArea(int x, int y, int width, int height) {
		public boolean fillsTexture(GpuTextureView texture) {
			return this.x == 0 && this.y == 0 && this.width == texture.getWidth(0) && this.height == texture.getHeight(0);
		}
	}
}
