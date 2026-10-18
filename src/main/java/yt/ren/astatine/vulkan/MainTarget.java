package yt.ren.astatine.vulkan;

import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.renderpearl.api.GpuFormat;
import com.mojang.renderpearl.api.textures.GpuTexture;
import com.mojang.renderpearl.api.textures.GpuTextureView;
import com.mojang.renderpearl.backend.vulkan.VulkanConst;
import com.mojang.renderpearl.backend.vulkan.VulkanDevice;
import com.mojang.renderpearl.backend.vulkan.VulkanGpuTexture;
import com.mojang.renderpearl.backend.vulkan.VulkanGpuTextureView;
import net.minecraft.client.Minecraft;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.vulkan.VK10;
import org.lwjgl.vulkan.VkCommandBuffer;
import org.lwjgl.vulkan.VkImageMemoryBarrier;
import yt.ren.astatine.Native;
import yt.ren.astatine.extension.FramebufferHolder;

public final class MainTarget {
	private static final RenderTarget TARGET = new RenderTarget();
	private static long framebuffer;
	private static VulkanGpuTexture color;
	private static VulkanGpuTextureView colorView;
	private static GpuTexture depth;
	private static GpuTextureView depthView;
	private static boolean general;

	private MainTarget() {
	}

	public static RenderTarget get() {
		var holder = (FramebufferHolder) Minecraft.getInstance().getRenderTarget();
		long current = holder.astatine$framebuffer();

		if (current != framebuffer || color == null) {
			framebuffer = current;
			var images = Native.framebufferImages(current);

			if (images == null) {
				throw new IllegalStateException("the main framebuffer has no color image");
			}

			int width = (int) images[3];
			int height = (int) images[4];
			color = new VulkanGpuTexture(VulkanDevice.get(), images[0],
				GpuTexture.USAGE_RENDER_ATTACHMENT | GpuTexture.USAGE_TEXTURE_BINDING | GpuTexture.USAGE_COPY_SRC | GpuTexture.USAGE_COPY_DST,
				"Minecraft main color", VulkanConst.fromVk((int) images[2]), width, height, 1, (int) images[5]);
			colorView = new VulkanGpuTextureView(color, images[1], 0, 1, VK10.VK_IMAGE_LAYOUT_GENERAL);

			if (depthView != null) {
				depthView.close();
				depth.close();
			}

			var device = RenderSystem.getDevice();
			depth = device.createTexture("Iris main depth",
				GpuTexture.USAGE_RENDER_ATTACHMENT | GpuTexture.USAGE_TEXTURE_BINDING | GpuTexture.USAGE_COPY_SRC | GpuTexture.USAGE_COPY_DST,
				GpuFormat.D32_FLOAT, width, height, 1, 1);
			depthView = device.createTextureView(depth);
			general = false;
			TARGET.update(width, height, color, colorView, depth, depthView);
		}

		return TARGET;
	}

	public static GpuTexture colorTexture() {
		return get().getColorTexture();
	}

	public static void beginAccess(VkCommandBuffer command) {
		get();

		if (!general) {
			transition(command, VK10.VK_IMAGE_LAYOUT_SHADER_READ_ONLY_OPTIMAL, VK10.VK_IMAGE_LAYOUT_GENERAL);
			general = true;
		}
	}

	public static void endAccess(VkCommandBuffer command) {
		if (general) {
			transition(command, VK10.VK_IMAGE_LAYOUT_GENERAL, VK10.VK_IMAGE_LAYOUT_SHADER_READ_ONLY_OPTIMAL);
			general = false;
		}
	}

	private static void transition(VkCommandBuffer command, int from, int to) {
		try (var stack = MemoryStack.stackPush()) {
			var barrier = VkImageMemoryBarrier.calloc(1, stack).sType$Default().oldLayout(from).newLayout(to)
				.srcAccessMask(VK10.VK_ACCESS_MEMORY_READ_BIT | VK10.VK_ACCESS_MEMORY_WRITE_BIT)
				.dstAccessMask(VK10.VK_ACCESS_MEMORY_READ_BIT | VK10.VK_ACCESS_MEMORY_WRITE_BIT)
				.srcQueueFamilyIndex(VK10.VK_QUEUE_FAMILY_IGNORED).dstQueueFamilyIndex(VK10.VK_QUEUE_FAMILY_IGNORED).image(color.vkImage());
			barrier.subresourceRange().aspectMask(VK10.VK_IMAGE_ASPECT_COLOR_BIT).baseMipLevel(0).levelCount(1).baseArrayLayer(0).layerCount(1);
			VK10.vkCmdPipelineBarrier(command, VK10.VK_PIPELINE_STAGE_ALL_COMMANDS_BIT, VK10.VK_PIPELINE_STAGE_ALL_COMMANDS_BIT, 0, null, null,
				barrier);
		}
	}
}
