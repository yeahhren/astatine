package yt.ren.astatine.config;

import com.google.gson.JsonElement;
import com.google.gson.JsonPrimitive;

import java.util.List;
import java.util.Objects;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;
import java.util.function.Supplier;

public abstract sealed class ConfigOption<T> permits ConfigOption.Bool, ConfigOption.Int, ConfigOption.Choice, ConfigOption.Text {
	private final String key;
	private final T defaultValue;
	private final ConfigStore store;
	private final Supplier<T> getter;
	private final Consumer<T> setter;
	private final List<Consumer<T>> listeners = new CopyOnWriteArrayList<>();
	private T value;

	ConfigOption(String key, T defaultValue, ConfigStore store, Supplier<T> getter, Consumer<T> setter) {
		this.key = key;
		this.defaultValue = defaultValue;
		this.store = store;
		this.getter = getter;
		this.setter = setter;
		this.value = defaultValue;
	}

	public String key() {
		return this.key;
	}

	public T defaultValue() {
		return this.defaultValue;
	}

	public ConfigStore store() {
		return this.store;
	}

	/**
	 * Whether this option is written to {@code astatine.json}, as opposed to being proxied to another store.
	 */
	public boolean isOwned() {
		return this.getter == null;
	}

	public T get() {
		return this.getter != null ? this.getter.get() : this.value;
	}

	public void set(T value) {
		T sanitized = this.sanitize(Objects.requireNonNull(value));

		if (Objects.equals(this.get(), sanitized)) {
			return;
		}

		if (this.setter != null) {
			this.setter.accept(sanitized);
		} else {
			this.value = sanitized;
		}

		for (Consumer<T> listener : this.listeners) {
			listener.accept(sanitized);
		}
	}

	public void reset() {
		this.set(this.defaultValue);
	}

	/**
	 * Registers a listener that runs after every change of value.
	 */
	public ConfigOption<T> onChange(Consumer<T> listener) {
		this.listeners.add(listener);
		return this;
	}

	protected T sanitize(T value) {
		return value;
	}

	abstract JsonElement toJson(T value);

	/**
	 * @throws RuntimeException if the element cannot be read as this option's type
	 */
	abstract T fromJson(JsonElement element);

	void read(JsonElement element) {
		this.value = this.sanitize(this.fromJson(element));
	}

	JsonElement write() {
		return this.toJson(this.value);
	}

	public static final class Bool extends ConfigOption<Boolean> {
		Bool(String key, boolean defaultValue, ConfigStore store, Supplier<Boolean> getter, Consumer<Boolean> setter) {
			super(key, defaultValue, store, getter, setter);
		}

		public boolean getBoolean() {
			return this.get();
		}

		@Override
		JsonElement toJson(Boolean value) {
			return new JsonPrimitive(value);
		}

		@Override
		Boolean fromJson(JsonElement element) {
			if (!element.isJsonPrimitive() || !element.getAsJsonPrimitive().isBoolean()) {
				throw new IllegalArgumentException("expected a boolean");
			}

			return element.getAsBoolean();
		}
	}

	public static final class Int extends ConfigOption<Integer> {
		private final Supplier<Integer> min;
		private final Supplier<Integer> max;
		private final int step;

		Int(String key, int defaultValue, Supplier<Integer> min, Supplier<Integer> max, int step, ConfigStore store,
			Supplier<Integer> getter, Consumer<Integer> setter) {
			super(key, defaultValue, store, getter, setter);
			this.min = min;
			this.max = max;
			this.step = step;
		}

		public int getInt() {
			return this.get();
		}

		public int min() {
			return this.min.get();
		}

		public int max() {
			return this.max.get();
		}

		public int step() {
			return this.step;
		}

		@Override
		protected Integer sanitize(Integer value) {
			int min = this.min();
			int clamped = Math.max(min, Math.min(this.max(), value));
			return min + (clamped - min) / this.step * this.step;
		}

		@Override
		JsonElement toJson(Integer value) {
			return new JsonPrimitive(value);
		}

		@Override
		Integer fromJson(JsonElement element) {
			if (!element.isJsonPrimitive() || !element.getAsJsonPrimitive().isNumber()) {
				throw new IllegalArgumentException("expected a number");
			}

			return element.getAsInt();
		}
	}

	public static final class Choice<E extends Enum<E>> extends ConfigOption<E> {
		private final Class<E> type;

		Choice(String key, Class<E> type, E defaultValue, ConfigStore store, Supplier<E> getter, Consumer<E> setter) {
			super(key, defaultValue, store, getter, setter);
			this.type = type;
		}

		public Class<E> type() {
			return this.type;
		}

		@Override
		JsonElement toJson(E value) {
			return new JsonPrimitive(value.name());
		}

		@Override
		E fromJson(JsonElement element) {
			return Enum.valueOf(this.type, element.getAsString());
		}
	}

	public static final class Text extends ConfigOption<String> {
		Text(String key, String defaultValue, ConfigStore store, Supplier<String> getter, Consumer<String> setter) {
			super(key, defaultValue, store, getter, setter);
		}

		@Override
		JsonElement toJson(String value) {
			return new JsonPrimitive(value);
		}

		@Override
		String fromJson(JsonElement element) {
			if (!element.isJsonPrimitive() || !element.getAsJsonPrimitive().isString()) {
				throw new IllegalArgumentException("expected a string");
			}

			return element.getAsString();
		}
	}
}
