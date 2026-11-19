package net.irisshaders.iris.pipeline;

import com.mojang.renderpearl.api.pipeline.RenderPipeline;
import it.unimi.dsi.fastutil.Function;
import it.unimi.dsi.fastutil.objects.Object2ObjectArrayMap;
import net.irisshaders.iris.Iris;
import net.irisshaders.iris.pathways.HandRenderer;
import net.irisshaders.iris.pipeline.programs.ShaderKey;
import net.irisshaders.iris.pipeline.programs.ShaderMap;
import net.irisshaders.iris.pipeline.programs.ShaderOverrides;
import net.irisshaders.iris.shaderpack.loading.ProgramId;
import net.irisshaders.iris.shadows.ShadowRenderingState;
import org.jetbrains.annotations.Nullable;

import java.util.Map;

public class IrisPipelines {
	public static final String TERRAIN_SOLID = "terrain_solid";
	public static final String TERRAIN_CUTOUT_MIPPED = "terrain_cutout_mipped";
	public static final String TERRAIN_CUTOUT = "terrain_cutout";
	public static final String TERRAIN_TRANSLUCENT = "terrain_translucent";
	public static final String IMMEDIATE = "immediate";
	private static final Map<String, Function<IrisRenderingPipeline, ShaderKey>> coreShaderMap = new Object2ObjectArrayMap<>();
	private static final Map<String, Function<IrisRenderingPipeline, ShaderKey>> coreShaderMapShadow = new Object2ObjectArrayMap<>();
	private static final Function<IrisRenderingPipeline, ShaderKey> FAKE_FUNCTION = p -> null;
	private static ProgramId special;

	static {
		assignToMain(TERRAIN_SOLID, p -> ShaderKey.SODIUM_TERRAIN_SOLID);
		assignToMain(TERRAIN_CUTOUT_MIPPED, p -> ShaderKey.SODIUM_TERRAIN_CUTOUT);
		assignToMain(TERRAIN_CUTOUT, p -> ShaderKey.SODIUM_TERRAIN_CUTOUT);
		assignToMain(TERRAIN_TRANSLUCENT, p -> ShaderKey.SODIUM_TERRAIN_TRANSLUCENT);

		assignToShadow(TERRAIN_SOLID, p -> ShaderKey.SHADOW_SODIUM_TERRAIN_SOLID);
		assignToShadow(TERRAIN_CUTOUT_MIPPED, p -> ShaderKey.SHADOW_SODIUM_TERRAIN_CUTOUT);
		assignToShadow(TERRAIN_CUTOUT, p -> ShaderKey.SHADOW_SODIUM_TERRAIN_CUTOUT);
		assignToShadow(TERRAIN_TRANSLUCENT, p -> ShaderKey.SHADOW_SODIUM_TERRAIN_TRANSLUCENT);
	}

	public static void setSpecial(ProgramId program) {
		special = program;
	}

	private static ShaderKey getCutout(Object p) {
		IrisRenderingPipeline pipeline = (IrisRenderingPipeline) p;

		if (HandRenderer.getInstance().isActive()) {
			return (HandRenderer.getInstance().isRenderingSolid() ? ShaderKey.HAND_CUTOUT_DIFFUSE : ShaderKey.HAND_WATER_DIFFUSE);
		} else if (ShaderOverrides.isBlockEntities(pipeline)) {
			return (ShaderKey.BLOCK_ENTITY_DIFFUSE);
		} else {
			return (ShaderKey.ENTITIES_CUTOUT_DIFFUSE);
		}
	}

	private static ShaderKey getSolid(Object p) {
		IrisRenderingPipeline pipeline = (IrisRenderingPipeline) p;

		if (HandRenderer.getInstance().isActive()) {
			return (HandRenderer.getInstance().isRenderingSolid() ? ShaderKey.HAND_CUTOUT : ShaderKey.HAND_TRANSLUCENT);
		} else if (ShaderOverrides.isBlockEntities(pipeline)) {
			return (ShaderKey.BLOCK_ENTITY);
		} else {
			return (ShaderKey.ENTITIES_SOLID);
		}
	}

	private static ShaderKey getTranslucent(Object p) {
		IrisRenderingPipeline pipeline = (IrisRenderingPipeline) p;

		if (HandRenderer.getInstance().isActive()) {
			return (HandRenderer.getInstance().isRenderingSolid() ? ShaderKey.HAND_CUTOUT_DIFFUSE : ShaderKey.HAND_WATER_DIFFUSE);
		} else if (ShaderOverrides.isBlockEntities(pipeline)) {
			return (ShaderKey.BE_TRANSLUCENT);
		} else {
			return (ShaderKey.ENTITIES_TRANSLUCENT);
		}
	}

	private static ShaderKey getGlint(ShaderKey key) {
		if (!HandRenderer.getInstance().isActive()) {
			return key;
		}

		boolean solid = HandRenderer.getInstance().isRenderingSolid();
		return switch (key) {
			case ENTITIES_CUTOUT_GLINT_SPECIAL, ENTITIES_TRANSLUCENT_GLINT_SPECIAL ->
				solid ? ShaderKey.HAND_CUTOUT_GLINT_SPECIAL : ShaderKey.HAND_TRANSLUCENT_GLINT_SPECIAL;
			case ENTITIES_CUTOUT_GLINT_ARMOR -> solid ? ShaderKey.HAND_CUTOUT_GLINT_ARMOR : ShaderKey.HAND_TRANSLUCENT_GLINT_ARMOR;
			case ENTITIES_SOLID_GLINT -> solid ? ShaderKey.HAND_CUTOUT_GLINT : ShaderKey.HAND_TRANSLUCENT_GLINT;
			default -> solid ? ShaderKey.HAND_CUTOUT_GLINT_DIFFUSE : ShaderKey.HAND_TRANSLUCENT_GLINT_DIFFUSE;
		};
	}

	private static void assignToMain(String pipeline, Function<IrisRenderingPipeline, ShaderKey> o) {
		if (coreShaderMap.containsKey(pipeline)) {
			Function<IrisRenderingPipeline, ShaderKey> current = coreShaderMap.get(pipeline);
			ShaderKey currentKey = current.apply(null);
			ShaderKey newKey = o.apply(null);
			if (currentKey != newKey) {
				Iris.logger.warn("Pair already assigned: " + pipeline + " to " + currentKey + " -> " + newKey);
			}
		}

		coreShaderMap.put(pipeline, o);
	}

	private static void assignToShadow(String pipeline, Function<IrisRenderingPipeline, ShaderKey> o) {
		if (coreShaderMapShadow.containsKey(pipeline)) {
			Iris.logger.warn("Pair already assigned: " + pipeline);
		}

		coreShaderMapShadow.put(pipeline, o);
	}

	private static boolean defined(RenderPipeline shader, String name) {
		return shader.getShaderDefines().values().containsKey(name);
	}

	private static ShaderKey getSpecial(boolean shadow) {
		if (special == null) {
			return null;
		}

		if (shadow) {
			return switch (special) {
				case Lightning -> ShaderKey.SHADOW_LIGHTNING;
				case BeaconBeam -> ShaderKey.SHADOW_BEACON_BEAM;
				case Line -> ShaderKey.SHADOW_LINES;
				case Weather, Particles, ParticlesTrans -> ShaderKey.SHADOW_PARTICLES;
				case Clouds -> ShaderKey.SHADOW_CLOUDS;
				default -> null;
			};
		}

		return switch (special) {
			case Lightning -> ShaderKey.LIGHTNING;
			case BeaconBeam -> ShaderKey.BEACON;
			case ArmorGlint -> getGlint(ShaderKey.GLINT);
			case SpiderEyes -> ShaderKey.ENTITIES_EYES;
			case DamagedBlock -> ShaderKey.CRUMBLING;
			case Line -> ShaderKey.LINES;
			case Weather -> ShaderKey.WEATHER;
			case Clouds -> ShaderKey.CLOUDS;
			case Particles -> ShaderKey.PARTICLES;
			case ParticlesTrans -> ShaderKey.PARTICLES_TRANS;
			case EntitiesTrans -> ShaderKey.ENTITIES_TRANSLUCENT;
			case Block -> ShaderKey.BLOCK_ENTITY;
			case BlockTrans -> ShaderKey.BE_TRANSLUCENT;
			case Textured -> ShaderKey.TEXTURED;
			case TexturedLit -> ShaderKey.TEXTURED_LIT;
			case Basic -> ShaderKey.BASIC;
			default -> null;
		};
	}

	private static ShaderKey getImmediate(IrisRenderingPipeline pipeline, RenderPipeline shader) {
		boolean textured = defined(shader, "TEXTURED");
		boolean lightmapped = defined(shader, "LIGHTMAP");
		boolean color = defined(shader, "COLOR");
		boolean blend = defined(shader, "BLEND");
		boolean cutout = defined(shader, "ALPHA_CUTOUT");

		if (ShadowRenderingState.areShadowsCurrentlyBeingRendered()) {
			ShaderKey special = getSpecial(true);

			if (special != null) {
				return special;
			}

			if (pipeline.getPhase() == WorldRenderingPhase.BLOCK_ENTITIES) {
				return ShaderKey.SHADOW_BLOCK;
			}

			if (textured) {
				return ShaderKey.SHADOW_ENTITIES_CUTOUT;
			}

			return color ? ShaderKey.SHADOW_BASIC_COLOR : ShaderKey.SHADOW_BASIC;
		}

		ShaderKey special = getSpecial(false);

		if (special != null) {
			return special;
		}

		return switch (pipeline.getPhase()) {
			case SKY, SUNSET, CUSTOM_SKY, SUN, MOON, STARS, VOID -> textured ? (color ? ShaderKey.SKY_TEXTURED_COLOR : ShaderKey.SKY_TEXTURED)
				: (color ? ShaderKey.SKY_BASIC_COLOR : ShaderKey.SKY_BASIC);
			case CLOUDS -> ShaderKey.CLOUDS;
			case ENTITIES, BLOCK_ENTITIES, HAND_SOLID, HAND_TRANSLUCENT -> !textured ? (color ? ShaderKey.BASIC_COLOR : ShaderKey.BASIC)
				: blend ? getTranslucent(pipeline) : cutout ? getCutout(pipeline) : getSolid(pipeline);
			case DESTROY -> ShaderKey.CRUMBLING;
			case OUTLINE -> ShaderKey.LINES;
			case PARTICLES -> blend ? ShaderKey.PARTICLES_TRANS : ShaderKey.PARTICLES;
			case RAIN_SNOW -> ShaderKey.WEATHER;
			case WORLD_BORDER -> ShaderKey.TEXTURED;
			default -> textured ? (lightmapped ? ShaderKey.TEXTURED_LIT : color ? ShaderKey.TEXTURED_COLOR : ShaderKey.TEXTURED)
				: (color ? ShaderKey.BASIC_COLOR : ShaderKey.BASIC);
		};
	}

	@Nullable
	public static ShaderKey getPipeline(IrisRenderingPipeline pipeline, RenderPipeline shader) {
		if (shader.getLocation().equals(IMMEDIATE)) {
			return getImmediate(pipeline, shader);
		}

		if (ShadowRenderingState.areShadowsCurrentlyBeingRendered()) {
			return coreShaderMapShadow.getOrDefault(shader.getLocation(), FAKE_FUNCTION).apply(pipeline);
		} else {
			return coreShaderMap.getOrDefault(shader.getLocation(), FAKE_FUNCTION).apply(pipeline);
		}
	}

	public static void preparePrograms(IrisRenderingPipeline pipeline, ShaderMap shaders) {
	}
}
