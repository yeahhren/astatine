package yt.ren.astatine.gui.frame;

import yt.ren.astatine.gui.Clip;
import yt.ren.astatine.gui.Dim2i;
import yt.ren.astatine.gui.options.control.ControlElement;
import yt.ren.astatine.gui.widget.AbstractWidget;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * A widget containing other widgets, with the event routing of modern Minecraft's {@code ContainerEventHandler}.
 */
public abstract class AbstractFrame extends AbstractWidget {
	protected final Dim2i dim;
	protected final List<AbstractWidget> children = new ArrayList<>();
	protected final List<ControlElement<?>> controlElements = new ArrayList<>();
	protected boolean renderOutline;
	private AbstractWidget focusedChild;
	private boolean dragging;
	private Consumer<AbstractWidget> focusListener;

	public AbstractFrame(Dim2i dim, boolean renderOutline) {
		this.dim = dim;
		this.renderOutline = renderOutline;
	}

	public void buildFrame() {
		for (AbstractWidget element : this.children) {
			if (element instanceof AbstractFrame frame) {
				this.controlElements.addAll(frame.controlElements);
			}
			if (element instanceof ControlElement<?> controlElement) {
				this.controlElements.add(controlElement);
			}
		}
	}

	@Override
	public void render(int mouseX, int mouseY, float delta) {
		if (this.renderOutline) {
			this.drawBorder(this.dim.x(), this.dim.y(), this.dim.getLimitX(), this.dim.getLimitY(), 0xFFAAAAAA);
		}
		for (AbstractWidget widget : this.children) {
			widget.render(mouseX, mouseY, delta);
		}
	}

	public void applyScissor(int x, int y, int width, int height, Runnable action) {
		Clip.run(x, y, width, height, action);
	}

	public void registerFocusListener(Consumer<AbstractWidget> focusListener) {
		this.focusListener = focusListener;
	}

	/**
	 * Adds every widget that keyboard navigation can reach, in navigation order.
	 */
	public void collectFocusables(List<AbstractWidget> out) {
		for (AbstractWidget child : this.children) {
			if (child instanceof AbstractFrame frame) {
				frame.collectFocusables(out);
			} else if (child.isFocusable()) {
				out.add(child);
			}
		}
	}

	/**
	 * Called when keyboard navigation focuses a widget, so frames containing it can scroll it into view.
	 *
	 * @return whether the widget is inside this frame
	 */
	public boolean onKeyboardFocus(AbstractWidget widget) {
		for (AbstractWidget child : this.children) {
			if (child == widget || (child instanceof AbstractFrame frame && frame.onKeyboardFocus(widget))) {
				if (this.focusListener != null) {
					this.focusListener.accept(widget);
				}

				return true;
			}
		}

		return false;
	}

	public void clearFocus() {
		for (AbstractWidget child : this.children) {
			if (child instanceof AbstractFrame frame) {
				frame.clearFocus();
			} else {
				child.setFocused(false);
			}
		}
	}

	public boolean isDragging() {
		return this.dragging;
	}

	public void setDragging(boolean dragging) {
		this.dragging = dragging;
	}

	public AbstractWidget getFocusedChild() {
		return this.focusedChild;
	}

	public void setFocusedChild(AbstractWidget focused) {
		this.focusedChild = focused;
	}

	public List<AbstractWidget> children() {
		return this.children;
	}

	@Override
	public boolean mouseClicked(double mouseX, double mouseY, int button) {
		for (AbstractWidget child : this.children) {
			if (child.mouseClicked(mouseX, mouseY, button)) {
				this.setFocusedChild(child);

				if (button == 0) {
					this.setDragging(true);
				}

				return true;
			}
		}

		return false;
	}

	@Override
	public boolean mouseReleased(double mouseX, double mouseY, int button) {
		if (button == 0 && this.isDragging()) {
			this.setDragging(false);

			if (this.focusedChild != null) {
				return this.focusedChild.mouseReleased(mouseX, mouseY, button);
			}
		}

		return false;
	}

	@Override
	public boolean mouseDragged(double mouseX, double mouseY, int button, double deltaX, double deltaY) {
		return this.focusedChild != null && this.isDragging() && button == 0
			&& this.focusedChild.mouseDragged(mouseX, mouseY, button, deltaX, deltaY);
	}

	@Override
	public boolean mouseScrolled(double mouseX, double mouseY, double amount) {
		for (AbstractWidget child : this.children) {
			if (child.isMouseOver(mouseX, mouseY)) {
				return child.mouseScrolled(mouseX, mouseY, amount);
			}
		}

		return false;
	}

	@Override
	public boolean keyPressed(int keyCode, char character) {
		for (AbstractWidget child : this.children) {
			if (child.keyPressed(keyCode, character)) {
				return true;
			}
		}

		return false;
	}

	@Override
	public boolean charTyped(char character) {
		for (AbstractWidget child : this.children) {
			if (child.charTyped(character)) {
				return true;
			}
		}

		return false;
	}

	@Override
	public boolean isMouseOver(double mouseX, double mouseY) {
		return this.dim.containsCursor(mouseX, mouseY);
	}
}
