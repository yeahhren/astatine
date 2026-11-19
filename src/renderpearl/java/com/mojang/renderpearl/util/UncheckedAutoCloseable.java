package com.mojang.renderpearl.util;

public interface UncheckedAutoCloseable extends AutoCloseable {
	static void safeClose(UncheckedAutoCloseable closeable) {
		if (closeable != null) {
			closeable.close();
		}
	}

	@Override
	void close();
}
