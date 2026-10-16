package yt.ren.astatine.mixin.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.render.GameRenderer;
import net.minecraft.client.render.world.WorldRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;
import yt.ren.astatine.render.GL;

@Mixin({Minecraft.class, GameRenderer.class, WorldRenderer.class})
public abstract class LineWidthMixin {
	@Redirect(method = "*", at = @At(value = "INVOKE", target = "Lorg/lwjgl/opengl/GL11;glLineWidth(F)V"), require = 0)
	private void astatine$lineWidth(float width) {
		GL.lineWidth(width);
	}
}
