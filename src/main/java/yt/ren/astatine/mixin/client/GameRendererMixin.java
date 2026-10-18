package yt.ren.astatine.mixin.client;

import java.nio.FloatBuffer;
import net.irisshaders.iris.pipeline.WorldRenderingPhase;
import net.minecraft.client.Minecraft;
import net.minecraft.client.render.GameRenderer;
import net.minecraft.client.render.platform.GlStateManager;
import net.minecraft.client.render.world.WorldRenderer;
import net.minecraft.entity.living.player.PlayerEntity;
import net.minecraft.world.HitResult;
import org.lwjgl.opengl.GL11;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import yt.ren.astatine.extension.FramebufferHolder;
import yt.ren.astatine.mixin.shaderpack.WorldRendererAccessor;
import yt.ren.astatine.render.GL;
import yt.ren.astatine.shaderpack.Shaderpacks;

@Mixin(GameRenderer.class)
public abstract class GameRendererMixin {
	@Shadow
	@Final
	private Minecraft minecraft;

	@Shadow
	private float fogRed;

	@Shadow
	private float fogGreen;

	@Shadow
	private float fogBlue;

	@Unique
	private WorldRenderer astatine$outlineRenderer;

	@Unique
	private PlayerEntity astatine$outlineCamera;

	@Unique
	private HitResult astatine$outlineHit;

	@Redirect(method = "setupFog", at = @At(value = "INVOKE", target = "Lorg/lwjgl/opengl/GL11;glFog(ILjava/nio/FloatBuffer;)V"))
	private void astatine$fog(int name, FloatBuffer values) {
		if (name == GL11.GL_FOG_COLOR) {
			int at = values.position();
			GL.fogColor(values.get(at), values.get(at + 1), values.get(at + 2), values.get(at + 3));
		}
	}

	@Redirect(method = "setupFog", at = @At(value = "INVOKE", target = "Lorg/lwjgl/opengl/GL11;glFogi(II)V"))
	private void astatine$fogDistance(int name, int mode) {
		GL.fogRadial(mode == 34139);
	}

	@Redirect(
		method = "render(IFJ)V",
		at = @At(value = "INVOKE", target = "Lnet/minecraft/client/render/world/WorldRenderer;renderBlockOutline(Lnet/minecraft/entity/living/player/PlayerEntity;Lnet/minecraft/world/HitResult;IF)V")
	)
	private void astatine$deferOutline(WorldRenderer renderer, PlayerEntity camera, HitResult hit, int mode, float tickDelta) {
		this.astatine$outlineRenderer = renderer;
		this.astatine$outlineCamera = camera;
		this.astatine$outlineHit = hit;
	}

	@Inject(
		method = "render(IFJ)V",
		at = @At(
			value = "INVOKE",
			target = "Lnet/minecraft/client/render/world/WorldRenderer;render(Lnet/minecraft/client/render/block/BlockLayer;DILnet/minecraft/entity/Entity;)I",
			ordinal = 3,
			shift = At.Shift.AFTER
		)
	)
	private void astatine$renderOutline(int anaglyphRenderPass, float tickDelta, long renderTimeLimit, CallbackInfo callback) {
		WorldRenderer renderer = this.astatine$outlineRenderer;
		if (renderer == null) {
			return;
		}
		GlStateManager.disableAlphaTest();
		renderer.renderBlockOutline(this.astatine$outlineCamera, this.astatine$outlineHit, 0, tickDelta);
		GlStateManager.enableAlphaTest();
		this.astatine$outlineRenderer = null;
		this.astatine$outlineCamera = null;
		this.astatine$outlineHit = null;
	}

	@Inject(
		method = "render(IFJ)V",
		at = @At(value = "INVOKE", target = "Lnet/minecraft/client/render/platform/GlStateManager;clear(I)V", ordinal = 0, shift = At.Shift.AFTER)
	)
	private void astatine$beginShaderpackWorld(int anaglyphRenderPass, float tickDelta, long renderTimeLimit, CallbackInfo callback) {
		if (Shaderpacks.isActive() && this.minecraft.getRenderTarget() != null) {
			long framebuffer = ((FramebufferHolder) this.minecraft.getRenderTarget()).astatine$framebuffer();
			Shaderpacks.beginWorld(framebuffer, this.fogRed, this.fogGreen, this.fogBlue);
		}
	}

	@Inject(method = "render(FJ)V", at = @At("HEAD"))
	private void astatine$beginShaderpackFrame(float tickDelta, long renderTimeLimit, CallbackInfo callback) {
		if (Shaderpacks.isInitialized()) {
			Shaderpacks.beginFrame();
		}
	}

	@Inject(
		method = "render(IFJ)V",
		at = @At(value = "INVOKE", target = "Lnet/minecraft/client/render/Camera;setup(Lnet/minecraft/entity/living/player/PlayerEntity;Z)V", shift = At.Shift.AFTER)
	)
	private void astatine$captureShaderpackCamera(int anaglyphRenderPass, float tickDelta, long renderTimeLimit, CallbackInfo callback) {
		if (Shaderpacks.isWorldActive()) {
			int ticks = ((WorldRendererAccessor) this.minecraft.worldRenderer).astatine$ticks();
			Shaderpacks.captureCamera(tickDelta, (ticks + tickDelta) * 0.03F);
		}
	}

	@Inject(method = "renderWorld", at = @At("RETURN"))
	private void astatine$endShaderpackWorld(float tickDelta, long renderTimeLimit, CallbackInfo callback) {
		Shaderpacks.endWorld();
	}

	@Inject(method = "renderSnowAndRain", at = @At("HEAD"))
	private void astatine$beginWeather(float tickDelta, CallbackInfo callback) {
		Shaderpacks.phase(WorldRenderingPhase.RAIN_SNOW);
	}

	@Inject(method = "renderSnowAndRain", at = @At("RETURN"))
	private void astatine$endWeather(float tickDelta, CallbackInfo callback) {
		Shaderpacks.phase(WorldRenderingPhase.NONE);
	}

	@Inject(method = "renderClouds", at = @At("HEAD"))
	private void astatine$beginClouds(WorldRenderer worldRenderer, float tickDelta, int anaglyphRenderPass, CallbackInfo callback) {
		Shaderpacks.phase(WorldRenderingPhase.CLOUDS);
	}

	@Inject(method = "renderClouds", at = @At("RETURN"))
	private void astatine$endClouds(WorldRenderer worldRenderer, float tickDelta, int anaglyphRenderPass, CallbackInfo callback) {
		Shaderpacks.phase(WorldRenderingPhase.NONE);
	}

	@Inject(
		method = "render(IFJ)V",
		at = {
			@At(value = "INVOKE", target = "Lnet/minecraft/client/ParticleManager;renderLit(Lnet/minecraft/entity/Entity;F)V"),
			@At(value = "INVOKE", target = "Lnet/minecraft/client/ParticleManager;render(Lnet/minecraft/entity/Entity;F)V")
		}
	)
	private void astatine$beginParticles(int anaglyphRenderPass, float tickDelta, long renderTimeLimit, CallbackInfo callback) {
		Shaderpacks.phase(WorldRenderingPhase.PARTICLES);
	}

	@Inject(
		method = "render(IFJ)V",
		at = {
			@At(value = "INVOKE", target = "Lnet/minecraft/client/ParticleManager;renderLit(Lnet/minecraft/entity/Entity;F)V", shift = At.Shift.AFTER),
			@At(value = "INVOKE", target = "Lnet/minecraft/client/ParticleManager;render(Lnet/minecraft/entity/Entity;F)V", shift = At.Shift.AFTER)
		}
	)
	private void astatine$endParticles(int anaglyphRenderPass, float tickDelta, long renderTimeLimit, CallbackInfo callback) {
		Shaderpacks.phase(WorldRenderingPhase.NONE);
	}
}
