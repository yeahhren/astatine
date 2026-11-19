package yt.ren.astatine.mixin.shaderpack;

import net.irisshaders.iris.shaderpack.loading.ProgramId;
import net.minecraft.client.render.block.entity.BeaconRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import yt.ren.astatine.shaderpack.Shaderpacks;

@Mixin(BeaconRenderer.class)
public abstract class BeaconRendererMixin {
	@Inject(method = "render", at = @At("HEAD"))
	private void astatine$beginSpecialProgram(CallbackInfo callback) {
		Shaderpacks.beginSpecial(ProgramId.BeaconBeam);
	}

	@Inject(method = "render", at = @At("RETURN"))
	private void astatine$endSpecialProgram(CallbackInfo callback) {
		Shaderpacks.endSpecial();
	}
}
