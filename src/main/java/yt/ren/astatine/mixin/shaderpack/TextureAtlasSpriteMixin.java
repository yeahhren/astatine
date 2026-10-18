package yt.ren.astatine.mixin.shaderpack;

import net.minecraft.client.render.texture.TextureAtlasSprite;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import yt.ren.astatine.shaderpack.PbrTextures;
import yt.ren.astatine.shaderpack.Shaderpacks;

@Mixin(TextureAtlasSprite.class)
public abstract class TextureAtlasSpriteMixin {
	@Shadow
	protected int activeFrame;

	@Inject(method = "tick", at = @At("RETURN"))
	private void astatine$tickPbrFrames(CallbackInfo callback) {
		if (Shaderpacks.isActive()) {
			PbrTextures.onSpriteTick((TextureAtlasSprite) (Object) this, this.activeFrame);
		}
	}
}
