package com.mojang.renderpearl.backend.vulkan;

import com.mojang.renderpearl.api.textures.GpuTextureView;
import net.irisshaders.iris.Iris;
import net.irisshaders.iris.vulkan.VulkanRenderBackend;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.vulkan.VK10;
import org.lwjgl.vulkan.VkImageViewCreateInfo;

public class VulkanGpuTextureView implements GpuTextureView, Destroyable {
	private final VulkanGpuTexture texture;
	private final int baseMipLevel;
	private final int mipLevels;
	private final long view;
	private final boolean owned;
	private final int imageLayout;
	private boolean closed;

	public VulkanGpuTextureView(VulkanGpuTexture texture, int baseMipLevel, int mipLevels) {
		this.texture = texture;
		this.baseMipLevel = baseMipLevel;
		this.mipLevels = mipLevels;
		this.owned = true;
		this.imageLayout = VK10.VK_IMAGE_LAYOUT_GENERAL;

		try (var stack = MemoryStack.stackPush()) {
			boolean cube = texture.getDepthOrLayers() == 6 && (texture.usage() & USAGE_CUBEMAP) != 0;
			int aspect = texture.getFormat().hasDepthAspect() ? VK10.VK_IMAGE_ASPECT_DEPTH_BIT : VulkanConst.formatAspectMask(texture.getFormat());
			var info = VkImageViewCreateInfo.calloc(stack).sType$Default().image(texture.vkImage())
				.viewType(cube ? VK10.VK_IMAGE_VIEW_TYPE_CUBE : texture.getDepthOrLayers() > 1 ? VK10.VK_IMAGE_VIEW_TYPE_2D_ARRAY : VK10.VK_IMAGE_VIEW_TYPE_2D)
				.format(VulkanConst.toVk(texture.getFormat()));
			info.subresourceRange().aspectMask(aspect).baseMipLevel(baseMipLevel).levelCount(mipLevels).baseArrayLayer(0)
				.layerCount(texture.getDepthOrLayers());
			var handle = stack.mallocLong(1);
			VulkanUtils.check(VK10.vkCreateImageView(texture.device().vkDevice(), info, null, handle), "create view of " + texture.getLabel());
			this.view = handle.get(0);
		}
	}

	public VulkanGpuTextureView(VulkanGpuTexture texture, long view, int baseMipLevel, int mipLevels, int imageLayout) {
		this.texture = texture;
		this.baseMipLevel = baseMipLevel;
		this.mipLevels = mipLevels;
		this.view = view;
		this.owned = false;
		this.imageLayout = imageLayout;
	}

	public int imageLayout() {
		return this.imageLayout;
	}

	private static final int USAGE_CUBEMAP = 16;

	public long vkImageView() {
		return this.view;
	}

	@Override
	public boolean isClosed() {
		return this.closed;
	}

	@Override
	public VulkanGpuTexture texture() {
		return this.texture;
	}

	@Override
	public int baseMipLevel() {
		return this.baseMipLevel;
	}

	@Override
	public int mipLevels() {
		return this.mipLevels;
	}

	@Override
	public int getWidth(int mipLevel) {
		return this.texture.getWidth(mipLevel + this.baseMipLevel);
	}

	@Override
	public int getHeight(int mipLevel) {
		return this.texture.getHeight(mipLevel + this.baseMipLevel);
	}

	@Override
	public void close() {
		if (this.closed) {
			return;
		}

		this.closed = true;

		if (Iris.getRenderBackend() instanceof VulkanRenderBackend backend) {
			backend.onDeleteTextureView(this.view);
		}

		if (this.owned) {
			this.texture.device().createCommandEncoder().queueForDestroy(this);
		}
	}

	@Override
	public void destroy() {
		VK10.vkDestroyImageView(this.texture.device().vkDevice(), this.view, null);
	}
}
