package yt.ren.astatine.mixin.client;

import net.minecraft.client.gui.screen.menu.TitleScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;
import yt.ren.astatine.Native;

@Mixin(TitleScreen.class)
public abstract class TitleScreenMixin {
	@Redirect(method = "drawBackgroundImage", at = @At(value = "INVOKE", target = "Lorg/lwjgl/opengl/GL11;glCopyTexSubImage2D(IIIIIIII)V"))
	private void astatine$copy(int target, int level, int destinationX, int destinationY, int x, int y, int width, int height) {
		Native.copyFromTarget(level, destinationX, destinationY, x, y, width, height);
	}
}
