package yt.ren.astatine.mixin.texture;

import java.awt.image.BufferedImage;
import net.minecraft.client.render.texture.HttpTexture;
import net.minecraft.client.render.texture.TextureUtil;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;
import yt.ren.astatine.extension.TextureHolder;
import yt.ren.astatine.render.TextureBinding;

@Mixin(HttpTexture.class)
public abstract class HttpTextureMixin {
	@Redirect(method = "getGlId", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/render/texture/TextureUtil;uploadTexture(ILjava/awt/image/BufferedImage;)I"))
	private int astatine$upload(int name, BufferedImage image) {
		TextureBinding.bind(((TextureHolder) this).astatine$texture());
		return TextureUtil.uploadTexture(TextureBinding.OBJECT, image);
	}
}
