package com.mojang.renderpearl.api.commands;

import com.mojang.renderpearl.api.buffers.GpuBuffer;
import com.mojang.renderpearl.api.buffers.GpuBufferSlice;
import com.mojang.renderpearl.api.pipeline.IndexType;
import com.mojang.renderpearl.api.textures.GpuTextureView;
import com.mojang.renderpearl.util.UncheckedAutoCloseable;
import net.irisshaders.iris.mixinterface.RenderPassInterface;

import java.nio.ByteBuffer;

public interface RenderPass extends UncheckedAutoCloseable, RenderPassInterface {
	int MAX_VERTEX_BUFFERS = 16;

	void pushConstants(ByteBuffer data);

	void enableScissor(int x, int y, int width, int height);

	void disableScissor();

	void setVertexBuffer(int slot, GpuBufferSlice buffer);

	void setIndexBuffer(GpuBuffer buffer, IndexType type);

	void draw(int count, int instances, int first, int firstInstance);

	void drawIndexed(int count, int instances, int first, int offset, int firstInstance);

	record RenderArea(int x, int y, int width, int height) {
		public boolean fillsTexture(GpuTextureView texture) {
			return this.x == 0 && this.y == 0 && this.width == texture.getWidth(0) && this.height == texture.getHeight(0);
		}
	}
}
