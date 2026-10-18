package net.irisshaders.iris.gui.element.widget;

import net.irisshaders.iris.gui.GuiBounds;
import net.irisshaders.iris.gui.GuiKeyEvent;
import net.irisshaders.iris.gui.GuiMouseEvent;
import net.irisshaders.iris.gui.IrisGraphics;
import net.irisshaders.iris.gui.NavigationController;
import net.irisshaders.iris.gui.screen.ShaderPackScreen;
import net.irisshaders.iris.shaderpack.option.menu.OptionMenuElement;

public abstract class AbstractElementWidget<T extends OptionMenuElement> {
	public static final AbstractElementWidget<OptionMenuElement> EMPTY = new AbstractElementWidget<>(null) {
		@Override
		public void render(IrisGraphics guiGraphics, int mouseX, int mouseY, float tickDelta, boolean hovered) {
		}

		@Override
		public GuiBounds getRectangle() {
			return GuiBounds.EMPTY;
		}
	};
	protected final T element;
	public GuiBounds bounds = GuiBounds.EMPTY;
	private boolean focused;

	public AbstractElementWidget(T element) {
		this.element = element;
	}

	public void init(ShaderPackScreen screen, NavigationController navigation) {
	}

	public abstract void render(IrisGraphics guiGraphics, int mouseX, int mouseY, float tickDelta, boolean hovered);

	public boolean mouseClicked(GuiMouseEvent event, boolean bl2) {
		return false;
	}

	public boolean mouseReleased(GuiMouseEvent event) {
		return false;
	}

	public boolean keyPressed(GuiKeyEvent event) {
		return false;
	}

	public boolean isFocused() {
		return focused;
	}

	public void setFocused(boolean focused) {
		this.focused = focused;
	}

	public GuiBounds getRectangle() {
		return bounds;
	}
}
