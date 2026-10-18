package com.mojang.renderpearl.backend.vulkan;

import com.mojang.renderpearl.api.textures.AddressMode;
import com.mojang.renderpearl.api.textures.FilterMode;
import com.mojang.renderpearl.api.textures.GpuSampler;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.vulkan.VK10;
import org.lwjgl.vulkan.VkSamplerCreateInfo;

import java.util.OptionalDouble;

public class VulkanGpuSampler implements GpuSampler, Destroyable {
	private final VulkanDevice device;
	private final AddressMode addressModeU;
	private final AddressMode addressModeV;
	private final FilterMode minFilter;
	private final FilterMode magFilter;
	private final int maxAnisotropy;
	private final OptionalDouble maxLod;
	private final long sampler;
	private final boolean owned;
	private boolean closed;

	public VulkanGpuSampler(VulkanDevice device, AddressMode addressModeU, AddressMode addressModeV, FilterMode minFilter, FilterMode magFilter,
		int maxAnisotropy, OptionalDouble maxLod) {
		this.device = device;
		this.addressModeU = addressModeU;
		this.addressModeV = addressModeV;
		this.minFilter = minFilter;
		this.magFilter = magFilter;
		this.maxAnisotropy = maxAnisotropy;
		this.maxLod = maxLod;
		this.owned = true;

		try (var stack = MemoryStack.stackPush()) {
			boolean anisotropy = maxAnisotropy > 1 && device.supportsAnisotropy();
			var info = VkSamplerCreateInfo.calloc(stack).sType$Default().magFilter(VulkanConst.toVk(magFilter)).minFilter(VulkanConst.toVk(minFilter))
				.mipmapMode(minFilter == FilterMode.LINEAR ? VK10.VK_SAMPLER_MIPMAP_MODE_LINEAR : VK10.VK_SAMPLER_MIPMAP_MODE_NEAREST)
				.addressModeU(VulkanConst.toVk(addressModeU)).addressModeV(VulkanConst.toVk(addressModeV))
				.addressModeW(VulkanConst.toVk(addressModeU)).anisotropyEnable(anisotropy).maxAnisotropy(anisotropy ? maxAnisotropy : 1.0f)
				.minLod(0.0f).maxLod(maxLod.isPresent() ? (float) maxLod.getAsDouble() : VK10.VK_LOD_CLAMP_NONE);
			var handle = stack.mallocLong(1);
			VulkanUtils.check(VK10.vkCreateSampler(device.vkDevice(), info, null, handle), "create sampler");
			this.sampler = handle.get(0);
		}
	}

	public VulkanGpuSampler(VulkanDevice device, long sampler, FilterMode minFilter, FilterMode magFilter, AddressMode addressModeU,
		AddressMode addressModeV) {
		this.device = device;
		this.addressModeU = addressModeU;
		this.addressModeV = addressModeV;
		this.minFilter = minFilter;
		this.magFilter = magFilter;
		this.maxAnisotropy = 1;
		this.maxLod = OptionalDouble.empty();
		this.owned = false;
		this.sampler = sampler;
	}

	public long vkSampler() {
		return this.sampler;
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
			this.device.createCommandEncoder().queueForDestroy(this);
		}
	}

	@Override
	public void destroy() {
		if (this.owned) {
			VK10.vkDestroySampler(this.device.vkDevice(), this.sampler, null);
		}
	}
}
