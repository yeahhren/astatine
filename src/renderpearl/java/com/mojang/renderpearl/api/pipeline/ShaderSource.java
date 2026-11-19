package com.mojang.renderpearl.api.pipeline;

public interface ShaderSource {
	String get(RenderPipeline pipeline, ShaderStage stage);
}
