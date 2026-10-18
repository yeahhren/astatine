package com.mojang.renderpearl.backend.vulkan;

import com.mojang.renderpearl.api.device.BackendCreationException;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.vulkan.VK10;
import org.lwjgl.vulkan.VkExtensionProperties;
import org.lwjgl.vulkan.VkPhysicalDevice;
import yt.ren.astatine.vulkan.VulkanContext;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

public final class VulkanUtils {
	private VulkanUtils() {
	}

	public static List<String> enumerateExtensions(VkPhysicalDevice device) throws BackendCreationException {
		try (var stack = MemoryStack.stackPush()) {
			var count = stack.mallocInt(1);
			check(VK10.vkEnumerateDeviceExtensionProperties(device, (String) null, count, null));
			var properties = VkExtensionProperties.malloc(count.get(0), stack);
			check(VK10.vkEnumerateDeviceExtensionProperties(device, (String) null, count, properties));
			var result = new ArrayList<String>(properties.capacity());

			for (var property : properties) {
				result.add(property.extensionNameString());
			}

			return result;
		}
	}

	private static void check(int result) throws BackendCreationException {
		if (result != VK10.VK_SUCCESS) {
			throw new BackendCreationException("cannot enumerate device extensions: " + result, BackendCreationException.Reason.OTHER);
		}
	}

	public static Set<String> enabledExtensions() {
		return VulkanContext.deviceExtensions();
	}

	public static Set<String> enabledFeatures() {
		return VulkanContext.deviceFeatures();
	}

	public static void check(int result, String action) {
		if (result != VK10.VK_SUCCESS) {
			throw new IllegalStateException("failed to " + action + ": VkResult " + result);
		}
	}
}
