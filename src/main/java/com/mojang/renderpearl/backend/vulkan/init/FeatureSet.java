package com.mojang.renderpearl.backend.vulkan.init;

import com.mojang.renderpearl.backend.vulkan.VulkanUtils;
import org.lwjgl.vulkan.VkPhysicalDevice;

import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.function.Predicate;

public class FeatureSet {
	private final String name;
	private final Set<String> extensions;
	private final Set<VulkanFeature> features;
	private final Set<FeatureSet> children;
	private final Predicate<VkPhysicalDevice> condition;

	public FeatureSet(String name, Set<String> extensions, Set<VulkanFeature> features, Predicate<VkPhysicalDevice> condition) {
		this.name = name;
		this.extensions = Set.copyOf(extensions);
		this.features = Set.copyOf(features);
		this.children = Set.of();
		this.condition = condition;
	}

	public FeatureSet(String name, Set<String> extensions, Set<VulkanFeature> features) {
		this(name, extensions, features, device -> true);
	}

	public FeatureSet(String name, Collection<FeatureSet> children) {
		this.name = name;
		this.extensions = Set.of();
		this.features = Set.of();
		this.children = Set.copyOf(children);
		this.condition = device -> true;
	}

	public String name() {
		return this.name;
	}

	public Set<String> extensions() {
		var result = new LinkedHashSet<>(this.extensions);
		this.children.forEach(child -> result.addAll(child.extensions()));
		return result;
	}

	public Set<VulkanFeature> features() {
		var result = new LinkedHashSet<>(this.features);
		this.children.forEach(child -> result.addAll(child.features()));
		return result;
	}

	public FeatureSet composite(FeatureSet other) {
		return new FeatureSet(this.name, Set.of(this, other));
	}

	public Set<VulkanFeature> supportedFeatures(VkPhysicalDevice device) {
		var enabled = VulkanUtils.enabledFeatures();
		var result = new HashSet<VulkanFeature>();

		for (var feature : this.features) {
			if (enabled.contains(feature.key())) {
				result.add(feature);
			}
		}

		return result;
	}

	public boolean isSupported(VkPhysicalDevice device) {
		return this.condition.test(device) && VulkanUtils.enabledExtensions().containsAll(this.extensions()) && this.features().stream()
			.allMatch(feature -> VulkanUtils.enabledFeatures().contains(feature.key()));
	}

	public boolean contains(FeatureSet other) {
		if (other == this) {
			return true;
		}

		for (var child : this.children) {
			if (child.contains(other)) {
				return true;
			}
		}

		return false;
	}

	@Override
	public String toString() {
		return this.name;
	}
}
