package com.mojang.renderpearl.frontend;

import com.mojang.renderpearl.api.buffers.GpuBuffer;
import com.mojang.renderpearl.api.buffers.GpuBufferSlice;
import com.mojang.renderpearl.api.commands.GpuQueryPool;
import com.mojang.renderpearl.api.commands.RenderPass;
import com.mojang.renderpearl.api.pipeline.RenderPipeline;
import com.mojang.renderpearl.api.textures.GpuSampler;
import com.mojang.renderpearl.api.textures.GpuTextureView;
import com.mojang.renderpearl.util.TextureViewAndSampler;
import com.mojang.renderpearl.api.pipeline.IndexType;
import com.mojang.renderpearl.backend.api.RenderPassBackend;
import net.irisshaders.iris.Iris;
import net.irisshaders.iris.backend.IrisRenderPass;
import net.irisshaders.iris.backend.WorldPassCommands;
import net.irisshaders.iris.mixinterface.RenderPassInterface;
import net.irisshaders.iris.pipeline.IrisPipelines;
import net.irisshaders.iris.pipeline.IrisRenderingPipeline;
import net.irisshaders.iris.pipeline.WorldRenderPass;

import java.nio.ByteBuffer;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

public final class FrontendRenderPass implements RenderPass, RenderPassInterface {
	private final FrontendCommandEncoder encoder;
	private final RenderPassBackend backend;
	private final RenderArea renderArea;
	private final GpuBufferSlice[] vertexBuffers = new GpuBufferSlice[MAX_VERTEX_BUFFERS];
	private GpuBuffer indexBuffer;
	private IndexType indexType = IndexType.INT;
	private final Map<String, Object> uniforms = new HashMap<>();
	private IrisRenderPass pass;
	private FrontendRenderPipeline pipeline;
	private boolean closed;

	FrontendRenderPass(FrontendCommandEncoder encoder, RenderPassBackend backend, RenderArea renderArea) {
		this.encoder = encoder;
		this.backend = backend;
		this.renderArea = renderArea;
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
	public void pushDebugGroup(Supplier<String> label) {
		this.backend.pushDebugGroup(label);
	}

	@Override
	public void popDebugGroup() {
		this.backend.popDebugGroup();
	}

	@Override
	public void setPipeline(RenderPipeline renderPipeline) {
		if (this.backend instanceof WorldRenderPass world) {
			if (!(Iris.getPipelineManager().getPipelineNullable() instanceof IrisRenderingPipeline iris)) {
				throw new IllegalStateException("world render pass without an Iris pipeline");
			}

			var key = IrisPipelines.getPipeline(iris, renderPipeline);
			var shader = key == null ? null : iris.getShaderMap().getShader(key);

			if (shader == null) {
				throw new IllegalStateException("the active shaderpack has no program for " + renderPipeline.getLocation());
			}

			var frontend = new FrontendRenderPipeline(renderPipeline.getLocation(), null, renderPipeline.getVertexFormatBindings(), Map.of(),
				List.of(), renderPipeline.getColorTargetStates(), false, 0);
			world.setUniforms(Map.copyOf(this.uniforms));
			world.setPipeline(shader.override(renderPipeline, frontend).backendRenderPipeline());
			return;
		}

		this.pipeline = this.encoder.device().pipeline(renderPipeline);
		this.backend.setPipeline(this.pipeline.backendRenderPipeline());
		this.uniforms.forEach(this::applyUniform);
	}

	private void applyUniform(String name, Object value) {
		if (this.backend instanceof WorldRenderPass world) {
			world.setUniforms(Map.copyOf(this.uniforms));
			return;
		}

		if (this.pipeline == null) {
			return;
		}

		var index = this.pipeline.uniformIndices().get(name);

		if (index != null) {
			this.backend.setUniform(index, value);
		}
	}

	@Override
	public void bindTexture(String name, GpuTextureView texture, GpuSampler sampler) {
		var value = new TextureViewAndSampler(texture, sampler);
		this.uniforms.put(name, value);
		this.applyUniform(name, value);
	}

	@Override
	public void setUniform(String name, GpuBufferSlice buffer) {
		this.uniforms.put(name, buffer);
		this.applyUniform(name, buffer);
	}

	@Override
	public void writeTimestamp(GpuQueryPool pool, int index) {
		this.backend.writeTimestamp(pool, index);
	}

	@Override
	public void drawIndirect(GpuBufferSlice commands, int count) {
		this.backend.drawIndirect(commands, count);
	}

	@Override
	public void drawIndexedIndirect(GpuBufferSlice commands, int count) {
		this.backend.drawIndexedIndirect(commands, count);
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

		if (this.backend instanceof WorldRenderPass world) {
			world.end();
		} else if (this.pass instanceof WorldPassCommands commands) {
			commands.end();
		}

		this.encoder.closePass(this);
	}
}
