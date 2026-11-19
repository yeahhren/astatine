package net.irisshaders.iris.uniforms.custom.cached;

import net.irisshaders.iris.gl.uniform.UniformUpdateFrequency;
import net.irisshaders.iris.parsing.VectorType;
import org.joml.Vector2f;

import java.util.function.Supplier;

public class Float2VectorCachedUniform extends VectorCachedUniform<Vector2f> {

	public Float2VectorCachedUniform(String name, UniformUpdateFrequency updateFrequency, Supplier<Vector2f> supplier) {
		super(name, updateFrequency, new Vector2f(), supplier);
	}

	@Override
	protected void setFrom(Vector2f other) {
		this.cached.set(other);
	}


	@Override
	public VectorType getType() {
		return VectorType.VEC2;
	}
}
