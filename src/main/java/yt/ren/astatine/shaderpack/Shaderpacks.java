package yt.ren.astatine.shaderpack;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.renderpearl.api.commands.RenderPass;
import it.unimi.dsi.fastutil.objects.Object2IntMap;
import net.irisshaders.iris.Iris;
import net.irisshaders.iris.gui.option.IrisVideoSettings;
import net.irisshaders.iris.pathways.HandRenderer;
import net.irisshaders.iris.pbr.format.TextureFormatLoader;
import net.irisshaders.iris.pipeline.IrisPipelines;
import net.irisshaders.iris.pipeline.IrisRenderingPipeline;
import net.irisshaders.iris.pipeline.WorldRenderingPhase;
import net.irisshaders.iris.pipeline.WorldRenderingPipeline;
import net.irisshaders.iris.shaderpack.loading.ProgramId;
import net.irisshaders.iris.shaderpack.materialmap.NamespacedId;
import net.irisshaders.iris.shaderpack.materialmap.WorldRenderingSettings;
import net.irisshaders.iris.shaderpack.properties.ParticleRenderingSettings;
import net.irisshaders.iris.shadows.ShadowRenderingState;
import net.irisshaders.iris.uniforms.CapturedRenderingState;
import net.irisshaders.iris.uniforms.IrisTimeUniforms;
import net.irisshaders.iris.uniforms.SystemTimeUniforms;
import net.minecraft.block.Block;
import net.minecraft.block.state.BlockState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resource.manager.ReloadableResourceManager;
import net.minecraft.client.resource.manager.ResourceManager;
import org.joml.Matrix4f;
import yt.ren.astatine.Native;
import yt.ren.astatine.memory.Memory;
import yt.ren.astatine.render.GLState;
import yt.ren.astatine.backend.MainTarget;
import yt.ren.astatine.iris.IrisPlatform;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.OptionalDouble;
import java.util.OptionalInt;

import static org.lwjgl.opengl.GL11.GL_LINEAR;

public class Shaderpacks {
	private static final int MODELVIEW_MATRIX = 0x0BA6;
	private static final int PROJECTION_MATRIX = 0x0BA7;
	public static final float HAND_DEPTH = HandRenderer.DEPTH;

	private static boolean initialized;
	private static boolean world;
	private static boolean threaded = true;
	private static NamespacedId loadedDimension;
	private static final float[] matrix = new float[16];
	private static ByteBuffer terrainBlockIds;
	private static Object2IntMap<BlockState> blockIdSource;
	private static RenderPass worldPass;
	private static int fogMode = GL_LINEAR;
	private static float fogDensity = 1.0f;

	private Shaderpacks() {
	}

	public static void load() {
		if (!initialized) {
			IrisPlatform.set(new ShaderpackPlatform());
			Iris.onEarlyInitialize();
			Iris.initializeRenderBackend();
			initialized = true;
			ResourceManager resources = Minecraft.getInstance().getResourceManager();
			TextureFormatLoader.reload(resources);

			if (resources instanceof ReloadableResourceManager reloadable) {
				reloadable.addListener(TextureFormatLoader::reload);
			}
		}

		Iris.loadShaderpack();
		apply();
	}

	public static void onReloaded() {
		if (initialized) {
			apply();
		}
	}

	public static void onDimensionChanged() {
		NamespacedId dimension = Iris.getCurrentDimension();

		if (dimension != null && !dimension.equals(loadedDimension)) {
			apply();
		}
	}

	private static void apply() {
		NamespacedId dimension = Iris.getCurrentDimension();
		boolean wasActive = isActive();
		if (dimension != null && Minecraft.getInstance().world != null) {
			renderThread(Iris.getCurrentPack().isEmpty());
			loadedDimension = dimension;
			Iris.getPipelineManager().preparePipeline(dimension);
		}

		boolean active = isActive();
		renderThread(!active);
		PbrTextures.clear();

		if (active) {
			PbrTextures.loadAll();
		}

		terrainBlockIds = null;
		blockIdSource = null;
		reloadTerrain(wasActive || active);
	}

	private static void renderThread(boolean enabled) {
		if (threaded != enabled) {
			threaded = enabled;
			Native.renderThread(enabled);
		}
	}

	private static void reloadTerrain(boolean changed) {
		Minecraft minecraft = Minecraft.getInstance();

		if (changed && minecraft.world != null && minecraft.worldRenderer != null) {
			minecraft.worldRenderer.reload();
		}
	}

	private static WorldRenderingPipeline pipeline() {
		return Iris.getPipelineManager().getPipelineNullable();
	}

	private static IrisRenderingPipeline iris() {
		return pipeline() instanceof IrisRenderingPipeline iris ? iris : null;
	}

	public static long terrainBlockIds() {
		if (iris() == null) {
			return 0L;
		}

		Object2IntMap<BlockState> ids = WorldRenderingSettings.INSTANCE.getBlockStateIds();

		if (ids == null) {
			return 0L;
		}

		if (ids != blockIdSource || terrainBlockIds == null) {
			blockIdSource = ids;
			var table = new short[1 << 16];
			Arrays.fill(table, (short) -1);

			for (int stateId = 0; stateId < table.length; stateId++) {
				BlockState state = Block.STATE_REGISTRY.get(stateId);

				if (state != null) {
					table[stateId] = (short) ids.getInt(state);
				}
			}

			terrainBlockIds = ByteBuffer.allocateDirect(table.length * 2).order(ByteOrder.nativeOrder());
			terrainBlockIds.asShortBuffer().put(table);
		}

		return Memory.INSTANCE.address(terrainBlockIds);
	}

	public static boolean particlesAfterTranslucent() {
		var pipeline = pipeline();
		return pipeline != null && iris() != null && pipeline.getParticleRenderingSettings() != ParticleRenderingSettings.BEFORE;
	}

	public static boolean shouldRenderSun() {
		var pipeline = pipeline();
		return pipeline == null || pipeline.shouldRenderSun();
	}

	public static boolean shouldRenderMoon() {
		var pipeline = pipeline();
		return pipeline == null || pipeline.shouldRenderMoon();
	}

	public static boolean shouldRenderStars() {
		var pipeline = pipeline();
		return pipeline == null || pipeline.shouldRenderStars();
	}

	public static boolean shouldRenderSkyDisc() {
		var pipeline = pipeline();
		return pipeline == null || pipeline.shouldRenderSkyDisc();
	}

	public static boolean shouldRenderWeather() {
		var pipeline = pipeline();
		return pipeline == null || pipeline.shouldRenderWeather();
	}

	public static boolean shouldRenderWeatherParticles() {
		var pipeline = pipeline();
		return pipeline == null || pipeline.shouldRenderWeatherParticles();
	}

	public static boolean shouldRenderVignette() {
		var pipeline = pipeline();
		return pipeline == null || pipeline.shouldRenderVignette();
	}

	public static boolean shouldRenderUnderwaterOverlay() {
		var pipeline = pipeline();
		return pipeline == null || pipeline.shouldRenderUnderwaterOverlay();
	}

	public static boolean shouldWriteRainAndSnowToDepthBuffer() {
		var pipeline = pipeline();
		return pipeline != null && pipeline.shouldWriteRainAndSnowToDepthBuffer();
	}

	public static boolean skipAllRendering() {
		var iris = iris();
		return world && iris != null && iris.skipAllRendering();
	}

	public static int cloudRenderMode(int mode) {
		var pipeline = pipeline();

		if (iris() == null) {
			return mode;
		}

		return switch (pipeline.getCloudSetting()) {
			case OFF -> 0;
			case FAST -> 1;
			case FANCY -> 2;
			case DEFAULT -> mode;
		};
	}

	public static boolean frustumCulling() {
		var pipeline = pipeline();
		return pipeline == null || !pipeline.shouldDisableFrustumCulling();
	}

	public static boolean occlusionCulling() {
		var pipeline = pipeline();
		return pipeline == null || !pipeline.shouldDisableOcclusionCulling();
	}

	public static float ambientOcclusionLevel() {
		return iris() != null ? WorldRenderingSettings.INSTANCE.getAmbientOcclusionLevel() : 1.0f;
	}

	public static boolean directionalShading() {
		var pipeline = pipeline();
		return pipeline == null || !pipeline.shouldDisableDirectionalShading();
	}

	public static boolean separateAo() {
		return iris() != null && WorldRenderingSettings.INSTANCE.shouldUseSeparateAo();
	}

	public static boolean isRenderingShadows() {
		return ShadowRenderingState.areShadowsCurrentlyBeingRendered();
	}

	public static float sunPathRotation() {
		var pipeline = pipeline();
		return pipeline != null ? pipeline.getSunPathRotation() : 0.0f;
	}

	public static boolean isInitialized() {
		return initialized;
	}

	public static boolean isActive() {
		return initialized && iris() != null;
	}

	public static boolean isWorldActive() {
		return world;
	}

	public static WorldRenderingPhase currentPhase() {
		var pipeline = pipeline();
		return pipeline == null ? WorldRenderingPhase.NONE : pipeline.getPhase();
	}

	public static void fogMode(int mode) {
		fogMode = mode;
		CapturedRenderingState.INSTANCE.setFogDensity(mode == GL_LINEAR ? -1.0f : fogDensity);
	}

	public static void fogDensity(float density) {
		fogDensity = density;
		CapturedRenderingState.INSTANCE.setFogDensity(fogMode == GL_LINEAR ? -1.0f : density);
	}

	public static void beginFrame() {
		SystemTimeUniforms.COUNTER.beginFrame();
		SystemTimeUniforms.TIMER.beginFrame(System.nanoTime());
	}

	public static void beginWorld(long framebuffer, float red, float green, float blue) {
		if (!isActive() || world || framebuffer == 0L) {
			return;
		}

		world = true;
		CapturedRenderingState.INSTANCE.setFogColor(red, green, blue);
		DrawIds.reset();
	}

	public static void captureCamera(float tickDelta, float cloudTime) {
		var iris = iris();

		if (!world || iris == null) {
			return;
		}

		GLState.copyMatrix(MODELVIEW_MATRIX, matrix);
		CapturedRenderingState.INSTANCE.setGbufferModelView(new Matrix4f().set(matrix));
		GLState.copyMatrix(PROJECTION_MATRIX, matrix);
		CapturedRenderingState.INSTANCE.setGbufferProjection(new Matrix4f().set(matrix));
		CapturedRenderingState.INSTANCE.setTickDelta(tickDelta);
		CapturedRenderingState.INSTANCE.setCloudTime(cloudTime);
		IrisTimeUniforms.updateTime();

		iris.setIsMainBound(true);
		RenderSystem.getDevice().createCommandEncoder().clearDepthTexture(MainTarget.get().getDepthTexture(), 1.0);
		iris.beginLevelRendering();
		iris.renderShadows(tickDelta);
		openWorldPass();
	}

	private static void openWorldPass() {
		if (worldPass == null && world) {
			var main = MainTarget.get();
			worldPass = RenderSystem.getDevice().createCommandEncoder()
				.createRenderPass(() -> "Terrain", main.getColorTextureView(), Optional.empty(), main.getDepthTextureView(), OptionalDouble.empty());
		}
	}

	private static void closeWorldPass() {
		if (worldPass != null) {
			worldPass.close();
			worldPass = null;
		}
	}

	public static void addDebugText(List<String> lines) {
		lines.add("");
		lines.add("[" + Iris.MODNAME + "] Version: " + Iris.getVersion());

		if (!Iris.getIrisConfig().areShadersEnabled()) {
			lines.add("[" + Iris.MODNAME + "] Shaders are disabled");
			return;
		}

		lines.add("[" + Iris.MODNAME + "] Shaderpack: " + Iris.getCurrentPackName() + (Iris.isFallback() ? " (fallback)" : ""));
		Iris.getCurrentPack().ifPresent(pack -> lines.add("[" + Iris.MODNAME + "] " + pack.getProfileInfo()));
		lines.add("[" + Iris.MODNAME + "] Color space: " + IrisVideoSettings.colorSpace().name());

		var pipeline = pipeline();

		if (pipeline != null) {
			pipeline.addDebugText(lines);
		}
	}

	public static void addGameDebugText(List<String> lines) {
		lines.add("");

		var pipeline = pipeline();

		if (pipeline != null) {
			pipeline.addDebugText(lines);
		}
	}

	public static OptionalInt forcedShadowRenderDistanceChunks() {
		var pipeline = pipeline();
		return pipeline == null ? OptionalInt.empty() : pipeline.getForcedShadowRenderDistanceChunksForDisplay();
	}

	public static void phase(WorldRenderingPhase phase) {
		var pipeline = pipeline();

		if (world && pipeline != null) {
			pipeline.setPhase(phase);
		}
	}

	public static void beginSpecial(ProgramId program) {
		IrisPipelines.setSpecial(program);
	}

	public static void endSpecial() {
		IrisPipelines.setSpecial(null);
	}

	public static void beginTranslucent() {
		var pipeline = pipeline();

		if (world && pipeline != null) {
			closeWorldPass();
			pipeline.beginTranslucents();
			openWorldPass();
		}
	}

	public static void renderHorizon() {
		var pipeline = pipeline();

		if (world && pipeline != null && Minecraft.getInstance().world != null) {
			pipeline.onBeginClear();
		}
	}

	public static void beginHand() {
		var pipeline = pipeline();

		if (world && pipeline != null) {
			closeWorldPass();
			pipeline.beginHand();
		}
	}

	public static void renderSolidHand(float tickDelta, Runnable renderHand) {
		var pipeline = pipeline();

		if (world && pipeline != null) {
			HandRenderer.getInstance().renderSolid(tickDelta, renderHand, pipeline);
		}
	}

	public static void renderTranslucentHand(float tickDelta, Runnable renderHand) {
		var pipeline = pipeline();

		if (world && pipeline != null) {
			HandRenderer.getInstance().renderTranslucent(tickDelta, renderHand, pipeline);
		}
	}

	public static boolean isHoldingTranslucent() {
		Minecraft minecraft = Minecraft.getInstance();
		return HandRenderer.getInstance().isHandTranslucent(minecraft.player == null ? null : minecraft.player.getItemInHand());
	}

	public static void endWorld() {
		var pipeline = pipeline();

		if (!world) {
			return;
		}

		closeWorldPass();

		if (pipeline != null) {
			pipeline.setPhase(WorldRenderingPhase.NONE);
			MainTarget.beginAccess();
			pipeline.finalizeLevelRendering();
			pipeline.finalizeGameRendering();
			MainTarget.endAccess();
		}

		DrawIds.reset();
		world = false;
	}
}
