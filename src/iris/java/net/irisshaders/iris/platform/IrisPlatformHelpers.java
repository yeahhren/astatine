package net.irisshaders.iris.platform;

import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.api.SemanticVersion;
import net.fabricmc.loader.api.VersionParsingException;
import net.minecraft.client.Minecraft;
import net.minecraft.client.options.GameOptions;
import net.minecraft.client.options.KeyBinding;
import org.apache.commons.lang3.ArrayUtils;

import java.nio.file.Path;

public final class IrisPlatformHelpers {
	public static final IrisPlatformHelpers INSTANCE = new IrisPlatformHelpers();

	private IrisPlatformHelpers() {
	}

	public static IrisPlatformHelpers getInstance() {
		return INSTANCE;
	}

	public boolean isModLoaded(String modId) {
		return FabricLoader.getInstance().isModLoaded(modId);
	}

	public String getVersion() {
		return FabricLoader.getInstance().getModContainer("astatine")
			.map(container -> container.getMetadata().getVersion().getFriendlyString()).orElse("unknown");
	}

	public boolean isDevelopmentEnvironment() {
		return FabricLoader.getInstance().isDevelopmentEnvironment();
	}

	public Path getGameDir() {
		return FabricLoader.getInstance().getGameDir();
	}

	public Path getConfigDir() {
		return FabricLoader.getInstance().getConfigDir();
	}

	public int compareVersions(String currentVersion, String semanticVersion) throws VersionParsingException {
		return SemanticVersion.parse(currentVersion).compareTo(SemanticVersion.parse(semanticVersion));
	}

	public KeyBinding registerKeyBinding(KeyBinding keyBinding) {
		GameOptions options = Minecraft.getInstance().options;

		if (options != null && !ArrayUtils.contains(options.keyBindings, keyBinding)) {
			options.keyBindings = ArrayUtils.add(options.keyBindings, keyBinding);
			options.load();
		}

		return keyBinding;
	}

	public boolean useELS() {
		return false;
	}
}
