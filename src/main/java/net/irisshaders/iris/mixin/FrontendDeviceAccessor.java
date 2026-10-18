package net.irisshaders.iris.mixin;

import com.mojang.renderpearl.backend.api.GpuDeviceBackend;

public interface FrontendDeviceAccessor {
	GpuDeviceBackend iris$getBackend();
}
