package yt.ren.astatine.mixin.shaderpack;

import net.minecraft.client.gui.overlay.DebugOverlay;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import yt.ren.astatine.shaderpack.Shaderpacks;

import java.util.List;

@Mixin(DebugOverlay.class)
public abstract class DebugOverlayMixin {
	@Inject(method = "getSystemInfo", at = @At("RETURN"))
	private void astatine$addShaderpackInfo(CallbackInfoReturnable<List<String>> callback) {
		if (Shaderpacks.isInitialized()) {
			Shaderpacks.addDebugText(callback.getReturnValue());
		}
	}
}
