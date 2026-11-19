package yt.ren.astatine.mixin.shaderpack;

import net.minecraft.client.render.entity.EntityRenderDispatcher;
import net.minecraft.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import yt.ren.astatine.shaderpack.DrawIds;
import yt.ren.astatine.shaderpack.Shaderpacks;

@Mixin(EntityRenderDispatcher.class)
public abstract class EntityRenderDispatcherMixin {
	@Inject(method = "render(Lnet/minecraft/entity/Entity;DDDFFZ)Z", at = @At("HEAD"))
	private void astatine$beginEntity(Entity entity, double x, double y, double z, float yaw, float tickDelta, boolean hitbox,
		CallbackInfoReturnable<Boolean> callback) {
		if (Shaderpacks.isWorldActive()) {
			DrawIds.setEntity(DrawIds.entityId(entity));
		}
	}

	@Inject(method = "render(Lnet/minecraft/entity/Entity;DDDFFZ)Z", at = @At("RETURN"))
	private void astatine$endEntity(Entity entity, double x, double y, double z, float yaw, float tickDelta, boolean hitbox,
		CallbackInfoReturnable<Boolean> callback) {
		if (Shaderpacks.isWorldActive()) {
			DrawIds.setEntity(-1);
		}
	}
}
