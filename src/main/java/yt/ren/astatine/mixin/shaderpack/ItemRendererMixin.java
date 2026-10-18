package yt.ren.astatine.mixin.shaderpack;

import net.irisshaders.iris.shaderpack.loading.ProgramId;
import net.minecraft.client.render.entity.ItemRenderer;
import net.minecraft.client.render.model.block.BakedModel;
import net.minecraft.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import yt.ren.astatine.shaderpack.DrawIds;
import yt.ren.astatine.shaderpack.Shaderpacks;

@Mixin(ItemRenderer.class)
public abstract class ItemRendererMixin {
	@Inject(method = "renderItem(Lnet/minecraft/item/ItemStack;Lnet/minecraft/client/render/model/block/BakedModel;)V", at = @At("HEAD"))
	private void astatine$beginItem(ItemStack stack, BakedModel model, CallbackInfo callback) {
		if (Shaderpacks.isWorldActive()) {
			DrawIds.setItem(DrawIds.itemId(stack));
		}
	}

	@Inject(method = "renderEnchantmentGlint", at = @At("HEAD"))
	private void astatine$beginGlint(BakedModel model, CallbackInfo callback) {
		Shaderpacks.beginSpecial(ProgramId.ArmorGlint);
	}

	@Inject(method = "renderEnchantmentGlint", at = @At("RETURN"))
	private void astatine$endGlint(BakedModel model, CallbackInfo callback) {
		Shaderpacks.endSpecial();
	}

	@Inject(method = "renderItem(Lnet/minecraft/item/ItemStack;Lnet/minecraft/client/render/model/block/BakedModel;)V", at = @At("RETURN"))
	private void astatine$endItem(ItemStack stack, BakedModel model, CallbackInfo callback) {
		if (Shaderpacks.isWorldActive()) {
			DrawIds.setItem(-1);
		}
	}
}
