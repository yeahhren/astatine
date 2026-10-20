package yt.ren.astatine.gui.options.control;

import net.minecraft.text.Formatting;
import org.lwjgl.input.Keyboard;
import yt.ren.astatine.gui.Dim2i;
import yt.ren.astatine.gui.options.Option;
import yt.ren.astatine.gui.widget.AbstractWidget;

public class ControlElement<T> extends AbstractWidget {
	protected final Option<T> option;

	protected final Dim2i dim;

	public ControlElement(Option<T> option, Dim2i dim) {
		this.option = option;
		this.dim = dim;
		option.setDim2i(dim);
	}

	@Override
	public void render(int mouseX, int mouseY, float delta) {
		String name = this.option.getName();
		String label;

		if ((this.hovered || this.isFocused()) && this.font.getWidth(name) > (this.dim.width() - this.option.getControl().getMaxWidth())) {
			name = name.substring(0, Math.min(name.length(), 10)) + "...";
		}

		if (this.option.isAvailable()) {
			if (this.option.hasChanged()) {
				label = Formatting.ITALIC + name + " *";
			} else {
				label = Formatting.WHITE + name;
			}
		} else {
			label = String.valueOf(Formatting.GRAY) + Formatting.STRIKETHROUGH + name;
		}

		if (this.option.isHighlight()) {
			String replacement = this.option.getSelected() ? Formatting.DARK_GREEN.toString() : Formatting.YELLOW.toString();

			label = label.replace(Formatting.WHITE.toString(), Formatting.WHITE + replacement);
			label = label.replace(Formatting.STRIKETHROUGH.toString(), Formatting.STRIKETHROUGH + replacement);
			label = label.replace(Formatting.ITALIC.toString(), Formatting.ITALIC + replacement);
		}

		this.hovered = this.isMouseOver(mouseX, mouseY);

		this.drawRect(this.dim.x(), this.dim.y(), this.dim.getLimitX(), this.dim.getLimitY(), this.hovered ? 0xE0000000 : 0x90000000);
		this.drawString(label, this.dim.x() + 6, this.dim.getCenterY() - 4, 0xFFFFFFFF);

		if (this.isFocused()) {
			this.drawBorder(this.dim.x(), this.dim.y(), this.dim.getLimitX(), this.dim.getLimitY(), -1);
		}
	}

	public Option<T> getOption() {
		return this.option;
	}

	public Dim2i getDimensions() {
		return this.dim;
	}

	@Override
	public boolean isMouseOver(double mouseX, double mouseY) {
		return this.dim.containsCursor(mouseX, mouseY);
	}

	@Override
	public boolean isFocusable() {
		return this.option.isAvailable();
	}

	protected static boolean isToggleKey(int keyCode) {
		return keyCode == Keyboard.KEY_RETURN || keyCode == Keyboard.KEY_SPACE
			|| keyCode == Keyboard.KEY_NUMPADENTER;
	}
}
