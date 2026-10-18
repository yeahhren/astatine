package com.mojang.renderpearl.backend.vulkan;

import com.mojang.renderpearl.api.GpuFormat;
import com.mojang.renderpearl.api.textures.GpuTexture;
import net.irisshaders.iris.Iris;
import net.irisshaders.iris.vulkan.VulkanRenderBackend;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.util.vma.Vma;
import org.lwjgl.util.vma.VmaAllocationCreateInfo;
import org.lwjgl.vulkan.VK10;
import org.lwjgl.vulkan.VkImageCreateInfo;
import org.lwjgl.vulkan.VkImageMemoryBarrier;

public class VulkanGpuTexture implements GpuTexture, Destroyable {
	private final VulkanDevice device;
	private final int usage;
	private final String label;
	private final GpuFormat format;
	private final int width;
	private final int height;
	private final int depthOrLayers;
	private final int mipLevels;
	private final long image;
	private final long allocation;
	private final boolean owned;
	private boolean closed;

	public VulkanGpuTexture(VulkanDevice device, int usage, String label, GpuFormat format, int width, int height, int depthOrLayers,
		int mipLevels) {
		this.device = device;
		this.usage = usage;
		this.label = label;
		this.format = format;
		this.width = width;
		this.height = height;
		this.depthOrLayers = depthOrLayers;
		this.mipLevels = mipLevels;
		this.owned = true;

		try (var stack = MemoryStack.stackPush()) {
			boolean cube = (usage & GpuTexture.USAGE_CUBEMAP_COMPATIBLE) != 0;
			int vkUsage = VulkanConst.textureUsageToVk(usage, format) | VK10.VK_IMAGE_USAGE_TRANSFER_SRC_BIT | VK10.VK_IMAGE_USAGE_TRANSFER_DST_BIT;

			if ((usage & VulkanGpuTexture.USAGE_STORAGE) != 0) {
				vkUsage |= VK10.VK_IMAGE_USAGE_STORAGE_BIT;
			}

			var info = VkImageCreateInfo.calloc(stack).sType$Default().imageType(VK10.VK_IMAGE_TYPE_2D).format(VulkanConst.toVk(format))
				.mipLevels(mipLevels).arrayLayers(depthOrLayers).samples(VK10.VK_SAMPLE_COUNT_1_BIT).tiling(VK10.VK_IMAGE_TILING_OPTIMAL)
				.usage(vkUsage).sharingMode(VK10.VK_SHARING_MODE_EXCLUSIVE).initialLayout(VK10.VK_IMAGE_LAYOUT_UNDEFINED)
				.flags(cube ? VK10.VK_IMAGE_CREATE_CUBE_COMPATIBLE_BIT : 0);
			info.extent().set(width, height, 1);
			var handle = stack.mallocLong(1);
			var memory = stack.mallocPointer(1);
			VulkanUtils.check(Vma.vmaCreateImage(device.vma(), info, VmaAllocationCreateInfo.calloc(stack).usage(Vma.VMA_MEMORY_USAGE_AUTO), handle,
				memory, null), "create texture " + label);
			this.image = handle.get(0);
			this.allocation = memory.get(0);
			device.instance().debug().setObjectName(device.vkDevice(), VK10.VK_OBJECT_TYPE_IMAGE, this.image, label);

			var command = device.createCommandEncoder().commandBuffer();
			var barrier = VkImageMemoryBarrier.calloc(1, stack).sType$Default().oldLayout(VK10.VK_IMAGE_LAYOUT_UNDEFINED)
				.newLayout(VK10.VK_IMAGE_LAYOUT_GENERAL).srcAccessMask(0).dstAccessMask(VK10.VK_ACCESS_MEMORY_READ_BIT | VK10.VK_ACCESS_MEMORY_WRITE_BIT)
				.srcQueueFamilyIndex(VK10.VK_QUEUE_FAMILY_IGNORED).dstQueueFamilyIndex(VK10.VK_QUEUE_FAMILY_IGNORED).image(this.image);
			barrier.subresourceRange().aspectMask(VulkanConst.formatAspectMask(format)).baseMipLevel(0).levelCount(mipLevels).baseArrayLayer(0)
				.layerCount(depthOrLayers);
			VK10.vkCmdPipelineBarrier(command, VK10.VK_PIPELINE_STAGE_TOP_OF_PIPE_BIT, VK10.VK_PIPELINE_STAGE_ALL_COMMANDS_BIT, 0, null, null,
				barrier);
		}
	}

	public VulkanGpuTexture(VulkanDevice device, long image, int usage, String label, GpuFormat format, int width, int height, int depthOrLayers,
		int mipLevels) {
		this.device = device;
		this.usage = usage;
		this.label = label;
		this.format = format;
		this.width = width;
		this.height = height;
		this.depthOrLayers = depthOrLayers;
		this.mipLevels = mipLevels;
		this.image = image;
		this.allocation = 0;
		this.owned = false;
	}

	public static final int USAGE_STORAGE = 1 << 20;

	public long vkImage() {
		return this.image;
	}

	public VulkanDevice device() {
		return this.device;
	}

	@Override
	public int getWidth(int mipLevel) {
		return Math.max(1, this.width >> mipLevel);
	}

	@Override
	public int getHeight(int mipLevel) {
		return Math.max(1, this.height >> mipLevel);
	}

	@Override
	public int getDepthOrLayers() {
		return this.depthOrLayers;
	}

	@Override
	public int getMipLevels() {
		return this.mipLevels;
	}

	@Override
	public GpuFormat getFormat() {
		return this.format;
	}

	@Override
	public int usage() {
		return this.usage;
	}

	@Override
	public String getLabel() {
		return this.label;
	}

	@Override
	public boolean isClosed() {
		return this.closed;
	}

	@Override
	public void close() {
		if (this.closed) {
			return;
		}

		this.closed = true;

		if (Iris.getRenderBackend() instanceof VulkanRenderBackend backend) {
			backend.onDeleteTexture(this);
		}

		if (this.owned) {
			this.device.createCommandEncoder().queueForDestroy(this);
		}
	}

	@Override
	public void destroy() {
		Vma.vmaDestroyImage(this.device.vma(), this.image, this.allocation);
	}
}
