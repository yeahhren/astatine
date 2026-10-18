package com.mojang.renderpearl.backend.vulkan.init;

public record VulkanPNextStruct(Class<?> type) {
	public String name() {
		return this.type.getSimpleName();
	}
}
