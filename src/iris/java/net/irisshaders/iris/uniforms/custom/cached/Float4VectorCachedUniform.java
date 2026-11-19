package net.irisshaders.iris.uniforms.custom.cached;

import net.irisshaders.iris.gl.uniform.UniformUpdateFrequency;
import net.irisshaders.iris.parsing.VectorType;
import org.joml.Vector4f;

import java.util.function.Supplier;

public class Float4VectorCachedUniform extends VectorCachedUniform<Vector4f> {

	public Float4VectorCachedUniform(String name, UniformUpdateFrequency updateFrequency, Supplier<Vector4f> supplier) {
		super(name, updateFrequency, new Vector4f(), supplier);
	}

	@Override
	protected void setFrom(Vector4f other) {
		this.cached.set(other);
	}


	@Override
	public VectorType getType() {
		return VectorType.VEC4;
	}
}
