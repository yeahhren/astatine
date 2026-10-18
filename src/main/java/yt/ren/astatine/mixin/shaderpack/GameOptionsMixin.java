package yt.ren.astatine.mixin.shaderpack;

import net.minecraft.client.options.GameOptions;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import yt.ren.astatine.shaderpack.Shaderpacks;

@Mixin(GameOptions.class)
public abstract class GameOptionsMixin {
	@Inject(method = "getCloudRenderMode", at = @At("RETURN"), cancellable = true)
	private void astatine$cloudRenderMode(CallbackInfoReturnable<Integer> callback) {
		callback.setReturnValue(Shaderpacks.cloudRenderMode(callback.getReturnValueI()));
	}
}
