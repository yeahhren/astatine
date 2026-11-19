package yt.ren.astatine.config;

import net.irisshaders.iris.pathways.colorspace.ColorSpace;

public final class AstatineOptions {
	private static final AstatineConfig CONFIG = AstatineConfig.INSTANCE;

	public static final int MAX_CHUNK_THREADS = Math.max(1, Runtime.getRuntime().availableProcessors());

	public static final ConfigOption.Int CHUNK_UPDATE_THREADS = CONFIG.integer("performance.chunkUpdateThreads", 0, 0, MAX_CHUNK_THREADS);

	public static final ConfigOption.Bool SHADERS_ENABLED = CONFIG.bool("shaders.enabled", true);
	public static final ConfigOption.Text SHADER_PACK = CONFIG.text("shaders.pack", "");
	public static final ConfigOption.Int SHADOW_DISTANCE = CONFIG.integer("shaders.maxShadowDistance", 32, 0, 32);
	public static final ConfigOption.Choice<ColorSpace> COLOR_SPACE = CONFIG.choice("shaders.colorSpace", ColorSpace.class, ColorSpace.SRGB);
	public static final ConfigOption.Bool ALLOW_UNKNOWN_SHADERS = CONFIG.bool("shaders.allowUnknownShaders", false);
	public static final ConfigOption.Bool DEBUG_OPTIONS = CONFIG.bool("shaders.debug", false);
	public static final ConfigOption.Bool DISABLE_UPDATE_MESSAGE = CONFIG.bool("shaders.disableUpdateMessage", false);
	public static final ConfigOption.Bool HAS_RUN_VULKAN = CONFIG.bool("internal.hasRunVulkan", false);

	static {
		CONFIG.load();
	}

	private AstatineOptions() {
	}

	/**
	 * Loads the config if it hasn't been loaded yet.
	 */
	public static void init() {
	}

	public static int chunkUpdateThreads() {
		int configured = CHUNK_UPDATE_THREADS.getInt();
		return configured > 0 ? configured : Math.clamp(Runtime.getRuntime().availableProcessors() / 2, 1, 8);
	}
}
