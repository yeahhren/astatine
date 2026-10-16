package yt.ren.astatine.mixin.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.overlay.DebugOverlay;
import net.minecraft.client.gui.screen.menu.TitleScreen;
import net.minecraft.client.gui.screen.menu.twitch.StreamUnavailableScreen;
import net.minecraft.client.render.GameRenderer;
import org.lwjgl.opengl.ContextCapabilities;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;
import yt.ren.astatine.render.GLCapabilities;

@Mixin({Minecraft.class, DebugOverlay.class, TitleScreen.class, StreamUnavailableScreen.class, GameRenderer.class})
public abstract class GlQueryMixin {
	@Redirect(method = "*", at = @At(value = "INVOKE", target = "Lorg/lwjgl/opengl/GLContext;getCapabilities()Lorg/lwjgl/opengl/ContextCapabilities;"), require = 0)
	private ContextCapabilities astatine$capabilities() {
		return GLCapabilities.getCapabilities();
	}

	@Redirect(method = "*", at = @At(value = "INVOKE", target = "Lorg/lwjgl/opengl/GL11;glGetString(I)Ljava/lang/String;"), require = 0)
	private String astatine$string(int name) {
		return GLCapabilities.string(name);
	}

	@Redirect(method = "*", at = @At(value = "INVOKE", target = "Lorg/lwjgl/opengl/GL11;glGetInteger(I)I"), require = 0)
	private int astatine$integer(int name) {
		return 0;
	}

	@Redirect(method = "*", at = @At(value = "INVOKE", target = "Lorg/lwjgl/opengl/GL11;glGetError()I"), require = 0)
	private int astatine$error() {
		return 0;
	}
}
