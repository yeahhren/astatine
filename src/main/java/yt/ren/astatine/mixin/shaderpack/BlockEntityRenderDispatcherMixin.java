package yt.ren.astatine.mixin.shaderpack;

import net.minecraft.block.entity.BlockEntity;
import net.minecraft.client.render.block.entity.BlockEntityRenderDispatcher;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import yt.ren.astatine.shaderpack.DrawIds;
import yt.ren.astatine.shaderpack.Shaderpacks;

@Mixin(BlockEntityRenderDispatcher.class)
public abstract class BlockEntityRenderDispatcherMixin {
	@Inject(method = "render(Lnet/minecraft/block/entity/BlockEntity;DDDFI)V", at = @At("HEAD"))
	private void astatine$beginBlockEntity(BlockEntity blockEntity, double x, double y, double z, float tickDelta, int destroyProgress,
		CallbackInfo callback) {
		if (Shaderpacks.isWorldActive()) {
			DrawIds.setBlockEntity(DrawIds.blockEntityId(blockEntity));
		}
	}

	@Inject(method = "render(Lnet/minecraft/block/entity/BlockEntity;DDDFI)V", at = @At("RETURN"))
	private void astatine$endBlockEntity(BlockEntity blockEntity, double x, double y, double z, float tickDelta, int destroyProgress,
		CallbackInfo callback) {
		if (Shaderpacks.isWorldActive()) {
			DrawIds.setBlockEntity(-1);
		}
	}
}
