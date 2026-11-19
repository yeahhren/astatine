package com.mojang.renderpearl.api.pipeline;

import com.mojang.renderpearl.api.vertex.VertexFormat;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

public final class RenderPipeline {
	private final String location;
	private final List<VertexFormat> vertexFormats;
	private final DepthStencilState depthStencilState;
	private final boolean cull;
	private final List<ColorTargetState> colorTargetStates;
	private final PolygonMode polygonMode;
	private final PrimitiveTopology primitiveTopology;
	private final List<BindGroupLayout> bindGroupLayouts;
	private final ShaderDefines shaderDefines;
	private final int hash;

	public RenderPipeline(String location, List<VertexFormat> vertexFormats, DepthStencilState depthStencilState, boolean cull,
		List<ColorTargetState> colorTargetStates, PolygonMode polygonMode, PrimitiveTopology primitiveTopology, List<BindGroupLayout> bindGroupLayouts,
		ShaderDefines shaderDefines) {
		this.location = location;
		this.vertexFormats = Collections.unmodifiableList(new ArrayList<>(vertexFormats));
		this.depthStencilState = depthStencilState;
		this.cull = cull;
		this.colorTargetStates = Collections.unmodifiableList(new ArrayList<>(colorTargetStates));
		this.polygonMode = polygonMode;
		this.primitiveTopology = primitiveTopology;
		this.bindGroupLayouts = List.copyOf(bindGroupLayouts);
		this.shaderDefines = shaderDefines;
		this.hash = Objects.hash(location, this.vertexFormats, depthStencilState, cull, this.colorTargetStates, polygonMode, primitiveTopology,
			shaderDefines);
	}

	public String getLocation() {
		return this.location;
	}

	public List<VertexFormat> getVertexFormatBindings() {
		return this.vertexFormats;
	}

	public VertexFormat getVertexFormatBinding(int binding) {
		return binding < this.vertexFormats.size() ? this.vertexFormats.get(binding) : null;
	}

	public DepthStencilState getDepthStencilState() {
		return this.depthStencilState;
	}

	public boolean isCull() {
		return this.cull;
	}

	public List<ColorTargetState> getColorTargetStates() {
		return this.colorTargetStates;
	}

	public PolygonMode getPolygonMode() {
		return this.polygonMode;
	}

	public PrimitiveTopology getPrimitiveTopology() {
		return this.primitiveTopology;
	}

	public List<BindGroupLayout> getBindGroupLayouts() {
		return this.bindGroupLayouts;
	}

	public ShaderDefines getShaderDefines() {
		return this.shaderDefines;
	}

	@Override
	public boolean equals(Object other) {
		return this == other || other instanceof RenderPipeline pipeline && this.hash == pipeline.hash && this.cull == pipeline.cull
			&& this.location.equals(pipeline.location) && this.vertexFormats.equals(pipeline.vertexFormats)
			&& Objects.equals(this.depthStencilState, pipeline.depthStencilState) && this.colorTargetStates.equals(pipeline.colorTargetStates)
			&& this.polygonMode == pipeline.polygonMode && this.primitiveTopology == pipeline.primitiveTopology
			&& this.shaderDefines.equals(pipeline.shaderDefines);
	}

	@Override
	public int hashCode() {
		return this.hash;
	}

	@Override
	public String toString() {
		return this.location;
	}
}
