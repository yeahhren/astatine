package yt.ren.astatine.mixin.shaderpack;

import net.minecraft.client.render.ItemInHandRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import yt.ren.astatine.shaderpack.Shaderpacks;

@Mixin(ItemInHandRenderer.class)
public abstract class ItemInHandRendererMixin {
	@Inject(method = "renderInWaterEffect", at = @At("HEAD"), cancellable = true)
	private void astatine$skipUnderwaterOverlay(float tickDelta, CallbackInfo callback) {
		if (!Shaderpacks.shouldRenderUnderwaterOverlay()) {
			callback.cancel();
		}
	}
}
