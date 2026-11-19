package com.mojang.renderpearl.backend.api;

import com.mojang.renderpearl.api.GpuFormat;
import com.mojang.renderpearl.api.buffers.GpuBuffer;
import com.mojang.renderpearl.api.commands.GpuQueryPool;
import com.mojang.renderpearl.api.device.DeviceInfo;
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

public interface GpuDeviceBackend {
	CommandEncoderBackend createCommandEncoder();

	GpuSampler createSampler(AddressMode addressModeU, AddressMode addressModeV, FilterMode minFilter, FilterMode magFilter, int maxAnisotropy,
		OptionalDouble maxLod);

	GpuTexture createTexture(String label, int usage, GpuFormat format, int width, int height, int depthOrLayers, int mipLevels);

	GpuTextureView createTextureView(GpuTexture texture, int baseMipLevel, int mipLevels);

	GpuBuffer createBuffer(Supplier<String> label, int usage, long size);

	GpuBuffer createBuffer(Supplier<String> label, int usage, ByteBuffer data);

	DeviceInfo getDeviceInfo();

	BackendRenderPipeline createPipeline(RenderPipeline pipeline, ShaderSource source);

	GpuQueryPool createQueryPool(int size);
}
