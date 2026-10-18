package yt.ren.astatine.backend;

import com.mojang.renderpearl.api.textures.AddressMode;
import com.mojang.renderpearl.api.textures.FilterMode;
import com.mojang.renderpearl.api.textures.GpuSampler;

import java.util.OptionalDouble;

public class AstatineSampler implements GpuSampler {
	private final long handle;
	private final AddressMode addressModeU;
	private final AddressMode addressModeV;
	private final FilterMode minFilter;
	private final FilterMode magFilter;
	private final int maxAnisotropy;
	private final OptionalDouble maxLod;
	private boolean closed;

	private AstatineSampler(long handle, AddressMode addressModeU, AddressMode addressModeV, FilterMode minFilter, FilterMode magFilter,
		int maxAnisotropy, OptionalDouble maxLod) {
		this.handle = handle;
		this.addressModeU = addressModeU;
		this.addressModeV = addressModeV;
		this.minFilter = minFilter;
		this.magFilter = magFilter;
		this.maxAnisotropy = maxAnisotropy;
		this.maxLod = maxLod;
	}

	public static AstatineSampler create(AddressMode addressModeU, AddressMode addressModeV, FilterMode minFilter, FilterMode magFilter,
		int maxAnisotropy, OptionalDouble maxLod) {
		long handle = BackendNatives.samplerCreate(addressModeU == AddressMode.CLAMP_TO_EDGE, addressModeV == AddressMode.CLAMP_TO_EDGE,
			minFilter == FilterMode.LINEAR, magFilter == FilterMode.LINEAR, maxAnisotropy,
			maxLod.isPresent() ? (float) maxLod.getAsDouble() : Float.NaN);
		return new AstatineSampler(handle, addressModeU, addressModeV, minFilter, magFilter, maxAnisotropy, maxLod);
	}

	public static AstatineSampler wrap(long vulkanSampler, FilterMode minFilter, FilterMode magFilter, AddressMode addressModeU,
		AddressMode addressModeV) {
		return new AstatineSampler(BackendNatives.samplerWrap(vulkanSampler), addressModeU, addressModeV, minFilter, magFilter, 1,
			OptionalDouble.empty());
	}

	public long handle() {
		return this.handle;
	}

	@Override
	public AddressMode getAddressModeU() {
		return this.addressModeU;
	}

	@Override
	public AddressMode getAddressModeV() {
		return this.addressModeV;
	}

	@Override
	public FilterMode getMinFilter() {
		return this.minFilter;
	}

	@Override
	public FilterMode getMagFilter() {
		return this.magFilter;
	}

	@Override
	public int getMaxAnisotropy() {
		return this.maxAnisotropy;
	}

	@Override
	public OptionalDouble getMaxLod() {
		return this.maxLod;
	}

	@Override
	public boolean isClosed() {
		return this.closed;
	}

	@Override
	public void close() {
		if (!this.closed) {
			this.closed = true;
			BackendNatives.samplerRelease(this.handle);
		}
	}
}
