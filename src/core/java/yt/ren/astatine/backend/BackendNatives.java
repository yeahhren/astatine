package yt.ren.astatine.backend;

public final class BackendNatives {
	private BackendNatives() {
	}

	public static native int[] deviceProperties();

	public static native long bufferCreate(int usage, long size, boolean hostVisible, String label);

	public static native long bufferWrap(long handle, long size, int usage);

	public static native long bufferAddress(long buffer);

	public static native void bufferRelease(long buffer);

	public static native long textureCreate(int usage, int format, int width, int height, int layers, int levels, String label);

	public static native int[] textureInfo(long texture);

	public static native void textureRelease(long texture);

	public static native long textureViewCreate(long texture, int baseLevel, int levels);

	public static native long textureViewTexture(long view);

	public static native void textureViewRelease(long view);

	public static native long framebufferColorView(long framebuffer);

	public static native long nativeTextureView(long texture);

	public static native void wrapperRelease(long view);

	public static native long imageCreate(int dimension, int format, int width, int height, int depth, boolean storage, String label);

	public static native long imageView(long texture);

	public static native void imageUpload(long texture, long source, long size);

	public static native void imageClear(long texture);

	public static native long samplerCreate(boolean clampU, boolean clampV, boolean linearMin, boolean linearMag, int maxAnisotropy,
		float maxLod);

	public static native long samplerWrap(long handle);

	public static native void samplerRelease(long sampler);

	public static native void transientAllocate(long size, long alignment, long out);

	public static native void clearColorTexture(long texture, float red, float green, float blue, float alpha);

	public static native void clearDepthTexture(long texture, double depth);

	public static native void writeBuffer(long buffer, long offset, long source, long size);

	public static native void writeTexture(long texture, long source, long size, int level, int layer, int x, int y, int width,
		int height);

	public static native void copyTexture(long source, long destination, int level, int destinationX, int destinationY, int sourceX,
		int sourceY, int width, int height);

	public static native void renderPassBegin(long attachments, int colorCount, boolean hasDepth, int x, int y, int width, int height);

	public static native void renderPassEnd(boolean world);

	public static native void renderPassAttachments(long views, int colorCount, long depth, int x, int y, int width, int height);

	public static native void setViewport(float x, float y, float width, float height);

	public static native void setScissor(int x, int y, int width, int height);

	public static native void setVertexBuffer(int slot, long buffer, long offset);

	public static native void setIndexBuffer(long buffer, long offset, boolean wide);

	public static native void draw(int count, int instances, int first, int firstInstance);

	public static native void drawIndexed(int count, int instances, int first, int vertexOffset, int firstInstance);

	public static native void drawIndirect(long buffer, long offset, int count, boolean indexed);

	public static native void multiDraw(long parameters, int count, int instances, int firstInstance, boolean indexed);

	public static native void memoryBarrier();

	public static native void mainTargetAccess(long texture, boolean general);

	public static native void synchronize(long records, int count, int defaultStage);

	public static native long resourceSetsCreate(long descriptors, int count, int capacity, int setIndex, long uniformSize);

	public static native void resourceSetsRelease(long sets);

	public static native long samplerHeapCreate(long types, int heapCount, int drawCount, int capacity);

	public static native void samplerHeapBeginFrame(long heap);

	public static native void samplerHeapRelease(long heap);

	public static native long shaderCompile(int stage, String name, String source);

	public static native void shaderRelease(long code);

	public static native long graphicsPipelineCreate(long description);

	public static native void graphicsPipelineBind(long pipeline, long heap, long bindings);

	public static native void graphicsPipelinePushConstants(long pipeline, long data, int size);

	public static native void graphicsPipelineRelease(long pipeline);

	public static native long computePipelineCreate(long heap, long sets, long frameSets, long code);

	public static native int computeLocalSize(long pipeline, int axis);

	public static native void computeDispatch(long pipeline, long heap, long bindings, int x, int y, int z, long indirect,
		long indirectOffset);

	public static native void computePipelineRelease(long pipeline);

	public static native long texelBufferView(long buffer, long offset, long length, int format);

	public static native void clearColors(long views, int count, float red, float green, float blue, float alpha);

	public static native void copyTextureSynced(long source, long destination, int width, int height);

	public static native void generateMipmaps(long texture, boolean linear);

	public static native void generateDepthMipmaps(long texture);

	public static native boolean mipmapsSupported(int format);

	public static native void mipmapSources(String core, String spd, String spdMain, String area, String tail);

	public static native long storageBufferCreate(String name, long size, long content, long contentSize);

	public static native int formatFromVulkan(int format);

	public static native long programCreate(long description);

	public static native void programRelease(long program);

	public static native void passUseProgram(long program);

	public static native void passSetSampler(int index, long view, long sampler);

	public static native void passSetUniformBuffer(int index, long buffer, long offset, long length);

	public static native void passSetTexelBuffer(int index, long buffer, long offset, long length, int format);

	public static native void passPushConstants(long data, int size);

	public static native void copyBuffer(long source, long sourceOffset, long destination, long destinationOffset, long size);

	public static native void copyTextureToBuffer(long source, long destination, long offset, int level, int x, int y, int width,
		int height);

	public static native long queryPoolCreate(int size);

	public static native void queryPoolRelease(long pool);

	public static native void writeTimestamp(long pool, int index);

	public static native long queryValue(long pool, int index);
}
