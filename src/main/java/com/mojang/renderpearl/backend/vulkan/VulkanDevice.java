package com.mojang.renderpearl.backend.vulkan;

import com.mojang.renderpearl.api.GpuFormat;
import com.mojang.renderpearl.api.buffers.GpuBuffer;
import com.mojang.renderpearl.api.device.DeviceInfo;
import com.mojang.renderpearl.api.device.DeviceLimits;
import com.mojang.renderpearl.api.textures.AddressMode;
import com.mojang.renderpearl.api.textures.FilterMode;
import com.mojang.renderpearl.api.textures.GpuSampler;
import com.mojang.renderpearl.api.textures.GpuTexture;
import com.mojang.renderpearl.api.textures.GpuTextureView;
import com.mojang.renderpearl.backend.api.GpuDeviceBackend;
import com.mojang.renderpearl.backend.vulkan.init.FeatureSet;
import net.irisshaders.iris.mixin.VulkanDeviceAccessor;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.system.MemoryUtil;
import org.lwjgl.util.vma.Vma;
import org.lwjgl.util.vma.VmaAllocatorCreateInfo;
import org.lwjgl.util.vma.VmaVulkanFunctions;
import org.lwjgl.vulkan.EXTDebugUtils;
import org.lwjgl.vulkan.VK10;
import org.lwjgl.vulkan.VkDevice;
import org.lwjgl.vulkan.VkPhysicalDevice;
import org.lwjgl.vulkan.VkPhysicalDeviceProperties;
import yt.ren.astatine.vulkan.VulkanContext;

import java.nio.ByteBuffer;
import java.util.OptionalDouble;
import java.util.function.Supplier;

public class VulkanDevice implements GpuDeviceBackend, VulkanDeviceAccessor {
	private static VulkanDevice instance;
	private final VulkanInstance vulkanInstance;
	private final VkDevice device;
	private final long vma;
	private final FeatureSet enabledFeatures;
	private final VulkanCommandEncoder encoder;
	private final DeviceInfo info;
	private final boolean anisotropy;

	private VulkanDevice() {
		this.device = VulkanContext.device();
		this.vulkanInstance = new VulkanInstance(VulkanContext.instance(),
			VulkanContext.instanceExtensions().contains(EXTDebugUtils.VK_EXT_DEBUG_UTILS_EXTENSION_NAME));
		this.enabledFeatures = new EnabledFeatures();
		this.anisotropy = VulkanContext.deviceFeatures().contains("VkPhysicalDeviceFeatures2.samplerAnisotropy");

		try (var stack = MemoryStack.stackPush()) {
			var functions = VmaVulkanFunctions.calloc(stack).set(VulkanContext.instance(), this.device);
			var create = VmaAllocatorCreateInfo.calloc(stack).physicalDevice(this.device.getPhysicalDevice()).device(this.device)
				.instance(VulkanContext.instance()).pVulkanFunctions(functions).vulkanApiVersion(VulkanContext.deviceApiVersion());
			var allocator = stack.mallocPointer(1);
			VulkanUtils.check(Vma.vmaCreateAllocator(create, allocator), "create memory allocator");
			this.vma = allocator.get(0);

			var properties = VkPhysicalDeviceProperties.calloc(stack);
			VK10.vkGetPhysicalDeviceProperties(this.device.getPhysicalDevice(), properties);
			var limits = properties.limits();
			this.info = new DeviceInfo(properties.deviceNameString(), Integer.toHexString(properties.vendorID()),
				Integer.toUnsignedString(properties.driverVersion()), true, "Vulkan", limits.timestampPeriod(),
				new DeviceLimits(Math.max(1, (int) limits.maxSamplerAnisotropy()), (int) Math.max(16, limits.minUniformBufferOffsetAlignment()),
					limits.maxImageDimension2D(), Long.MAX_VALUE, limits.maxColorAttachments()), VulkanContext.deviceExtensions());
		}

		this.encoder = new VulkanCommandEncoder(this);
	}

	public static synchronized VulkanDevice get() {
		if (instance == null) {
			instance = new VulkanDevice();
		}

		return instance;
	}

	public VulkanInstance instance() {
		return this.vulkanInstance;
	}

	public VkDevice vkDevice() {
		return this.device;
	}

	public VkPhysicalDevice getPhysicalDevice() {
		return this.device.getPhysicalDevice();
	}

	public long vma() {
		return this.vma;
	}

	public boolean supportsAnisotropy() {
		return this.anisotropy;
	}

	public DeviceInfo getDeviceInfo() {
		return this.info;
	}

	public VulkanCommandEncoder createCommandEncoder() {
		return this.encoder;
	}

	public GpuSampler createSampler(AddressMode addressModeU, AddressMode addressModeV, FilterMode minFilter, FilterMode magFilter,
		int maxAnisotropy, OptionalDouble maxLod) {
		return new VulkanGpuSampler(this, addressModeU, addressModeV, minFilter, magFilter, maxAnisotropy, maxLod);
	}

	public GpuTexture createTexture(String label, int usage, GpuFormat format, int width, int height, int depthOrLayers, int mipLevels) {
		return new VulkanGpuTexture(this, usage, label == null ? "Iris texture" : label, format, width, height, depthOrLayers, mipLevels);
	}

	public GpuTextureView createTextureView(GpuTexture texture, int baseMipLevel, int mipLevels) {
		return new VulkanGpuTextureView((VulkanGpuTexture) texture, baseMipLevel, mipLevels);
	}

	public VulkanGpuBuffer createBuffer(Supplier<String> label, int usage, long size) {
		return new VulkanGpuBuffer.Direct(this, label, usage, size, false);
	}

	public GpuBuffer createBuffer(Supplier<String> label, int usage, ByteBuffer data) {
		var buffer = new VulkanGpuBuffer.Direct(this, label, usage, data.remaining(), true);
		MemoryUtil.memCopy(MemoryUtil.memAddress(data), buffer.mappedAddress(), data.remaining());
		try (var view = buffer.map(0, Math.max(1, data.remaining()), false, true)) {
			return buffer;
		}
	}

	@Override
	public FeatureSet iris$getEnabledFeatures() {
		return this.enabledFeatures;
	}

	private static final class EnabledFeatures extends FeatureSet {
		private EnabledFeatures() {
			super("Astatine enabled features", java.util.Set.of(), java.util.Set.of());
		}

		@Override
		public boolean contains(FeatureSet other) {
			return other == this || other.isSupported(null);
		}
	}
}
