package net.irisshaders.iris.pbr.mipmap;

import org.jetbrains.annotations.Nullable;

public interface CustomMipmapGenerator {
	int[][] generateMipLevels(int[][] image, int width, int height, int mipLevel);

	interface Provider {
		@Nullable CustomMipmapGenerator getMipmapGenerator();
	}
}
