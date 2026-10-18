package yt.ren.astatine.iris;

import com.mojang.renderpearl.api.GpuFormat;
import com.mojang.renderpearl.api.buffers.GpuBuffer;
import com.mojang.renderpearl.api.buffers.GpuBufferSlice;
import com.mojang.renderpearl.api.commands.RenderPass.RenderArea;
import com.mojang.renderpearl.api.commands.RenderPassDescriptor;
import com.mojang.renderpearl.api.pipeline.IndexType;
import com.mojang.renderpearl.api.textures.GpuTextureView;
import com.mojang.renderpearl.backend.api.BackendRenderPipeline;
import com.mojang.renderpearl.backend.api.RenderPassBackend;
import net.irisshaders.iris.backend.ForwardingRenderPass;
import net.irisshaders.iris.backend.GraphicsPipeline;
import net.irisshaders.iris.backend.WorldPassCommands;
import org.lwjgl.PointerBuffer;
import yt.ren.astatine.backend.AstatineBuffer;
import yt.ren.astatine.backend.AstatineCommandEncoder;
import yt.ren.astatine.backend.AstatineTextureView;
import yt.ren.astatine.backend.BackendNatives;
import yt.ren.astatine.backend.Scratch;
import yt.ren.astatine.memory.Memory;

import java.nio.ByteBuffer;
import java.nio.IntBuffer;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class AstatineWorldCommands extends ForwardingRenderPass implements WorldPassCommands {
	private final AstatineCommandEncoder encoder;
	private final boolean owned;
	private final Map<TexelBufferKey, Long> texels = new HashMap<>();
	private final Scratch attachments = new Scratch(256);
	private boolean closed;
	private AstatineGraphicsShader.Pipeline pipeline;
	private int indexSize = 4;

	public AstatineWorldCommands(AstatineCommandEncoder encoder, RenderPassDescriptor descriptor) {
		this(encoder, encoder.createRenderPass(descriptor), true);
	}

	public AstatineWorldCommands(AstatineCommandEncoder encoder, RenderPassBackend pass, boolean owned) {
		super(pass);
		this.encoder = encoder;
		this.owned = owned;
		encoder.markWorldPass();
	}

	public boolean isOwned() {
		return this.owned;
	}

	@Override
	public void setViewport(int x, int y, int width, int height) {
		BackendNatives.setViewport(x, y, width, height);
	}

	@Override
	public void pushConstants(ByteBuffer data) {
		if (this.pipeline == null) {
			throw new IllegalStateException("no graphics pipeline bound");
		}

		this.pipeline.pushConstants(data);
	}

	@Override
	public void close() {
		if (this.owned && !this.closed) {
			this.closed = true;
			this.encoder.submitRenderPass();
			this.end();
		}
	}

	@Override
	public void setPipeline(BackendRenderPipeline pipeline) {
	}

	@Override
	public void setIndexBuffer(GpuBuffer buffer, IndexType type) {
		this.indexSize = type == IndexType.INT ? 4 : 2;
		super.setIndexBuffer(buffer, type);
	}

	@Override
	public void setUniform(int index, Object value) {
	}

	@Override
	public void bindPipeline(GraphicsPipeline pipeline) {
		this.pipeline = (AstatineGraphicsShader.Pipeline) pipeline;
		this.pipeline.bind();
	}

	@Override
	public void setAttachments(List<GpuTextureView> colors, GpuTextureView depth, RenderArea area) {
		if (!this.owned) {
			throw new IllegalStateException("cannot change vanilla render pass attachments");
		}

		var scratch = this.attachments.clear();

		for (var color : colors) {
			scratch.putLong(((AstatineTextureView) color).handle());
		}

		BackendNatives.renderPassAttachments(scratch.address(), colors.size(), depth == null ? 0L : ((AstatineTextureView) depth).handle(),
			area.x(), area.y(), area.width(), area.height());
	}

	@Override
	public long getTexelBufferView(GpuBufferSlice slice, GpuFormat format) {
		var key = new TexelBufferKey(((AstatineBuffer) slice.buffer()).handle(), slice.offset(), slice.length(), format);
		return this.texels.computeIfAbsent(key,
			ignored -> BackendNatives.texelBufferView(key.buffer(), key.offset(), key.length(), format.ordinal()));
	}

	@Override
	public void end() {
		this.texels.clear();
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
	public void multiDraw(IntBuffer parameters, int instances, int firstInstance, int count) {
		BackendNatives.multiDraw(Memory.INSTANCE.address(parameters) + parameters.position() * 4L, count, instances, firstInstance, false);
	}

	@Override
	public void multiDrawIndexed(IntBuffer parameters, int instances, int firstInstance, int count) {
		BackendNatives.multiDraw(Memory.INSTANCE.address(parameters) + parameters.position() * 4L, count, instances, firstInstance, true);
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

	public record TexelBufferKey(long buffer, long offset, long length, GpuFormat format) {
	}
}
