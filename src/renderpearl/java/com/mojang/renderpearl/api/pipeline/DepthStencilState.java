package com.mojang.renderpearl.api.pipeline;

public record DepthStencilState(CompareOp depthTest, boolean writeDepth, float depthBiasScaleFactor, float depthBiasConstant) {
	public static final DepthStencilState DEFAULT = new DepthStencilState(CompareOp.GREATER_THAN_OR_EQUAL, true);

	public DepthStencilState(CompareOp depthTest, boolean writeDepth) {
		this(depthTest, writeDepth, 0.0f, 0.0f);
	}
}
