package net.irisshaders.iris.gl.blending;

public record BlendModeOverride(BlendMode blendMode) {
	public static final BlendModeOverride OFF = new BlendModeOverride(null);
}
