package yt.ren.astatine.backend;

import com.mojang.renderpearl.api.buffers.GpuBufferSlice;
import com.mojang.renderpearl.api.commands.RenderPassDescriptor;
import com.mojang.renderpearl.api.textures.GpuTexture;
import com.mojang.renderpearl.backend.api.CommandEncoderBackend;
import com.mojang.renderpearl.backend.api.RenderPassBackend;
import org.joml.Vector4fc;
import yt.ren.astatine.Native;
import yt.ren.astatine.memory.Memory;

import java.nio.ByteBuffer;
import java.util.ArrayDeque;

public final class AstatineCommandEncoder implements CommandEncoderBackend {
	private final AstatineTransientMemory transientMemory = new AstatineTransientMemory();
	private final Scratch attachments = new Scratch(512);
	private final Scratch upload = new Scratch(4096);
	private final ArrayDeque<Retired> retired = new ArrayDeque<>();
	private final int delay = Native.framesInFlight() + 2;
	private long frame = Long.MIN_VALUE;
	private AstatineRenderPass currentRenderPass;
	private boolean worldPass;

	AstatineCommandEncoder() {
	}

	private void advance() {
		long presented = Native.presentedFrames();

		if (presented == this.frame) {
			return;
		}

		this.frame = presented;

		while (!this.retired.isEmpty() && this.retired.peekFirst().frame() + this.delay <= presented) {
			this.retired.pollFirst().destructor().run();
		}
	}

	public void queueForDestroy(Runnable destructor) {
		this.advance();
		this.retired.addLast(new Retired(destructor, this.frame));
	}

	@Override
	public AstatineTransientMemory transientMemory() {
		return this.transientMemory;
	}

	@Override
	public RenderPassBackend createRenderPass(RenderPassDescriptor descriptor) {
		if (this.currentRenderPass != null) {
			throw new IllegalStateException("a render pass is already open");
		}

		this.advance();
		var scratch = this.attachments.clear();

		for (var attachment : descriptor.colorAttachments()) {
			scratch.putLong(attachment == null ? 0L : ((AstatineTextureView) attachment.textureView()).handle());
			boolean clear = attachment != null && attachment.clearValue().isPresent();
			scratch.putInt(clear ? 1 : 0).putInt(0);

			if (clear) {
				var color = attachment.clearValue().get();
				scratch.putFloat(color.x()).putFloat(color.y()).putFloat(color.z()).putFloat(color.w());
			} else {
				scratch.putFloat(0.0f).putFloat(0.0f).putFloat(0.0f).putFloat(0.0f);
			}
		}

		var depth = descriptor.depthAttachment();

		if (depth != null) {
			scratch.putLong(((AstatineTextureView) depth.textureView()).handle());
			scratch.putInt(depth.clearValue().isPresent() ? 1 : 0);
			scratch.putFloat(depth.clearValue().isPresent() ? (float) depth.clearValue().getAsDouble() : 0.0f);
		}

		var area = descriptor.renderArea();
		BackendNatives.renderPassBegin(scratch.address(), descriptor.colorAttachments().size(), depth != null, area.x(), area.y(), area.width(),
			area.height());
		this.worldPass = false;
		this.currentRenderPass = new AstatineRenderPass(area);
		return this.currentRenderPass;
	}

	public void markWorldPass() {
		if (this.currentRenderPass == null) {
			throw new IllegalStateException("no render pass is open");
		}

		this.worldPass = true;
	}

	@Override
	public void submitRenderPass() {
		if (this.currentRenderPass == null) {
			throw new IllegalStateException("no render pass is open");
		}

		boolean world = this.worldPass;
		this.worldPass = false;
		this.currentRenderPass = null;
		BackendNatives.renderPassEnd(world);
	}

	@Override
	public boolean isInRenderPass() {
		return this.currentRenderPass != null;
	}

	public AstatineRenderPass currentRenderPass() {
		return this.currentRenderPass;
	}

	@Override
	public void clearColorTexture(GpuTexture texture, Vector4fc color) {
		BackendNatives.clearColorTexture(((AstatineTexture) texture).handle(), color.x(), color.y(), color.z(), color.w());
	}

	@Override
	public void clearDepthTexture(GpuTexture texture, double depth) {
		BackendNatives.clearDepthTexture(((AstatineTexture) texture).handle(), depth);
	}

	private long address(ByteBuffer data) {
		if (data.isDirect()) {
			return Memory.INSTANCE.address(data) + data.position();
		}

		return this.upload.clear().putBytes(data).address();
	}

	@Override
	public void writeToBuffer(GpuBufferSlice destination, ByteBuffer data) {
		BackendNatives.writeBuffer(((AstatineBuffer) destination.buffer()).handle(), destination.offset(), this.address(data), data.remaining());
	}

	@Override
	public void writeToTexture(GpuTexture destination, ByteBuffer source, int mipLevel, int depthOrLayer, int x, int y, int width,
		int height) {
		BackendNatives.writeTexture(((AstatineTexture) destination).handle(), this.address(source), source.remaining(), mipLevel, depthOrLayer, x,
			y, width, height);
	}

	@Override
	public void copyTextureToTexture(GpuTexture source, GpuTexture destination, int mipLevel, int destinationX, int destinationY,
		int sourceX, int sourceY, int width, int height) {
		BackendNatives.copyTexture(((AstatineTexture) source).handle(), ((AstatineTexture) destination).handle(), mipLevel, destinationX,
			destinationY, sourceX, sourceY, width, height);
	}

	@Override
	public void copyToBuffer(GpuBufferSlice source, GpuBufferSlice destination) {
		if (source.length() > destination.length()) {
			throw new IllegalArgumentException("copy source is larger than the destination");
		}

		BackendNatives.copyBuffer(((AstatineBuffer) source.buffer()).handle(), source.offset(), ((AstatineBuffer) destination.buffer()).handle(),
			destination.offset(), source.length());
	}

	@Override
	public void copyTextureToBuffer(GpuTexture source, GpuBufferSlice destination, int mipLevel, int x, int y, int width, int height) {
		BackendNatives.copyTextureToBuffer(((AstatineTexture) source).handle(), ((AstatineBuffer) destination.buffer()).handle(),
			destination.offset(), mipLevel, x, y, width, height);
	}

	private record Retired(Runnable destructor, long frame) {
	}
}
