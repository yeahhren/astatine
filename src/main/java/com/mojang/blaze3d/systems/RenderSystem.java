package com.mojang.blaze3d.systems;

import com.mojang.renderpearl.api.device.GpuDevice;
import com.mojang.renderpearl.backend.vulkan.VulkanDevice;
import com.mojang.renderpearl.frontend.FrontendGpuDevice;

public final class RenderSystem {
	private static GpuDevice device;

	private RenderSystem() {
	}

	public static void assertOnRenderThread() {
	}

	public static synchronized GpuDevice getDevice() {
		if (device == null) {
			device = new FrontendGpuDevice(VulkanDevice.get());
		}

		return device;
	}
}
