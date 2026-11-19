package com.mojang.renderpearl.api.vertex;

import com.mojang.renderpearl.api.GpuFormat;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class VertexFormat {
	private final Map<String, VertexFormatElement> elements = new LinkedHashMap<>();
	private final List<VertexFormatElement> elementValues;
	private final int vertexSize;
	private final int stepRate;

	private VertexFormat(List<VertexFormatElement> elements, int vertexSize, int stepRate) {
		this.vertexSize = vertexSize;
		this.stepRate = stepRate;

		for (var element : elements) {
			this.elements.putIfAbsent(element.name(), element);
		}

		this.elementValues = List.copyOf(elements);
	}

	public static Builder builder() {
		return new Builder(0);
	}

	public static Builder builder(int stepRate) {
		return new Builder(stepRate);
	}

	public int getVertexSize() {
		return this.vertexSize;
	}

	public int getStepRate() {
		return this.stepRate;
	}

	public List<VertexFormatElement> getElements() {
		return this.elementValues;
	}

	public VertexFormatElement getElement(String name) {
		return this.elements.get(name);
	}

	public boolean contains(String name) {
		return this.elements.containsKey(name);
	}

	@Override
	public boolean equals(Object other) {
		return this == other || other instanceof VertexFormat format && this.vertexSize == format.vertexSize && this.stepRate == format.stepRate
			&& this.elementValues.equals(format.elementValues);
	}

	@Override
	public int hashCode() {
		return this.elementValues.hashCode() * 31 + this.vertexSize;
	}

	@Override
	public String toString() {
		return "VertexFormat" + this.elementValues.stream().map(VertexFormatElement::name).collect(Collectors.joining(", ", "[", "]"));
	}

	public static class Builder {
		private final List<VertexFormatElement> elements = new ArrayList<>();
		private final int stepRate;
		private int offset;

		private Builder(int stepRate) {
			this.stepRate = stepRate;
		}

		public Builder addAttribute(String name, GpuFormat format) {
			return this.addAttribute(name, format.blockSize(), format);
		}

		public Builder addAttribute(String name, int stride, GpuFormat format) {
			for (var element : this.elements) {
				if (element.name().equals(name)) {
					throw new IllegalArgumentException("duplicate vertex attribute " + name);
				}
			}

			this.elements.add(new VertexFormatElement(name, this.offset, format));
			this.offset += stride;
			return this;
		}

		public Builder addAttributeAt(String name, int offset, GpuFormat format) {
			this.elements.add(new VertexFormatElement(name, offset, format));
			this.offset = Math.max(this.offset, offset + format.blockSize());
			return this;
		}

		public Builder padding(int bytes) {
			this.offset += bytes;
			return this;
		}

		public VertexFormat build() {
			return new VertexFormat(this.elements, this.offset, this.stepRate);
		}

		public VertexFormat build(int vertexSize) {
			return new VertexFormat(this.elements, vertexSize, this.stepRate);
		}
	}
}
