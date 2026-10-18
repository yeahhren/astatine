package net.irisshaders.iris.uniforms;

import net.irisshaders.iris.gl.uniform.UniformHolder;
import net.irisshaders.iris.gl.uniform.UniformUpdateFrequency;
import net.irisshaders.iris.gui.option.IrisVideoSettings;
import net.irisshaders.iris.helpers.JomlConversions;
import net.irisshaders.iris.shaderpack.materialmap.WorldRenderingSettings;
import net.minecraft.block.material.Material;
import net.minecraft.block.state.BlockState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.entity.Entity;
import net.minecraft.entity.global.LightningBoltEntity;
import net.minecraft.entity.living.LivingEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.HitResult;
import net.minecraft.world.WorldSettings;
import org.joml.Math;
import org.joml.Vector3d;
import org.joml.Vector3f;
import org.joml.Vector4f;
import yt.ren.astatine.mixin.shaderpack.GameRendererAccessor;
import yt.ren.astatine.shaderpack.DrawIds;

import java.util.Objects;

import static net.irisshaders.iris.gl.uniform.UniformUpdateFrequency.PER_FRAME;

public class IrisExclusiveUniforms {
	private static final Vector3d ZERO = new Vector3d(0);

	public static void addIrisExclusiveUniforms(UniformHolder uniforms, FrameUpdateNotifier updateNotifier) {
		WorldInfoUniforms.addWorldInfoUniforms(uniforms);

		EndFlashStorage endFlashStorage = new EndFlashStorage();
		updateNotifier.addListener(endFlashStorage::tick);

		uniforms.uniform1i(UniformUpdateFrequency.PER_TICK, "currentColorSpace", () -> IrisVideoSettings.colorSpace.ordinal());

		uniforms.uniform1f(PER_FRAME, "chunkFadeTimeInv", () -> 0.0f);
		uniforms.uniform1i(PER_FRAME, "textureFilteringMode", () -> 0);

		//All Iris-exclusive uniforms (uniforms which do not exist in either OptiFine or ShadersMod) should be registered here.
		uniforms.uniform1b(UniformUpdateFrequency.PER_TICK, "feetInWater", IrisExclusiveUniforms::getIsInShallowWater);
		uniforms.uniform1b(UniformUpdateFrequency.PER_TICK, "inSwimmingAnimation", IrisExclusiveUniforms::getIsSwimming);
		uniforms.uniform1b(UniformUpdateFrequency.PER_TICK, "isRiding", IrisExclusiveUniforms::getIsPassenger);
		uniforms.uniform1b(UniformUpdateFrequency.PER_TICK, "isElytraFlying", IrisExclusiveUniforms::isElytraFlying);
		uniforms.uniform1b(UniformUpdateFrequency.PER_TICK, "vehicleInWater", IrisExclusiveUniforms::getVehicleInShallowWater);
		uniforms.uniform1i(UniformUpdateFrequency.PER_TICK, "vehicleId", IrisExclusiveUniforms::getVehicleId);
		uniforms.uniform3d(PER_FRAME, "vehicleLookVector", IrisExclusiveUniforms::getVehicleLookVector);
		uniforms.uniform3d(PER_FRAME, "relativeVehiclePosition", IrisExclusiveUniforms::getRelativeVehiclePosition);
		uniforms.uniform1f(PER_FRAME, "thunderStrength", IrisExclusiveUniforms::getThunderStrength);
		uniforms.uniform1f(PER_FRAME, "glintAlpha", () -> 1.0f);
		uniforms.uniform1f(UniformUpdateFrequency.PER_TICK, "currentPlayerHealth", IrisExclusiveUniforms::getCurrentHealth);
		uniforms.uniform1b(UniformUpdateFrequency.PER_TICK, "heavyFog", IrisExclusiveUniforms::isHeavyFog);
		uniforms.uniform1f(UniformUpdateFrequency.PER_TICK, "maxPlayerHealth", IrisExclusiveUniforms::getMaxHealth);
		uniforms.uniform1f(UniformUpdateFrequency.PER_TICK, "currentPlayerHunger", IrisExclusiveUniforms::getCurrentHunger);
		uniforms.uniform1f(UniformUpdateFrequency.PER_TICK, "maxPlayerHunger", () -> 20);
		uniforms.uniform1f(UniformUpdateFrequency.PER_TICK, "endFlashIntensity", endFlashStorage::getCurrentEndFlash);
		uniforms.uniform1f(UniformUpdateFrequency.PER_TICK, "previousEndFlashIntensity", endFlashStorage::getLastEndFlash);
		uniforms.uniform1f(UniformUpdateFrequency.PER_TICK, "currentPlayerArmor", IrisExclusiveUniforms::getCurrentArmor);
		uniforms.uniform1f(UniformUpdateFrequency.PER_TICK, "maxPlayerArmor", () -> 50);
		uniforms.uniform1f(UniformUpdateFrequency.PER_TICK, "currentPlayerAir", IrisExclusiveUniforms::getCurrentAir);
		uniforms.uniform1f(UniformUpdateFrequency.PER_TICK, "maxPlayerAir", IrisExclusiveUniforms::getMaxAir);
		uniforms.uniform1b(PER_FRAME, "firstPersonCamera", IrisExclusiveUniforms::isFirstPersonCamera);
		uniforms.uniform1b(UniformUpdateFrequency.PER_TICK, "isSpectator", IrisExclusiveUniforms::isSpectator);
		uniforms.uniform1i(PER_FRAME, "currentSelectedBlockId", IrisExclusiveUniforms::getCurrentSelectedBlockId);
		uniforms.uniform1i(PER_FRAME, "seaLevel",
			() -> Minecraft.getInstance().world == null ? 0 : Minecraft.getInstance().world.getSeaLevel());
		uniforms.uniform3f(PER_FRAME, "currentSelectedBlockPos", IrisExclusiveUniforms::getCurrentSelectedBlockPos);
		uniforms.uniform3d(PER_FRAME, "eyePosition", IrisExclusiveUniforms::getEyePosition);
		uniforms.uniform1f(UniformUpdateFrequency.PER_TICK, "cloudTime", CapturedRenderingState.INSTANCE::getCloudTime);
		uniforms.uniform3d(PER_FRAME, "relativeEyePosition", () -> CameraUniforms.getUnshiftedCameraPosition().sub(getEyePosition()));
		uniforms.uniform3d(PER_FRAME, "playerLookVector", () -> {
			if (Minecraft.getInstance().getCamera() instanceof LivingEntity livingEntity) {
				return JomlConversions.fromVec3(livingEntity.getRotationVector(CapturedRenderingState.INSTANCE.getTickDelta()));
			} else {
				return ZERO;
			}
		});
		uniforms.uniform3d(PER_FRAME, "playerBodyVector",
			() -> JomlConversions.fromVec3(Minecraft.getInstance().getCamera().getRotationVector(1.0f)));
		Vector4f zero = new Vector4f(0, 0, 0, 0);
		uniforms.uniform4f(UniformUpdateFrequency.PER_TICK, "lightningBoltPosition", () -> {
			if (Minecraft.getInstance().world != null) {
				return Minecraft.getInstance().world.globalEntities.stream()
					.filter(bolt -> bolt instanceof LightningBoltEntity).findAny().map(bolt -> {
						Vector3d unshiftedCameraPosition = CameraUniforms.getUnshiftedCameraPosition();
						Vec3d vec3 = new Vec3d(bolt.x, bolt.y, bolt.z);
						return new Vector4f((float) (vec3.x - unshiftedCameraPosition.x), (float) (vec3.y - unshiftedCameraPosition.y),
							(float) (vec3.z - unshiftedCameraPosition.z), 1);
					}).orElse(zero);
			} else {
				return zero;
			}
		});
	}

	private static int getVehicleId() {
		if (Minecraft.getInstance().player == null)
			return 0;
		if (Minecraft.getInstance().player.vehicle == null)
			return 0;

		if (WorldRenderingSettings.INSTANCE.getEntityIds() == null)
			return 0;

		return DrawIds.entityId(Minecraft.getInstance().player.vehicle);
	}

	private static Vector3d getVehicleLookVector() {
		if (Minecraft.getInstance().player == null)
			return ZERO;
		if (Minecraft.getInstance().player.vehicle == null)
			return ZERO;

		return JomlConversions.fromVec3(Minecraft.getInstance().player.vehicle.getRotationVector(1.0f));
	}

	private static Vector3d getRelativeVehiclePosition() {
		if (Minecraft.getInstance().player == null)
			return ZERO;
		if (Minecraft.getInstance().player.vehicle == null)
			return ZERO;

		Vec3d vehiclePos = interpolatedPosition(Minecraft.getInstance().player.vehicle);

		Vector3d pos = new Vector3d(vehiclePos.x, vehiclePos.y, vehiclePos.z);

		return CameraUniforms.getUnshiftedCameraPosition().sub(pos);
	}

	private static boolean getVehicleInShallowWater() {
		if (Minecraft.getInstance().player == null)
			return false;
		if (Minecraft.getInstance().player.vehicle == null)
			return false;

		return Minecraft.getInstance().player.vehicle.isInWater();
	}

	private static boolean getIsInShallowWater() {
		if (Minecraft.getInstance().player == null) {
			return false;
		}

		return Minecraft.getInstance().player.isInWater();
	}

	private static boolean getIsSwimming() {
		if (Minecraft.getInstance().player == null) {
			return false;
		}

		return Minecraft.getInstance().player.isInWater() && Minecraft.getInstance().player.isSubmergedIn(Material.WATER);
	}

	private static boolean getIsPassenger() {
		if (Minecraft.getInstance().player == null) {
			return false;
		}

		return Minecraft.getInstance().player.isRiding();
	}

	private static boolean isElytraFlying() {
		if (Minecraft.getInstance().player == null) {
			return false;
		}

		return false;
	}

	private static boolean isHeavyFog() {
		if (Minecraft.getInstance().world != null) {
			return ((GameRendererAccessor) Minecraft.getInstance().gameRenderer).astatine$hasThickFog();
		}

		return false;
	}

	private static int getCurrentSelectedBlockId() {
		HitResult hitResult = Minecraft.getInstance().crosshairTarget;
		if (Minecraft.getInstance().world != null && !Minecraft.getInstance().options.hideGui && hitResult != null && hitResult.type == HitResult.Type.BLOCK) {
			BlockPos blockPos4 = hitResult.getPos();
			BlockState blockState = Minecraft.getInstance().world.getBlockState(blockPos4);
			if (blockState.getBlock().getMaterial() != Material.AIR && Minecraft.getInstance().world.getWorldBorder().contains(blockPos4)) {
				return WorldRenderingSettings.INSTANCE.getBlockStateIds().getInt(blockState);
			}
		}

		return 0;
	}

	private static Vector3f getCurrentSelectedBlockPos() {
		HitResult hitResult = Minecraft.getInstance().crosshairTarget;
		if (Minecraft.getInstance().world != null && !Minecraft.getInstance().options.hideGui && hitResult != null && hitResult.type == HitResult.Type.BLOCK) {
			BlockPos blockPos4 = hitResult.getPos();
			Vector3d camera = CameraUniforms.getUnshiftedCameraPosition();
			return new Vector3f((float) (blockPos4.getX() + 0.5 - camera.x), (float) (blockPos4.getY() + 0.5 - camera.y),
				(float) (blockPos4.getZ() + 0.5 - camera.z));
		}

		return new Vector3f(-256.0f);
	}

	private static float getThunderStrength() {
		// Note: Ensure this is in the range of 0 to 1 - some custom servers send out of range values.
		return Math.clamp(0.0F, 1.0F, Minecraft.getInstance().world.getThunder(CapturedRenderingState.INSTANCE.getTickDelta()));
	}

	private static float getCurrentHealth() {
		if (Minecraft.getInstance().player == null || !Minecraft.getInstance().interactionManager.hasStatusBars()) {
			return -1;
		}

		return Minecraft.getInstance().player.getHealth() / Minecraft.getInstance().player.getMaxHealth();
	}

	private static float getCurrentHunger() {
		if (Minecraft.getInstance().player == null || !Minecraft.getInstance().interactionManager.hasStatusBars()) {
			return -1;
		}

		return Minecraft.getInstance().player.getHungerManager().getFoodLevel() / 20f;
	}

	private static float getCurrentAir() {
		if (Minecraft.getInstance().player == null || !Minecraft.getInstance().interactionManager.hasStatusBars()) {
			return -1;
		}

		return (float) Minecraft.getInstance().player.getBreath() / 300.0f;
	}

	private static float getCurrentArmor() {
		if (Minecraft.getInstance().player == null || !Minecraft.getInstance().interactionManager.hasStatusBars()) {
			return -1;
		}

		return Minecraft.getInstance().player.getArmorProtection() / 50.0f;
	}

	private static float getMaxAir() {
		if (Minecraft.getInstance().player == null || !Minecraft.getInstance().interactionManager.hasStatusBars()) {
			return -1;
		}

		return 300;
	}

	private static float getMaxHealth() {
		if (Minecraft.getInstance().player == null || !Minecraft.getInstance().interactionManager.hasStatusBars()) {
			return -1;
		}

		return Minecraft.getInstance().player.getMaxHealth();
	}


	private static boolean isFirstPersonCamera() {
		// If camera type is not explicitly third-person, assume it's first-person.
		return switch (Minecraft.getInstance().options.perspective) {
			case 1, 2 -> false;
			default -> true;
		};
	}

	private static boolean isSpectator() {
		return Minecraft.getInstance().interactionManager.getGameMode() == WorldSettings.GameMode.SPECTATOR;
	}

	private static Vector3d getEyePosition() {
		Objects.requireNonNull(Minecraft.getInstance().getCamera());
		Vec3d pos = Minecraft.getInstance().getCamera().getEyePosition(CapturedRenderingState.INSTANCE.getTickDelta());
		return new Vector3d(pos.x, pos.y, pos.z);
	}

	private static Vec3d interpolatedPosition(Entity entity) {
		double delta = CapturedRenderingState.INSTANCE.getTickDelta();
		return new Vec3d(entity.prevX + (entity.x - entity.prevX) * delta, entity.prevY + (entity.y - entity.prevY) * delta,
			entity.prevZ + (entity.z - entity.prevZ) * delta);
	}

	public static class WorldInfoUniforms {
		private static ClientWorld level() {
			return Minecraft.getInstance().world;
		}

		public static void addWorldInfoUniforms(UniformHolder uniforms) {
			// TODO: Use level.dimensionType() coordinates for 1.18!
			uniforms.uniform1i(PER_FRAME, "bedrockLevel", () -> {
				if (level() != null) {
					return 0;
				} else {
					return 0;
				}
			});
			uniforms.uniform1f(PER_FRAME, "cloudHeight", () -> {
				if (level() != null) {
					return level().dimension.getCloudHeight();
				} else {
					return 192.0;
				}
			});

			uniforms.uniform1i(PER_FRAME, "heightLimit", () -> {
				if (level() != null) {
					return 256;
				} else {
					return 256;
				}
			});
			uniforms.uniform1i(PER_FRAME, "logicalHeightLimit", () -> {
				if (level() != null) {
					return level().dimension.getId() == -1 ? 128 : 256;
				} else {
					return 256;
				}
			});
			uniforms.uniform1b(PER_FRAME, "hasCeiling", () -> {
				if (level() != null) {
					return level().dimension.getId() == -1;
				} else {
					return false;
				}
			});
			uniforms.uniform1b(PER_FRAME, "hasSkylight", () -> {
				if (level() != null) {
					return !level().dimension.hasNoSky();
				} else {
					return true;
				}
			});
			uniforms.uniform1f(PER_FRAME, "ambientLight", () -> {
				if (level() != null) {
					return level().dimension.getBrightnessTable()[0];
				} else {
					return 0f;
				}
			});

		}
	}
}
