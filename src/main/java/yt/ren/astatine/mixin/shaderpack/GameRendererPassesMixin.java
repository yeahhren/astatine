package yt.ren.astatine.mixin.shaderpack;

import net.irisshaders.iris.pipeline.WorldRenderingPhase;
import net.minecraft.client.Minecraft;
import net.minecraft.client.ParticleManager;
import net.minecraft.client.render.GameRenderer;
import net.minecraft.client.render.ItemInHandRenderer;
import net.minecraft.client.render.platform.GlStateManager;
import net.minecraft.client.render.platform.Lighting;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.entity.Entity;
import net.minecraft.entity.particle.ParticleType;
import net.minecraft.client.options.GameOptions;
import org.lwjgl.opengl.GL11;
import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import yt.ren.astatine.shaderpack.Shaderpacks;

@Mixin(GameRenderer.class)
public abstract class GameRendererPassesMixin {
	@Shadow
	private Minecraft minecraft;

	@Unique
	private Entity astatine$particleCamera;

	@Unique
	private float astatine$weatherTickDelta = -1.0f;

	@Unique
	private boolean astatine$solidHand;

	@Shadow
	private boolean newCamPitch;

	@Shadow
	private float renderDistance;

	@Shadow
	protected abstract void renderItemInHand(float tickDelta, int anaglyphRenderPass);

	@Shadow
	protected abstract void renderSnowAndRain(float tickDelta);

	@Shadow
	public abstract void enableLightMap();

	@Shadow
	public abstract void disableLightMap();

	@Redirect(method = "render(IFJ)V", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/ParticleManager;renderLit(Lnet/minecraft/entity/Entity;F)V"))
	private void astatine$renderLitParticles(ParticleManager particles, Entity camera, float tickDelta) {
		if (Shaderpacks.particlesAfterTranslucent()) {
			this.astatine$particleCamera = camera;
			return;
		}

		particles.renderLit(camera, tickDelta);
	}

	@Redirect(method = "render(IFJ)V", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/ParticleManager;render(Lnet/minecraft/entity/Entity;F)V"))
	private void astatine$renderParticles(ParticleManager particles, Entity camera, float tickDelta) {
		if (Shaderpacks.particlesAfterTranslucent()) {
			this.astatine$particleCamera = camera;
			return;
		}

		particles.render(camera, tickDelta);
	}

	@Redirect(method = "render(IFJ)V", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/render/GameRenderer;renderSnowAndRain(F)V"))
	private void astatine$renderWeather(GameRenderer renderer, float tickDelta) {
		if (Shaderpacks.isActive()) {
			this.astatine$weatherTickDelta = tickDelta;
			return;
		}

		this.renderSnowAndRain(tickDelta);
	}

	@Inject(
		method = "render(IFJ)V",
		at = @At(value = "INVOKE", target = "Lnet/minecraft/client/render/world/WorldRenderer;render(Lnet/minecraft/client/render/block/BlockLayer;DILnet/minecraft/entity/Entity;)I", ordinal = 3, shift = At.Shift.AFTER)
	)
	private void astatine$renderAfterTranslucent(int anaglyphRenderPass, float tickDelta, long renderTimeLimit, CallbackInfo callback) {
		Entity camera = this.astatine$particleCamera;
		this.astatine$particleCamera = null;

		if (camera != null) {
			Shaderpacks.phase(WorldRenderingPhase.PARTICLES);
			this.enableLightMap();
			this.minecraft.particleManager.renderLit(camera, tickDelta);
			Lighting.turnOff();
			this.minecraft.particleManager.render(camera, tickDelta);
			this.disableLightMap();
			Shaderpacks.phase(WorldRenderingPhase.NONE);
		}

		float weatherTickDelta = this.astatine$weatherTickDelta;
		this.astatine$weatherTickDelta = -1.0f;

		if (weatherTickDelta >= 0.0f) {
			GlStateManager.depthMask(Shaderpacks.shouldWriteRainAndSnowToDepthBuffer());
			this.renderSnowAndRain(weatherTickDelta);
			GlStateManager.depthMask(false);
		}
	}

	@Redirect(method = "render(IFJ)V", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/render/platform/GlStateManager;clear(I)V", ordinal = 1))
	private void astatine$clearBeforeHand(int mask) {
		if (!Shaderpacks.isWorldActive()) {
			GlStateManager.clear(mask);
		}
	}

	@Inject(
		method = "render(IFJ)V",
		at = @At(value = "INVOKE", target = "Lnet/minecraft/client/render/world/WorldRenderer;renderWorldBorder(Lnet/minecraft/entity/Entity;F)V")
	)
	private void astatine$renderSolidHand(int anaglyphRenderPass, float tickDelta, long renderTimeLimit, CallbackInfo callback) {
		if (!Shaderpacks.isWorldActive() || !this.newCamPitch) {
			return;
		}

		Shaderpacks.beginHand();
		Shaderpacks.renderSolidHand(tickDelta, () -> {
			GlStateManager.depthMask(true);
			this.astatine$solidHand = true;
			this.renderItemInHand(tickDelta, anaglyphRenderPass);
			this.astatine$solidHand = false;
		});
	}

	@ModifyArg(method = "setupCamera", at = @At(value = "INVOKE", target = "Lorg/lwjgl/util/glu/Project;gluPerspective(FFFF)V"), index = 3)
	private float astatine$worldDepthFar(float far) {
		return Shaderpacks.isActive() ? this.renderDistance * 4.0f : far;
	}

	@ModifyArg(method = "render(IFJ)V", at = @At(value = "INVOKE", target = "Lorg/lwjgl/util/glu/Project;gluPerspective(FFFF)V", ordinal = 1), index = 3)
	private float astatine$restoredDepthFar(float far) {
		return Shaderpacks.isActive() ? this.renderDistance * 4.0f : far;
	}

	@Inject(method = "renderItemInHand", at = @At(value = "INVOKE", target = "Lorg/lwjgl/util/glu/Project;gluPerspective(FFFF)V"))
	private void astatine$scaleHandDepth(float tickDelta, int anaglyphRenderPass, CallbackInfo callback) {
		if (Shaderpacks.isWorldActive()) {
			GlStateManager.scalef(1.0f, 1.0f, Shaderpacks.HAND_DEPTH);
		}
	}

	@Redirect(method = "renderItemInHand", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/render/ItemInHandRenderer;renderInFirstPerson(F)V"))
	private void astatine$renderHeldItem(ItemInHandRenderer renderer, float tickDelta) {
		if (!Shaderpacks.isWorldActive()) {
			renderer.renderInFirstPerson(tickDelta);
			return;
		}

		boolean translucent = Shaderpacks.isHoldingTranslucent();

		if (this.astatine$solidHand) {
			if (!translucent) {
				renderer.renderInFirstPerson(tickDelta);
			}

			return;
		}

		if (translucent) {
			Shaderpacks.renderTranslucentHand(tickDelta, () -> renderer.renderInFirstPerson(tickDelta));
		}
	}

	@Redirect(method = "renderItemInHand", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/render/ItemInHandRenderer;renderScreenEffects(F)V"))
	private void astatine$renderScreenEffects(ItemInHandRenderer renderer, float tickDelta) {
		if (!this.astatine$solidHand) {
			renderer.renderScreenEffects(tickDelta);
		}
	}

	@Inject(method = "render(IFJ)V", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/render/world/WorldRenderer;renderSky(FI)V"))
	private void astatine$renderHorizon(int anaglyphRenderPass, float tickDelta, long renderTimeLimit, CallbackInfo callback) {
		Shaderpacks.renderHorizon();
	}

	@Redirect(method = "render(IFJ)V", at = @At(value = "FIELD", target = "Lnet/minecraft/client/options/GameOptions;viewDistance:I", opcode = Opcodes.GETFIELD))
	private int astatine$alwaysRenderSky(GameOptions options) {
		return Shaderpacks.isWorldActive() ? Math.max(options.viewDistance, 4) : options.viewDistance;
	}

	@Inject(method = "renderSnowAndRain", at = @At("HEAD"), cancellable = true)
	private void astatine$skipWeather(float tickDelta, CallbackInfo callback) {
		if (!Shaderpacks.shouldRenderWeather()) {
			callback.cancel();
		}
	}

	@Redirect(method = "tickRain", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/world/ClientWorld;addParticle(Lnet/minecraft/entity/particle/ParticleType;DDDDDD[I)V"))
	private void astatine$addRainParticle(ClientWorld world, ParticleType type, double x, double y, double z, double velocityX, double velocityY,
		double velocityZ, int[] parameters) {
		if (Shaderpacks.shouldRenderWeatherParticles()) {
			world.addParticle(type, x, y, z, velocityX, velocityY, velocityZ, parameters);
		}
	}
}
