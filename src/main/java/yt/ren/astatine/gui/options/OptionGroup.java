package yt.ren.astatine.gui.options;

import java.util.ArrayList;
import java.util.List;

public final class OptionGroup {
	private final List<Option<?>> options;

	private OptionGroup(List<Option<?>> options) {
		this.options = List.copyOf(options);
	}

	public static Builder createBuilder() {
		return new Builder();
	}

	public List<Option<?>> getOptions() {
		return this.options;
	}

	public static final class Builder {
		private final List<Option<?>> options = new ArrayList<>();

		public Builder add(Option<?> option) {
			this.options.add(option);
			return this;
		}

		public OptionGroup build() {
			if (this.options.isEmpty()) {
				throw new IllegalStateException("At least one option must be specified");
			}

			return new OptionGroup(this.options);
		}
	}
}
