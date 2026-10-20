package yt.ren.astatine.gui.options;

import net.minecraft.client.resource.language.I18n;
import yt.ren.astatine.config.ConfigOption;
import yt.ren.astatine.config.ConfigStore;
import yt.ren.astatine.gui.Dim2i;
import yt.ren.astatine.gui.options.control.Control;

import java.util.Collection;
import java.util.Collections;
import java.util.EnumSet;
import java.util.Objects;
import java.util.function.BooleanSupplier;
import java.util.function.Function;

/**
 * An entry on the options screen, editing a {@link ConfigOption}. Edits are held as a pending value until applied.
 */
public final class Option<T> {
	private final ConfigOption<T> config;
	private final Control<T> control;
	private final EnumSet<OptionFlag> flags;
	private final String name;
	private final String tooltip;
	private final OptionImpact impact;
	private final BooleanSupplier enabled;

	private T value;
	private T modifiedValue;

	private Dim2i parentDimension;
	private Dim2i dimension;
	private boolean highlight;
	private boolean selected;

	private Option(Builder<T> builder) {
		this.config = builder.config;
		this.name = builder.name;
		this.tooltip = builder.tooltip;
		this.impact = builder.impact;
		this.flags = builder.flags;
		this.enabled = builder.enabled;
		this.control = builder.control.apply(this);

		this.reset();
	}

	public static <T> Builder<T> createBuilder(ConfigOption<T> config) {
		return new Builder<>(config);
	}

	public String getName() {
		return this.name;
	}

	public String getTooltip() {
		return this.tooltip;
	}

	public OptionImpact getImpact() {
		return this.impact;
	}

	public Control<T> getControl() {
		return this.control;
	}

	public ConfigOption<T> getConfig() {
		return this.config;
	}

	public T getValue() {
		return this.modifiedValue;
	}

	public void setValue(T value) {
		this.modifiedValue = value;
	}

	public void reset() {
		this.value = this.config.get();
		this.modifiedValue = this.value;
	}

	public ConfigStore getStorage() {
		return this.config.store();
	}

	public boolean isAvailable() {
		return this.enabled.getAsBoolean();
	}

	public boolean hasChanged() {
		return !Objects.equals(this.value, this.modifiedValue);
	}

	public void applyChanges() {
		this.config.set(this.modifiedValue);
		this.reset();
	}

	public Collection<OptionFlag> getFlags() {
		return Collections.unmodifiableSet(this.flags);
	}

	public boolean isHighlight() {
		return this.highlight;
	}

	public void setHighlight(boolean highlight) {
		this.highlight = highlight;
	}

	public boolean getSelected() {
		return this.selected;
	}

	public void setSelected(boolean selected) {
		this.selected = selected;
	}

	public Dim2i getDim2i() {
		return this.dimension;
	}

	public void setDim2i(Dim2i dimension) {
		this.dimension = dimension;
	}

	public Dim2i getParentDimension() {
		return this.parentDimension;
	}

	public void setParentDimension(Dim2i parentDimension) {
		this.parentDimension = parentDimension;
	}

	public static final class Builder<T> {
		private final ConfigOption<T> config;
		private final EnumSet<OptionFlag> flags = EnumSet.noneOf(OptionFlag.class);
		private String name;
		private String tooltip;
		private Function<Option<T>, Control<T>> control;
		private OptionImpact impact;
		private BooleanSupplier enabled = () -> true;

		private Builder(ConfigOption<T> config) {
			this.config = Objects.requireNonNull(config);
		}

		/**
		 * Sets the name from a translation key.
		 */
		public Builder<T> setName(String key, Object... args) {
			this.name = I18n.translate(key, args);
			return this;
		}

		/**
		 * Sets the tooltip from a translation key.
		 */
		public Builder<T> setTooltip(String key, Object... args) {
			this.tooltip = I18n.translate(key, args);
			return this;
		}

		public Builder<T> setControl(Function<Option<T>, Control<T>> control) {
			this.control = Objects.requireNonNull(control);
			return this;
		}

		public Builder<T> setImpact(OptionImpact impact) {
			this.impact = impact;
			return this;
		}

		public Builder<T> setEnabled(boolean enabled) {
			return this.setEnabled(() -> enabled);
		}

		public Builder<T> setEnabled(BooleanSupplier enabled) {
			this.enabled = Objects.requireNonNull(enabled);
			return this;
		}

		public Builder<T> setFlags(OptionFlag... flags) {
			Collections.addAll(this.flags, flags);
			return this;
		}

		public Option<T> build() {
			Objects.requireNonNull(this.name, "Name must be specified");
			Objects.requireNonNull(this.tooltip, "Tooltip must be specified");
			Objects.requireNonNull(this.control, "Control must be specified");

			return new Option<>(this);
		}
	}
}
