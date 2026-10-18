package com.mojang.renderpearl.api.pipeline;

import com.mojang.renderpearl.api.GpuFormat;

import java.util.List;

public record BindGroupLayout(List<UniformDescription> uniforms) {
	public BindGroupLayout {
		uniforms = List.copyOf(uniforms);
	}

	public record UniformDescription(String name, UniformType type, GpuFormat gpuFormat) {
		public UniformDescription(String name, UniformType type) {
			this(name, type, null);
		}
	}
}
