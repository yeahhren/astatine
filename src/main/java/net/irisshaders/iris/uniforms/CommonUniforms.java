package net.irisshaders.iris.uniforms;

import net.irisshaders.iris.gl.state.FogMode;
import net.irisshaders.iris.gl.state.StateUpdateNotifiers;
import net.irisshaders.iris.gl.uniform.DynamicUniformHolder;
import net.irisshaders.iris.gl.uniform.UniformHolder;
import net.irisshaders.iris.gl.uniform.UniformUpdateFrequency;
import net.irisshaders.iris.shaderpack.IdMap;
import net.irisshaders.iris.shaderpack.properties.PackDirectives;
import net.irisshaders.iris.uniforms.transforms.SmoothedFloat;
import net.irisshaders.iris.uniforms.transforms.SmoothedVec2f;
import net.minecraft.block.material.Material;
import net.minecraft.client.Minecraft;
import net.minecraft.client.render.texture.TextureAtlas;
import net.minecraft.client.render.texture.TextureAtlasSprite;
import net.minecraft.entity.Entity;
import net.minecraft.entity.living.LivingEntity;
import net.minecraft.entity.living.effect.StatusEffect;
import net.minecraft.entity.living.effect.StatusEffectInstance;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.LightType;
import org.joml.Math;
import org.joml.Vector2f;
import org.joml.Vector2i;
import org.joml.Vector3d;
import org.joml.Vector4f;
import org.joml.Vector4i;
import yt.ren.astatine.mixin.shaderpack.GameRendererAccessor;
import yt.ren.astatine.shaderpack.Shaderpacks;

public final class CommonUniforms {
	private static final Minecraft client = Minecraft.getInstance();
	private static final Vector2i ZERO_VECTOR_2i = new Vector2i();
	private static final Vector4i ZERO_VECTOR_4i = new Vector4i(0, 0, 0, 0);
	private static final Vector3d ZERO_VECTOR_3d = new Vector3d();

	private CommonUniforms() {
		// no construction allowed
	}

	public static void addDynamicUniforms(DynamicUniformHolder uniforms, FogMode fogMode) {
		ExternallyManagedUniforms.addExternallyManagedUniforms117(uniforms);
		FogUniforms.addFogUniforms(uniforms, fogMode);
		IrisInternalUniforms.addFogUniforms(uniforms, fogMode);

		// This is a fallback for when entityId via attributes cannot be used. (lightning)
		uniforms.uniform1i("entityId", CapturedRenderingState.INSTANCE::getCurrentRenderedEntity,
			StateUpdateNotifiers.fallbackEntityNotifier);

		// TODO: OptiFine doesn't think that atlasSize is a "dynamic" uniform,
		//       but we do. How will custom uniforms depending on atlasSize work?
		//
		// Note: on 1.17+ we don't need to reset this when textures are bound, since
		// the shader will always be setup (and therefore uniforms will be re-uploaded)
		// after the texture is changed and before rendering starts.
		uniforms.uniform2i("atlasSize", CommonUniforms::getBlockAtlasSize, listener -> {
		});

		uniforms.uniform1i("gtextureId", () -> 0, StateUpdateNotifiers.bindTextureNotifier);
		uniforms.uniform1i("textureReloadCount", CapturedRenderingState.INSTANCE::getTextureReloadCount,
			StateUpdateNotifiers.bindTextureNotifier);

		uniforms.uniform2i("gtextureSize", CommonUniforms::getBlockAtlasSize, StateUpdateNotifiers.bindTextureNotifier);

		uniforms.uniform4i("blendFunc", CapturedRenderingState.INSTANCE::getBlendFunction, StateUpdateNotifiers.blendFuncNotifier);

		uniforms.uniform1i("renderStage", () -> Shaderpacks.currentPhase().ordinal(), StateUpdateNotifiers.phaseChangeNotifier);
	}

	public static void addCommonUniforms(DynamicUniformHolder uniforms, IdMap idMap, PackDirectives directives,
		FrameUpdateNotifier updateNotifier, FogMode fogMode) {
		CommonUniforms.addNonDynamicUniforms(uniforms, idMap, directives, updateNotifier);
		CommonUniforms.addDynamicUniforms(uniforms, fogMode);
	}

	public static void addNonDynamicUniforms(UniformHolder uniforms, IdMap idMap, PackDirectives directives,
		FrameUpdateNotifier updateNotifier) {
		CameraUniforms.addCameraUniforms(uniforms, updateNotifier);
		ViewportUniforms.addViewportUniforms(uniforms);
		WorldTimeUniforms.addWorldTimeUniforms(uniforms);
		SystemTimeUniforms.addSystemTimeUniforms(uniforms);
		BiomeUniforms.addBiomeUniforms(uniforms);
		new CelestialUniforms(directives.getSunPathRotation()).addCelestialUniforms(uniforms);
		IrisExclusiveUniforms.addIrisExclusiveUniforms(uniforms, updateNotifier);
		IrisTimeUniforms.addTimeUniforms(uniforms);
		MatrixUniforms.addMatrixUniforms(uniforms, directives);
		IdMapUniforms.addIdMapUniforms(updateNotifier, uniforms, idMap, directives.isOldHandLight());
		CommonUniforms.generalCommonUniforms(uniforms, updateNotifier, directives);
		IrisInternalUniforms.addOtherUniforms(uniforms, updateNotifier, directives);
	}

	public static void generalCommonUniforms(UniformHolder uniforms, FrameUpdateNotifier updateNotifier, PackDirectives directives) {
		ExternallyManagedUniforms.addExternallyManagedUniforms117(uniforms);

		SmoothedVec2f eyeBrightnessSmooth = new SmoothedVec2f(directives.getEyeBrightnessHalfLife(), directives.getEyeBrightnessHalfLife(),
			CommonUniforms::getEyeBrightness, updateNotifier);

		uniforms.uniform1b(UniformUpdateFrequency.PER_FRAME, "hideGUI", () -> client.options.hideGui)
			.uniform1b(UniformUpdateFrequency.PER_FRAME, "isRightHanded", () -> true)
			.uniform1i(UniformUpdateFrequency.PER_FRAME, "isEyeInWater", CommonUniforms::isEyeInWater)
			.uniform1f(UniformUpdateFrequency.PER_FRAME, "blindness", CommonUniforms::getBlindness)
			.uniform1f(UniformUpdateFrequency.PER_FRAME, "darknessFactor", CommonUniforms::getDarknessFactor)
			.uniform1f(UniformUpdateFrequency.PER_FRAME, "darknessLightFactor", CapturedRenderingState.INSTANCE::getDarknessLightFactor)
			.uniform1f(UniformUpdateFrequency.PER_FRAME, "nightVision", CommonUniforms::getNightVision)
			.uniform1b(UniformUpdateFrequency.PER_FRAME, "is_sneaking", CommonUniforms::isSneaking)
			.uniform1b(UniformUpdateFrequency.PER_FRAME, "is_sprinting", CommonUniforms::isSprinting)
			.uniform1b(UniformUpdateFrequency.PER_FRAME, "is_hurt", CommonUniforms::isHurt)
			.uniform1b(UniformUpdateFrequency.PER_FRAME, "is_invisible", CommonUniforms::isInvisible)
			.uniform1b(UniformUpdateFrequency.PER_FRAME, "is_burning", CommonUniforms::isBurning)
			.uniform1b(UniformUpdateFrequency.PER_FRAME, "is_on_ground", CommonUniforms::isOnGround)
			// TODO: Do we need to clamp this to avoid fullbright breaking shaders? Or should shaders be able to detect
			//       that the player is trying to turn on fullbright?
			.uniform1f(UniformUpdateFrequency.PER_FRAME, "screenBrightness", () -> client.options.gamma)
			// just a dummy value for shaders where entityColor isn't supplied through a vertex attribute (and thus is
			// not available) - suppresses warnings. See AttributeShaderTransformer for the actual entityColor code.
			.uniform4f(UniformUpdateFrequency.ONCE, "entityColor", () -> new Vector4f(0, 0, 0, 0))
			.uniform1i(UniformUpdateFrequency.ONCE, "blockEntityId", () -> -1)
			.uniform1i(UniformUpdateFrequency.ONCE, "currentRenderedItemId", () -> -1)
			.uniform1i(UniformUpdateFrequency.PER_FRAME, "anisotropicFiltering", () -> 0)
			.uniform1f(UniformUpdateFrequency.ONCE, "pi", () -> Math.PI)
			.uniform1f(UniformUpdateFrequency.PER_TICK, "playerMood", CommonUniforms::getPlayerMood)
			.uniform1f(UniformUpdateFrequency.PER_TICK, "constantMood", CommonUniforms::getConstantMood)
			.uniform2i(UniformUpdateFrequency.PER_FRAME, "eyeBrightness", CommonUniforms::getEyeBrightness)
			.uniform2i(UniformUpdateFrequency.PER_FRAME, "eyeBrightnessSmooth", () -> {
				Vector2f smoothed = eyeBrightnessSmooth.get();
				return new Vector2i((int) smoothed.x(), (int) smoothed.y());
			}).uniform1f(UniformUpdateFrequency.PER_TICK, "rainStrength", CommonUniforms::getRainStrength)
			.uniform1f(UniformUpdateFrequency.PER_TICK, "wetness",
				new SmoothedFloat(directives.getWetnessHalfLife(), directives.getDrynessHalfLife(), CommonUniforms::getRainStrength,
					updateNotifier)).uniform3d(UniformUpdateFrequency.PER_FRAME, "skyColor", CommonUniforms::getSkyColor)
			.uniform1f(UniformUpdateFrequency.PER_FRAME, "dhFarPlane", () -> 0.0f)
			.uniform1f(UniformUpdateFrequency.PER_FRAME, "dhNearPlane", () -> 0.0f)
			.uniform1i(UniformUpdateFrequency.PER_FRAME, "dhRenderDistance", () -> 0);
	}

	private static Vector2i getBlockAtlasSize() {
		TextureAtlas atlas = client.getBlocksAtlas();
		TextureAtlasSprite sprite = atlas == null ? null : atlas.getMissingSprite();

		if (sprite == null || sprite.getUMax() <= sprite.getUMin() || sprite.getVMax() <= sprite.getVMin()) {
			return ZERO_VECTOR_2i;
		}

		return new Vector2i(Math.round((sprite.getWidth() - 0.02f) / (sprite.getUMax() - sprite.getUMin())),
			Math.round((sprite.getHeight() - 0.02f) / (sprite.getVMax() - sprite.getVMin())));
	}

	private static boolean isOnGround() {
		return client.player != null && client.player.onGround;
	}

	private static boolean isHurt() {
		if (client.player != null) {
			return client.player.damagedTimer > 0; // Do not use isHurt, that's not what we want!
		} else {
			return false;
		}
	}

	private static boolean isInvisible() {
		if (client.player != null) {
			return client.player.isInvisible();
		} else {
			return false;
		}
	}

	private static boolean isBurning() {
		if (client.player != null) {
			return client.player.isOnFire();
		} else {
			return false;
		}
	}

	private static boolean isSneaking() {
		if (client.player != null) {
			return client.player.isSneaking();
		} else {
			return false;
		}
	}

	private static boolean isSprinting() {
		if (client.player != null) {
			return client.player.isSprinting();
		} else {
			return false;
		}
	}

	private static Vector3d getSkyColor() {
		if (client.world == null || client.getCamera() == null) {
			return ZERO_VECTOR_3d;
		}

		Vec3d skyColor = client.world.getSkyColor(client.getCamera(), CapturedRenderingState.INSTANCE.getTickDelta());

		return new Vector3d(skyColor.x, skyColor.y, skyColor.z);
	}

	static float getBlindness() {
		Entity cameraEntity = client.getCamera();

		if (cameraEntity instanceof LivingEntity) {
			StatusEffectInstance blindness = ((LivingEntity) cameraEntity).getEffectInstance(StatusEffect.BLINDNESS);

			if (blindness != null) {
				// Guessing that this is what OF uses, based on how vanilla calculates the fog value in FogRenderer
				// TODO: Add this to ShaderDoc
				return Math.clamp(0.0F, 1.0F, blindness.getDuration() / 20.0F);
			}
		}

		return 0.0F;
	}

	static float getDarknessFactor() {
		return 0.0F;
	}

	private static float getPlayerMood() {
		return 0.0F;
	}

	private static float getConstantMood() {
		return 0.0F;
	}

	static float getRainStrength() {
		if (client.world == null) {
			return 0f;
		}

		// Note: Ensure this is in the range of 0 to 1 - some custom servers send out of range values.
		return Math.clamp(0.0F, 1.0F, client.world.getRain(CapturedRenderingState.INSTANCE.getTickDelta()));
	}

	private static Vector2i getEyeBrightness() {
		Entity camera = client.getCamera();

		if (camera == null || client.world == null) {
			return ZERO_VECTOR_2i;
		}

		BlockPos eyeBlockPos = new BlockPos(camera.x, camera.y + camera.getEyeHeight(), camera.z);

		int blockLight = client.world.getBrightness(LightType.BLOCK, eyeBlockPos);
		int skyLight = client.world.getBrightness(LightType.SKY, eyeBlockPos);

		return new Vector2i(blockLight * 16, skyLight * 16);
	}

	private static float getNightVision() {
		Entity cameraEntity = client.getCamera();

		if (cameraEntity instanceof LivingEntity livingEntity) {

			try {
				// See MixinGameRenderer#iris$safecheckNightvisionStrength.
				//
				// We modify the behavior of getNightVisionScale so that it's safe for us to call it even on entities
				// that don't have the effect, allowing us to pick up modified night vision strength values from mods
				// like Origins.
				//
				// See: https://github.com/apace100/apoli/blob/320b0ef547fbbf703de7154f60909d30366f6500/src/main/java/io/github/apace100/apoli/mixin/GameRendererMixin.java#L153
				float nightVisionStrength = livingEntity.hasStatusEffect(StatusEffect.NIGHTVISION) ? ((GameRendererAccessor) client.gameRenderer).astatine$getNightVisionScale(
					livingEntity, CapturedRenderingState.INSTANCE.getTickDelta()) : 0.0F;

				if (nightVisionStrength > 0) {
					// Just protecting against potential weird mod behavior
					return Math.clamp(0.0F, 1.0F, nightVisionStrength);
				}
			} catch (NullPointerException e) {
				// If our injection didn't get applied, a NullPointerException will occur from calling that method if
				// the entity doesn't currently have night vision. This isn't pretty but it's functional.
				return 0.0F;
			}
		}

		return 0.0F;
	}

	static int isEyeInWater() {
		// Note: With certain utility / cheat mods, this method will return air even when the player is submerged when
		// the "No Overlay" feature is enabled.
		//
		// I'm not sure what the best way to deal with this is, but the current approach seems to be an acceptable one -
		// after all, disabling the overlay results in the intended effect of it not really looking like you're
		// underwater on most shaderpacks. For now, I will leave this as-is, but it is something to keep in mind.
		Entity camera = client.getCamera();
		boolean isSpectator = client.player != null && client.player.isSpectator();
		if (camera == null) {
			return 0;
		} else if (camera.isSubmergedIn(Material.WATER)) {
			return 1;
		} else if (!isSpectator && camera.isSubmergedIn(Material.LAVA)) {
			return 2;
		} else {
			return 0;
		}
	}
}
