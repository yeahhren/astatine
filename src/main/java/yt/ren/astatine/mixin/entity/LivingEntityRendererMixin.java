package yt.ren.astatine.mixin.entity;

import java.nio.FloatBuffer;
import net.minecraft.client.render.entity.LivingEntityRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;
import yt.ren.astatine.render.GL;

@Mixin(LivingEntityRenderer.class)
public abstract class LivingEntityRendererMixin {
	@Redirect(method = "setupOverlayColor(Lnet/minecraft/entity/living/LivingEntity;FZ)Z", at = @At(value = "INVOKE", target = "Lorg/lwjgl/opengl/GL11;glTexEnvi(III)V"))
	private void astatine$combine(int target, int name, int value) {
	}

	@Redirect(method = "setupOverlayColor(Lnet/minecraft/entity/living/LivingEntity;FZ)Z", at = @At(value = "INVOKE", target = "Lorg/lwjgl/opengl/GL11;glTexEnv(IILjava/nio/FloatBuffer;)V"))
	private void astatine$overlay(int target, int name, FloatBuffer color) {
		int at = color.position();
		GL.overlay(color.get(at), color.get(at + 1), color.get(at + 2), color.get(at + 3));
	}

	@Redirect(method = "tearDownOverlayColor", at = @At(value = "INVOKE", target = "Lorg/lwjgl/opengl/GL11;glTexEnvi(III)V"))
	private void astatine$restore(int target, int name, int value) {
		GL.clearOverlay();
	}
}
