package yt.ren.astatine.mixin.texture;

import java.nio.IntBuffer;
import net.minecraft.client.render.texture.TextureUtil;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import yt.ren.astatine.memory.Memory;
import yt.ren.astatine.Native;
import yt.ren.astatine.render.GL;

@Mixin(TextureUtil.class)
public abstract class TextureUtilMixin {
	@Redirect(method = "prepare(IIII)V", at = @At(value = "INVOKE", target = "Lorg/lwjgl/opengl/GL11;glTexImage2D(IIIIIIIILjava/nio/IntBuffer;)V"))
	private static void astatine$allocate(int target, int level, int internalFormat, int width, int height, int border, int format, int type, IntBuffer pixels) {
		if (level == 0) {
			Native.textureAllocateActive(width, height);
		}
	}

	@Redirect(method = "*", at = @At(value = "INVOKE", target = "Lorg/lwjgl/opengl/GL11;glTexSubImage2D(IIIIIIIILjava/nio/IntBuffer;)V"))
	private static void astatine$upload(int target, int level, int x, int y, int width, int height, int format, int type, IntBuffer pixels) {
		Native.textureUploadActive(level, x, y, width, height, Memory.INSTANCE.address(pixels) + pixels.position() * 4L);
	}

	@Redirect(method = "*", at = @At(value = "INVOKE", target = "Lorg/lwjgl/opengl/GL11;glTexParameteri(III)V"))
	private static void astatine$parameter(int target, int name, int value) {
		astatine$apply(name, value);
	}

	@Redirect(method = "*", at = @At(value = "INVOKE", target = "Lorg/lwjgl/opengl/GL11;glTexParameterf(IIF)V"))
	private static void astatine$parameter(int target, int name, float value) {
		astatine$apply(name, value);
	}

	@Inject(method = "generateMipmaps", at = @At("HEAD"))
	private static void astatine$fillTransparentColor(int levels, int width, int[][] data, CallbackInfoReturnable<int[][]> callback) {
		int[] pixels = data[0];
		long red = 0, green = 0, blue = 0, count = 0;
		boolean transparent = false;

		for (int pixel : pixels) {
			if (pixel >>> 24 == 0) {
				transparent = true;
				continue;
			}

			red += pixel >> 16 & 255;
			green += pixel >> 8 & 255;
			blue += pixel & 255;
			count++;
		}

		if (!transparent || count == 0) {
			return;
		}

		int color = (int) (red / count) << 16 | (int) (green / count) << 8 | (int) (blue / count);

		for (int i = 0; i < pixels.length; i++) {
			if (pixels[i] >>> 24 == 0) {
				pixels[i] = color;
			}
		}
	}

	@Inject(method = "blendPixels", at = @At("HEAD"), cancellable = true)
	private static void astatine$blendTransparent(int first, int second, int third, int fourth, boolean transparent, CallbackInfoReturnable<Integer> callback) {
		if (!transparent) {
			return;
		}

		int[] pixels = {first, second, third, fourth};
		float alpha = 0.0f, weight = 0.0f, red = 0.0f, green = 0.0f, blue = 0.0f;

		for (int pixel : pixels) {
			int a = pixel >>> 24;

			if (a == 0) {
				continue;
			}

			float coverage = a / 255.0f;
			alpha += (float) Math.pow(coverage, 2.2);
			weight += coverage;
			red += (float) Math.pow((pixel >> 16 & 255) / 255.0f, 2.2) * coverage;
			green += (float) Math.pow((pixel >> 8 & 255) / 255.0f, 2.2) * coverage;
			blue += (float) Math.pow((pixel & 255) / 255.0f, 2.2) * coverage;
		}

		if (weight == 0.0f) {
			callback.setReturnValue(0);
			return;
		}

		int a = (int) (Math.pow(alpha / 4.0f, 1.0 / 2.2) * 255.0);

		if (a < 96) {
			a = 0;
		}

		int r = (int) (Math.pow(red / weight, 1.0 / 2.2) * 255.0);
		int g = (int) (Math.pow(green / weight, 1.0 / 2.2) * 255.0);
		int b = (int) (Math.pow(blue / weight, 1.0 / 2.2) * 255.0);
		callback.setReturnValue(a << 24 | r << 16 | g << 8 | b);
	}

	@Unique
	private static void astatine$apply(int name, float value) {
		GL.textureParameter(name, value);
	}
}
