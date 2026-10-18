package net.irisshaders.iris.pbr.mipmap;

public abstract class AbstractMipmapGenerator implements CustomMipmapGenerator {
	@Override
	public int[][] generateMipLevels(int[][] image, int imageWidth, int imageHeight, int mipLevel) {
		if (mipLevel + 1 <= image.length) {
			return image;
		}

		int[][] newImages = new int[mipLevel + 1][];

		if (mipLevel > 0) {
			for (int level = 1; level <= mipLevel; ++level) {
				int[] prevMipmap = level == 1 ? image[0] : newImages[level - 1];
				int prevWidth = Math.max(imageWidth >> (level - 1), 1);
				int prevHeight = Math.max(imageHeight >> (level - 1), 1);
				int width = Math.max(prevWidth >> 1, 1);
				int height = Math.max(prevHeight >> 1, 1);
				int[] mipmap = new int[width * height];
				for (int x = 0; x < width; ++x) {
					for (int y = 0; y < height; ++y) {
						int x0 = Math.min(x * 2, prevWidth - 1);
						int x1 = Math.min(x * 2 + 1, prevWidth - 1);
						int y0 = Math.min(y * 2, prevHeight - 1);
						int y1 = Math.min(y * 2 + 1, prevHeight - 1);
						mipmap[y * width + x] = blend(prevMipmap[y0 * prevWidth + x0], prevMipmap[y0 * prevWidth + x1],
							prevMipmap[y1 * prevWidth + x0], prevMipmap[y1 * prevWidth + x1]);
					}
				}
				newImages[level] = mipmap;
			}
		}

		newImages[0] = image[0];

		return newImages;
	}

	public abstract int blend(int c0, int c1, int c2, int c3);
}
