package com.mojang.renderpearl.backend.vulkan;

import org.lwjgl.vulkan.VkInstance;

public final class VulkanInstance {
	private final VkInstance instance;
	private final VulkanDebug debug;

	VulkanInstance(VkInstance instance, boolean debug) {
		this.instance = instance;
		this.debug = new VulkanDebug(debug);
	}

	public VkInstance vkInstance() {
		return this.instance;
	}

	public VulkanDebug debug() {
		return this.debug;
	}
}
