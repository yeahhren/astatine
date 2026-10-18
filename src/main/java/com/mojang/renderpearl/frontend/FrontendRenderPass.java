package com.mojang.renderpearl.frontend;

import com.mojang.renderpearl.api.buffers.GpuBuffer;
import com.mojang.renderpearl.api.buffers.GpuBufferSlice;
import com.mojang.renderpearl.api.commands.RenderPass;
import com.mojang.renderpearl.api.pipeline.IndexType;
import com.mojang.renderpearl.backend.api.RenderPassBackend;
import net.irisshaders.iris.Iris;
import net.irisshaders.iris.backend.IrisRenderPass;
import net.irisshaders.iris.backend.WorldPassCommands;
import net.irisshaders.iris.pipeline.WorldRenderPass;
import yt.ren.astatine.vulkan.WorldDraws;

import java.nio.ByteBuffer;

public final class FrontendRenderPass implements RenderPass {
	private final FrontendCommandEncoder encoder;
	private final RenderPassBackend backend;
	private final RenderArea renderArea;
	private final GpuBufferSlice[] vertexBuffers = new GpuBufferSlice[MAX_VERTEX_BUFFERS];
	private GpuBuffer indexBuffer;
	private IndexType indexType = IndexType.INT;
	private IrisRenderPass pass;
	private boolean closed;

	FrontendRenderPass(FrontendCommandEncoder encoder, RenderPassBackend backend, RenderArea renderArea) {
		this.encoder = encoder;
		this.backend = backend;
		this.renderArea = renderArea;
		WorldDraws.open(this);
	}

	public RenderPassBackend backend() {
		return this.backend;
	}

	@Override
	public IrisRenderPass iris$getIrisPass() {
		if (this.closed) {
			throw new IllegalStateException("Minecraft render pass is closed");
		}

		if (this.pass == null) {
			this.pass = this.backend instanceof WorldRenderPass world ? world.getCommands() : Iris.getRenderBackend()
				.adaptRenderPass(this.backend, this.renderArea);

			if (this.indexBuffer != null) {
				this.pass.setIndexBuffer(this.indexBuffer, this.indexType);
			}

			for (int i = 0; i < this.vertexBuffers.length; i++) {
				if (this.vertexBuffers[i] != null) {
					this.pass.setVertexBuffer(i, this.vertexBuffers[i]);
				}
			}
		}

		return this.pass;
	}

	@Override
	public void pushConstants(ByteBuffer data) {
		this.backend.pushConstants(data);
	}

	@Override
	public void enableScissor(int x, int y, int width, int height) {
		this.backend.enableScissor(x, y, width, height);
	}

	@Override
	public void disableScissor() {
		this.backend.disableScissor();
	}

	@Override
	public void setVertexBuffer(int slot, GpuBufferSlice buffer) {
		this.vertexBuffers[slot] = buffer;
		this.backend.setVertexBuffer(slot, buffer);

		if (this.pass != null && !(this.backend instanceof WorldRenderPass)) {
			this.pass.setVertexBuffer(slot, buffer);
		}
	}

	@Override
	public void setIndexBuffer(GpuBuffer buffer, IndexType type) {
		this.indexBuffer = buffer;
		this.indexType = type;
		this.backend.setIndexBuffer(buffer, type);

		if (this.pass != null && !(this.backend instanceof WorldRenderPass)) {
			this.pass.setIndexBuffer(buffer, type);
		}
	}

	@Override
	public void draw(int count, int instances, int first, int firstInstance) {
		this.backend.draw(count, instances, first, firstInstance);
	}

	@Override
	public void drawIndexed(int count, int instances, int first, int offset, int firstInstance) {
		this.backend.drawIndexed(count, instances, first, offset, firstInstance);
	}

	@Override
	public void close() {
		if (this.closed) {
			return;
		}

		this.closed = true;
		WorldDraws.close(this);

		if (this.backend instanceof WorldRenderPass world) {
			world.end();
		} else if (this.pass instanceof WorldPassCommands commands) {
			commands.end();
		}

		this.encoder.closePass(this);
	}
}
