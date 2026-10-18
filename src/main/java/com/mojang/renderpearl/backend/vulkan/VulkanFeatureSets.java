package com.mojang.renderpearl.backend.vulkan;

import com.mojang.renderpearl.backend.vulkan.init.FeatureSet;
import com.mojang.renderpearl.backend.vulkan.init.VulkanFeature;
import com.mojang.renderpearl.backend.vulkan.init.VulkanPNextStruct;
import org.lwjgl.vulkan.VkPhysicalDeviceDynamicRenderingFeatures;
import org.lwjgl.vulkan.VkPhysicalDeviceFeatures2;
import org.lwjgl.vulkan.VkPhysicalDeviceMultiDrawFeaturesEXT;
import org.lwjgl.vulkan.VkPhysicalDeviceSynchronization2Features;
import org.lwjgl.vulkan.VkPhysicalDeviceVertexAttributeDivisorFeaturesEXT;
import org.lwjgl.vulkan.VkPhysicalDeviceVulkan11Features;
import org.lwjgl.vulkan.VkPhysicalDeviceVulkan12Features;

import java.util.Set;

public final class VulkanFeatureSets {
	public static final VulkanPNextStruct VK10_FEATURES_STRUCT = new VulkanPNextStruct(VkPhysicalDeviceFeatures2.class);
	public static final VulkanPNextStruct VK11_FEATURES_STRUCT = new VulkanPNextStruct(VkPhysicalDeviceVulkan11Features.class);
	public static final VulkanPNextStruct VK12_FEATURES_STRUCT = new VulkanPNextStruct(VkPhysicalDeviceVulkan12Features.class);
	public static final VulkanPNextStruct SYNC2_FEATURES_STRUCT = new VulkanPNextStruct(VkPhysicalDeviceSynchronization2Features.class);
	public static final VulkanPNextStruct DYNAMIC_RENDERING_FEATURES_STRUCT = new VulkanPNextStruct(VkPhysicalDeviceDynamicRenderingFeatures.class);
	public static final VulkanPNextStruct VERTEX_ATTRIB_DIVISOR_FEATURES_STRUCT = new VulkanPNextStruct(
		VkPhysicalDeviceVertexAttributeDivisorFeaturesEXT.class);
	public static final VulkanPNextStruct MULTI_DRAW_FEATURES_STRUCT = new VulkanPNextStruct(VkPhysicalDeviceMultiDrawFeaturesEXT.class);
	public static final FeatureSet REQUIRED_FEATURESET = new FeatureSet("Astatine Vulkan requirements",
		Set.of("VK_KHR_push_descriptor", "VK_KHR_synchronization2", "VK_KHR_swapchain"),
		Set.of(new VulkanFeature(VK10_FEATURES_STRUCT, "multiDrawIndirect"), new VulkanFeature(VK10_FEATURES_STRUCT, "drawIndirectFirstInstance"),
			new VulkanFeature(VK11_FEATURES_STRUCT, "shaderDrawParameters"), new VulkanFeature(SYNC2_FEATURES_STRUCT, "synchronization2")));
	public static final FeatureSet MULTI_DRAW_FEATURESET = new FeatureSet("EXT MultiDraw", Set.of("VK_EXT_multi_draw"),
		Set.of(new VulkanFeature(MULTI_DRAW_FEATURES_STRUCT, "multiDraw")));
	public static final FeatureSet WIREFRAME_FEATURESET = new FeatureSet("Wireframe", Set.of(),
		Set.of(new VulkanFeature(VK10_FEATURES_STRUCT, "fillModeNonSolid")));

	private VulkanFeatureSets() {
	}
}
