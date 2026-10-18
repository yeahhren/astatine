package com.mojang.renderpearl.api.device;

import com.mojang.renderpearl.api.GpuFormat;
import com.mojang.renderpearl.api.buffers.GpuBuffer;
import com.mojang.renderpearl.api.commands.CommandEncoder;
import com.mojang.renderpearl.api.commands.GpuQueryPool;
import com.mojang.renderpearl.api.pipeline.RenderPipeline;
import com.mojang.renderpearl.api.pipeline.ShaderSource;
import com.mojang.renderpearl.api.textures.AddressMode;
import com.mojang.renderpearl.api.textures.FilterMode;
import com.mojang.renderpearl.api.textures.GpuSampler;
import com.mojang.renderpearl.api.textures.GpuTexture;
import com.mojang.renderpearl.api.textures.GpuTextureView;

import java.nio.ByteBuffer;
import java.util.OptionalDouble;
import java.util.function.Supplier;

public interface GpuDevice {
	CommandEncoder createCommandEncoder();

	GpuSampler createSampler(AddressMode addressModeU, AddressMode addressModeV, FilterMode minFilter, FilterMode magFilter, int maxAnisotropy,
		OptionalDouble maxLod);

	GpuTexture createTexture(Supplier<String> label, int usage, GpuFormat format, int width, int height, int depthOrLayers, int mipLevels);

	GpuTexture createTexture(String label, int usage, GpuFormat format, int width, int height, int depthOrLayers, int mipLevels);

	GpuTextureView createTextureView(GpuTexture texture);

	GpuTextureView createTextureView(GpuTexture texture, int baseMipLevel, int mipLevels);

	GpuBuffer createBuffer(Supplier<String> label, int usage, long size);

	GpuBuffer createBuffer(Supplier<String> label, int usage, ByteBuffer data);

	DeviceInfo getDeviceInfo();

	void setShaderSource(ShaderSource source);

	void precompilePipeline(RenderPipeline pipeline, ShaderSource source);

	default void precompilePipeline(RenderPipeline pipeline) {
		this.precompilePipeline(pipeline, null);
	}

	void clearPipelineCache();

	GpuQueryPool createQueryPool(int size);
}
