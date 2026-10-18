package yt.ren.astatine.mixin.shaderpack;

import net.irisshaders.iris.shaderpack.loading.ProgramId;
import net.minecraft.client.render.entity.layer.AbstractArmorLayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import yt.ren.astatine.shaderpack.Shaderpacks;

@Mixin(AbstractArmorLayer.class)
public abstract class ArmorLayerGlintMixin {
	@Inject(method = "renderEnchantmentGlint", at = @At("HEAD"))
	private void astatine$beginSpecialProgram(CallbackInfo callback) {
		Shaderpacks.beginSpecial(ProgramId.ArmorGlint);
	}

	@Inject(method = "renderEnchantmentGlint", at = @At("RETURN"))
	private void astatine$endSpecialProgram(CallbackInfo callback) {
		Shaderpacks.endSpecial();
	}
}
