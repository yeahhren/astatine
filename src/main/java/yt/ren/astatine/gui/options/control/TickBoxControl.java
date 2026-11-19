package yt.ren.astatine.gui.options.control;

import yt.ren.astatine.gui.Dim2i;
import yt.ren.astatine.gui.options.Option;

public class TickBoxControl implements Control<Boolean> {
	private final Option<Boolean> option;

	public TickBoxControl(Option<Boolean> option) {
		this.option = option;
	}

	@Override
	public ControlElement<Boolean> createElement(Dim2i dim) {
		return new TickBoxControlElement(this.option, dim);
	}

	@Override
	public int getMaxWidth() {
		return 30;
	}

	@Override
	public Option<Boolean> getOption() {
		return this.option;
	}

	private static class TickBoxControlElement extends ControlElement<Boolean> {
		TickBoxControlElement(Option<Boolean> option, Dim2i dim) {
			super(option, dim);
		}

		@Override
		public void render(int mouseX, int mouseY, float delta) {
			super.render(mouseX, mouseY, delta);

			// The box is positioned from the live dimension so it follows scrolling.
			final int x = this.dim.getLimitX() - 16;
			final int y = this.dim.getCenterY() - 5;
			final int w = x + 10;
			final int h = y + 10;

			final boolean enabled = this.option.isAvailable();
			final boolean ticked = enabled && this.option.getValue();

			final int color;

			if (enabled) {
				color = ticked ? 0xFF94E4D3 : 0xFFFFFFFF;
			} else {
				color = 0xFFAAAAAA;
			}

			if (ticked) {
				this.drawRect(x + 2, y + 2, w - 2, h - 2, color);
			}

			this.drawBorder(x, y, w, h, color);
		}

		@Override
		public boolean mouseClicked(double mouseX, double mouseY, int button) {
			if (this.option.isAvailable() && button == 0 && this.dim.containsCursor(mouseX, mouseY)) {
				this.toggleControl();
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
				this.toggleControl();
				this.playClickSound();

				return true;
			}

			return false;
		}

		public void toggleControl() {
			this.option.setValue(!this.option.getValue());
		}
	}
}
