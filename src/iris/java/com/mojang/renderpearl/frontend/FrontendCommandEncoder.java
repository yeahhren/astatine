package com.mojang.renderpearl.frontend;

import com.mojang.renderpearl.api.buffers.GpuBufferSlice;
import com.mojang.renderpearl.api.buffers.TransientMemory;
import com.mojang.renderpearl.api.commands.CommandEncoder;
import com.mojang.renderpearl.api.commands.RenderPass;
import com.mojang.renderpearl.api.commands.RenderPassDescriptor;
import com.mojang.renderpearl.api.textures.GpuTexture;
import com.mojang.renderpearl.backend.api.CommandEncoderBackend;
import com.mojang.renderpearl.backend.api.RenderPassBackend;
import net.irisshaders.iris.Iris;
import net.irisshaders.iris.pipeline.IrisRenderingPipeline;
import net.irisshaders.iris.pipeline.WorldRenderPass;
import net.irisshaders.iris.targets.WorldRenderTargets;
import net.irisshaders.iris.vertices.ImmediateState;
import org.joml.Vector4fc;
import yt.ren.astatine.backend.MainTarget;

import java.nio.ByteBuffer;
import java.util.Optional;
import java.util.OptionalDouble;

public final class FrontendCommandEncoder implements CommandEncoder {
	private final FrontendGpuDevice device;
	private final CommandEncoderBackend backend;
	private FrontendRenderPass currentPass;

	FrontendCommandEncoder(FrontendGpuDevice device, CommandEncoderBackend backend) {
		this.device = device;
		this.backend = backend;
	}

	public FrontendGpuDevice device() {
		return this.device;
	}

	public CommandEncoderBackend backend() {
		return this.backend;
	}

	public boolean isInRenderPass() {
		return this.currentPass != null;
	}

	public FrontendRenderPass currentPass() {
		return this.currentPass;
	}

	@Override
	public void submit() {
	}

	@Override
	public TransientMemory transientMemory() {
		return this.backend.transientMemory();
	}

	@Override
	public RenderPass createRenderPass(RenderPassDescriptor descriptor) {
		if (this.currentPass != null) {
			throw new IllegalStateException("a render pass is already open");
		}

		var pass = new FrontendRenderPass(this, this.createBackendPass(descriptor), descriptor.renderArea());
		this.currentPass = pass;
		return pass;
	}

	private RenderPassBackend createBackendPass(RenderPassDescriptor descriptor) {
		var pipeline = Iris.getPipelineManager().getPipelineNullable();

		if (!(pipeline instanceof IrisRenderingPipeline iris) || !iris.shouldOverrideShaders() || ImmediateState.bypass || ImmediateState.temporarilyIgnorePass) {
			return this.backend.createRenderPass(descriptor);
		}

		if (descriptor.colorAttachments().isEmpty() || descriptor.colorAttachments().getFirst() == null || descriptor.colorAttachments()
			.getFirst().textureView().texture() != MainTarget.colorTexture()) {
			return this.backend.createRenderPass(descriptor);
		}

		var phase = iris.getWorldTargetPhase();
		var framebuffer = iris.getWorldTargets().getFramebuffer(phase);
		var colors = framebuffer.getColorViews();
		var depth = phase == WorldRenderTargets.Phase.SHADOW ? framebuffer.getDepthView() : descriptor.depthAttachment() == null ? null : descriptor.depthAttachment()
			.textureView();
		var size = colors.isEmpty() ? framebuffer.getDepthView() : colors.getFirst();
		var area = new RenderPass.RenderArea(0, 0, size.getWidth(0), size.getHeight(0));
		var builder = RenderPassDescriptor.builder(descriptor.label()).withRenderArea(area);

		for (var color : colors) {
			builder.withColorAttachment(color, Optional.empty());
		}

		if (depth != null) {
			builder.withDepthAttachment(depth, OptionalDouble.empty());
		}

		var commands = Iris.getRenderBackend().createRenderPass(this.backend, builder.build());

		return new WorldRenderPass(iris, commands, area, depth, phase);
	}

	void closePass(FrontendRenderPass pass) {
		if (this.currentPass != pass) {
			throw new IllegalStateException("closing a render pass that is not open");
		}

		this.currentPass = null;

		if (this.backend.isInRenderPass()) {
			this.backend.submitRenderPass();
		}
	}

	@Override
	public void copyToBuffer(GpuBufferSlice source, GpuBufferSlice destination) {
		this.backend.copyToBuffer(source, destination);
	}

	@Override
	public void copyTextureToBuffer(GpuTexture source, GpuBufferSlice destination, int mipLevel, int x, int y, int width, int height) {
		this.backend.copyTextureToBuffer(source, destination, mipLevel, x, y, width, height);
	}

	@Override
	public void clearColorTexture(GpuTexture texture, Vector4fc color) {
		this.backend.clearColorTexture(texture, color);
	}

	@Override
	public void clearDepthTexture(GpuTexture texture, double depth) {
		this.backend.clearDepthTexture(texture, depth);
	}

	@Override
	public void writeToBuffer(GpuBufferSlice destination, ByteBuffer data) {
		this.backend.writeToBuffer(destination, data);
	}

	@Override
	public void writeToTexture(GpuTexture destination, ByteBuffer source, int mipLevel, int depthOrLayer, int x, int y, int width,
		int height) {
		this.backend.writeToTexture(destination, source, mipLevel, depthOrLayer, x, y, width, height);
	}

	@Override
	public void copyTextureToTexture(GpuTexture source, GpuTexture destination, int mipLevel, int destinationX, int destinationY,
		int sourceX, int sourceY, int width, int height) {
		this.backend.copyTextureToTexture(source, destination, mipLevel, destinationX, destinationY, sourceX, sourceY, width, height);
	}
}
