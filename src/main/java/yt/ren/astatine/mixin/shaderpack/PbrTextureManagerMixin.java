package yt.ren.astatine.mixin.shaderpack;

import net.minecraft.client.render.texture.Texture;
import net.minecraft.client.render.texture.TextureManager;
import net.minecraft.resource.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import yt.ren.astatine.shaderpack.PbrTextures;

@Mixin(TextureManager.class)
public abstract class PbrTextureManagerMixin {
	@Inject(method = "register(Lnet/minecraft/resource/Identifier;Lnet/minecraft/client/render/texture/Texture;)Z", at = @At("RETURN"))
	private void astatine$loadPbrTextures(Identifier location, Texture texture, CallbackInfoReturnable<Boolean> callback) {
		PbrTextures.onRegistered(texture);
	}
}
