package net.irisshaders.iris.gui.element;

import net.irisshaders.iris.gui.GuiKeyEvent;
import net.irisshaders.iris.gui.GuiMouseEvent;
import net.irisshaders.iris.gui.IrisGraphics;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.widget.EntryListWidget;

import java.util.ArrayList;
import java.util.List;

public class IrisObjectSelectionList<E extends IrisObjectSelectionList.Entry> extends EntryListWidget {
	private final List<E> entries = new ArrayList<>();
	private E selected;
	private E focused;

	public IrisObjectSelectionList(Minecraft client, int width, int height, int top, int bottom, int left, int right, int itemHeight) {
		super(client, width, height, top, bottom, itemHeight);
	}

	@Override
	public int getScrollbarPosition() {
		// Position the scrollbar at the rightmost edge of the screen.
		// By default, the scrollbar is positioned moderately offset from the center.
		return width - 6;
	}

	public void select(int entry) {
		setSelected(this.children().get(entry));
	}

	public List<E> children() {
		return this.entries;
	}

	public int getItemCount() {
		return this.entries.size();
	}

	protected void addEntry(E entry) {
		entry.list = this;
		this.entries.add(entry);
	}

	protected void clearEntries() {
		this.entries.clear();
		this.selected = null;
		this.focused = null;
	}

	public E getSelected() {
		return this.selected;
	}

	public void setSelected(E entry) {
		this.selected = entry;
	}

	public E getFocused() {
		return this.focused;
	}

	public void setFocused(E entry) {
		this.focused = entry;
	}

	public void setScrollAmount(int amount) {
		this.scroll(amount - this.getScrollAmount());
	}

	public void centerScrollOn(E entry) {
		int index = this.entries.indexOf(entry);

		if (index >= 0) {
			this.setScrollAmount(index * this.entryHeight + this.entryHeight / 2 - (this.maxY - this.minY) / 2);
		}
	}

	public int getRowTop(int index) {
		return this.minY + 4 - this.getScrollAmount() + index * this.entryHeight + (this.renderHeader ? this.headerHeight : 0);
	}

	@Override
	public Entry getEntry(int index) {
		return this.entries.get(index);
	}

	@Override
	protected int size() {
		return this.entries.size();
	}

	@Override
	protected boolean isEntrySelected(int index) {
		return this.entries.get(index) == this.selected;
	}

	public boolean keyPressed(GuiKeyEvent event) {
		if (this.entries.isEmpty()) {
			return false;
		}

		if (event.isUp() || event.isDown()) {
			int index = this.focused == null ? -1 : this.entries.indexOf(this.focused);
			index = Math.max(0, Math.min(this.entries.size() - 1, index + (event.isUp() ? -1 : 1)));
			this.focused = this.entries.get(index);
			return true;
		}

		return this.focused != null && this.focused.keyPressed(event);
	}

	public abstract static class Entry implements EntryListWidget.Entry {
		protected IrisObjectSelectionList<?> list;
		private int contentX;
		private int contentY;
		private int contentWidth;
		private int contentHeight;

		public int getContentX() {
			return contentX;
		}

		public int getContentY() {
			return contentY;
		}

		public int getContentWidth() {
			return contentWidth;
		}

		public int getContentHeight() {
			return contentHeight;
		}

		public int getX() {
			return contentX;
		}

		public int getY() {
			return contentY;
		}

		public int getWidth() {
			return contentWidth;
		}

		public int getHeight() {
			return contentHeight;
		}

		public boolean isMouseOver(double mouseX, double mouseY) {
			return mouseX >= contentX && mouseX < contentX + contentWidth && mouseY >= contentY && mouseY < contentY + contentHeight;
		}

		public abstract void extractContent(IrisGraphics guiGraphics, int mouseX, int mouseY, boolean isHovered, float tickDelta);

		public boolean mouseClicked(GuiMouseEvent event, boolean doubleClick) {
			return false;
		}

		public boolean mouseReleased(GuiMouseEvent event) {
			return false;
		}

		public boolean keyPressed(GuiKeyEvent event) {
			return false;
		}

		@Override
		public void renderOutOfBounds(int index, int x, int y) {
		}

		@Override
		public void render(int index, int x, int y, int rowWidth, int rowHeight, int mouseX, int mouseY, boolean hovered) {
			this.contentX = x;
			this.contentY = y;
			this.contentWidth = rowWidth;
			this.contentHeight = rowHeight;
			this.extractContent(IrisGraphics.INSTANCE, mouseX, mouseY, hovered || (this.list != null && this.list.getFocused() == this), 0.0f);
		}

		@Override
		public boolean mouseClicked(int index, int mouseX, int mouseY, int button, int relativeX, int relativeY) {
			if (this.list != null) {
				this.list.focusEntry(this);
			}

			return this.mouseClicked(new GuiMouseEvent(mouseX, mouseY, button), false);
		}

		@Override
		public void mouseReleased(int index, int mouseX, int mouseY, int button, int relativeX, int relativeY) {
			this.mouseReleased(new GuiMouseEvent(mouseX, mouseY, button));
		}
	}

	@SuppressWarnings("unchecked")
	private void focusEntry(Entry entry) {
		this.focused = (E) entry;
	}
}
