package com.mojang.renderpearl.api.pipeline;

public enum IndexType {
	SHORT(2),
	INT(4);

	public final int bytes;

	IndexType(int bytes) {
		this.bytes = bytes;
	}
}
