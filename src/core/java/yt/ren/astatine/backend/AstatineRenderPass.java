package yt.ren.astatine.backend;

import com.mojang.renderpearl.api.buffers.GpuBuffer;
import com.mojang.renderpearl.api.buffers.GpuBufferSlice;
import com.mojang.renderpearl.api.commands.GpuQueryPool;
import com.mojang.renderpearl.api.commands.RenderPass;
import com.mojang.renderpearl.api.pipeline.IndexType;
import com.mojang.renderpearl.api.pipeline.UniformType;
import com.mojang.renderpearl.backend.api.BackendRenderPipeline;
import com.mojang.renderpearl.backend.api.RenderPassBackend;
import com.mojang.renderpearl.util.TextureViewAndSampler;
import yt.ren.astatine.memory.Memory;
import org.lwjgl.PointerBuffer;

import java.nio.ByteBuffer;
import java.nio.IntBuffer;
import java.util.function.Supplier;

public class AstatineRenderPass implements RenderPassBackend {
	private final RenderPass.RenderArea area;
	private final Scratch constants = new Scratch(128);
	private int indexSize = 4;
	private AstatineRenderPipeline pipeline;

	AstatineRenderPass(RenderPass.RenderArea area) {
		this.area = area;
	}

	public RenderPass.RenderArea renderArea() {
		return this.area;
	}

	@Override
	public void pushDebugGroup(Supplier<String> label) {
	}

	@Override
	public void popDebugGroup() {
	}

	@Override
	public void setPipeline(BackendRenderPipeline pipeline) {
		this.pipeline = (AstatineRenderPipeline) pipeline;
		BackendNatives.passUseProgram(this.pipeline == null ? 0L : this.pipeline.handle());
	}

	@Override
	public void setUniform(int index, Object value) {
		if (this.pipeline == null) {
			throw new IllegalStateException("no render pipeline is bound");
		}

		switch (value) {
			case TextureViewAndSampler texture -> BackendNatives.passSetSampler(index, ((AstatineTextureView) texture.view()).handle(),
				((AstatineSampler) texture.sampler()).handle());
			case GpuBufferSlice slice when this.pipeline.uniformType(index) == UniformType.TEXEL_BUFFER -> BackendNatives.passSetTexelBuffer(index,
				((AstatineBuffer) slice.buffer()).handle(), slice.offset(), slice.length(), this.pipeline.uniforms().get(index).gpuFormat().ordinal());
			case GpuBufferSlice slice -> BackendNatives.passSetUniformBuffer(index, ((AstatineBuffer) slice.buffer()).handle(), slice.offset(),
				slice.length());
			default -> throw new IllegalArgumentException("unsupported uniform value: " + value);
		}
	}

	@Override
	public void pushConstants(ByteBuffer data) {
		long address = data.isDirect() ? Memory.INSTANCE.address(data) + data.position() : this.constants.clear().putBytes(data).address();
		BackendNatives.passPushConstants(address, data.remaining());
	}

	@Override
	public void enableScissor(int x, int y, int width, int height) {
		BackendNatives.setScissor(x, y, width, height);
	}

	@Override
	public void disableScissor() {
		this.enableScissor(this.area.x(), this.area.y(), this.area.width(), this.area.height());
	}

	@Override
	public void setVertexBuffer(int index, GpuBufferSlice slice) {
		if (slice != null) {
			BackendNatives.setVertexBuffer(index, ((AstatineBuffer) slice.buffer()).handle(), slice.offset());
		}
	}

	@Override
	public void setIndexBuffer(GpuBuffer buffer, IndexType type) {
		this.indexSize = type.bytes;
		BackendNatives.setIndexBuffer(((AstatineBuffer) buffer).handle(), 0L, type == IndexType.INT);
	}

	@Override
	public void writeTimestamp(GpuQueryPool pool, int query) {
		BackendNatives.writeTimestamp(((AstatineQueryPool) pool).handle(), query);
	}

	@Override
	public void draw(int count, int instances, int first, int firstInstance) {
		BackendNatives.draw(count, instances, first, firstInstance);
	}

	@Override
	public void drawIndexed(int count, int instances, int first, int offset, int firstInstance) {
		BackendNatives.drawIndexed(count, instances, first, offset, firstInstance);
	}

	@Override
	public void drawIndirect(GpuBufferSlice commands, int count) {
		BackendNatives.drawIndirect(((AstatineBuffer) commands.buffer()).handle(), commands.offset(), count, false);
	}

	@Override
	public void drawIndexedIndirect(GpuBufferSlice commands, int count) {
		BackendNatives.drawIndirect(((AstatineBuffer) commands.buffer()).handle(), commands.offset(), count, true);
	}

	@Override
	public void multiDraw(IntBuffer commands, int instances, int firstInstance, int count) {
		for (int i = 0; i < count; i++) {
			int base = commands.position() + i * 2;
			this.draw(commands.get(base + 1), instances, commands.get(base), firstInstance);
		}
	}

	@Override
	public void multiDrawIndexed(IntBuffer commands, int instances, int firstInstance, int count) {
		for (int i = 0; i < count; i++) {
			int base = commands.position() + i * 3;
			this.drawIndexed(commands.get(base + 1), instances, commands.get(base), commands.get(base + 2), firstInstance);
		}
	}

	@Override
	public void multiDraw(IntBuffer first, IntBuffer counts, int count) {
		for (int i = 0; i < count; i++) {
			this.draw(counts.get(counts.position() + i), 1, first.get(first.position() + i), 0);
		}
	}

	@Override
	public void multiDrawIndexed(PointerBuffer offsets, IntBuffer counts, IntBuffer bases, int count) {
		for (int i = 0; i < count; i++) {
			this.drawIndexed(counts.get(counts.position() + i), 1, Math.toIntExact(offsets.get(offsets.position() + i) / this.indexSize),
				bases == null ? 0 : bases.get(bases.position() + i), 0);
		}
	}
}
