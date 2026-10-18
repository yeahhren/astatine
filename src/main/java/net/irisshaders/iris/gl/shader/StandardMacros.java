package net.irisshaders.iris.gl.shader;

import com.google.common.collect.ImmutableList;
import net.irisshaders.iris.Iris;
import net.irisshaders.iris.gl.IrisLimits;
import net.irisshaders.iris.helpers.StringPair;
import net.irisshaders.iris.pbr.format.TextureFormat;
import net.irisshaders.iris.pbr.format.TextureFormatLoader;
import net.irisshaders.iris.pipeline.WorldRenderingPhase;
import net.irisshaders.iris.platform.IrisPlatformHelpers;
import net.irisshaders.iris.vulkan.VulkanShaderExtensions;
import net.minecraft.client.Minecraft;
import org.apache.commons.lang3.SystemUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import yt.ren.astatine.Native;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class StandardMacros {
	private static final Pattern SEMVER_PATTERN = Pattern.compile("(?<major>\\d+)\\.(?<minor>\\d+)\\.*(?<bugfix>\\d*)(.*)");

	private static void define(List<StringPair> defines, String key) {
		defines.add(new StringPair(key, ""));
	}

	private static void define(List<StringPair> defines, String key, String value) {
		defines.add(new StringPair(key, value));
	}

	public static ImmutableList<StringPair> createStandardEnvironmentDefines() {
		ArrayList<StringPair> standardDefines = new ArrayList<>();

		define(standardDefines, "MC_VERSION", getMcVersion());
		define(standardDefines, "MC_MIPMAP_LEVEL", String.valueOf(Minecraft.getInstance().options.mipmapLevels));
		define(standardDefines, "IRIS_VERSION", getFormattedIrisVersion());
		define(standardDefines, "MC_GL_VERSION", "0");
		define(standardDefines, "MC_GLSL_VERSION", "450");
		define(standardDefines, "IRIS_VULKAN");
		for (String extension : VulkanShaderExtensions.EXTENSIONS) {
			define(standardDefines, "MC_" + extension);
		}
		define(standardDefines, getOsString());
		define(standardDefines, getVendor());
		define(standardDefines, getRenderer());
		define(standardDefines, "IS_IRIS");
		define(standardDefines, "IRIS_REQUIRES_SEPARATE_ENTITY_DRAWS");
		define(standardDefines, "MAX_COLOR_BUFFERS", String.valueOf(IrisLimits.MAX_COLOR_BUFFERS));
		define(standardDefines, "IRIS_HAS_TRANSLUCENCY_SORTING");
		define(standardDefines, "IRIS_INLINE_GLINT");
		define(standardDefines, "IRIS_TAG_SUPPORT", "2");


		if (IrisPlatformHelpers.getInstance().isModLoaded("continuity")) {
			define(standardDefines, "IRIS_HAS_CONNECTED_TEXTURES");
		}

		if (IrisPlatformHelpers.getInstance().isModLoaded("monocle")) {
			define(standardDefines, "IS_MONOCLE");
		}

		if (Iris.getIrisConfig().shouldAllowUnknownShaders()) {
			define(standardDefines, "ALLOWS_UNKNOWN_SHADERS");
		}

		define(standardDefines, "DH_BLOCK_UNKNOWN", String.valueOf(0));
		define(standardDefines, "DH_BLOCK_LEAVES", String.valueOf(1));
		define(standardDefines, "DH_BLOCK_STONE", String.valueOf(2));
		define(standardDefines, "DH_BLOCK_WOOD", String.valueOf(3));
		define(standardDefines, "DH_BLOCK_METAL", String.valueOf(4));
		define(standardDefines, "DH_BLOCK_DIRT", String.valueOf(5));
		define(standardDefines, "DH_BLOCK_LAVA", String.valueOf(6));
		define(standardDefines, "DH_BLOCK_DEEPSLATE", String.valueOf(7));
		define(standardDefines, "DH_BLOCK_SNOW", String.valueOf(8));
		define(standardDefines, "DH_BLOCK_SAND", String.valueOf(9));
		define(standardDefines, "DH_BLOCK_TERRACOTTA", String.valueOf(10));
		define(standardDefines, "DH_BLOCK_NETHER_STONE", String.valueOf(11));
		define(standardDefines, "DH_BLOCK_WATER", String.valueOf(12));
		define(standardDefines, "DH_BLOCK_GRASS", String.valueOf(13));
		define(standardDefines, "DH_BLOCK_AIR", String.valueOf(14));
		define(standardDefines, "DH_BLOCK_ILLUMINATED", String.valueOf(15));

		define(standardDefines, "MC_NORMAL_MAP");
		define(standardDefines, "MC_SPECULAR_MAP");
		define(standardDefines, "MC_RENDER_QUALITY", "1.0");
		define(standardDefines, "MC_SHADOW_QUALITY", "1.0");
		define(standardDefines, "MC_HAND_DEPTH", Float.toString(0.125F));

		TextureFormat textureFormat = TextureFormatLoader.getFormat();
		if (textureFormat != null) {
			for (String define : textureFormat.getDefines()) {
				define(standardDefines, define);
			}
		}

		getRenderStages().forEach((stage, index) -> define(standardDefines, stage, index));

		for (String irisDefine : getIrisDefines()) {
			define(standardDefines, irisDefine);
		}

		return ImmutableList.copyOf(standardDefines);
	}


	/**
	 * Gets the current mc version String in a 5 digit format
	 *
	 * @return mc version string
	 * @see <a href="https://github.com/sp614x/optifine/blob/9c6a5b5326558ccc57c6490b66b3be3b2dc8cbef/OptiFineDoc/doc/shaders.txt#L696-L699">Optifine Doc</a>
	 */
	public static String getMcVersion() {
		String version = Iris.getReleaseTarget();
        String formattedVersion = formatVersionString(version);
		if (formattedVersion == null) {
			throw new IllegalStateException("Could not parse game version \"" + version + "\"");
		} else {
			return formattedVersion;
		}
	}


	/**
	 * Gets the current Iris version String in a 5 digit format
	 *
	 * @return The Iris version string
	 */
	public static String getFormattedIrisVersion() {
		String rawVersion = Iris.getVersion();
		if (rawVersion == null) {
			throw new IllegalArgumentException("Could not get current Iris version!");
		}
		String irisSemver = getIrisSemver(rawVersion);
		String formattedSemver = formatVersionString(irisSemver);
		if (formattedSemver == null) {
			throw new IllegalArgumentException("Could not get a valid semantic version string for Iris version \"" + irisSemver + "\"");
		} else {
			return formattedSemver;
		}
	}

	private static @NotNull String getIrisSemver(String rawVersion) {
		Matcher matcher = SEMVER_PATTERN.matcher(rawVersion);
		if (!matcher.matches()) {
			throw new IllegalArgumentException("Could not parse semantic Iris version from \"" + rawVersion + "\"");
		}
		String major = matcher.group("major");
		String minor = matcher.group("minor");
		String bugFix = matcher.group("bugfix");
		if (bugFix == null) {
			bugFix = "0";
		}
		if (major == null || minor == null) {
			throw new IllegalArgumentException("Could not parse semantic Iris version from \"" + rawVersion + "\"");
		}
        return "%s.%s.%s".formatted(major, minor, bugFix);
	}

	/**
	 * Formats a semver string into a 122 format
	 *
	 * @param version The string version to format
	 * @return the formatted version in a 122 format, or <b>null</b> if the string is not a valid semver string.
	 */
	@Nullable
	public static String formatVersionString(String version) {
		String[] splitVersion = version.split("\\.");
		if (splitVersion.length < 2) {
			return null;
		}
		String major = splitVersion[0];
		String minor = splitVersion[1].length() == 1 ? 0 + splitVersion[1] : splitVersion[1];
		String bugFix = splitVersion.length < 3 ? "00" : splitVersion[2];
		if (bugFix.length() == 1) {
			bugFix = 0 + bugFix;
		}
		return major + minor + bugFix;
	}

	/**
	 * Expanded version of {@link Matcher#group(String)} that does not throw an exception.
	 * If the argument is incorrect (normally resulting in an exception), it returns null
	 *
	 * @param matcher matcher to check the group by
	 * @param name    name of the group
	 * @return the section of the matcher that is a group, or null, if that matcher does not contain said group
	 */
	public static String group(Matcher matcher, String name) {
		try {
			return matcher.group(name);
		} catch (IllegalArgumentException | IllegalStateException exception) {
			return null;
		}
	}

	/**
	 * Returns the current OS String
	 *
	 * @return the string based on the current OS
	 * @see <a href="https://github.com/sp614x/optifine/blob/9c6a5b5326558ccc57c6490b66b3be3b2dc8cbef/OptiFineDoc/doc/shaders.txt#L709-L714">Optifine Doc</a>
	 */
	public static String getOsString() {
		if (SystemUtils.IS_OS_MAC) {
			return "MC_OS_MAC_VK";
		} else if (SystemUtils.IS_OS_LINUX) {
			return "MC_OS_LINUX";
		} else if (SystemUtils.IS_OS_WINDOWS) {
			return "MC_OS_WINDOWS"; // Note: Optifine doesn't have a macro for Solaris. https://github.com/sp614x/optifine/blob/9c6a5b5326558ccc57c6490b66b3be3b2dc8cbef/OptiFineDoc/doc/shaders.txt#L709-L714
		}
		return "MC_OS_UNKNOWN";
	}

	/**
	 * Returns a string indicating the graphics card being used
	 *
	 * @return the graphics card prefixed with "MC_GL_VENDOR_"
	 * @see <a href="https://github.com/sp614x/optifine/blob/9c6a5b5326558ccc57c6490b66b3be3b2dc8cbef/OptiFineDoc/doc/shaders.txt#L716-L723">Optifine Doc</a>
	 */
	public static String getVendor() {
		return switch (Native.vendorId()) {
			case 0x8086 -> "MC_GL_VENDOR_INTEL";
			case 0x10DE -> "MC_GL_VENDOR_NVIDIA";
			case 0x1002 -> "MC_GL_VENDOR_AMD";
			default -> "MC_GL_VENDOR_OTHER";
		};
	}

	/**
	 * Returns the graphics driver being used
	 *
	 * @return graphics driver prefixed with "MC_GL_RENDERER_"
	 * @see <a href="https://github.com/sp614x/optifine/blob/9c6a5b5326558ccc57c6490b66b3be3b2dc8cbef/OptiFineDoc/doc/shaders.txt#L725-L733">Optifine Doc</a>
	 */
	public static String getRenderer() {
		String renderer = Native.deviceName().toLowerCase(Locale.ROOT);
		if (renderer.startsWith("amd")) {
			return "MC_GL_RENDERER_RADEON";
		} else if (renderer.startsWith("ati")) {
			return "MC_GL_RENDERER_RADEON";
		} else if (renderer.startsWith("radeon")) {
			return "MC_GL_RENDERER_RADEON";
		} else if (renderer.startsWith("gallium")) {
			return "MC_GL_RENDERER_GALLIUM";
		} else if (renderer.startsWith("intel")) {
			return "MC_GL_RENDERER_INTEL";
		} else if (renderer.startsWith("geforce")) {
			return "MC_GL_RENDERER_GEFORCE";
		} else if (renderer.startsWith("nvidia")) {
			return "MC_GL_RENDERER_GEFORCE";
		} else if (renderer.startsWith("quadro")) {
			return "MC_GL_RENDERER_QUADRO";
		} else if (renderer.startsWith("nvs")) {
			return "MC_GL_RENDERER_QUADRO";
		} else if (renderer.startsWith("mesa")) {
			return "MC_GL_RENDERER_MESA";
		} else if (renderer.startsWith("apple")) {
			return "MC_GL_RENDERER_APPLE";
		}
		return "MC_GL_RENDERER_OTHER";
	}


	public static Map<String, String> getRenderStages() {
		Map<String, String> stages = new HashMap<>();
		for (WorldRenderingPhase phase : WorldRenderingPhase.values()) {
			stages.put("MC_RENDER_STAGE_" + phase.name(), String.valueOf(phase.ordinal()));
		}
		return stages;
	}

	/**
	 * Returns the list of Iris-exclusive uniforms supported in the current version of Iris.
	 *
	 * @return List of definitions corresponding to the uniform names prefixed with "MC_"
	 */
	public static List<String> getIrisDefines() {
		// All Iris-exclusive uniforms should have a corresponding definition here. Example:
		// defines.add("MC_UNIFORM_DRAGON_DEATH_PROGRESS");

		return new ArrayList<>();
	}
}
