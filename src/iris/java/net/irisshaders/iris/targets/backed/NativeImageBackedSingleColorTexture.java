package net.irisshaders.iris.targets.backed;

import com.mojang.blaze3d.platform.NativeImage;
import yt.ren.astatine.iris.textures.DynamicTexture;

public class NativeImageBackedSingleColorTexture extends DynamicTexture {
	public NativeImageBackedSingleColorTexture(int red, int green, int blue, int alpha) {
		super(() -> "Single color texture", create(1, 1, (alpha & 255) << 24 | (red & 255) << 16 | (green & 255) << 8 | blue & 255));
	}

	public NativeImageBackedSingleColorTexture(int rgba) {
		this(rgba >> 24 & 0xFF, rgba >> 16 & 0xFF, rgba >> 8 & 0xFF, rgba & 0xFF);
	}

	public NativeImageBackedSingleColorTexture(int width, int height, int red, int green, int blue, int alpha) {
		super(() -> "Single color texture", create(width, height, (alpha & 255) << 24 | (red & 255) << 16 | (green & 255) << 8 | blue & 255));
	}

	private static NativeImage create(int width, int height, int color) {
		NativeImage image = new NativeImage(NativeImage.Format.RGBA, width, height, false);

		for (int i = 0; i < width; i++) {
			for (int j = 0; j < height; j++) {
				image.setPixel(i, j, color);
			}
		}

		return image;
	}
}
