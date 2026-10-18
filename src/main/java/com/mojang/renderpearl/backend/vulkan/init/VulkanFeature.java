package com.mojang.renderpearl.backend.vulkan.init;

public record VulkanFeature(VulkanPNextStruct struct, String name) {
	public String key() {
		return this.struct.name() + "." + this.name;
	}
}
