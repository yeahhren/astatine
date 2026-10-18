package com.mojang.renderpearl.frontend;

import com.mojang.renderpearl.api.GpuFormat;
import com.mojang.renderpearl.api.buffers.GpuBuffer;
import com.mojang.renderpearl.api.commands.CommandEncoder;
import com.mojang.renderpearl.api.commands.GpuQueryPool;
import com.mojang.renderpearl.api.pipeline.BindGroupLayout;
import com.mojang.renderpearl.api.pipeline.RenderPipeline;
import com.mojang.renderpearl.api.pipeline.ShaderSource;
import com.mojang.renderpearl.api.device.DeviceInfo;
import com.mojang.renderpearl.api.device.GpuDevice;
import com.mojang.renderpearl.api.textures.AddressMode;
import com.mojang.renderpearl.api.textures.FilterMode;
import com.mojang.renderpearl.api.textures.GpuSampler;
import com.mojang.renderpearl.api.textures.GpuTexture;
import com.mojang.renderpearl.api.textures.GpuTextureView;
import com.mojang.renderpearl.backend.api.GpuDeviceBackend;
import net.irisshaders.iris.mixin.FrontendDeviceAccessor;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.OptionalDouble;
import java.util.function.Supplier;

public final class FrontendGpuDevice implements GpuDevice, FrontendDeviceAccessor {
	private final GpuDeviceBackend backend;
	private final FrontendCommandEncoder encoder;
	private final Map<RenderPipeline, FrontendRenderPipeline> pipelines = new HashMap<>();
	private ShaderSource shaderSource;

	public FrontendGpuDevice(GpuDeviceBackend backend) {
		this.backend = backend;
		this.encoder = new FrontendCommandEncoder(this, backend.createCommandEncoder());
	}

	@Override
	public void setShaderSource(ShaderSource source) {
		this.shaderSource = source;
	}

	@Override
	public void precompilePipeline(RenderPipeline pipeline, ShaderSource source) {
		this.compile(pipeline, source == null ? this.shaderSource : source);
	}

	public FrontendRenderPipeline pipeline(RenderPipeline pipeline) {
		var compiled = this.pipelines.get(pipeline);
		return compiled != null && !compiled.isClosed() ? compiled : this.compile(pipeline, this.shaderSource);
	}

	private FrontendRenderPipeline compile(RenderPipeline pipeline, ShaderSource source) {
		var previous = this.pipelines.remove(pipeline);

		if (previous != null) {
			previous.close();
		}

		var uniforms = new ArrayList<BindGroupLayout.UniformDescription>();
		var indices = new HashMap<String, Integer>();

		for (var group : pipeline.getBindGroupLayouts()) {
			for (var uniform : group.uniforms()) {
				indices.putIfAbsent(uniform.name(), uniforms.size());
				uniforms.add(uniform);
			}
		}

		var compiled = new FrontendRenderPipeline(pipeline.getLocation(), this.backend.createPipeline(pipeline, source),
			pipeline.getVertexFormatBindings(), Map.copyOf(indices), List.copyOf(uniforms), pipeline.getColorTargetStates(), false, 128);
		this.pipelines.put(pipeline, compiled);
		return compiled;
	}

	@Override
	public void clearPipelineCache() {
		this.pipelines.values().forEach(FrontendRenderPipeline::close);
		this.pipelines.clear();
	}

	@Override
	public GpuQueryPool createQueryPool(int size) {
		return this.backend.createQueryPool(size);
	}

	@Override
	public GpuDeviceBackend iris$getBackend() {
		return this.backend;
	}

	@Override
	public CommandEncoder createCommandEncoder() {
		return this.encoder;
	}

	@Override
	public GpuSampler createSampler(AddressMode addressModeU, AddressMode addressModeV, FilterMode minFilter, FilterMode magFilter,
		int maxAnisotropy, OptionalDouble maxLod) {
		return this.backend.createSampler(addressModeU, addressModeV, minFilter, magFilter, maxAnisotropy, maxLod);
	}

	@Override
	public GpuTexture createTexture(Supplier<String> label, int usage, GpuFormat format, int width, int height, int depthOrLayers,
		int mipLevels) {
		return this.backend.createTexture(label == null ? null : label.get(), usage, format, width, height, depthOrLayers, mipLevels);
	}

	@Override
	public GpuTexture createTexture(String label, int usage, GpuFormat format, int width, int height, int depthOrLayers, int mipLevels) {
		return this.backend.createTexture(label, usage, format, width, height, depthOrLayers, mipLevels);
	}

	@Override
	public GpuTextureView createTextureView(GpuTexture texture) {
		return this.createTextureView(texture, 0, texture.getMipLevels());
	}

	@Override
	public GpuTextureView createTextureView(GpuTexture texture, int baseMipLevel, int mipLevels) {
		return this.backend.createTextureView(texture, baseMipLevel, mipLevels);
	}

	@Override
	public GpuBuffer createBuffer(Supplier<String> label, int usage, long size) {
		return this.backend.createBuffer(label, usage, size);
	}

	@Override
	public GpuBuffer createBuffer(Supplier<String> label, int usage, ByteBuffer data) {
		return this.backend.createBuffer(label, usage, data);
	}

	@Override
	public DeviceInfo getDeviceInfo() {
		return this.backend.getDeviceInfo();
	}
}
