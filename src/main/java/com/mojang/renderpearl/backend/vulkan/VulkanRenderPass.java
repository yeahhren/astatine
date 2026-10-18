package com.mojang.renderpearl.backend.vulkan;

import com.mojang.renderpearl.api.buffers.GpuBuffer;
import com.mojang.renderpearl.api.buffers.GpuBufferSlice;
import com.mojang.renderpearl.api.commands.GpuQueryPool;
import com.mojang.renderpearl.api.commands.RenderPass;
import com.mojang.renderpearl.api.pipeline.IndexType;
import com.mojang.renderpearl.backend.api.BackendRenderPipeline;
import com.mojang.renderpearl.backend.api.RenderPassBackend;
import org.lwjgl.PointerBuffer;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.vulkan.VK10;
import org.lwjgl.vulkan.VkCommandBuffer;
import org.lwjgl.vulkan.VkRect2D;
import org.lwjgl.vulkan.VkViewport;

import java.nio.ByteBuffer;
import java.nio.IntBuffer;
import java.util.function.Supplier;

public class VulkanRenderPass implements RenderPassBackend {
	private final VulkanCommandEncoder encoder;
	private final VkCommandBuffer command;
	private final RenderPass.RenderArea area;
	private int indexSize = 4;

	VulkanRenderPass(VulkanCommandEncoder encoder, VkCommandBuffer command, RenderPass.RenderArea area) {
		this.encoder = encoder;
		this.command = command;
		this.area = area;

		try (var stack = MemoryStack.stackPush()) {
			VK10.vkCmdSetViewport(command, 0,
				VkViewport.calloc(1, stack).x(0).y(0).width(area.x() + area.width()).height(area.y() + area.height()).minDepth(0).maxDepth(1));
		}

		this.disableScissor();
	}

	public RenderPass.RenderArea renderArea() {
		return this.area;
	}

	public VkCommandBuffer commandBuffer() {
		return this.command;
	}

	@Override
	public void pushDebugGroup(Supplier<String> label) {
	}

	@Override
	public void popDebugGroup() {
	}

	@Override
	public void setPipeline(BackendRenderPipeline pipeline) {
	}

	@Override
	public void setUniform(int index, Object value) {
	}

	@Override
	public void pushConstants(ByteBuffer data) {
	}

	@Override
	public void enableScissor(int x, int y, int width, int height) {
		try (var stack = MemoryStack.stackPush()) {
			var scissor = VkRect2D.calloc(1, stack);
			scissor.offset().set(Math.max(0, x), Math.max(0, y));
			scissor.extent().set(Math.max(0, width), Math.max(0, height));
			VK10.vkCmdSetScissor(this.command, 0, scissor);
		}
	}

	@Override
	public void disableScissor() {
		this.enableScissor(this.area.x(), this.area.y(), this.area.width(), this.area.height());
	}

	@Override
	public void setVertexBuffer(int index, GpuBufferSlice slice) {
		if (slice == null) {
			return;
		}

		try (var stack = MemoryStack.stackPush()) {
			VK10.vkCmdBindVertexBuffers(this.command, index, stack.longs(((VulkanGpuBuffer) slice.buffer()).vkBuffer()), stack.longs(slice.offset()));
		}
	}

	@Override
	public void setIndexBuffer(GpuBuffer buffer, IndexType type) {
		this.indexSize = type.bytes;
		VK10.vkCmdBindIndexBuffer(this.command, ((VulkanGpuBuffer) buffer).vkBuffer(), 0,
			type == IndexType.INT ? VK10.VK_INDEX_TYPE_UINT32 : VK10.VK_INDEX_TYPE_UINT16);
	}

	@Override
	public void writeTimestamp(GpuQueryPool pool, int query) {
	}

	@Override
	public void draw(int count, int instances, int first, int firstInstance) {
		VK10.vkCmdDraw(this.command, count, instances, first, firstInstance);
	}

	@Override
	public void drawIndexed(int count, int instances, int first, int offset, int firstInstance) {
		VK10.vkCmdDrawIndexed(this.command, count, instances, first, offset, firstInstance);
	}

	@Override
	public void drawIndirect(GpuBufferSlice commands, int count) {
		VK10.vkCmdDrawIndirect(this.command, ((VulkanGpuBuffer) commands.buffer()).vkBuffer(), commands.offset(), count, 16);
	}

	@Override
	public void drawIndexedIndirect(GpuBufferSlice commands, int count) {
		VK10.vkCmdDrawIndexedIndirect(this.command, ((VulkanGpuBuffer) commands.buffer()).vkBuffer(), commands.offset(), count, 20);
	}

	@Override
	public void multiDraw(IntBuffer commands, int instances, int firstInstance, int count) {
		for (int i = 0; i < count; i++) {
			int base = commands.position() + i * 2;
			this.draw(commands.get(base + 1), instances, commands.get(base), firstInstance);
		}
	}

	@Override
	public void multiDrawIndexed(IntBuffer commands, int instances, int firstInstance, int count) {
		for (int i = 0; i < count; i++) {
			int base = commands.position() + i * 3;
			this.drawIndexed(commands.get(base + 1), instances, commands.get(base), commands.get(base + 2), firstInstance);
		}
	}

	@Override
	public void multiDraw(IntBuffer first, IntBuffer counts, int count) {
		for (int i = 0; i < count; i++) {
			this.draw(counts.get(counts.position() + i), 1, first.get(first.position() + i), 0);
		}
	}

	@Override
	public void multiDrawIndexed(PointerBuffer offsets, IntBuffer counts, IntBuffer bases, int count) {
		for (int i = 0; i < count; i++) {
			this.drawIndexed(counts.get(counts.position() + i), 1, Math.toIntExact(offsets.get(offsets.position() + i) / this.indexSize),
				bases == null ? 0 : bases.get(bases.position() + i), 0);
		}
	}
}
