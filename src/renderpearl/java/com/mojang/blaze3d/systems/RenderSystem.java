package com.mojang.blaze3d.systems;

import com.mojang.renderpearl.api.device.GpuDevice;

import java.util.function.Supplier;

public final class RenderSystem {
	private static Supplier<GpuDevice> factory;
	private static GpuDevice device;

	private RenderSystem() {
	}

	public static void assertOnRenderThread() {
	}

	public static synchronized void setDeviceFactory(Supplier<GpuDevice> deviceFactory) {
		factory = deviceFactory;
	}

	public static synchronized GpuDevice getDevice() {
		if (device == null) {
			if (factory == null) {
				throw new IllegalStateException("no renderpearl device has been registered");
			}

			device = factory.get();
		}

		return device;
	}
}
