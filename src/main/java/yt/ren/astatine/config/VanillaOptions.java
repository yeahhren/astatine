package yt.ren.astatine.config;

import net.minecraft.client.Minecraft;
import net.minecraft.client.options.GameOptions;
import org.lwjgl.opengl.Display;

public final class VanillaOptions {
	public static final ConfigStore STORE = () -> options().save();

	private static final AstatineConfig CONFIG = AstatineConfig.INSTANCE;

	public static final ConfigOption.Int RENDER_DISTANCE = CONFIG.bindInt("minecraft.renderDistance", STORE,
		() -> 2, () -> (int) GameOptions.Option.RENDER_DISTANCE.getMax(), 1,
		() -> options().viewDistance, value -> {
			options().viewDistance = value;

			if (minecraft().worldRenderer != null) {
				minecraft().worldRenderer.onViewChanged();
			}
		});
	public static final ConfigOption.Int BRIGHTNESS = CONFIG.bindInt("minecraft.gamma", STORE, () -> 0, () -> 100, 1,
		() -> Math.round(options().gamma * 100.0f), value -> options().gamma = value / 100.0f);
	public static final ConfigOption.Int GUI_SCALE = CONFIG.bindInt("minecraft.guiScale", STORE, () -> 0, () -> 3, 1,
		() -> options().guiScale, value -> options().guiScale = value);
	public static final ConfigOption.Bool FULLSCREEN = CONFIG.bindBool("minecraft.fullscreen", STORE,
		() -> options().fullscreen, value -> {
			if (minecraft().isFullscreen() != value) {
				minecraft().toggleFullscreen();
			}

			options().fullscreen = minecraft().isFullscreen();
		});
	public static final ConfigOption.Bool VSYNC = CONFIG.bindBool("minecraft.vsync", STORE,
		() -> options().vsync, value -> {
			options().vsync = value;
			Display.setVSyncEnabled(value);
		});
	public static final ConfigOption.Int MAX_FRAMERATE = CONFIG.bindInt("minecraft.maxFps", STORE, () -> 10, () -> 260, 10,
		() -> options().fpsLimit, value -> options().fpsLimit = value);
	public static final ConfigOption.Bool VIEW_BOBBING = CONFIG.bindBool("minecraft.viewBobbing", STORE,
		() -> options().viewBobbing, value -> options().viewBobbing = value);
	public static final ConfigOption.Bool ANAGLYPH = CONFIG.bindBool("minecraft.anaglyph", STORE,
		() -> options().anaglyph, value -> options().anaglyph = value);

	public static final ConfigOption.Choice<Graphics> GRAPHICS = CONFIG.bindChoice("minecraft.graphics", Graphics.class, STORE,
		() -> options().fancyGraphics ? Graphics.FANCY : Graphics.FAST, value -> options().fancyGraphics = value == Graphics.FANCY);
	public static final ConfigOption.Choice<Clouds> CLOUDS = CONFIG.bindChoice("minecraft.clouds", Clouds.class, STORE,
		() -> Clouds.values()[Math.floorMod(options().cloudRenderMode, 3)], value -> options().cloudRenderMode = value.ordinal());
	public static final ConfigOption.Choice<Particles> PARTICLES = CONFIG.bindChoice("minecraft.particles", Particles.class, STORE,
		() -> Particles.values()[Math.floorMod(options().particles, 3)], value -> options().particles = value.ordinal());
	public static final ConfigOption.Choice<SmoothLighting> SMOOTH_LIGHTING = CONFIG.bindChoice("minecraft.ao", SmoothLighting.class, STORE,
		() -> SmoothLighting.values()[Math.floorMod(options().ambientOcclusion, 3)], value -> options().ambientOcclusion = value.ordinal());
	public static final ConfigOption.Bool ENTITY_SHADOWS = CONFIG.bindBool("minecraft.entityShadows", STORE,
		() -> options().entityShadows, value -> options().entityShadows = value);
	public static final ConfigOption.Bool ALTERNATE_BLOCKS = CONFIG.bindBool("minecraft.alternateBlocks", STORE,
		() -> options().allowBlockAlternatives, value -> options().allowBlockAlternatives = value);
	public static final ConfigOption.Bool USE_VBO = CONFIG.bindBool("minecraft.useVbo", STORE,
		() -> options().useVbo, value -> options().useVbo = value);
	public static final ConfigOption.Int MIPMAP_LEVELS = CONFIG.bindInt("minecraft.mipmapLevels", STORE, () -> 0, () -> 4, 1,
		() -> options().mipmapLevels, value -> options().set(GameOptions.Option.MIPMAP_LEVELS, value.floatValue()));

	private VanillaOptions() {
	}

	/**
	 * Registers the vanilla options. Must be called once the game options exist.
	 */
	public static void init() {
	}

	private static Minecraft minecraft() {
		return Minecraft.getInstance();
	}

	private static GameOptions options() {
		return minecraft().options;
	}

	public enum Graphics {
		FAST, FANCY
	}

	public enum Clouds {
		OFF, FAST, FANCY
	}

	public enum Particles {
		ALL, DECREASED, MINIMAL
	}

	public enum SmoothLighting {
		OFF, MINIMUM, MAXIMUM
	}
}
