package com.mojang.renderpearl.api.vertex;

import com.mojang.renderpearl.api.GpuFormat;

public record VertexFormatElement(String name, int offset, GpuFormat format) {
}
