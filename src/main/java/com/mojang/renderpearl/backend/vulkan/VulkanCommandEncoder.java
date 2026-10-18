package com.mojang.renderpearl.backend.vulkan;

import com.mojang.renderpearl.api.buffers.GpuBufferSlice;
import com.mojang.renderpearl.api.buffers.TransientMemory;
import com.mojang.renderpearl.api.commands.RenderPassDescriptor;
import com.mojang.renderpearl.api.textures.GpuTexture;
import com.mojang.renderpearl.backend.api.CommandEncoderBackend;
import com.mojang.renderpearl.backend.api.RenderPassBackend;
import net.irisshaders.iris.Iris;
import net.irisshaders.iris.mixinterface.VulkanWorldPassAccess;
import net.irisshaders.iris.vulkan.VulkanRenderBackend;
import net.irisshaders.iris.vulkan.VulkanTextureOps;
import net.irisshaders.iris.vulkan.VulkanWorldCommands;
import org.joml.Vector4fc;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.system.MemoryUtil;
import org.lwjgl.vulkan.KHRSynchronization2;
import org.lwjgl.vulkan.VK10;
import org.lwjgl.vulkan.VkBufferCopy;
import org.lwjgl.vulkan.VkBufferImageCopy;
import org.lwjgl.vulkan.VkClearColorValue;
import org.lwjgl.vulkan.VkClearDepthStencilValue;
import org.lwjgl.vulkan.VkCommandBuffer;
import org.lwjgl.vulkan.VkDependencyInfo;
import org.lwjgl.vulkan.VkImageCopy;
import org.lwjgl.vulkan.VkImageSubresourceRange;
import org.lwjgl.vulkan.VkMemoryBarrier2;
import yt.ren.astatine.Native;

import java.nio.ByteBuffer;
import java.util.ArrayDeque;

public class VulkanCommandEncoder implements CommandEncoderBackend, VulkanWorldPassAccess {
	private final VulkanDevice device;
	private final int delay;
	private final VulkanTransientMemory transientMemory;
	private final ArrayDeque<Retired> retired = new ArrayDeque<>();
	public VulkanRenderPass currentRenderPass;
	private VkCommandBuffer command;
	private long frame = Long.MIN_VALUE;
	private VulkanWorldCommands worldPass;
	private VulkanWorldCommands closingWorldPass;
	private RenderPassDescriptor creatingPass;

	VulkanCommandEncoder(VulkanDevice device) {
		this.device = device;
		this.delay = Native.framesInFlight() + 2;
		this.transientMemory = new VulkanTransientMemory(device, this.delay);
	}

	public static void memoryBarrier(VkCommandBuffer command, MemoryStack stack) {
		var barrier = VkMemoryBarrier2.calloc(1, stack).sType$Default()
			.srcStageMask(VK10.VK_PIPELINE_STAGE_ALL_COMMANDS_BIT).srcAccessMask(VK10.VK_ACCESS_MEMORY_READ_BIT | VK10.VK_ACCESS_MEMORY_WRITE_BIT)
			.dstStageMask(VK10.VK_PIPELINE_STAGE_ALL_COMMANDS_BIT).dstAccessMask(VK10.VK_ACCESS_MEMORY_READ_BIT | VK10.VK_ACCESS_MEMORY_WRITE_BIT);
		KHRSynchronization2.vkCmdPipelineBarrier2KHR(command, VkDependencyInfo.calloc(stack).sType$Default().pMemoryBarriers(barrier));
	}

	private void advance() {
		long presented = Native.presentedFrames();

		if (presented == this.frame) {
			return;
		}

		this.frame = presented;
		this.transientMemory.beginFrame(presented);

		while (!this.retired.isEmpty() && this.retired.peekFirst().frame() + this.delay <= presented) {
			this.retired.pollFirst().destroyable().destroy();
		}
	}

	public VkCommandBuffer commandBuffer() {
		this.advance();
		long handle = Native.borrowCommands();

		if (this.command == null || this.command.address() != handle) {
			this.command = new VkCommandBuffer(handle, this.device.vkDevice());
		}

		return this.command;
	}

	public void queueForDestroy(Destroyable destroyable) {
		this.advance();
		this.retired.addLast(new Retired(destroyable, this.frame));
	}

	@Override
	public TransientMemory transientMemory() {
		this.advance();
		return this.transientMemory;
	}

	public VulkanTransientMemory transientAllocator() {
		this.advance();
		return this.transientMemory;
	}

	@Override
	public RenderPassBackend createRenderPass(RenderPassDescriptor descriptor) {
		if (this.currentRenderPass != null) {
			throw new IllegalStateException("a render pass is already open");
		}

		var command = this.commandBuffer();

		try (var stack = MemoryStack.stackPush()) {
			((VulkanRenderBackend) Iris.getRenderBackend()).getRenderPassCache().begin(command, stack, descriptor);
		}

		this.currentRenderPass = new VulkanRenderPass(this, command, descriptor.renderArea());
		Native.externalPass(true);
		return this.currentRenderPass;
	}

	@Override
	public RenderPassBackend iris$createRenderPass(RenderPassDescriptor descriptor) {
		if (this.currentRenderPass != null || this.creatingPass != null) {
			throw new IllegalStateException("a render pass is already open");
		}

		this.creatingPass = descriptor;

		try {
			return this.createRenderPass(descriptor);
		} finally {
			this.creatingPass = null;
		}
	}

	@Override
	public void iris$setWorldPass(VulkanWorldCommands pass) {
		if (this.worldPass != null || !pass.owns(this.currentRenderPass)) {
			throw new IllegalStateException("invalid Iris world pass ownership");
		}

		this.worldPass = pass;
	}

	@Override
	public void submitRenderPass() {
		if (this.currentRenderPass == null) {
			throw new IllegalStateException("no render pass is open");
		}

		var pass = this.worldPass;
		this.worldPass = null;
		this.closingWorldPass = pass != null && pass.owns(this.currentRenderPass) ? pass : null;
		var command = this.commandBuffer();
		VK10.vkCmdEndRenderPass(command);
		this.currentRenderPass = null;
		Native.externalPass(false);

		try (var stack = MemoryStack.stackPush()) {
			var closing = this.closingWorldPass;
			this.closingWorldPass = null;

			if (closing == null) {
				memoryBarrier(command, stack);
			} else {
				closing.finish(stack);
			}
		}
	}

	public boolean isInRenderPass() {
		return this.currentRenderPass != null;
	}

	public void clearColorTexture(GpuTexture texture, Vector4fc color) {
		var command = this.commandBuffer();

		try (var stack = MemoryStack.stackPush()) {
			memoryBarrier(command, stack);
			var value = VkClearColorValue.calloc(stack);
			VulkanTextureOps.clearValue(value, texture.getFormat(), color);
			var range = VkImageSubresourceRange.calloc(stack).aspectMask(VK10.VK_IMAGE_ASPECT_COLOR_BIT).baseMipLevel(0)
				.levelCount(texture.getMipLevels()).baseArrayLayer(0).layerCount(texture.getDepthOrLayers());
			VK10.vkCmdClearColorImage(command, ((VulkanGpuTexture) texture).vkImage(), VK10.VK_IMAGE_LAYOUT_GENERAL, value, range);
			memoryBarrier(command, stack);
		}
	}

	public void clearDepthTexture(GpuTexture texture, double depth) {
		var command = this.commandBuffer();

		try (var stack = MemoryStack.stackPush()) {
			memoryBarrier(command, stack);
			var range = VkImageSubresourceRange.calloc(stack).aspectMask(VulkanConst.formatAspectMask(texture.getFormat())).baseMipLevel(0)
				.levelCount(texture.getMipLevels()).baseArrayLayer(0).layerCount(texture.getDepthOrLayers());
			VK10.vkCmdClearDepthStencilImage(command, ((VulkanGpuTexture) texture).vkImage(), VK10.VK_IMAGE_LAYOUT_GENERAL,
				VkClearDepthStencilValue.calloc(stack).depth((float) depth), range);
			memoryBarrier(command, stack);
		}
	}

	public void writeToBuffer(GpuBufferSlice destination, ByteBuffer data) {
		var staging = this.transientAllocator().allocateMapped(data.remaining(), 4);
		MemoryUtil.memCopy(MemoryUtil.memAddress(data), staging.address(), data.remaining());
		staging.flush();
		var command = this.commandBuffer();

		try (var stack = MemoryStack.stackPush()) {
			memoryBarrier(command, stack);
			VK10.vkCmdCopyBuffer(command, staging.buffer().vkBuffer(), ((VulkanGpuBuffer) destination.buffer()).vkBuffer(),
				VkBufferCopy.calloc(1, stack).srcOffset(staging.offset()).dstOffset(destination.offset()).size(data.remaining()));
			memoryBarrier(command, stack);
		}
	}

	public void writeToTexture(GpuTexture destination, ByteBuffer source, int mipLevel, int depthOrLayer, int x, int y, int width, int height) {
		var staging = this.transientAllocator().allocateMapped(source.remaining(), destination.getFormat().blockSize());
		MemoryUtil.memCopy(MemoryUtil.memAddress(source), staging.address(), source.remaining());
		staging.flush();
		var command = this.commandBuffer();

		try (var stack = MemoryStack.stackPush()) {
			memoryBarrier(command, stack);
			var region = VkBufferImageCopy.calloc(1, stack).bufferOffset(staging.offset()).bufferRowLength(width).bufferImageHeight(height);
			region.imageSubresource().aspectMask(VulkanConst.formatAspectMask(destination.getFormat())).mipLevel(mipLevel)
				.baseArrayLayer(depthOrLayer).layerCount(1);
			region.imageOffset().set(x, y, 0);
			region.imageExtent().set(width, height, 1);
			VK10.vkCmdCopyBufferToImage(command, staging.buffer().vkBuffer(), ((VulkanGpuTexture) destination).vkImage(),
				VK10.VK_IMAGE_LAYOUT_GENERAL, region);
			memoryBarrier(command, stack);
		}
	}

	public void copyTextureToTexture(GpuTexture source, GpuTexture destination, int mipLevel, int destinationX, int destinationY, int sourceX,
		int sourceY, int width, int height) {
		var command = this.commandBuffer();

		try (var stack = MemoryStack.stackPush()) {
			memoryBarrier(command, stack);
			var region = VkImageCopy.calloc(1, stack);
			region.srcSubresource().aspectMask(VulkanConst.formatAspectMask(source.getFormat())).mipLevel(mipLevel).baseArrayLayer(0).layerCount(1);
			region.dstSubresource().aspectMask(VulkanConst.formatAspectMask(destination.getFormat())).mipLevel(mipLevel).baseArrayLayer(0)
				.layerCount(1);
			region.srcOffset().set(sourceX, sourceY, 0);
			region.dstOffset().set(destinationX, destinationY, 0);
			region.extent().set(width, height, 1);
			VK10.vkCmdCopyImage(command, ((VulkanGpuTexture) source).vkImage(), VK10.VK_IMAGE_LAYOUT_GENERAL,
				((VulkanGpuTexture) destination).vkImage(), VK10.VK_IMAGE_LAYOUT_GENERAL, region);
			memoryBarrier(command, stack);
		}
	}

	private record Retired(Destroyable destroyable, long frame) {
	}
}
