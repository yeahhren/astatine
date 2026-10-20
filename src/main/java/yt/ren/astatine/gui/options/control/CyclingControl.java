package yt.ren.astatine.gui.options.control;

import net.minecraft.client.gui.screen.Screen;
import yt.ren.astatine.gui.Dim2i;
import yt.ren.astatine.gui.options.Option;

public class CyclingControl<T extends Enum<T>> implements Control<T> {
	private final Option<T> option;
	private final T[] allowedValues;
	private final String[] names;

	public CyclingControl(Option<T> option, Class<T> enumType, String[] names) {
		T[] universe = enumType.getEnumConstants();

		if (universe.length != names.length) {
			throw new IllegalArgumentException("Mismatch between universe length and names array length");
		}

		if (universe.length == 0) {
			throw new IllegalArgumentException("The enum universe must contain at least one item");
		}

		this.option = option;
		this.allowedValues = universe;
		this.names = names;
	}

	@Override
	public Option<T> getOption() {
		return this.option;
	}

	@Override
	public ControlElement<T> createElement(Dim2i dim) {
		return new CyclingControlElement<>(this.option, dim, this.allowedValues, this.names);
	}

	@Override
	public int getMaxWidth() {
		return 70;
	}

	private static class CyclingControlElement<T extends Enum<T>> extends ControlElement<T> {
		private final T[] allowedValues;
		private final String[] names;
		private int currentIndex;

		CyclingControlElement(Option<T> option, Dim2i dim, T[] allowedValues, String[] names) {
			super(option, dim);

			this.allowedValues = allowedValues;
			this.names = names;
			this.currentIndex = 0;

			for (int i = 0; i < allowedValues.length; i++) {
				if (allowedValues[i] == option.getValue()) {
					this.currentIndex = i;
					break;
				}
			}
		}

		@Override
		public void render(int mouseX, int mouseY, float delta) {
			super.render(mouseX, mouseY, delta);

			Enum<T> value = this.option.getValue();
			String name = this.names[value.ordinal()];

			int strWidth = this.getStringWidth(name);
			this.drawString(name, this.dim.getLimitX() - strWidth - 6, this.dim.getCenterY() - 4, 0xFFFFFFFF);
		}

		@Override
		public boolean mouseClicked(double mouseX, double mouseY, int button) {
			if (this.option.isAvailable() && this.dim.containsCursor(mouseX, mouseY) && (button == 0 || button == 1)) {
				this.currentIndex = Math.floorMod(this.option.getValue().ordinal() + (button == 0 ? 1 : -1), this.allowedValues.length);
				this.option.setValue(this.allowedValues[this.currentIndex]);
				this.playClickSound();

				return true;
			}

			return false;
		}

		@Override
		public boolean keyPressed(int keyCode, char character) {
			if (!this.isFocused()) {
				return false;
			}

			if (isToggleKey(keyCode)) {
				this.cycleControl(Screen.isShiftDown());
				return true;
			}

			return false;
		}

		public void cycleControl(boolean reverse) {
			if (reverse) {
				this.currentIndex = (this.currentIndex + this.allowedValues.length - 1) % this.allowedValues.length;
			} else {
				this.currentIndex = (this.currentIndex + 1) % this.allowedValues.length;
			}
			this.option.setValue(this.allowedValues[this.currentIndex]);
		}
	}
}
