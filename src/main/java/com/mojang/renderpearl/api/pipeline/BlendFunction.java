package com.mojang.renderpearl.api.pipeline;

public record BlendFunction(BlendEquation color, BlendEquation alpha) {
	public BlendFunction(BlendFactor sourceColor, BlendFactor destColor, BlendFactor sourceAlpha, BlendFactor destAlpha) {
		this(new BlendEquation(sourceColor, destColor, BlendOp.ADD), new BlendEquation(sourceAlpha, destAlpha, BlendOp.ADD));
	}

	public BlendFunction(BlendFactor source, BlendFactor dest) {
		this(source, dest, source, dest);
	}
}
