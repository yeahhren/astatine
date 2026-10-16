package yt.ren.astatine.mixin.client;

import java.io.File;
import java.nio.IntBuffer;
import net.minecraft.client.Screenshot;
import net.minecraft.client.render.pipeline.RenderTarget;
import net.minecraft.client.render.platform.GlStateManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;
import yt.ren.astatine.Native;
import yt.ren.astatine.extension.FramebufferHolder;
import yt.ren.astatine.memory.Memory;
import yt.ren.astatine.render.TextureBinding;

@Mixin(Screenshot.class)
public abstract class ScreenshotMixin {
	@Redirect(
		method = "take(Ljava/io/File;Ljava/lang/String;IILnet/minecraft/client/render/pipeline/RenderTarget;)Lnet/minecraft/text/Text;",
		at = @At(value = "INVOKE", target = "Lnet/minecraft/client/render/platform/GlStateManager;bindTexture(I)V")
	)
	private static void astatine$bind(int texture, File gameDir, String name, int width, int height, RenderTarget target) {
		long framebuffer = ((FramebufferHolder) target).astatine$framebuffer();
		if (framebuffer != 0L) {
			TextureBinding.bind(Native.framebufferTexture(framebuffer));
		} else {
			GlStateManager.bindTexture(texture);
		}
	}

	@Redirect(
		method = "take(Ljava/io/File;Ljava/lang/String;IILnet/minecraft/client/render/pipeline/RenderTarget;)Lnet/minecraft/text/Text;",
		at = @At(value = "INVOKE", target = "Lorg/lwjgl/opengl/GL11;glPixelStorei(II)V")
	)
	private static void astatine$pixelStore(int name, int value) {
	}

	@Redirect(
		method = "take(Ljava/io/File;Ljava/lang/String;IILnet/minecraft/client/render/pipeline/RenderTarget;)Lnet/minecraft/text/Text;",
		at = @At(value = "INVOKE", target = "Lorg/lwjgl/opengl/GL11;glGetTexImage(IIIILjava/nio/IntBuffer;)V")
	)
	private static void astatine$read(int target, int level, int format, int type, IntBuffer pixels) {
		long texture = Native.activeTexture();
		if (texture != 0L) {
			Native.readTexture(texture, Memory.INSTANCE.address(pixels) + pixels.position() * 4L);
		}
	}
}
