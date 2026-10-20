package net.irisshaders.iris.config;

import net.irisshaders.iris.Iris;
import net.irisshaders.iris.pathways.colorspace.ColorSpace;
import yt.ren.astatine.config.AstatineConfig;
import yt.ren.astatine.config.AstatineOptions;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import java.util.Properties;

/**
 * Iris' view of its settings.
 */
public class IrisConfig {
	private final Path legacyPropertiesPath;

	public IrisConfig(Path legacyPropertiesPath) {
		this.legacyPropertiesPath = legacyPropertiesPath;
	}

	public void initialize() throws IOException {
		AstatineOptions.init();

		if (!AstatineConfig.INSTANCE.existed() && Files.exists(this.legacyPropertiesPath)) {
			this.importLegacy();
			this.save();
		}
	}

	private void importLegacy() throws IOException {
		Properties properties = new Properties();

		try (InputStream is = Files.newInputStream(this.legacyPropertiesPath)) {
			properties.load(is);
		}

		this.setShaderPackName(properties.getProperty("shaderPack"));
		AstatineOptions.SHADERS_ENABLED.set(!"false".equals(properties.getProperty("enableShaders")));
		AstatineOptions.ALLOW_UNKNOWN_SHADERS.set("true".equals(properties.getProperty("allowUnknownShaders")));
		AstatineOptions.DEBUG_OPTIONS.set("true".equals(properties.getProperty("enableDebugOptions")));
		AstatineOptions.DISABLE_UPDATE_MESSAGE.set("true".equals(properties.getProperty("disableUpdateMessage")));
		AstatineOptions.HAS_RUN_VULKAN.set("true".equals(properties.getProperty("hasRunVulkan")));

		try {
			AstatineOptions.SHADOW_DISTANCE.set(Integer.parseInt(properties.getProperty("maxShadowRenderDistance", "32")));
			AstatineOptions.COLOR_SPACE.set(ColorSpace.valueOf(properties.getProperty("colorSpace", "SRGB")));
		} catch (IllegalArgumentException e) {
			Iris.logger.error("Ignoring invalid shadow distance or color space in iris.properties");
		}

		Iris.logger.info("Imported settings from " + this.legacyPropertiesPath);
	}

	public boolean isInternal() {
		return false;
	}

	/**
	 * @return the selected shader pack, or empty if none is selected
	 */
	public Optional<String> getShaderPackName() {
		String name = AstatineOptions.SHADER_PACK.get();
		return name.isEmpty() ? Optional.empty() : Optional.of(name);
	}

	public void setShaderPackName(String name) {
		if (name == null || name.equals("(internal)")) {
			name = "";
		}

		AstatineOptions.SHADER_PACK.set(name);
	}

	public boolean areShadersEnabled() {
		return AstatineOptions.SHADERS_ENABLED.getBoolean();
	}

	public void setShadersEnabled(boolean enabled) {
		AstatineOptions.SHADERS_ENABLED.set(enabled);
	}

	public boolean areDebugOptionsEnabled() {
		return AstatineOptions.DEBUG_OPTIONS.getBoolean();
	}

	public void setDebugEnabled(boolean enabled) {
		AstatineOptions.DEBUG_OPTIONS.set(enabled);
	}

	public boolean shouldDisableUpdateMessage() {
		return AstatineOptions.DISABLE_UPDATE_MESSAGE.getBoolean();
	}

	public boolean hasRunVulkan() {
		return AstatineOptions.HAS_RUN_VULKAN.getBoolean();
	}

	public void setHasRunVulkan(boolean hasRunVulkan) {
		AstatineOptions.HAS_RUN_VULKAN.set(hasRunVulkan);
	}

	public boolean shouldAllowUnknownShaders() {
		return AstatineOptions.ALLOW_UNKNOWN_SHADERS.getBoolean();
	}

	public void setUnknown(boolean allow) throws IOException {
		AstatineOptions.ALLOW_UNKNOWN_SHADERS.set(allow);
		this.save();
	}

	public void load() throws IOException {
		AstatineConfig.INSTANCE.load();
	}

	public void save() throws IOException {
		AstatineConfig.INSTANCE.save();
	}
}
