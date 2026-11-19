package net.irisshaders.iris.pbr.util;

public class ImageManipulationUtil {
	public static int[] scaleNearestNeighbor(int[] image, int width, int height, int newWidth, int newHeight) {
		int[] scaled = new int[newWidth * newHeight];
		float xScale = (float) newWidth / width;
		float yScale = (float) newHeight / height;
		for (int y = 0; y < newHeight; ++y) {
			for (int x = 0; x < newWidth; ++x) {
				float unscaledX = (x + 0.5f) / xScale;
				float unscaledY = (y + 0.5f) / yScale;
				scaled[y * newWidth + x] = image[(int) unscaledY * width + (int) unscaledX];
			}
		}
		return scaled;
	}

	public static int[] scaleBilinear(int[] image, int width, int height, int newWidth, int newHeight) {
		int[] scaled = new int[newWidth * newHeight];
		float xScale = (float) newWidth / width;
		float yScale = (float) newHeight / height;
		for (int y = 0; y < newHeight; ++y) {
			for (int x = 0; x < newWidth; ++x) {
				float unscaledX = (x + 0.5f) / xScale;
				float unscaledY = (y + 0.5f) / yScale;

				int x1 = Math.round(unscaledX);
				int y1 = Math.round(unscaledY);
				int x0 = x1 - 1;
				int y0 = y1 - 1;

				boolean x0valid = true;
				boolean y0valid = true;
				boolean x1valid = true;
				boolean y1valid = true;
				if (x0 < 0) {
					x0valid = false;
				}
				if (y0 < 0) {
					y0valid = false;
				}
				if (x1 >= width) {
					x1valid = false;
				}
				if (y1 >= height) {
					y1valid = false;
				}

				int finalColor = 0;
				if (x0valid & y0valid & x1valid & y1valid) {
					float leftWeight = (x1 + 0.5f) - unscaledX;
					float rightWeight = unscaledX - (x0 + 0.5f);
					float topWeight = (y1 + 0.5f) - unscaledY;
					float bottomWeight = unscaledY - (y0 + 0.5f);

					float weightTL = leftWeight * topWeight;
					float weightTR = rightWeight * topWeight;
					float weightBL = leftWeight * bottomWeight;
					float weightBR = rightWeight * bottomWeight;

					int colorTL = image[y0 * width + x0];
					int colorTR = image[y0 * width + x1];
					int colorBL = image[y1 * width + x0];
					int colorBR = image[y1 * width + x1];

					finalColor = blendColor(colorTL, colorTR, colorBL, colorBR, weightTL, weightTR, weightBL, weightBR);
				} else if (x0valid & x1valid) {
					float leftWeight = (x1 + 0.5f) - unscaledX;
					float rightWeight = unscaledX - (x0 + 0.5f);

					int validY = y0valid ? y0 : y1;
					int colorLeft = image[validY * width + x0];
					int colorRight = image[validY * width + x1];

					finalColor = blendColor(colorLeft, colorRight, leftWeight, rightWeight);
				} else if (y0valid & y1valid) {
					float topWeight = (y1 + 0.5f) - unscaledY;
					float bottomWeight = unscaledY - (y0 + 0.5f);

					int validX = x0valid ? x0 : x1;
					int colorTop = image[y0 * width + validX];
					int colorBottom = image[y1 * width + validX];

					finalColor = blendColor(colorTop, colorBottom, topWeight, bottomWeight);
				} else {
					finalColor = image[(y0valid ? y0 : y1) * width + (x0valid ? x0 : x1)];
				}
				scaled[y * newWidth + x] = finalColor;
			}
		}
		return scaled;
	}

	private static int channel(int color, int shift) {
		return color >>> shift & 0xFF;
	}

	private static int blendColor(int c0, int c1, int c2, int c3, float w0, float w1, float w2, float w3) {
		int result = 0;
		for (int shift = 0; shift < 32; shift += 8) {
			result |= blendChannel(channel(c0, shift), channel(c1, shift), channel(c2, shift), channel(c3, shift), w0, w1, w2, w3) << shift;
		}
		return result;
	}

	private static int blendChannel(int v0, int v1, int v2, int v3, float w0, float w1, float w2, float w3) {
		return Math.round(v0 * w0 + v1 * w1 + v2 * w2 + v3 * w3);
	}

	private static int blendColor(int c0, int c1, float w0, float w1) {
		int result = 0;
		for (int shift = 0; shift < 32; shift += 8) {
			result |= blendChannel(channel(c0, shift), channel(c1, shift), w0, w1) << shift;
		}
		return result;
	}

	private static int blendChannel(int v0, int v1, float w0, float w1) {
		return Math.round(v0 * w0 + v1 * w1);
	}
}
