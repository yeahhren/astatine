package yt.ren.astatine.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.fabricmc.loader.api.FabricLoader;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Supplier;

public final class AstatineConfig implements ConfigStore {
	public static final AstatineConfig INSTANCE = new AstatineConfig(FabricLoader.getInstance().getConfigDir().resolve("astatine.json"));

	private static final Logger LOGGER = LogManager.getLogger("Astatine/Config");
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

	private final Path path;
	private final Map<String, ConfigOption<?>> options = new LinkedHashMap<>();
	// Keys we don't know about are kept so that downgrading or removing an option doesn't lose values.
	private JsonObject unknown = new JsonObject();
	private boolean existed;

	private AstatineConfig(Path path) {
		this.path = path;
	}

	public Path path() {
		return this.path;
	}

	/**
	 * Whether {@code astatine.json} was present when the config was loaded.
	 */
	public boolean existed() {
		return this.existed;
	}

	public Collection<ConfigOption<?>> options() {
		return Collections.unmodifiableCollection(this.options.values());
	}

	public ConfigOption<?> option(String key) {
		return this.options.get(key);
	}

	public ConfigOption.Bool bool(String key, boolean defaultValue) {
		return this.register(new ConfigOption.Bool(key, defaultValue, this, null, null));
	}

	public ConfigOption.Int integer(String key, int defaultValue, int min, int max) {
		return this.integer(key, defaultValue, () -> min, () -> max, 1);
	}

	public ConfigOption.Int integer(String key, int defaultValue, Supplier<Integer> min, Supplier<Integer> max, int step) {
		return this.register(new ConfigOption.Int(key, defaultValue, min, max, step, this, null, null));
	}

	public <E extends Enum<E>> ConfigOption.Choice<E> choice(String key, Class<E> type, E defaultValue) {
		return this.register(new ConfigOption.Choice<>(key, type, defaultValue, this, null, null));
	}

	public ConfigOption.Text text(String key, String defaultValue) {
		return this.register(new ConfigOption.Text(key, defaultValue, this, null, null));
	}

	public ConfigOption.Bool bindBool(String key, ConfigStore store, Supplier<Boolean> getter, Consumer<Boolean> setter) {
		return this.register(new ConfigOption.Bool(key, getter.get(), store, getter, setter));
	}

	public ConfigOption.Int bindInt(String key, ConfigStore store, Supplier<Integer> min, Supplier<Integer> max, int step,
									Supplier<Integer> getter, Consumer<Integer> setter) {
		return this.register(new ConfigOption.Int(key, getter.get(), min, max, step, store, getter, setter));
	}

	public <E extends Enum<E>> ConfigOption.Choice<E> bindChoice(String key, Class<E> type, ConfigStore store, Supplier<E> getter, Consumer<E> setter) {
		return this.register(new ConfigOption.Choice<>(key, type, getter.get(), store, getter, setter));
	}

	private synchronized <T extends ConfigOption<?>> T register(T option) {
		if (this.options.putIfAbsent(option.key(), option) != null) {
			throw new IllegalStateException("Duplicate config option " + option.key());
		}

		if (option.isOwned() && this.unknown.has(option.key())) {
			this.readInto(option, this.unknown.remove(option.key()));
		}

		return option;
	}

	public synchronized void load() {
		this.existed = Files.exists(this.path);

		if (!this.existed) {
			this.save();
			return;
		}

		JsonObject root;

		try (Reader reader = Files.newBufferedReader(this.path, StandardCharsets.UTF_8)) {
			JsonElement element = new JsonParser().parse(reader);
			root = element.isJsonObject() ? element.getAsJsonObject() : new JsonObject();
		} catch (Exception e) {
			LOGGER.error("Failed to read {}, using defaults", this.path, e);
			this.backupCorrupted();
			this.save();
			return;
		}

		this.unknown = new JsonObject();

		for (Map.Entry<String, JsonElement> entry : root.entrySet()) {
			ConfigOption<?> option = this.options.get(entry.getKey());

			if (option == null || !option.isOwned()) {
				this.unknown.add(entry.getKey(), entry.getValue());
			} else {
				this.readInto(option, entry.getValue());
			}
		}
	}

	private void readInto(ConfigOption<?> option, JsonElement element) {
		try {
			option.read(element);
		} catch (RuntimeException e) {
			LOGGER.warn("Invalid value {} for {}, using the default", element, option.key());
		}
	}

	private void backupCorrupted() {
		try {
			Files.move(this.path, this.path.resolveSibling("astatine.json.corrupted"), StandardCopyOption.REPLACE_EXISTING);
		} catch (IOException e) {
			LOGGER.warn("Failed to back up corrupted config", e);
		}
	}

	@Override
	public synchronized void save() {
		JsonObject root = new JsonObject();

		for (ConfigOption<?> option : this.options.values()) {
			if (option.isOwned()) {
				root.add(option.key(), option.write());
			}
		}

		for (Map.Entry<String, JsonElement> entry : this.unknown.entrySet()) {
			root.add(entry.getKey(), entry.getValue());
		}

		try {
			Files.createDirectories(this.path.getParent());
			Path temporary = this.path.resolveSibling("astatine.json.tmp");

			try (Writer writer = Files.newBufferedWriter(temporary, StandardCharsets.UTF_8)) {
				GSON.toJson(root, writer);
			}

			Files.move(temporary, this.path, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
		} catch (IOException e) {
			LOGGER.error("Failed to save {}", this.path, e);
		}
	}
}
