package yt.ren.astatine.mixin.texture;

import net.minecraft.client.render.texture.TextureAtlas;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;
import yt.ren.astatine.extension.TextureHolder;
import yt.ren.astatine.render.TextureBinding;

@Mixin(TextureAtlas.class)
public abstract class TextureAtlasMixin {
	@Redirect(method = "*", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/render/texture/TextureAtlas;getGlId()I"))
	private int astatine$bindSelf(TextureAtlas texture) {
		TextureBinding.bind(((TextureHolder) texture).astatine$texture());
		return TextureBinding.OBJECT;
	}
}
