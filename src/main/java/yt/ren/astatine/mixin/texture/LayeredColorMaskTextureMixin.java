package yt.ren.astatine.mixin.texture;

import net.minecraft.client.render.texture.LayeredColorMaskTexture;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;
import yt.ren.astatine.extension.TextureHolder;
import yt.ren.astatine.render.TextureBinding;

@Mixin(LayeredColorMaskTexture.class)
public abstract class LayeredColorMaskTextureMixin {
	@Redirect(method = "*", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/render/texture/LayeredColorMaskTexture;getGlId()I"))
	private int astatine$bindSelf(LayeredColorMaskTexture texture) {
		TextureBinding.bind(((TextureHolder) texture).astatine$texture());
		return TextureBinding.OBJECT;
	}
}
