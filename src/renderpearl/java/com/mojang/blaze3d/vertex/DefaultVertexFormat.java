package com.mojang.blaze3d.vertex;

import com.mojang.renderpearl.api.GpuFormat;
import com.mojang.renderpearl.api.vertex.VertexFormat;

public final class DefaultVertexFormat {
	public static final VertexFormat BLOCK = VertexFormat.builder(0).addAttribute("Position", GpuFormat.RGB32_FLOAT)
		.addAttribute("Color", GpuFormat.RGBA8_UNORM).addAttribute("UV0", GpuFormat.RG32_FLOAT).addAttribute("UV2", GpuFormat.RG16_SINT).build();
	public static final VertexFormat ENTITY = VertexFormat.builder(0).addAttribute("Position", GpuFormat.RGB32_FLOAT)
		.addAttribute("Color", GpuFormat.RGBA8_UNORM).addAttribute("UV0", GpuFormat.RG32_FLOAT).addAttribute("UV1", GpuFormat.RG16_SINT)
		.addAttribute("UV2", GpuFormat.RG16_SINT).addAttribute("Normal", GpuFormat.RGBA8_SNORM).build();
	public static final VertexFormat PARTICLE = VertexFormat.builder(0).addAttribute("Position", GpuFormat.RGB32_FLOAT)
		.addAttribute("UV0", GpuFormat.RG32_FLOAT).addAttribute("Color", GpuFormat.RGBA8_UNORM).addAttribute("UV2", GpuFormat.RG16_SINT).build();
	public static final VertexFormat POSITION = VertexFormat.builder(0).addAttribute("Position", GpuFormat.RGB32_FLOAT).build();
	public static final VertexFormat POSITION_COLOR = VertexFormat.builder(0).addAttribute("Position", GpuFormat.RGB32_FLOAT)
		.addAttribute("Color", GpuFormat.RGBA8_UNORM).build();
	public static final VertexFormat POSITION_COLOR_NORMAL = VertexFormat.builder(0).addAttribute("Position", GpuFormat.RGB32_FLOAT)
		.addAttribute("Color", GpuFormat.RGBA8_UNORM).addAttribute("Normal", GpuFormat.RGBA8_SNORM).build();
	public static final VertexFormat POSITION_COLOR_LIGHTMAP = VertexFormat.builder(0).addAttribute("Position", GpuFormat.RGB32_FLOAT)
		.addAttribute("Color", GpuFormat.RGBA8_UNORM).addAttribute("UV2", GpuFormat.RG16_SINT).build();
	public static final VertexFormat POSITION_TEX = VertexFormat.builder(0).addAttribute("Position", GpuFormat.RGB32_FLOAT)
		.addAttribute("UV0", GpuFormat.RG32_FLOAT).build();
	public static final VertexFormat POSITION_TEX_COLOR = VertexFormat.builder(0).addAttribute("Position", GpuFormat.RGB32_FLOAT)
		.addAttribute("UV0", GpuFormat.RG32_FLOAT).addAttribute("Color", GpuFormat.RGBA8_UNORM).build();
	public static final VertexFormat POSITION_TEX_COLOR_NORMAL = VertexFormat.builder(0).addAttribute("Position", GpuFormat.RGB32_FLOAT)
		.addAttribute("UV0", GpuFormat.RG32_FLOAT).addAttribute("Color", GpuFormat.RGBA8_UNORM).addAttribute("Normal", GpuFormat.RGBA8_SNORM)
		.build();
	public static final VertexFormat POSITION_COLOR_NORMAL_LINE_WIDTH = VertexFormat.builder(0).addAttribute("Position", GpuFormat.RGB32_FLOAT)
		.addAttribute("Color", GpuFormat.RGBA8_UNORM).addAttribute("Normal", GpuFormat.RGBA8_SNORM).addAttribute("LineWidth", GpuFormat.R32_FLOAT)
		.build();

	private DefaultVertexFormat() {
	}
}
