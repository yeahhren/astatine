package com.mojang.renderpearl.frontend;

import com.mojang.renderpearl.api.pipeline.BindGroupLayout;
import com.mojang.renderpearl.api.pipeline.ColorTargetState;
import com.mojang.renderpearl.api.vertex.VertexFormat;
import com.mojang.renderpearl.backend.api.BackendRenderPipeline;

import java.util.List;
import java.util.Map;

public record FrontendRenderPipeline(String name, BackendRenderPipeline backendRenderPipeline, List<VertexFormat> vertexFormats, Map<String, Integer> uniformIndices, List<BindGroupLayout.UniformDescription> uniforms, List<ColorTargetState> colorTargetStates, boolean wantsDepthTexture, int pushConstantSize) {
	public boolean isClosed() {
		return this.backendRenderPipeline.isClosed();
	}

	public void close() {
		this.backendRenderPipeline.close();
	}
}
