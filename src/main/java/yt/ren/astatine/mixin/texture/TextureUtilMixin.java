package yt.ren.astatine.mixin.texture;

import java.nio.IntBuffer;
import net.minecraft.client.render.texture.TextureUtil;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;
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

	@Unique
	private static void astatine$apply(int name, float value) {
		GL.textureParameter(name, value);
	}
}
