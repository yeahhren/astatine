package com.mojang.renderpearl.backend.api;

import com.mojang.renderpearl.api.buffers.GpuBuffer;
import com.mojang.renderpearl.api.buffers.GpuBufferSlice;
import com.mojang.renderpearl.api.commands.GpuQueryPool;
import com.mojang.renderpearl.api.pipeline.IndexType;
import org.lwjgl.PointerBuffer;

import java.nio.ByteBuffer;
import java.nio.IntBuffer;
import java.util.function.Supplier;

public interface RenderPassBackend {
	void pushDebugGroup(Supplier<String> label);

	void popDebugGroup();

	void setPipeline(BackendRenderPipeline pipeline);

	void setUniform(int index, Object value);

	void pushConstants(ByteBuffer data);

	void enableScissor(int x, int y, int width, int height);

	void disableScissor();

	void setVertexBuffer(int index, GpuBufferSlice slice);

	void setIndexBuffer(GpuBuffer buffer, IndexType type);

	void writeTimestamp(GpuQueryPool pool, int query);

	void draw(int count, int instances, int first, int firstInstance);

	void drawIndexed(int count, int instances, int first, int offset, int firstInstance);

	void drawIndirect(GpuBufferSlice commands, int count);

	void drawIndexedIndirect(GpuBufferSlice commands, int count);

	void multiDraw(IntBuffer commands, int instances, int firstInstance, int count);

	void multiDrawIndexed(IntBuffer commands, int instances, int firstInstance, int count);

	void multiDraw(IntBuffer first, IntBuffer counts, int count);

	void multiDrawIndexed(PointerBuffer offsets, IntBuffer counts, IntBuffer bases, int count);
}
