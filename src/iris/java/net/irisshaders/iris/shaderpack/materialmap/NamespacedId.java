package net.irisshaders.iris.shaderpack.materialmap;

public record NamespacedId(String namespace, String name) {
	public NamespacedId(String combined) {
		this(combined.indexOf(':') == -1 ? "minecraft" : combined.substring(0, combined.indexOf(':')),
			combined.substring(combined.indexOf(':') + 1));
	}

	@Override
	public String toString() {
		return namespace + ":" + name;
	}
}
