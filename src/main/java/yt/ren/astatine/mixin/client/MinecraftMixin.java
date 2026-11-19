package yt.ren.astatine.mixin.client;

import java.nio.ByteBuffer;
import net.irisshaders.iris.Iris;
import net.minecraft.client.Minecraft;
import net.minecraft.client.world.ClientWorld;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import yt.ren.astatine.Native;
import yt.ren.astatine.shaderpack.Shaderpacks;

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

	@Inject(method = "init", at = @At("TAIL"))
	private void astatine$loadShaderpack(CallbackInfo callback) {
		Shaderpacks.load();
	}

	@Inject(method = "tick", at = @At("HEAD"))
	private void astatine$handleShaderpackKeys(CallbackInfo callback) {
		if (Shaderpacks.isInitialized()) {
			Iris.handleKeybinds((Minecraft) (Object) this);
		}
	}

	@Inject(method = "setWorld(Lnet/minecraft/client/world/ClientWorld;Ljava/lang/String;)V", at = @At("TAIL"))
	private void astatine$changeDimension(ClientWorld world, String message, CallbackInfo callback) {
		if (world != null) {
			Shaderpacks.onDimensionChanged();
		}
	}
}
