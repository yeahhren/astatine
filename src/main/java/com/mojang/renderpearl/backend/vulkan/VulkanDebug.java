package com.mojang.renderpearl.backend.vulkan;

import org.lwjgl.system.MemoryStack;
import org.lwjgl.vulkan.EXTDebugUtils;
import org.lwjgl.vulkan.VkDebugUtilsObjectNameInfoEXT;
import org.lwjgl.vulkan.VkDevice;

public final class VulkanDebug {
	private final boolean enabled;

	VulkanDebug(boolean enabled) {
		this.enabled = enabled;
	}

	public void setObjectName(VkDevice device, int type, long handle, String name) {
		if (!this.enabled || handle == 0 || name == null) {
			return;
		}

		try (var stack = MemoryStack.stackPush()) {
			EXTDebugUtils.vkSetDebugUtilsObjectNameEXT(device,
				VkDebugUtilsObjectNameInfoEXT.calloc(stack).sType$Default().objectType(type).objectHandle(handle).pObjectName(stack.UTF8(name)));
		}
	}
}
