package yt.ren.astatine.mixin.state;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;
import yt.ren.astatine.render.GL;

@Mixin(targets = "net.minecraft.client.render.platform.GlStateManager$GlState")
public abstract class GlStateMixin {
	@Redirect(method = "setEnabled", at = @At(value = "INVOKE", target = "Lorg/lwjgl/opengl/GL11;glEnable(I)V"))
	private void astatine$enable(int capability) {
		GL.enable(capability);
	}

	@Redirect(method = "setEnabled", at = @At(value = "INVOKE", target = "Lorg/lwjgl/opengl/GL11;glDisable(I)V"))
	private void astatine$disable(int capability) {
		GL.disable(capability);
	}
}
