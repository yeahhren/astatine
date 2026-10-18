package net.irisshaders.iris.shadows;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.renderpearl.api.commands.RenderPass;
import net.irisshaders.iris.Iris;
import net.irisshaders.iris.compat.dh.DHCompat;
import net.irisshaders.iris.gl.GLDebug;
import net.irisshaders.iris.gl.IrisRenderSystem;
import net.irisshaders.iris.gui.option.IrisVideoSettings;
import net.irisshaders.iris.pipeline.IrisRenderingPipeline;
import net.irisshaders.iris.pipeline.WorldRenderingPhase;
import net.irisshaders.iris.shaderpack.programs.ProgramSource;
import net.irisshaders.iris.shaderpack.properties.PackDirectives;
import net.irisshaders.iris.shaderpack.properties.PackShadowDirectives;
import net.irisshaders.iris.shaderpack.properties.ShadowCullState;
import net.irisshaders.iris.shadows.frustum.BoxCuller;
import net.irisshaders.iris.shadows.frustum.CullEverythingFrustum;
import net.irisshaders.iris.shadows.frustum.FrustumHolder;
import net.irisshaders.iris.shadows.frustum.advanced.AdvancedShadowCullingFrustum;
import net.irisshaders.iris.shadows.frustum.advanced.SafeZoneCullingFrustum;
import net.irisshaders.iris.shadows.frustum.fallback.BoxCullingFrustum;
import net.irisshaders.iris.shadows.frustum.fallback.NonCullingFrustum;
import net.irisshaders.iris.uniforms.CameraUniforms;
import net.irisshaders.iris.uniforms.CapturedRenderingState;
import net.irisshaders.iris.uniforms.CelestialUniforms;
import net.irisshaders.iris.uniforms.custom.CustomUniforms;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.client.render.Culler;
import net.minecraft.client.render.block.BlockLayer;
import net.minecraft.client.render.block.entity.BlockEntityRenderDispatcher;
import net.minecraft.client.render.entity.EntityRenderDispatcher;
import net.minecraft.client.render.platform.GlStateManager;
import net.minecraft.client.render.platform.Lighting;
import net.minecraft.client.render.texture.TextureAtlas;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.entity.Entity;
import net.minecraft.entity.living.player.PlayerEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.profiler.Profiler;
import org.joml.Matrix4f;
import org.joml.Vector3d;
import org.joml.Vector3f;
import org.joml.Vector4f;
import org.lwjgl.opengl.GL11;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.system.MemoryUtil;
import yt.ren.astatine.render.GL;
import yt.ren.astatine.vulkan.MainTarget;
import yt.ren.astatine.vulkan.ShadowTerrain;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalDouble;

public class ShadowRenderer {
	public static int RESOLUTION;
	public static boolean ACTIVE = false;
	public static List<BlockEntity> visibleBlockEntities;
	public static int renderDistance;
	public static Matrix4f MODELVIEW;
	public static Matrix4f PROJECTION;

	public static Culler FRUSTUM;
	private final float halfPlaneLength;
	private final float nearPlane;
	private final float farPlane;
	private final float voxelDistance;
	private final float renderDistanceMultiplier;
	private final float entityShadowDistanceMultiplier;
	private final int resolution;
	private final float intervalSize;
	private final Float fov;
	private final ShadowRenderTargets targets;
	private final ShadowCullState packCullingState;
	private final ShadowCompositeRenderer compositeRenderer;
	private final boolean shouldRenderTerrain;
	private final boolean shouldRenderTranslucent;
	private final boolean shouldRenderEntities;
	private final boolean shouldRenderPlayer;
	private final boolean shouldRenderBlockEntities;
	private final boolean shouldRenderDH;
	private final float sunPathRotation;
	private final String debugStringOverall;
	private final boolean separateHardwareSamplers;
	private final boolean shouldRenderLightBlockEntities;
	private final IrisRenderingPipeline pipeline;
	private boolean packHasVoxelization;
	private FrustumHolder terrainFrustumHolder;
	private FrustumHolder entityFrustumHolder;
	private String debugStringTerrain = "(unavailable)";
	private int renderedShadowTerrain = 0;
	private int renderedShadowEntities = 0;
	private int renderedShadowBlockEntities = 0;

	public ShadowRenderer(IrisRenderingPipeline pipeline, ProgramSource shadow, PackDirectives directives,
		ShadowRenderTargets shadowRenderTargets, ShadowCompositeRenderer compositeRenderer, CustomUniforms customUniforms,
		boolean separateHardwareSamplers) {

		this.pipeline = pipeline;
		this.separateHardwareSamplers = separateHardwareSamplers;

		final PackShadowDirectives shadowDirectives = directives.getShadowDirectives();

		this.halfPlaneLength = shadowDirectives.getDistance();
		this.nearPlane = shadowDirectives.getNearPlane();
		this.farPlane = shadowDirectives.getFarPlane();

		this.voxelDistance = shadowDirectives.getVoxelDistance();
		this.renderDistanceMultiplier = shadowDirectives.getDistanceRenderMul();
		this.entityShadowDistanceMultiplier = shadowDirectives.getEntityShadowDistanceMul();
		this.resolution = shadowDirectives.getResolution();
		this.intervalSize = shadowDirectives.getIntervalSize();
		this.shouldRenderTerrain = shadowDirectives.shouldRenderTerrain();
		this.shouldRenderTranslucent = shadowDirectives.shouldRenderTranslucent();
		this.shouldRenderEntities = shadowDirectives.shouldRenderEntities();
		this.shouldRenderPlayer = shadowDirectives.shouldRenderPlayer();
		this.shouldRenderBlockEntities = shadowDirectives.shouldRenderBlockEntities();
		this.shouldRenderLightBlockEntities = shadowDirectives.shouldRenderLightBlockEntities();
		this.shouldRenderDH = shadowDirectives.isDhShadowEnabled().orElse(false);

		this.compositeRenderer = compositeRenderer;

		debugStringOverall = "half plane = " + halfPlaneLength + " meters @ " + resolution + "x" + resolution;

		this.terrainFrustumHolder = new FrustumHolder();
		this.entityFrustumHolder = new FrustumHolder();

		this.fov = shadowDirectives.getFov();
		this.targets = shadowRenderTargets;

		if (shadow != null) {
			// Assume that the shader pack is doing voxelization if a geometry shader is detected.
			// Also assume voxelization if image load / store is detected.
			this.packHasVoxelization = shadow.getGeometrySource().isPresent();
			this.packCullingState = shadowDirectives.getCullingState();
		} else {
			this.packHasVoxelization = false;
			this.packCullingState = ShadowCullState.DEFAULT;
		}

		this.sunPathRotation = directives.getSunPathRotation();
	}

	public static Matrix4f createShadowModelView(float sunPathRotation, float intervalSize, float nearPlane, float farPlane) {
		// Determine the camera position
		Vector3d cameraPos = CameraUniforms.getUnshiftedCameraPosition();

		double cameraX = cameraPos.x;
		double cameraY = cameraPos.y;
		double cameraZ = cameraPos.z;

		// Set up our modelview matrix stack
		Matrix4f modelView = new Matrix4f();
		ShadowMatrices.createModelViewMatrix(modelView, getShadowAngle(), intervalSize, sunPathRotation, cameraX, cameraY, cameraZ,
			nearPlane, farPlane);

		return modelView;
	}

	private static ClientWorld getLevel() {
		return Objects.requireNonNull(Minecraft.getInstance().world);
	}

	public static float getSunAngle(boolean sun) {
		float skyAngle = getLevel().getTimeOfDay(CapturedRenderingState.INSTANCE.getTickDelta()) * 360.0f;
		float currentAngle = sun ? skyAngle : skyAngle + 180.0f;

		float c = currentAngle + 90.0f;

		if (c < 0) {
			c += 360;
		} else if (c > 360) {
			c -= 360;
		}

		return c;
	}

	private static float getShadowAngle() {
		float shadowAngle = getSunAngle(CelestialUniforms.isDay());

		return shadowAngle / 360.0f;
	}

	public void setUsesImages(boolean usesImages) {
		this.packHasVoxelization = packHasVoxelization || usesImages;
	}

	private void generateMipmaps() {
		this.targets.generateMipmaps();
	}

	private FrustumHolder createShadowFrustum(float renderMultiplier, FrustumHolder holder) {
		// TODO: Cull entities / block entities with Advanced Frustum Culling even if voxelization is detected.
		String distanceInfo;
		String cullingInfo;
		if ((packCullingState == ShadowCullState.DEFAULT && packHasVoxelization) || packCullingState == ShadowCullState.DISTANCE) {
			double distance = halfPlaneLength * renderMultiplier;

			String reason;

			if (packCullingState == ShadowCullState.DISTANCE) {
				reason = "(set by shader pack)";
			} else /*if (packHasVoxelization)*/ {
				reason = "(voxelization detected)";
			}

			if (distance <= 0 || distance > Minecraft.getInstance().options.viewDistance * 16) {
				distanceInfo = "render distance = " + Minecraft.getInstance().options.viewDistance * 16 + " blocks ";
				distanceInfo += Minecraft.getInstance()
					.isIntegratedServerRunning() ? "(capped by normal render distance)" : "(capped by normal/server render distance)";
				cullingInfo = "disabled " + reason;
				return holder.setInfo(new NonCullingFrustum(), distanceInfo, cullingInfo);
			} else {
				distanceInfo = distance + " blocks (set by shader pack)";
				cullingInfo = "distance only " + reason;
				BoxCuller boxCuller = new BoxCuller(distance);
				holder.setInfo(new BoxCullingFrustum(boxCuller), distanceInfo, cullingInfo);
			}
		} else {
			BoxCuller boxCuller;

			boolean hasSafeZone = packCullingState == ShadowCullState.SAFE_ZONE;

			// Assume render multiplier is meant to be 1 if safe zone culling is on
			if (hasSafeZone && renderMultiplier < 0) {
				renderMultiplier = 1.0f;
			}

			double distance = (hasSafeZone ? voxelDistance : halfPlaneLength) * renderMultiplier;
			String setter = "(set by shader pack)";

			if (renderMultiplier < 0) {
				distance = IrisVideoSettings.shadowDistance * 16;
				setter = "(set by user)";
			}

			if (distance >= Minecraft.getInstance().options.viewDistance * 16 && !hasSafeZone) {
				distanceInfo = "render distance = " + Minecraft.getInstance().options.viewDistance * 16 + " blocks ";
				distanceInfo += Minecraft.getInstance()
					.isIntegratedServerRunning() ? "(capped by normal render distance)" : "(capped by normal/server render distance)";
				boxCuller = null;
			} else {
				distanceInfo = distance + " blocks " + setter;

				if (distance == 0.0 && !hasSafeZone) {
					cullingInfo = "no shadows rendered";
					holder.setInfo(new CullEverythingFrustum(), distanceInfo, cullingInfo);
				}

				boxCuller = new BoxCuller(distance);
			}

			cullingInfo = (hasSafeZone ? "Safe Zone" : "Advanced") + " Frustum Culling enabled";

			Vector4f shadowLightPosition = new CelestialUniforms(sunPathRotation).getShadowLightPositionInWorldSpace();

			Vector3f shadowLightVectorFromOrigin = new Vector3f(shadowLightPosition.x(), shadowLightPosition.y(), shadowLightPosition.z());

			shadowLightVectorFromOrigin.normalize();

			Matrix4f projView = ((shouldRenderDH && DHCompat.hasRenderingEnabled()) ? DHCompat.getProjection() : CapturedRenderingState.INSTANCE.getGbufferProjection()).mul(
				CapturedRenderingState.INSTANCE.getGbufferModelView(), new Matrix4f());

			if (hasSafeZone) {
				return holder.setInfo(new SafeZoneCullingFrustum(projView, PROJECTION, shadowLightVectorFromOrigin, boxCuller,
					new BoxCuller(halfPlaneLength * renderMultiplier)), distanceInfo, cullingInfo);
			} else {
				return holder.setInfo(new AdvancedShadowCullingFrustum(projView, PROJECTION, shadowLightVectorFromOrigin, boxCuller),
					distanceInfo, cullingInfo);
			}
		}

		return holder;
	}

	public void renderShadows(float tickDelta) {
		if (IrisVideoSettings.getOverriddenShadowDistance(IrisVideoSettings.shadowDistance) == 0) {
			return;
		}

		Minecraft client = Minecraft.getInstance();

		Profiler profiler = client.profiler;

		profiler.swap("shadows");
		ACTIVE = true;
		RESOLUTION = resolution;

		renderDistance = (int) ((halfPlaneLength * renderDistanceMultiplier) / 16);

		if (renderDistanceMultiplier < 0) {
			renderDistance = IrisVideoSettings.shadowDistance;
		}


		visibleBlockEntities = new ArrayList<>();

		// Create our camera
		Matrix4f modelView = createShadowModelView(this.sunPathRotation, this.intervalSize, nearPlane, farPlane);
		MODELVIEW = new Matrix4f(modelView);

		GL.matrixMode(GL11.GL_MODELVIEW);
		GL.pushMatrix();
		loadMatrix(MODELVIEW);

		// Set up our orthographic projection matrix and load it into RenderSystem
		Matrix4f shadowProjection;
		if (this.fov != null) {
			// If FOV is not null, the pack wants a perspective based projection matrix. (This is to support legacy packs)
			shadowProjection = ShadowMatrices.createPerspectiveMatrix(this.fov, false);
		} else {
			shadowProjection = ShadowMatrices.createOrthoMatrix(halfPlaneLength,
				nearPlane == -1.0f ? -DHCompat.getRenderDistance() * 16 : nearPlane,
				farPlane == -1.0f ? DHCompat.getRenderDistance() * 16 : farPlane, false);
		}

		IrisRenderSystem.setShadowProjection(shadowProjection);

		PROJECTION = shadowProjection;

		profiler.push("terrain_setup");

		profiler.push("initialize frustum");

		terrainFrustumHolder = createShadowFrustum(renderDistanceMultiplier, terrainFrustumHolder);

		FRUSTUM = terrainFrustumHolder.getFrustum();

		// Determine the player camera position
		Vector3d cameraPos = CameraUniforms.getUnshiftedCameraPosition();

		double cameraX = cameraPos.x();
		double cameraY = cameraPos.y();
		double cameraZ = cameraPos.z();

		// Center the frustum on the player camera position
		terrainFrustumHolder.getFrustum().prepare(cameraX, cameraY, cameraZ);

		profiler.pop();

		ShadowTerrain.begin(MODELVIEW, PROJECTION, terrainFrustumHolder.getFrustum(), cameraX, cameraY, cameraZ);

		try {
			profiler.swap("terrain");

			// Disable backface culling
			// This partially works around an issue where if the front face of a mountain isn't visible, it casts no
			// shadow.
			//
			// However, it only partially resolves issues of light leaking into caves.
			//
			// TODO: Better way of preventing light from leaking into places where it shouldn't

			GL.pushAttributes();
			GL.enable(GL11.GL_DEPTH_TEST);
			GL.disable(GL11.GL_BLEND);
			GL.disable(GL11.GL_CULL_FACE);
			GL.alphaFunc(GL11.GL_GREATER, 0.1f);
			GlStateManager.depthMask(true);
			client.getTextureManager().bind(TextureAtlas.BLOCKS_LOCATION);
			client.gameRenderer.enableLightMap();
			renderedShadowTerrain = 0;

			// Render all opaque terrain unless pack requests not to
			if (shouldRenderTerrain) {
				pipeline.setPhase(WorldRenderingPhase.TERRAIN_SOLID);
				try (RenderPass renderPass = RenderSystem.getDevice().createCommandEncoder()
					.createRenderPass(() -> "Terrain", MainTarget.get().getColorTextureView(), Optional.empty(),
						MainTarget.get().getDepthTextureView(), OptionalDouble.empty())) {
					GL.disable(GL11.GL_ALPHA_TEST);
					renderedShadowTerrain += ShadowTerrain.draw(BlockLayer.SOLID);
					GL.enable(GL11.GL_ALPHA_TEST);
					pipeline.setPhase(WorldRenderingPhase.TERRAIN_CUTOUT_MIPPED);
					renderedShadowTerrain += ShadowTerrain.draw(BlockLayer.CUTOUT_MIPPED);
					pipeline.setPhase(WorldRenderingPhase.TERRAIN_CUTOUT);
					renderedShadowTerrain += ShadowTerrain.draw(BlockLayer.CUTOUT);
				}
				pipeline.setPhase(WorldRenderingPhase.NONE);

			}
			if (!ShadowRenderCallbacks.isEmpty()) {
				profiler.swap("iris_shadow_callbacks");
				pipeline.setPhase(WorldRenderingPhase.TERRAIN_CUTOUT);
				ShadowRenderCallbacks.invoke(MODELVIEW, PROJECTION, cameraX, cameraY, cameraZ,
					CapturedRenderingState.INSTANCE.getTickDelta());
				pipeline.setPhase(WorldRenderingPhase.NONE);
			}

			pipeline.setPhase(WorldRenderingPhase.ENTITIES);

			// Reset our viewport in case Sodium overrode it

			profiler.swap("entities");

			// Create a constrained shadow frustum for entities to avoid rendering faraway entities in the shadow pass,
			// if the shader pack has requested it. Otherwise, use the same frustum as for terrain.
			boolean hasEntityFrustum = false;

			if (entityShadowDistanceMultiplier == 1.0F || entityShadowDistanceMultiplier < 0.0F) {
				entityFrustumHolder.setInfo(terrainFrustumHolder.getFrustum(), terrainFrustumHolder.getDistanceInfo(),
					terrainFrustumHolder.getCullingInfo());
			} else {
				hasEntityFrustum = true;
				entityFrustumHolder = createShadowFrustum(renderDistanceMultiplier * entityShadowDistanceMultiplier, entityFrustumHolder);
			}

			Culler entityShadowFrustum = entityFrustumHolder.getFrustum();
			entityShadowFrustum.prepare(cameraX, cameraY, cameraZ);

			// Render nearby entities
			//
			// Note: We must use a separate BuilderBufferStorage object here, or else very weird things will happen during
			// rendering.

			try (RenderPass renderPass = RenderSystem.getDevice().createCommandEncoder()
				.createRenderPass(() -> "Terrain", MainTarget.get().getColorTextureView(), Optional.empty(),
					MainTarget.get().getDepthTextureView(), OptionalDouble.empty())) {
				renderedShadowEntities = 0;
				renderedShadowBlockEntities = 0;
				renderEntities(client, tickDelta, entityShadowFrustum, cameraX, cameraY, cameraZ);

				profiler.swap("build blockentities");

				if (shouldRenderBlockEntities || shouldRenderLightBlockEntities) {
					renderedShadowBlockEntities = renderBlockEntities(client, tickDelta, terrainFrustumHolder.getFrustum(),
						!shouldRenderBlockEntities && shouldRenderLightBlockEntities);
				}
			}

			if (this.shouldRenderDH) {
				DHCompat.renderLods(false);
			}
			copyPreTranslucentDepth();

			profiler.swap("translucent terrain");
			pipeline.setPhase(WorldRenderingPhase.NONE);

			// TODO: Prevent these calls from scheduling translucent sorting...
			// It doesn't matter a ton, since this just means that they won't be sorted in the normal rendering pass.
			// Just something to watch out for, however...
			if (shouldRenderTranslucent) {
				pipeline.setPhase(WorldRenderingPhase.TERRAIN_TRANSLUCENT);
				try (RenderPass renderPass = RenderSystem.getDevice().createCommandEncoder()
					.createRenderPass(() -> "Terrain", MainTarget.get().getColorTextureView(), Optional.empty(),
						MainTarget.get().getDepthTextureView(), OptionalDouble.empty())) {
					GL.enable(GL11.GL_BLEND);
					renderedShadowTerrain += ShadowTerrain.draw(BlockLayer.TRANSLUCENT);
				}
				if (this.shouldRenderDH) {
					DHCompat.renderLods(true);
				}
				pipeline.setPhase(WorldRenderingPhase.NONE);
			}

			client.gameRenderer.disableLightMap();
			GL.popAttributes();
		} finally {
			ShadowTerrain.end();
		}

		IrisRenderSystem.restorePlayerProjection();

		debugStringTerrain = renderedShadowTerrain + " sections";

		profiler.swap("generate mipmaps");

		generateMipmaps();

		profiler.swap("restore gl state");

		// Restore the old viewport

		pipeline.removePhaseIfNeeded();

		GLDebug.pushGroup(901, "shadowcomp");
		compositeRenderer.renderAll();
		GLDebug.popGroup();

		visibleBlockEntities = null;
		ACTIVE = false;

		GL.matrixMode(GL11.GL_MODELVIEW);
		GL.popMatrix();

		profiler.pop();
		profiler.swap("updatechunks");
	}

	private static void loadMatrix(Matrix4f matrix) {
		GL.loadIdentity();

		try (var stack = MemoryStack.stackPush()) {
			var values = stack.mallocFloat(16);
			matrix.get(values);
			GL.multMatrix(MemoryUtil.memAddress(values));
		}
	}

	private int renderBlockEntities(Minecraft client, float tickDelta, Culler frustum, boolean lightsOnly) {
		pipeline.setPhase(WorldRenderingPhase.BLOCK_ENTITIES);
		int i = 0;

		for (BlockEntity blockEntity : client.world.blockEntities) {
			if (lightsOnly && blockEntity.getBlock().getLight() == 0) {
				continue;
			}

			BlockPos pos = blockEntity.getPos();

			if (frustum.isVisible(new Box(pos.getX(), pos.getY(), pos.getZ(), pos.getX() + 1, pos.getY() + 1, pos.getZ() + 1))) {
				visibleBlockEntities.add(blockEntity);
				BlockEntityRenderDispatcher.INSTANCE.render(blockEntity, tickDelta, -1);
				i++;
			}
		}

		pipeline.setPhase(WorldRenderingPhase.NONE);
		Lighting.turnOff();
		client.getTextureManager().bind(TextureAtlas.BLOCKS_LOCATION);

		return i;
	}

	private void renderEntities(Minecraft client, float tickDelta, Culler frustum, double cameraX, double cameraY, double cameraZ) {
		Entity cameraEntity = client.getCamera();

		if (cameraEntity == null) {
			return;
		}

		EntityRenderDispatcher dispatcher = client.getEntityRenderDispatcher();
		dispatcher.prepare(client.world, client.textRenderer, cameraEntity, client.targetEntity, client.options, tickDelta);
		dispatcher.setCameraPos(cameraX, cameraY, cameraZ);
		BlockEntityRenderDispatcher.INSTANCE.prepare(client.world, client.getTextureManager(), client.textRenderer, cameraEntity, tickDelta);
		BlockEntityRenderDispatcher.offsetX = cameraX;
		BlockEntityRenderDispatcher.offsetY = cameraY;
		BlockEntityRenderDispatcher.offsetZ = cameraZ;
		Lighting.turnOn();

		if (!shouldRenderEntities && !shouldRenderPlayer) {
			return;
		}

		client.profiler.push("cull");
		List<Entity> visible = new ArrayList<>();

		if (shouldRenderEntities) {
			for (Entity entity : client.world.entities) {
				if (entity instanceof PlayerEntity player && player.isSpectator()) {
					continue;
				}

				if (dispatcher.shouldRender(entity, frustum, cameraX, cameraY, cameraZ) || entity.rider == client.player) {
					visible.add(entity);
				}
			}

			visible.sort(Comparator.comparing(entity -> entity.getClass().getName()));
		} else {
			PlayerEntity player = client.player;

			if (player != null && !player.isSpectator() && !player.isInvisible()) {
				visible.add(player);
			}

			if (player != null && player.vehicle != null) {
				visible.add(player.vehicle);
			}
		}

		client.profiler.swap("build geometry");

		for (Entity entity : visible) {
			dispatcher.render(entity, tickDelta);
		}

		renderedShadowEntities = visible.size();
		client.profiler.pop();
	}

	private void copyPreTranslucentDepth() {
		Minecraft.getInstance().profiler.swap("translucent depth copy");

		targets.copyPreTranslucentDepth();
	}

	public void addDebugText(List<String> messages) {
		if (IrisVideoSettings.getOverriddenShadowDistance(IrisVideoSettings.shadowDistance) == 0) {
			messages.add("[" + Iris.MODNAME + "] Shadow Maps: off, shadow distance 0");
			return;
		}

		if (Iris.getIrisConfig().areDebugOptionsEnabled()) {
			messages.add("[" + Iris.MODNAME + "] Shadow Maps: " + debugStringOverall);
			messages.add(
				"[" + Iris.MODNAME + "] Shadow Distance Terrain: " + terrainFrustumHolder.getDistanceInfo() + " Entity: " + entityFrustumHolder.getDistanceInfo());
			messages.add(
				"[" + Iris.MODNAME + "] Shadow Culling Terrain: " + terrainFrustumHolder.getCullingInfo() + " Entity: " + entityFrustumHolder.getCullingInfo());
			messages.add("[" + Iris.MODNAME + "] Shadow Projection: " + getProjectionInfo());
			messages.add(
				"[" + Iris.MODNAME + "] Shadow Terrain: " + debugStringTerrain + (shouldRenderTerrain ? "" : " (no terrain) ") + (shouldRenderTranslucent ? "" : "(no translucent)"));
			messages.add("[" + Iris.MODNAME + "] Shadow Entities: " + getEntitiesDebugString());
			messages.add("[" + Iris.MODNAME + "] Shadow Block Entities: " + getBlockEntitiesDebugString());
		} else {
			messages.add("[" + Iris.MODNAME + "] Shadow info: " + debugStringTerrain);
			messages.add("[" + Iris.MODNAME + "] E: " + renderedShadowEntities);
			messages.add("[" + Iris.MODNAME + "] BE: " + renderedShadowBlockEntities);
		}
	}

	private String getProjectionInfo() {
		return "Near: " + nearPlane + " Far: " + farPlane + " distance " + halfPlaneLength;
	}

	private String getEntitiesDebugString() {
		return (shouldRenderEntities || shouldRenderPlayer) ? (renderedShadowEntities + "/" + (Minecraft.getInstance().world == null ? 0 : Minecraft.getInstance().world.entities.size())) : "disabled by pack";
	}

	private String getBlockEntitiesDebugString() {
		return (shouldRenderBlockEntities || shouldRenderLightBlockEntities) ? renderedShadowBlockEntities + "" : "disabled by pack"; // TODO: + "/" + MinecraftClient.getInstance().world.blockEntities.size();
	}

	public void destroy() {
	}

	private record MipmapPass(int texture, int targetFilteringMode) {


	}
}
