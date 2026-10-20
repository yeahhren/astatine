package yt.ren.astatine.gui.widget;

import org.lwjgl.input.Keyboard;
import yt.ren.astatine.gui.Dim2i;

import java.util.Objects;

public class FlatButtonWidget extends AbstractWidget {
	private final Dim2i dim;
	private final Runnable action;

	private Style style = Style.defaults();

	private boolean selected;
	private boolean enabled = true;
	private boolean visible = true;
	private boolean leftAligned;

	private String label;

	public FlatButtonWidget(Dim2i dim, String label, Runnable action) {
		this.dim = dim;
		this.label = label;
		this.action = action;
	}

	@Override
	public void render(int mouseX, int mouseY, float delta) {
		if (!this.visible) {
			return;
		}

		this.hovered = this.dim.containsCursor(mouseX, mouseY);

		int backgroundColor = this.enabled ? (this.hovered ? this.style.bgHovered : this.style.bgDefault) : this.style.bgDisabled;
		int textColor = this.enabled ? this.style.textDefault : this.style.textDisabled;

		int strWidth = this.font.getWidth(this.label);

		this.drawRect(this.dim.x(), this.dim.y(), this.dim.getLimitX(), this.dim.getLimitY(), backgroundColor);

		if (this.leftAligned) {
			this.drawString(this.label, this.dim.x() + 10, this.dim.getCenterY() - 4, textColor);
		} else {
			this.drawString(this.label, this.dim.getCenterX() - (strWidth / 2), this.dim.getCenterY() - 4, textColor);
		}

		if (this.enabled && this.selected) {
			if (this.leftAligned) {
				this.drawRect(this.dim.x(), this.dim.y(), this.dim.x() + 1, this.dim.getLimitY(), 0xFF94E4D3);
			} else {
				this.drawRect(this.dim.x(), this.dim.getLimitY() - 1, this.dim.getLimitX(), this.dim.getLimitY(), 0xFF94E4D3);
			}
		}

		if (this.enabled && this.isFocused()) {
			this.drawBorder(this.dim.x(), this.dim.y(), this.dim.getLimitX(), this.dim.getLimitY(), -1);
		}
	}

	public void setStyle(Style style) {
		this.style = Objects.requireNonNull(style);
	}

	public void setSelected(boolean selected) {
		this.selected = selected;
	}

	public void setLeftAligned(boolean leftAligned) {
		this.leftAligned = leftAligned;
	}

	@Override
	public boolean mouseClicked(double mouseX, double mouseY, int button) {
		if (!this.enabled || !this.visible) {
			return false;
		}

		if (button == 0 && this.dim.containsCursor(mouseX, mouseY)) {
			this.doAction();

			return true;
		}

		return false;
	}

	@Override
	public boolean keyPressed(int keyCode, char character) {
		if (!this.isFocused()) {
			return false;
		}

		if (keyCode == Keyboard.KEY_RETURN || keyCode == Keyboard.KEY_SPACE || keyCode == Keyboard.KEY_NUMPADENTER) {
			this.doAction();
			return true;
		}

		return false;
	}

	@Override
	public boolean isFocusable() {
		return this.enabled && this.visible;
	}

	private void doAction() {
		this.action.run();
		this.playClickSound();
	}

	public void setEnabled(boolean enabled) {
		this.enabled = enabled;
	}

	public void setVisible(boolean visible) {
		this.visible = visible;
	}

	public void setLabel(String label) {
		this.label = label;
	}

	public String getLabel() {
		return this.label;
	}

	public Dim2i getDimensions() {
		return this.dim;
	}

	public static class Style {
		public int bgHovered, bgDefault, bgDisabled;
		public int textDefault, textDisabled;

		public static Style defaults() {
			Style style = new Style();
			style.bgHovered = 0xE0000000;
			style.bgDefault = 0x90000000;
			style.bgDisabled = 0x60000000;
			style.textDefault = 0xFFFFFFFF;
			style.textDisabled = 0x90FFFFFF;

			return style;
		}
	}
}
