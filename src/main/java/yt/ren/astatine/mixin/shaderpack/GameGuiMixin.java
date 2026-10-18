package yt.ren.astatine.mixin.shaderpack;

import net.minecraft.client.gui.GameGui;
import net.minecraft.client.render.Window;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import yt.ren.astatine.shaderpack.Shaderpacks;

@Mixin(GameGui.class)
public abstract class GameGuiMixin {
	@Inject(method = "renderVignette", at = @At("HEAD"), cancellable = true)
	private void astatine$skipVignette(float brightness, Window window, CallbackInfo callback) {
		if (!Shaderpacks.shouldRenderVignette()) {
			callback.cancel();
		}
	}
}
