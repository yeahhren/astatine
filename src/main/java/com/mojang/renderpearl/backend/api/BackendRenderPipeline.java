package com.mojang.renderpearl.backend.api;

public interface BackendRenderPipeline extends AutoCloseable {
	boolean isClosed();

	@Override
	void close();
}
