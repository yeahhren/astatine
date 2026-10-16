package yt.ren.astatine.mixin.texture;

import net.minecraft.client.render.texture.Texture;
import net.minecraft.client.render.texture.TextureManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;
import yt.ren.astatine.Native;
import yt.ren.astatine.extension.TextureHolder;
import yt.ren.astatine.render.TextureBinding;

@Mixin(TextureManager.class)
public abstract class TextureManagerMixin {
	@Redirect(method = "bind", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/render/texture/Texture;getGlId()I"))
	private int astatine$bind(Texture texture) {
		texture.getGlId();
		TextureBinding.bind(texture instanceof TextureHolder ? ((TextureHolder) texture).astatine$texture() : 0L);
		return TextureBinding.OBJECT;
	}

	@Redirect(method = "close", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/render/texture/Texture;getGlId()I"))
	private int astatine$close(Texture texture) {
		if (texture instanceof TextureHolder) {
			Native.textureDetach(((TextureHolder) texture).astatine$texture());
		}
		return TextureBinding.OBJECT;
	}
}
