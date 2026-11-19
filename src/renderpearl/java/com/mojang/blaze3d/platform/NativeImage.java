package com.mojang.blaze3d.platform;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;

public class NativeImage implements AutoCloseable {
	private final Format format;
	private final int width;
	private final int height;
	private final int[] pixels;

	public NativeImage(Format format, int width, int height, boolean calloc) {
		this.format = format;
		this.width = width;
		this.height = height;
		this.pixels = new int[width * height];
	}

	public NativeImage(int width, int height, boolean calloc) {
		this(Format.RGBA, width, height, calloc);
	}

	public static NativeImage read(ByteBuffer buffer) throws IOException {
		var bytes = new byte[buffer.remaining()];
		buffer.duplicate().get(bytes);
		BufferedImage decoded = ImageIO.read(new ByteArrayInputStream(bytes));

		if (decoded == null) {
			throw new IOException("unsupported image data");
		}

		var image = new NativeImage(Format.RGBA, decoded.getWidth(), decoded.getHeight(), false);
		decoded.getRGB(0, 0, image.width, image.height, image.pixels, 0, image.width);
		return image;
	}

	public Format format() {
		return this.format;
	}

	public int getWidth() {
		return this.width;
	}

	public int getHeight() {
		return this.height;
	}

	public int getPixel(int x, int y) {
		return this.pixels[x + y * this.width];
	}

	public void setPixel(int x, int y, int argb) {
		this.pixels[x + y * this.width] = argb;
	}

	public ByteBuffer toRgba() {
		var data = ByteBuffer.allocateDirect(this.pixels.length * 4).order(ByteOrder.nativeOrder());

		for (int argb : this.pixels) {
			data.put((byte) (argb >> 16)).put((byte) (argb >> 8)).put((byte) argb).put((byte) (argb >>> 24));
		}

		return data.flip();
	}

	@Override
	public void close() {
	}

	public enum Format {
		RGBA,
		RGB,
		LUMINANCE_ALPHA,
		LUMINANCE
	}
}
