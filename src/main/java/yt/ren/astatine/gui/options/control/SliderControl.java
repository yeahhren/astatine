package yt.ren.astatine.gui.options.control;

import net.minecraft.client.gui.screen.Screen;
import net.minecraft.util.math.MathHelper;
import org.lwjgl.input.Keyboard;
import yt.ren.astatine.gui.Dim2i;
import yt.ren.astatine.gui.options.Option;

public class SliderControl implements Control<Integer> {
	private final Option<Integer> option;

	private final int min, max, interval;

	private final ControlValueFormatter mode;

	public SliderControl(Option<Integer> option, int min, int max, int interval, ControlValueFormatter mode) {
		if (max <= min) {
			throw new IllegalArgumentException("The maximum value must be greater than the minimum value");
		}

		if (interval <= 0) {
			throw new IllegalArgumentException("The slider interval must be greater than zero");
		}

		if (((max - min) % interval) != 0) {
			throw new IllegalArgumentException("The maximum value must be divisable by the interval");
		}

		this.option = option;
		this.min = min;
		this.max = max;
		this.interval = interval;
		this.mode = mode;
	}

	@Override
	public ControlElement<Integer> createElement(Dim2i dim) {
		return new Button(this.option, dim, this.min, this.max, this.interval, this.mode);
	}

	@Override
	public Option<Integer> getOption() {
		return this.option;
	}

	@Override
	public int getMaxWidth() {
		return 130;
	}

	private static class Button extends ControlElement<Integer> {
		private static final int THUMB_WIDTH = 2, TRACK_HEIGHT = 1;

		private final ControlValueFormatter formatter;

		private final int min;
		private final int max;
		private final int range;
		private final int interval;

		private double thumbPosition;

		private boolean sliderHeld;
		private boolean editMode;

		Button(Option<Integer> option, Dim2i dim, int min, int max, int interval, ControlValueFormatter formatter) {
			super(option, dim);

			this.min = min;
			this.max = max;
			this.range = max - min;
			this.interval = interval;
			this.thumbPosition = this.getThumbPositionForValue(option.getValue());
			this.formatter = formatter;
			this.sliderHeld = false;
		}

		// Computed from the live dimension so that the slider follows scrolling.
		private Dim2i getSliderBounds() {
			return new Dim2i(this.dim.getLimitX() - 96, this.dim.getCenterY() - 5, 90, 10);
		}

		@Override
		public void render(int mouseX, int mouseY, float delta) {
			super.render(mouseX, mouseY, delta);

			if (this.option.isAvailable() && (this.hovered || this.isFocused())) {
				this.renderSlider();
			} else {
				this.renderStandaloneValue();
			}
		}

		private void renderStandaloneValue() {
			Dim2i bounds = this.getSliderBounds();

			String label = this.formatter.format(this.option.getValue());
			int labelWidth = this.font.getWidth(label);

			this.drawString(label, bounds.x() + bounds.width() - labelWidth, bounds.y() + (bounds.height() / 2) - 4, 0xFFFFFFFF);
		}

		private void renderSlider() {
			Dim2i bounds = this.getSliderBounds();
			int sliderX = bounds.x();
			int sliderY = bounds.y();
			int sliderWidth = bounds.width();
			int sliderHeight = bounds.height();

			this.thumbPosition = this.getThumbPositionForValue(this.option.getValue());

			double thumbOffset = MathHelper.clamp((double) (this.getIntValue() - this.min) / this.range * sliderWidth, 0, sliderWidth);

			int thumbX = (int) (sliderX + thumbOffset - THUMB_WIDTH);
			int trackY = (int) (sliderY + (sliderHeight / 2f) - ((double) TRACK_HEIGHT / 2));

			if (this.isFocused() && this.editMode) {
				this.drawRect(thumbX - 1, sliderY - 1, thumbX + 5, sliderY + sliderHeight + 1, 0xFFFFFFFF);
			}

			this.drawRect(thumbX, sliderY, thumbX + (THUMB_WIDTH * 2), sliderY + sliderHeight, 0xFFFFFFFF);
			this.drawRect(sliderX, trackY, sliderX + sliderWidth, trackY + TRACK_HEIGHT, 0xFFFFFFFF);

			String label = String.valueOf(this.getIntValue());

			int labelWidth = this.font.getWidth(label);

			this.drawString(label, sliderX - labelWidth - 6, sliderY + (sliderHeight / 2) - 4, 0xFFFFFFFF);
		}

		public int getIntValue() {
			return this.min + (this.interval * (int) Math.round(this.getSnappedThumbPosition() / this.interval));
		}

		public double getSnappedThumbPosition() {
			return this.thumbPosition / (1.0D / this.range);
		}

		public double getThumbPositionForValue(int value) {
			return (value - this.min) * (1.0D / this.range);
		}

		@Override
		public boolean mouseClicked(double mouseX, double mouseY, int button) {
			this.sliderHeld = false;

			if (this.option.isAvailable() && button == 0 && this.dim.containsCursor(mouseX, mouseY)) {
				if (this.getSliderBounds().containsCursor((int) mouseX, (int) mouseY)) {
					this.setValueFromMouse(mouseX);
					this.sliderHeld = true;
				}

				return true;
			}

			return false;
		}

		@Override
		public boolean mouseReleased(double mouseX, double mouseY, int button) {
			if (button == 0) {
				this.sliderHeld = false;
			}

			return false;
		}

		private void setValueFromMouse(double d) {
			Dim2i bounds = this.getSliderBounds();
			this.setValue((d - (double) bounds.x()) / (double) bounds.width());
		}

		public void setValue(double d) {
			this.thumbPosition = MathHelper.clamp(d, 0.0D, 1.0D);

			int value = this.getIntValue();

			if (this.option.getValue() != value) {
				this.option.setValue(value);
			}
		}

		@Override
		public void setFocused(boolean focused) {
			super.setFocused(focused);

			if (!focused) {
				this.editMode = false;
			}
		}

		@Override
		public boolean keyPressed(int keyCode, char character) {
			if (!this.isFocused()) {
				return false;
			}

			if (keyCode == Keyboard.KEY_RETURN || keyCode == Keyboard.KEY_NUMPADENTER) {
				this.editMode = !this.editMode;
				return true;
			}

			if (this.editMode) {
				if (keyCode == Keyboard.KEY_LEFT) {
					this.option.setValue(MathHelper.clamp(this.option.getValue() - this.interval, this.min, this.max));
					return true;
				} else if (keyCode == Keyboard.KEY_RIGHT) {
					this.option.setValue(MathHelper.clamp(this.option.getValue() + this.interval, this.min, this.max));
					return true;
				}
			}

			return false;
		}

		@Override
		public boolean mouseDragged(double mouseX, double mouseY, int button, double deltaX, double deltaY) {
			if (this.option.isAvailable() && button == 0) {
				if (this.sliderHeld) {
					this.setValueFromMouse(mouseX);
				}

				return true;
			}

			return false;
		}

		private void setValueFromMouseScroll(double amount) {
			if (this.option.getValue() + this.interval * (int) amount <= this.max && this.option.getValue() + this.interval * (int) amount >= this.min) {
				this.option.setValue(this.option.getValue() + this.interval * (int) amount);
				this.thumbPosition = this.getThumbPositionForValue(this.option.getValue());
			}
		}

		@Override
		public boolean mouseScrolled(double mouseX, double mouseY, double amount) {
			if (this.option.isAvailable() && this.getSliderBounds().containsCursor(mouseX, mouseY) && Screen.isShiftDown()) {
				this.setValueFromMouseScroll(amount);

				return true;
			}

			return false;
		}
	}
}
