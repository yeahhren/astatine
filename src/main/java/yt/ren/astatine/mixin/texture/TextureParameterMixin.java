package yt.ren.astatine.mixin.texture;

import net.minecraft.client.gui.screen.menu.TitleScreen;
import net.minecraft.client.render.GameRenderer;
import net.minecraft.client.render.block.entity.BeaconRenderer;
import net.minecraft.client.render.entity.GuardianRenderer;
import net.minecraft.client.render.pipeline.RenderTarget;
import net.minecraft.client.render.texture.AbstractTexture;
import net.minecraft.client.render.world.WorldRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;
import yt.ren.astatine.render.GL;

@Mixin({
	AbstractTexture.class,
	RenderTarget.class,
	GameRenderer.class,
	WorldRenderer.class,
	TitleScreen.class,
	BeaconRenderer.class,
	GuardianRenderer.class,
})
public abstract class TextureParameterMixin {
	@Redirect(method = "*", at = @At(value = "INVOKE", target = "Lorg/lwjgl/opengl/GL11;glTexParameteri(III)V"), require = 0)
	private void astatine$parameter(int target, int name, int value) {
		astatine$apply(name, value);
	}

	@Redirect(method = "*", at = @At(value = "INVOKE", target = "Lorg/lwjgl/opengl/GL11;glTexParameterf(IIF)V"), require = 0)
	private void astatine$parameter(int target, int name, float value) {
		astatine$apply(name, value);
	}

	@Unique
	private static void astatine$apply(int name, float value) {
		GL.textureParameter(name, value);
	}
}
