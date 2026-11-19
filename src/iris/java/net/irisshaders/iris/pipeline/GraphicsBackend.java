package net.irisshaders.iris.pipeline;

import com.mojang.renderpearl.api.device.GpuDevice;

public enum GraphicsBackend {
	VULKAN;

	public static void initialize(GpuDevice device) {
		if (!"Vulkan".equalsIgnoreCase(device.getDeviceInfo().backendName())) {
			throw new IllegalArgumentException("unsupported Iris graphics backend: " + device.getDeviceInfo().backendName());
		}
	}

	public static boolean isOpenGL() {
		return false;
	}

	public static boolean isVulkan() {
		return true;
	}

	public static boolean isZZeroToOne() {
		return true;
	}

	public static boolean isReversedDepth() {
		return false;
	}
}
