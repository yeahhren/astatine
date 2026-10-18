package com.mojang.renderpearl.api.device;

import java.util.List;

public class BackendCreationException extends Exception {
	private final Reason reason;
	private final List<String> missingCapabilities;

	public BackendCreationException(String message, Reason reason, List<String> missingCapabilities) {
		super(message);
		this.reason = reason;
		this.missingCapabilities = List.copyOf(missingCapabilities);
	}

	public BackendCreationException(String message, Reason reason) {
		this(message, reason, List.of());
	}

	public Reason getReason() {
		return this.reason;
	}

	public List<String> getMissingCapabilities() {
		return this.missingCapabilities;
	}

	public enum Reason {
		VULKAN_MISSING_EXTENSION,
		VULKAN_MISSING_FEATURE,
		OTHER
	}
}
