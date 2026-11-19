package net.irisshaders.iris.gui.element;

import com.google.common.collect.ImmutableList;
import net.irisshaders.iris.gui.GuiBounds;
import net.irisshaders.iris.gui.GuiKeyEvent;
import net.irisshaders.iris.gui.GuiMouseEvent;
import net.irisshaders.iris.gui.GuiUtil;
import net.irisshaders.iris.gui.IrisGraphics;
import net.minecraft.client.Minecraft;
import net.minecraft.client.render.TextRenderer;
import net.minecraft.text.Text;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;

/**
 * Intended to make very simple rows of buttons easier to make
 */
public class IrisElementRow {
	private final Map<Element, Integer> elements = new HashMap<>();
	private final List<Element> orderedElements = new ArrayList<>();
	private final int spacing;
	private int x;
	private int y;
	private int width;
	private int height;

	public IrisElementRow(int spacing) {
		this.spacing = spacing;
	}

	public IrisElementRow() {
		this(1);
	}

	public int getWidth() {
		return width;
	}

	/**
	 * Adds an element to the right of this row.
	 *
	 * @param element The element to add
	 * @param width   The width of the element in this row
	 * @return {@code this}, to be used for chaining statements
	 */
	public IrisElementRow add(Element element, int width) {
		if (!this.orderedElements.contains(element)) {
			this.orderedElements.add(element);
		}
		this.elements.put(element, width);

		this.width += width + this.spacing;

		return this;
	}

	/**
	 * Modifies the width of an element.
	 *
	 * @param element The element whose width to modify
	 * @param width   The width to be assigned to the specified element
	 */
	public void setWidth(Element element, int width) {
		if (!this.elements.containsKey(element)) {
			return;
		}

		this.width -= this.elements.get(element) + 2;

		add(element, width);
	}

	/**
	 * Renders the row, with the anchor point being the top left.
	 */
	public void render(IrisGraphics guiGraphics, int x, int y, int height, int mouseX, int mouseY, float tickDelta,
		boolean rowHovered) {
		this.x = x;
		this.y = y;
		this.height = height;

		int currentX = x;

		for (Element element : this.orderedElements) {
			int currentWidth = this.elements.get(element);

			element.render(guiGraphics, currentX, y, currentWidth, height, mouseX, mouseY, tickDelta,
				rowHovered && sectionHovered(currentX, currentWidth, mouseX, mouseY));

			currentX += currentWidth + this.spacing;
		}
	}

	/**
	 * Renders the row, with the anchor point being the top right.
	 */
	public void renderRightAligned(IrisGraphics guiGraphics, int x, int y, int height, int mouseX, int mouseY, float tickDelta,
		boolean hovered) {
		render(guiGraphics, x - this.width, y, height, mouseX, mouseY, tickDelta, hovered);
	}

	private boolean sectionHovered(int sectionX, int sectionWidth, double mx, double my) {
		return mx > sectionX && mx < sectionX + sectionWidth && my > this.y && my < this.y + this.height;
	}

	private Optional<Element> getHovered(double mx, double my) {
		int currentX = this.x;

		for (Element element : this.orderedElements) {
			int currentWidth = this.elements.get(element);

			if (sectionHovered(currentX, currentWidth, mx, my)) {
				return Optional.of(element);
			}

			currentX += currentWidth + this.spacing;
		}

		return Optional.empty();
	}

	private Optional<Element> getFocused() {
		return this.orderedElements.stream().filter(Element::isFocused).findFirst();
	}

	public boolean mouseClicked(GuiMouseEvent event, boolean bl2) {
		return getHovered(event.x(), event.y()).map(element -> element.mouseClicked(event, bl2)).orElse(false);
	}

	public boolean mouseReleased(GuiMouseEvent event) {
		return getHovered(event.x(), event.y()).map(element -> element.mouseReleased(event)).orElse(false);
	}

	public boolean keyPressed(GuiKeyEvent event) {
		return getFocused().map(element -> element.keyPressed(event)).orElse(false);
	}

	public List<Element> children() {
		return ImmutableList.copyOf(this.orderedElements);
	}

	public static abstract class Element {
		public boolean disabled = false;
		private boolean hovered = false;
		private boolean focused;
		private GuiBounds bounds = GuiBounds.EMPTY;

		public void render(IrisGraphics guiGraphics, int x, int y, int width, int height, int mouseX, int mouseY, float tickDelta,
			boolean hovered) {
			this.bounds = new GuiBounds(x, y, width, height);

			GuiUtil.bindIrisWidgetsTexture();
			GuiUtil.drawButton(guiGraphics, x, y, width, height, isHovered() || isFocused(), this.disabled);

			this.hovered = hovered;
			this.renderLabel(guiGraphics, x, y, width, height, mouseX, mouseY, tickDelta, hovered);
		}

		public abstract void renderLabel(IrisGraphics guiGraphics, int x, int y, int width, int height, int mouseX, int mouseY,
			float tickDelta, boolean hovered);

		public boolean isHovered() {
			return hovered;
		}

		public boolean isFocused() {
			return focused;
		}

		public void setFocused(boolean focused) {
			this.focused = focused;
		}

		public boolean mouseClicked(GuiMouseEvent event, boolean bl2) {
			return false;
		}

		public boolean mouseReleased(GuiMouseEvent event) {
			return false;
		}

		public boolean keyPressed(GuiKeyEvent event) {
			return false;
		}

		public GuiBounds getRectangle() {
			return bounds;
		}
	}

	@SuppressWarnings("unchecked")
	public static abstract class ButtonElement<T extends ButtonElement<T>> extends Element {
		private final Function<T, Boolean> onClick;

		protected ButtonElement(Function<T, Boolean> onClick) {
			this.onClick = onClick;
		}

		@Override
		public boolean mouseClicked(GuiMouseEvent event, boolean bl2) {
			if (this.disabled) {
				return false;
			}

			if (event.button() == GuiMouseEvent.LEFT) {
				return this.onClick.apply((T) this);
			}

			return super.mouseClicked(event, bl2);
		}

		@Override
		public boolean keyPressed(GuiKeyEvent event) {
			if (event.isConfirmation()) {
				return this.onClick.apply((T) this);
			}
			return false;
		}
	}

	/**
	 * A clickable button element that uses a {@link net.irisshaders.iris.gui.GuiUtil.Icon} as its label.
	 */
	public static class IconButtonElement extends ButtonElement<IconButtonElement> {
		public final GuiUtil.Icon icon;
		public final GuiUtil.Icon hoveredIcon;

		public IconButtonElement(GuiUtil.Icon icon, GuiUtil.Icon hoveredIcon, Function<IconButtonElement, Boolean> onClick) {
			super(onClick);
			this.icon = icon;
			this.hoveredIcon = hoveredIcon;
		}

		public IconButtonElement(GuiUtil.Icon icon, Function<IconButtonElement, Boolean> onClick) {
			this(icon, icon, onClick);
		}

		@Override
		public void renderLabel(IrisGraphics guiGraphics, int x, int y, int width, int height, int mouseX, int mouseY,
			float tickDelta, boolean hovered) {
			int iconX = x + (int) ((width - this.icon.getWidth()) * 0.5);
			int iconY = y + (int) ((height - this.icon.getHeight()) * 0.5);

			GuiUtil.bindIrisWidgetsTexture();
			if (!this.disabled && (hovered || isFocused())) {
				this.hoveredIcon.draw(guiGraphics, iconX, iconY);
			} else {
				this.icon.draw(guiGraphics, iconX, iconY);
			}
		}
	}

	/**
	 * A clickable button element that uses a text component as its label.
	 */
	public static class TextButtonElement extends ButtonElement<TextButtonElement> {
		protected final TextRenderer font;
		public Text text;

		public TextButtonElement(Text text, Function<TextButtonElement, Boolean> onClick) {
			super(onClick);

			this.font = Minecraft.getInstance().textRenderer;
			this.text = text;
		}

		@Override
		public void renderLabel(IrisGraphics guiGraphics, int x, int y, int width, int height, int mouseX, int mouseY,
			float tickDelta, boolean hovered) {
			int textX = x + (int) ((width - GuiUtil.width(this.text)) * 0.5);
			int textY = y + (int) ((height - 8) * 0.5);

			guiGraphics.text(this.font, this.text, textX, textY, 0xFFFFFFFF);
		}
	}
}
