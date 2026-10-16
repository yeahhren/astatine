package yt.ren.astatine.mixin.client;

import net.minecraft.client.render.GameRenderer;
import net.minecraft.client.render.ItemInHandRenderer;
import net.minecraft.client.render.block.entity.SignRenderer;
import net.minecraft.client.render.entity.ArrowRenderer;
import net.minecraft.client.render.entity.EntityRenderer;
import net.minecraft.client.render.entity.ItemFrameRenderer;
import net.minecraft.client.render.entity.LivingEntityRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;
import yt.ren.astatine.render.GL;

@Mixin({
	GameRenderer.class,
	ItemInHandRenderer.class,
	SignRenderer.class,
	ArrowRenderer.class,
	EntityRenderer.class,
	ItemFrameRenderer.class,
	LivingEntityRenderer.class,
})
public abstract class NormalMixin {
	@Redirect(method = "*", at = @At(value = "INVOKE", target = "Lorg/lwjgl/opengl/GL11;glNormal3f(FFF)V"), require = 0)
	private void astatine$normal(float x, float y, float z) {
		GL.normal(x, y, z);
	}
}
