package yt.ren.astatine.mixin.texture;

import net.minecraft.client.render.texture.LayeredTexture;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;
import yt.ren.astatine.extension.TextureHolder;
import yt.ren.astatine.render.TextureBinding;

@Mixin(LayeredTexture.class)
public abstract class LayeredTextureMixin {
	@Redirect(method = "*", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/render/texture/LayeredTexture;getGlId()I"))
	private int astatine$bindSelf(LayeredTexture texture) {
		TextureBinding.bind(((TextureHolder) texture).astatine$texture());
		return TextureBinding.OBJECT;
	}
}
