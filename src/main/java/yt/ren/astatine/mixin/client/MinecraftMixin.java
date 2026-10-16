package yt.ren.astatine.mixin.client;

import java.nio.ByteBuffer;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;
import yt.ren.astatine.Native;

@Mixin(Minecraft.class)
public abstract class MinecraftMixin {
	@Unique
	private static int astatine$proxySize;

	@Redirect(method = "getMaxTextureSize", at = @At(value = "INVOKE", target = "Lorg/lwjgl/opengl/GL11;glTexImage2D(IIIIIIIILjava/nio/ByteBuffer;)V"))
	private static void astatine$proxy(int target, int level, int internalFormat, int width, int height, int border, int format, int type, ByteBuffer pixels) {
		astatine$proxySize = width;
	}

	@Redirect(method = "getMaxTextureSize", at = @At(value = "INVOKE", target = "Lorg/lwjgl/opengl/GL11;glGetTexLevelParameteri(III)I"))
	private static int astatine$proxyWidth(int target, int level, int name) {
		return astatine$proxySize <= Native.maxTextureSize() ? astatine$proxySize : 0;
	}
}
