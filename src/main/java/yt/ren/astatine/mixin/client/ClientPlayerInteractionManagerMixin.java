package yt.ren.astatine.mixin.client;

import net.minecraft.client.ClientPlayerInteractionManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import yt.ren.astatine.terrain.TerrainRenderer;

@Mixin(ClientPlayerInteractionManager.class)
public abstract class ClientPlayerInteractionManagerMixin {
	@Inject(method = {"finishMiningBlock", "useBlock"}, at = @At("HEAD"))
	private void astatine$beginPlayerChange(CallbackInfoReturnable<Boolean> callback) {
		TerrainRenderer.beginPlayerChange();
	}

	@Inject(method = {"finishMiningBlock", "useBlock"}, at = @At("RETURN"))
	private void astatine$endPlayerChange(CallbackInfoReturnable<Boolean> callback) {
		TerrainRenderer.endPlayerChange();
	}
}
