package yt.ren.astatine.gui.frame.components;

import net.minecraft.SharedConstants;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.render.platform.GlStateManager;
import net.minecraft.client.resource.language.I18n;
import net.minecraft.util.math.MathHelper;
import org.lwjgl.input.Keyboard;
import yt.ren.astatine.gui.AstatineOptionsScreen;
import yt.ren.astatine.gui.Clip;
import yt.ren.astatine.gui.Dim2i;
import yt.ren.astatine.gui.StringUtils;
import yt.ren.astatine.gui.options.Option;
import yt.ren.astatine.gui.options.OptionPage;
import yt.ren.astatine.gui.widget.AbstractWidget;

import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

public class SearchTextFieldComponent extends AbstractWidget {
	private static final int GL_OR_REVERSE = 0x150B;

	protected final Dim2i dim;
	protected final List<OptionPage> pages;
	private final AtomicReference<String> tabFrameSelectedTab;
	private final AtomicReference<Integer> tabFrameScrollBarOffset;
	private final AtomicReference<Integer> optionPageScrollBarOffset;
	private final int tabDimHeight;
	private final AstatineOptionsScreen optionsScreen;
	private final AtomicReference<String> lastSearch;
	private final AtomicReference<Integer> lastSearchIndex;
	protected boolean selecting;
	protected String text = "";
	protected int maxLength = 100;
	protected boolean visible = true;
	protected boolean editable = true;
	private int firstCharacterIndex;
	private int selectionStart;
	private int selectionEnd;
	private int lastCursorPosition = this.getCursor();

	public SearchTextFieldComponent(Dim2i dim, List<OptionPage> pages, AtomicReference<String> tabFrameSelectedTab, AtomicReference<Integer> tabFrameScrollBarOffset, AtomicReference<Integer> optionPageScrollBarOffset, int tabDimHeight, AstatineOptionsScreen optionsScreen, AtomicReference<String> lastSearch, AtomicReference<Integer> lastSearchIndex) {
		this.dim = dim;
		this.pages = pages;
		this.tabFrameSelectedTab = tabFrameSelectedTab;
		this.tabFrameScrollBarOffset = tabFrameScrollBarOffset;
		this.optionPageScrollBarOffset = optionPageScrollBarOffset;
		this.tabDimHeight = tabDimHeight;
		this.optionsScreen = optionsScreen;
		this.lastSearch = lastSearch;
		this.lastSearchIndex = lastSearchIndex;
		if (!lastSearch.get().trim().isEmpty()) {
			this.write(lastSearch.get());
		}
	}

	@Override
	public void render(int mouseX, int mouseY, float delta) {
		if (!this.isVisible()) {
			return;
		}
		if (!this.isFocused() && this.text.isBlank()) {
			this.drawString(I18n.translate("astatine.search_bar_empty"), this.dim.x() + 6, this.dim.y() + 6, 0xFFAAAAAA);
		}

		this.drawRect(this.dim.x(), this.dim.y(), this.dim.getLimitX(), this.dim.getLimitY(), this.isFocused() ? 0xE0000000 : 0x90000000);
		int j = this.selectionStart - this.firstCharacterIndex;
		int k = this.selectionEnd - this.firstCharacterIndex;
		String string = this.font.trim(this.text.substring(this.firstCharacterIndex), this.getInnerWidth());
		boolean bl = j >= 0 && j <= string.length();
		int l = this.dim.x() + 6;
		int m = this.dim.y() + 6;
		int n = l;
		if (k > string.length()) {
			k = string.length();
		}
		if (!string.isEmpty()) {
			String string2 = bl ? string.substring(0, j) : string;
			n = this.font.drawWithShadow(string2, n, m, 0xFFFFFFFF);
		}
		boolean bl3 = this.selectionStart < this.text.length() || this.text.length() >= this.getMaxLength();
		int o = n;
		if (!bl) {
			o = j > 0 ? l + this.dim.width() - 12 : l;
		} else if (bl3) {
			--o;
			--n;
		}
		if (!string.isEmpty() && bl && j < string.length()) {
			this.font.drawWithShadow(string.substring(j), n, m, 0xFFFFFFFF);
		}
		// Cursor
		if (this.isFocused()) {
			Clip.fill(o, m - 1, o + 1, m + 1 + this.font.fontHeight, 0xFFD0D0D0);
		}
		// Highlighted text
		if (k != j) {
			int p = l + this.font.getWidth(string.substring(0, k));
			this.drawSelectionHighlight(o, m - 1, p - 1, m + 1 + this.font.fontHeight);
		}
	}

	@Override
	public boolean mouseClicked(double mouseX, double mouseY, int button) {
		int i = MathHelper.floor(mouseX) - this.dim.x() - 6;
		String string = this.font.trim(this.text.substring(this.firstCharacterIndex), this.getInnerWidth());
		this.setCursor(this.font.trim(string, i).length() + this.firstCharacterIndex);

		this.setFocused(this.dim.containsCursor(mouseX, mouseY));
		this.pages.forEach(page -> page.getOptions().forEach(option -> option.setSelected(false)));
		return this.isFocused();
	}

	@Override
	public boolean isFocusable() {
		return this.visible;
	}

	private void drawSelectionHighlight(int x1, int y1, int x2, int y2) {
		int i;
		if (x1 < x2) {
			i = x1;
			x1 = x2;
			x2 = i;
		}
		if (y1 < y2) {
			i = y1;
			y1 = y2;
			y2 = i;
		}
		if (x2 > this.dim.x() + this.dim.width()) {
			x2 = this.dim.x() + this.dim.width();
		}
		if (x1 > this.dim.x() + this.dim.width()) {
			x1 = this.dim.x() + this.dim.width();
		}

		// Same inverting highlight as vanilla's text field.
		GlStateManager.enableColorLogicOp();
		GlStateManager.logicOp(GL_OR_REVERSE);
		Clip.fill(x2, y2, x1, y1, 0xFF0000FF);
		GlStateManager.disableColorLogicOp();
	}

	private int getMaxLength() {
		return this.maxLength;
	}

	public String getSelectedText() {
		int i = Math.min(this.selectionStart, this.selectionEnd);
		int j = Math.max(this.selectionStart, this.selectionEnd);
		return this.text.substring(i, j);
	}

	public void write(String text) {
		int i = Math.min(this.selectionStart, this.selectionEnd);
		int j = Math.max(this.selectionStart, this.selectionEnd);
		int k = this.maxLength - this.text.length() - (i - j);
		String string = SharedConstants.stripInvalidChars(text);
		int l = string.length();
		if (k < l) {
			string = string.substring(0, k);
			l = k;
		}

		this.text = new StringBuilder(this.text).replace(i, j, string).toString();
		this.setSelectionStart(i + l);
		this.setSelectionEnd(this.selectionStart);
		this.onChanged(this.text);
	}

	private void onChanged(String newText) {
		this.pages.forEach(page -> page.getOptions().forEach(option -> option.setHighlight(false)));

		this.lastSearch.set(newText.trim());
		if (this.editable) {
			if (!newText.trim().isEmpty()) {
				List<Option<?>> fuzzy = StringUtils.fuzzySearch(this.pages, newText, 2);
				fuzzy.forEach(option -> option.setHighlight(true));
			}
		}
	}

	private void erase(int offset) {
		if (Screen.isControlDown()) {
			this.eraseWords(offset);
		} else {
			this.eraseCharacters(offset);
		}
	}

	public void eraseWords(int wordOffset) {
		if (!this.text.isEmpty()) {
			if (this.selectionEnd != this.selectionStart) {
				this.write("");
			} else {
				this.eraseCharacters(this.getWordSkipPosition(wordOffset) - this.selectionStart);
			}
		}
	}

	public void eraseCharacters(int characterOffset) {
		if (!this.text.isEmpty()) {
			if (this.selectionEnd != this.selectionStart) {
				this.write("");
			} else {
				int i = this.getCursorPosWithOffset(characterOffset);
				int j = Math.min(i, this.selectionStart);
				int k = Math.max(i, this.selectionStart);
				if (j != k) {
					this.text = new StringBuilder(this.text).delete(j, k).toString();
					this.setCursor(j);
					this.onChanged(this.text);
				}
			}
		}
	}

	public int getWordSkipPosition(int wordOffset) {
		return this.getWordSkipPosition(wordOffset, this.getCursor());
	}

	private int getWordSkipPosition(int wordOffset, int cursorPosition) {
		int i = cursorPosition;
		boolean bl = wordOffset < 0;
		int j = Math.abs(wordOffset);

		for (int k = 0; k < j; ++k) {
			if (!bl) {
				int l = this.text.length();
				i = this.text.indexOf(32, i);
				if (i == -1) {
					i = l;
				} else {
					while (i < l && this.text.charAt(i) == ' ') {
						++i;
					}
				}
			} else {
				while (i > 0 && this.text.charAt(i - 1) == ' ') {
					--i;
				}

				while (i > 0 && this.text.charAt(i - 1) != ' ') {
					--i;
				}
			}
		}

		return i;
	}

	public int getCursor() {
		return this.selectionStart;
	}

	public void setCursor(int cursor) {
		this.setSelectionStart(cursor);
		if (!this.selecting) {
			this.setSelectionEnd(this.selectionStart);
		}

		this.onChanged(this.text);
	}

	public void moveCursor(int offset) {
		this.setCursor(this.getCursorPosWithOffset(offset));
	}

	private int getCursorPosWithOffset(int offset) {
		return MathHelper.clamp(this.selectionStart + offset, 0, this.text.length());
	}

	public void setSelectionStart(int cursor) {
		this.selectionStart = MathHelper.clamp(cursor, 0, this.text.length());
	}

	public void setCursorToStart() {
		this.setCursor(0);
	}

	public void setCursorToEnd() {
		this.setCursor(this.text.length());
	}

	public void setSelectionEnd(int index) {
		int i = this.text.length();
		this.selectionEnd = MathHelper.clamp(index, 0, i);
		if (this.font != null) {
			if (this.firstCharacterIndex > i) {
				this.firstCharacterIndex = i;
			}

			int j = this.getInnerWidth();
			String string = this.font.trim(this.text.substring(this.firstCharacterIndex), j);
			int k = string.length() + this.firstCharacterIndex;
			if (this.selectionEnd == this.firstCharacterIndex) {
				this.firstCharacterIndex -= this.font.trim(this.text, j, true).length();
			}

			if (this.selectionEnd > k) {
				this.firstCharacterIndex += this.selectionEnd - k;
			} else if (this.selectionEnd <= this.firstCharacterIndex) {
				this.firstCharacterIndex -= this.firstCharacterIndex - this.selectionEnd;
			}

			this.firstCharacterIndex = MathHelper.clamp(this.firstCharacterIndex, 0, i);
		}
	}

	public boolean isActive() {
		return this.isVisible() && this.isFocused() && this.isEditable();
	}

	@Override
	public boolean charTyped(char chr) {
		if (!this.isActive()) {
			return false;
		}
		if (SharedConstants.isValidChatChar(chr)) {
			if (this.editable) {
				this.lastSearch.set(this.text.trim());
				this.write(Character.toString(chr));
				this.lastSearchIndex.set(0);
			}
			return true;
		}
		return false;
	}

	@Override
	public boolean keyPressed(int keyCode, char character) {
		if (!this.isActive()) {
			return false;
		}

		this.pages.forEach(page -> page.getOptions().forEach(option -> option.setSelected(false)));
		this.selecting = Screen.isShiftDown();
		if (Screen.isSelectAll(keyCode)) {
			this.setCursorToEnd();
			this.setSelectionEnd(0);
			return true;
		} else if (Screen.isCopy(keyCode)) {
			Screen.setClipboard(this.getSelectedText());
			return true;
		} else if (Screen.isPaste(keyCode)) {
			if (this.editable) {
				this.write(Screen.getClipboard());
			}

			return true;
		} else if (Screen.isCut(keyCode)) {
			Screen.setClipboard(this.getSelectedText());
			if (this.editable) {
				this.write("");
			}

			return true;
		}

		if (keyCode == Keyboard.KEY_RETURN || keyCode == Keyboard.KEY_NUMPADENTER) {
			if (this.editable) {
				this.jumpToNextResult();
			}
			return true;
		} else if (keyCode == Keyboard.KEY_BACK) {
			if (this.editable) {
				this.selecting = false;
				this.erase(-1);
				this.selecting = Screen.isShiftDown();
			}
			return true;
		} else if (keyCode == Keyboard.KEY_DELETE) {
			if (this.editable) {
				this.selecting = false;
				this.erase(1);
				this.selecting = Screen.isShiftDown();
			}
			return true;
		} else if (keyCode == Keyboard.KEY_RIGHT) {
			if (Screen.isControlDown()) {
				this.setCursor(this.getWordSkipPosition(1));
			} else {
				this.moveCursor(1);
			}
			boolean state = this.getCursor() != this.lastCursorPosition && this.getCursor() != this.text.length() + 1;
			this.lastCursorPosition = this.getCursor();
			return state;
		} else if (keyCode == Keyboard.KEY_LEFT) {
			if (Screen.isControlDown()) {
				this.setCursor(this.getWordSkipPosition(-1));
			} else {
				this.moveCursor(-1);
			}
			boolean state = this.getCursor() != this.lastCursorPosition && this.getCursor() != 0;
			this.lastCursorPosition = this.getCursor();
			return state;
		} else if (keyCode == Keyboard.KEY_HOME) {
			this.setCursorToStart();
			return true;
		} else if (keyCode == Keyboard.KEY_END) {
			this.setCursorToEnd();
			return true;
		}

		return false;
	}

	/**
	 * Selects the next highlighted option, switching to its page and scrolling it into view.
	 */
	private void jumpToNextResult() {
		int count = 0;
		for (OptionPage page : this.pages) {
			for (Option<?> option : page.getOptions()) {
				if (option.isHighlight() && option.getParentDimension() != null) {
					if (count == this.lastSearchIndex.get()) {
						Dim2i optionDim = option.getDim2i();
						Dim2i parentDim = option.getParentDimension();
						int maxOffset = parentDim.height() - this.tabDimHeight;
						int input = optionDim.y() - parentDim.y();
						int inputOffset = input + optionDim.height() == parentDim.height() ? parentDim.height() : input;
						int offset = inputOffset * maxOffset / parentDim.height();

						int total = this.pages.stream().mapToInt(page2 -> (int) page2.getOptions().stream().filter(Option::isHighlight).count()).sum();

						int value = total == this.lastSearchIndex.get() + 1 ? 0 : this.lastSearchIndex.get() + 1;
						option.setSelected(true);
						this.lastSearchIndex.set(value);
						this.tabFrameSelectedTab.set(page.getName());
						this.tabFrameScrollBarOffset.set(0);

						this.optionPageScrollBarOffset.set(offset);
						this.setFocused(false);
						this.optionsScreen.rebuildUI();
						return;
					}
					count++;
				}
			}
		}
	}

	public boolean isVisible() {
		return this.visible;
	}

	public boolean isEditable() {
		return this.editable;
	}

	public int getInnerWidth() {
		return this.dim.width() - 12;
	}
}
