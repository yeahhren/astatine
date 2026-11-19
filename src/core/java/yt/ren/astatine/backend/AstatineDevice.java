package yt.ren.astatine.backend;

import com.mojang.renderpearl.api.GpuFormat;
import com.mojang.renderpearl.api.buffers.GpuBuffer;
import com.mojang.renderpearl.api.commands.GpuQueryPool;
import com.mojang.renderpearl.api.device.DeviceInfo;
import com.mojang.renderpearl.api.device.DeviceLimits;
import com.mojang.renderpearl.api.pipeline.RenderPipeline;
import com.mojang.renderpearl.api.pipeline.ShaderSource;
import com.mojang.renderpearl.api.textures.AddressMode;
import com.mojang.renderpearl.api.textures.FilterMode;
import com.mojang.renderpearl.api.textures.GpuSampler;
import com.mojang.renderpearl.api.textures.GpuTexture;
import com.mojang.renderpearl.api.textures.GpuTextureView;
import com.mojang.renderpearl.backend.api.GpuDeviceBackend;
import yt.ren.astatine.Native;

import java.nio.ByteBuffer;
import java.util.OptionalDouble;
import java.util.Set;
import java.util.function.Supplier;

public final class AstatineDevice implements GpuDeviceBackend {
	private static AstatineDevice instance;
	private final AstatineCommandEncoder encoder = new AstatineCommandEncoder();
	private final DeviceInfo info;
	private final boolean demote;
	private final boolean geometryShaders;
	private final boolean tessellationShaders;

	private AstatineDevice() {
		int[] properties = BackendNatives.deviceProperties();
		this.demote = properties[4] != 0;
		this.geometryShaders = properties[5] != 0;
		this.tessellationShaders = properties[6] != 0;
		this.info = new DeviceInfo(Native.deviceName(), Integer.toHexString(properties[8]), Integer.toUnsignedString(properties[9]), true,
			"Vulkan", Float.intBitsToFloat(properties[7]), new DeviceLimits(properties[0], properties[1], properties[2], Long.MAX_VALUE,
			properties[3]), Set.of(Native.vulkanDeviceExtensions()));
	}

	public static synchronized AstatineDevice get() {
		if (instance == null) {
			instance = new AstatineDevice();
		}

		return instance;
	}

	public boolean supportsDemote() {
		return this.demote;
	}

	public boolean supportsGeometryShaders() {
		return this.geometryShaders;
	}

	public boolean supportsTessellationShaders() {
		return this.tessellationShaders;
	}

	@Override
	public AstatineCommandEncoder createCommandEncoder() {
		return this.encoder;
	}

	@Override
	public GpuSampler createSampler(AddressMode addressModeU, AddressMode addressModeV, FilterMode minFilter, FilterMode magFilter,
		int maxAnisotropy, OptionalDouble maxLod) {
		return AstatineSampler.create(addressModeU, addressModeV, minFilter, magFilter, maxAnisotropy, maxLod);
	}

	@Override
	public GpuTexture createTexture(String label, int usage, GpuFormat format, int width, int height, int depthOrLayers, int mipLevels) {
		return AstatineTexture.create(usage, label == null ? "Iris texture" : label, format, width, height, depthOrLayers, mipLevels);
	}

	@Override
	public GpuTextureView createTextureView(GpuTexture texture, int baseMipLevel, int mipLevels) {
		return AstatineTextureView.create((AstatineTexture) texture, baseMipLevel, mipLevels);
	}

	@Override
	public GpuBuffer createBuffer(Supplier<String> label, int usage, long size) {
		return AstatineBuffer.create(label, usage, size, false);
	}

	@Override
	public GpuBuffer createBuffer(Supplier<String> label, int usage, ByteBuffer data) {
		var buffer = AstatineBuffer.create(label, usage, data.remaining(), true);
		AstatineTransientMemory.copy(data, buffer.mappedAddress());
		return buffer;
	}

	@Override
	public DeviceInfo getDeviceInfo() {
		return this.info;
	}

	@Override
	public AstatineRenderPipeline createPipeline(RenderPipeline pipeline, ShaderSource source) {
		return new AstatineRenderPipeline(pipeline, source);
	}

	@Override
	public GpuQueryPool createQueryPool(int size) {
		return new AstatineQueryPool(size);
	}
}
