package yt.ren.astatine.mixin.shaderpack;

import net.irisshaders.iris.pipeline.WorldRenderingPhase;
import net.minecraft.client.render.platform.GlStateManager;
import net.minecraft.client.render.vertex.Tesselator;
import net.minecraft.client.render.vertex.VertexBuffer;
import net.minecraft.client.render.world.WorldRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import yt.ren.astatine.shaderpack.Shaderpacks;

@Mixin(WorldRenderer.class)
public abstract class SkyMixin {
	@Shadow
	private VertexBuffer starsBuffer;

	@Shadow
	private VertexBuffer lightSkyBuffer;

	@Unique
	private static void astatine$draw(Tesselator tesselator, WorldRenderingPhase phase, boolean enabled) {
		if (!enabled) {
			tesselator.getBuffer().end();
			tesselator.getBuffer().clear();
			return;
		}

		Shaderpacks.phase(phase);
		tesselator.end();
		Shaderpacks.phase(WorldRenderingPhase.SKY);
	}

	@Redirect(method = "renderSky", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/render/vertex/Tesselator;end()V", ordinal = 0))
	private void astatine$drawSunset(Tesselator tesselator) {
		astatine$draw(tesselator, WorldRenderingPhase.SUNSET, true);
	}

	@Redirect(method = "renderSky", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/render/vertex/Tesselator;end()V", ordinal = 1))
	private void astatine$drawSun(Tesselator tesselator) {
		astatine$draw(tesselator, WorldRenderingPhase.SUN, Shaderpacks.shouldRenderSun());
	}

	@Redirect(method = "renderSky", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/render/vertex/Tesselator;end()V", ordinal = 2))
	private void astatine$drawMoon(Tesselator tesselator) {
		astatine$draw(tesselator, WorldRenderingPhase.MOON, Shaderpacks.shouldRenderMoon());
	}

	@Redirect(method = "renderSky", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/render/vertex/Tesselator;end()V", ordinal = 3))
	private void astatine$drawVoid(Tesselator tesselator) {
		astatine$draw(tesselator, WorldRenderingPhase.VOID, true);
	}

	@Redirect(method = "renderSky", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/render/vertex/VertexBuffer;draw(I)V"))
	private void astatine$drawBuffer(VertexBuffer buffer, int mode) {
		if (buffer == this.lightSkyBuffer && !Shaderpacks.shouldRenderSkyDisc()) {
			return;
		}

		if (buffer == this.starsBuffer) {
			if (!Shaderpacks.shouldRenderStars()) {
				return;
			}

			Shaderpacks.phase(WorldRenderingPhase.STARS);
			buffer.draw(mode);
			Shaderpacks.phase(WorldRenderingPhase.SKY);
			return;
		}

		if (buffer != this.lightSkyBuffer) {
			Shaderpacks.phase(WorldRenderingPhase.VOID);
			buffer.draw(mode);
			Shaderpacks.phase(WorldRenderingPhase.SKY);
			return;
		}

		buffer.draw(mode);
	}

	@Redirect(method = "renderSky", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/render/platform/GlStateManager;callList(I)V", ordinal = 3))
	private void astatine$skipHorizonVoidPlane(int list) {
		if (!Shaderpacks.isWorldActive()) {
			GlStateManager.callList(list);
		}
	}

	@Inject(method = "renderSky", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/render/platform/GlStateManager;rotatef(FFFF)V", ordinal = 3, shift = At.Shift.AFTER))
	private void astatine$tiltSunPath(float tickDelta, int anaglyphRenderPass, CallbackInfo callback) {
		float rotation = Shaderpacks.sunPathRotation();

		if (rotation != 0.0f) {
			GlStateManager.rotatef(rotation, 0.0f, 0.0f, 1.0f);
		}
	}

	@Inject(method = "renderEndSky", at = @At("HEAD"))
	private void astatine$beginEndSky(CallbackInfo callback) {
		Shaderpacks.phase(WorldRenderingPhase.CUSTOM_SKY);
	}

	@Inject(method = "renderEndSky", at = @At("RETURN"))
	private void astatine$endEndSky(CallbackInfo callback) {
		Shaderpacks.phase(WorldRenderingPhase.SKY);
	}
}
