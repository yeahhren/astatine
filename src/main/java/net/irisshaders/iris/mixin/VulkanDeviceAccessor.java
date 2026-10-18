package net.irisshaders.iris.mixin;

import com.mojang.renderpearl.backend.vulkan.init.FeatureSet;

public interface VulkanDeviceAccessor {
	FeatureSet iris$getEnabledFeatures();
}
