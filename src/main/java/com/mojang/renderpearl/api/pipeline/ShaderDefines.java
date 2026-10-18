package com.mojang.renderpearl.api.pipeline;

import java.util.Map;
import java.util.stream.Collectors;

public record ShaderDefines(Map<String, String> values) {
	public ShaderDefines {
		values = Map.copyOf(values);
	}

	public String asSourceDirectives() {
		return this.values.entrySet().stream().map(entry -> "#define " + entry.getKey() + " " + entry.getValue())
			.collect(Collectors.joining(System.lineSeparator()));
	}
}
