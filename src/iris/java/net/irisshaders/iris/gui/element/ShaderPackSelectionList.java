package net.irisshaders.iris.gui.element;

import net.irisshaders.iris.Iris;
import net.irisshaders.iris.gui.GuiKeyEvent;
import net.irisshaders.iris.gui.GuiMouseEvent;
import net.irisshaders.iris.gui.GuiUtil;
import net.irisshaders.iris.gui.IrisGraphics;
import net.irisshaders.iris.gui.screen.ShaderPackScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screen.game.ConfirmChatLinkScreen;
import net.minecraft.client.render.TextRenderer;
import net.minecraft.text.Formatting;
import net.minecraft.text.Text;

import java.io.IOException;
import java.nio.file.FileSystems;
import java.nio.file.StandardWatchEventKinds;
import java.nio.file.WatchEvent;
import java.nio.file.WatchKey;
import java.nio.file.WatchService;
import java.util.List;
import java.util.function.Function;

public class ShaderPackSelectionList extends IrisObjectSelectionList<ShaderPackSelectionList.BaseEntry> {
	public static final String DOWNLOAD_LINK = "https://modrinth.com/shaders";
	public static final int DOWNLOAD_CONFIRMATION = 7031;
	private static final Text PACK_LIST_LABEL = GuiUtil.withStyle(GuiUtil.translatable("pack.iris.list.label"), Formatting.ITALIC,
		Formatting.GRAY);
	private final ShaderPackScreen screen;
	private final TopButtonRowEntry topButtonRow;
	private final WatchService watcher;
	private final WatchKey key;
	private final PinnedEntry downloadButton;
	private boolean keyValid;
	private ShaderPackEntry applied = null;

	public ShaderPackSelectionList(ShaderPackScreen screen, Minecraft client, int width, int height, int top, int bottom, int left,
		int right) {
		super(client, width, height, top + 4, bottom, left, right, 20);
		WatchKey key1;
		WatchService watcher1;

		this.screen = screen;
		this.topButtonRow = new TopButtonRowEntry(this, Iris.getIrisConfig().areShadersEnabled());
		this.downloadButton = new PinnedEntry(GuiUtil.literal("Download Shaders"),
			() -> this.minecraft.openScreen(new ConfirmChatLinkScreen(this.screen, DOWNLOAD_LINK, DOWNLOAD_CONFIRMATION, true)), this);
		try {
			watcher1 = FileSystems.getDefault().newWatchService();
			key1 = Iris.getShaderpacksDirectory()
				.register(watcher1, StandardWatchEventKinds.ENTRY_CREATE, StandardWatchEventKinds.ENTRY_MODIFY,
					StandardWatchEventKinds.ENTRY_DELETE);
			keyValid = true;
		} catch (IOException e) {
			Iris.logger.error("Couldn't register file watcher!", e);
			watcher1 = null;
			key1 = null;
			keyValid = false;
		}

		this.key = key1;
		this.watcher = watcher1;
		refresh();
	}

	@Override
	public boolean keyPressed(GuiKeyEvent event) {
		if (event.isUp()) {
			if (getFocused() == this.children().getFirst())
				return true;
		}

		return super.keyPressed(event);
	}

	@Override
	public void render(int pInt1, int pInt2, float pFloat3) {
		if (keyValid) {
			for (WatchEvent<?> event : key.pollEvents()) {
				if (event.kind() == StandardWatchEventKinds.OVERFLOW)
					continue;

				refresh();
				break;
			}

			keyValid = key.reset();
		}

		super.render(pInt1, pInt2, pFloat3);
	}

	public void close() throws IOException {
		if (key != null) {
			key.cancel();
		}

		if (watcher != null) {
			watcher.close();
		}
	}

	@Override
	public int getRowWidth() {
		return Math.min(308, width - 50);
	}

	@Override
	public int getRowTop(int index) {
		return super.getRowTop(index) + 2;
	}

	public void refresh() {
		this.clearEntries();

		List<String> names;

		try {
			names = Iris.getShaderpacksDirectoryManager().enumerate();
		} catch (Throwable e) {
			Iris.logger.error("Error reading files while constructing selection UI", e);

			// Not translating this since it's going to be seen very rarely,
			// We're just trying to get more information on a seemingly untraceable bug:
			// - https://github.com/IrisShaders/Iris/issues/785
			this.addLabelEntries(GuiUtil.empty(),
				GuiUtil.withStyle(GuiUtil.literal("There was an error reading your shaderpacks directory"), Formatting.RED, Formatting.BOLD),
				GuiUtil.empty(), GuiUtil.literal("Check your logs for more information."),
				GuiUtil.literal("Please file an issue report including a log file."),
				GuiUtil.literal("If you are able to identify the file causing this, " + "please include it in your report as well."),
				GuiUtil.literal("Note that this might be an issue with folder " + "permissions; ensure those are correct first."));

			return;
		}

		this.addEntry(topButtonRow);

		if (names.isEmpty()) {
			this.addEntry(downloadButton);
		}

		// Only allow the enable/disable shaders button if the user has
		// added a shader pack. Otherwise, the button will be disabled.
		topButtonRow.allowEnableShadersButton = !names.isEmpty();

		int index = 0;
		String selectedName = Iris.getIrisConfig().getShaderPackName().orElse(null);
		ShaderPackEntry selectedPack = null;

		for (String name : names) {
			index++;
			ShaderPackEntry entry = addPackEntry(index, name);
			if (name.equals(selectedName)) {
				selectedPack = entry;
			}
		}
		if (selectedPack != null) {
			setSelected(selectedPack);
			setFocused(selectedPack);
			centerScrollOn(selectedPack);
			setApplied(selectedPack);
		}

		this.addLabelEntries(PACK_LIST_LABEL);
	}

	public ShaderPackEntry addPackEntry(int index, String name) {
		ShaderPackEntry entry = new ShaderPackEntry(index, this, name);
		this.addEntry(entry);
		return entry;
	}

	public void addLabelEntries(Text... lines) {
		for (Text text : lines) {
			this.addEntry(new LabelEntry(text));
		}
	}

	public void select(String name) {
		for (int i = 0; i < getItemCount(); i++) {
			BaseEntry entry = this.children().get(i);

			if (entry instanceof ShaderPackEntry && ((ShaderPackEntry) entry).packName.equals(name)) {
				setSelected(entry);

				return;
			}
		}
	}

	public ShaderPackEntry getApplied() {
		return this.applied;
	}

	public void setApplied(ShaderPackEntry entry) {
		this.applied = entry;
	}

	public TopButtonRowEntry getTopButtonRow() {
		return topButtonRow;
	}

	public static abstract class BaseEntry extends IrisObjectSelectionList.Entry {
		protected BaseEntry() {
		}
	}

	public static class LabelEntry extends BaseEntry {
		private final Text label;

		public LabelEntry(Text label) {
			this.label = label;
		}

		@Override
		public void extractContent(IrisGraphics guiGraphics, int mouseX, int mouseY, boolean isHovered, float tickDelta) {
			// Draw dividing line
			int x = getContentX();
			int y = getContentY();
			int entryWidth = getContentWidth();
			int entryHeight = getContentHeight();

			guiGraphics.centeredText(Minecraft.getInstance().textRenderer, label, (x + entryWidth / 2) - 2, y + (entryHeight - 11) / 2,
				0xFFC2C2C2);
		}
	}

	public static class TopButtonRowEntry extends BaseEntry {
		private static final Text NONE_PRESENT_LABEL = GuiUtil.withStyle(GuiUtil.translatable("options.iris.shaders.nonePresent"),
			Formatting.GRAY);
		private static final Text SHADERS_DISABLED_LABEL = GuiUtil.translatable("options.iris.shaders.disabled");
		private static final Text SHADERS_ENABLED_LABEL = GuiUtil.translatable("options.iris.shaders.enabled");

		private final ShaderPackSelectionList list;

		public boolean allowEnableShadersButton = true;
		public boolean shadersEnabled;

		public TopButtonRowEntry(ShaderPackSelectionList list, boolean shadersEnabled) {
			this.list = list;
			this.shadersEnabled = shadersEnabled;
		}

		public void setShadersEnabled(boolean shadersEnabled) {
			this.shadersEnabled = shadersEnabled;
			this.list.screen.refreshScreenSwitchButton();
		}

		@Override
		public void extractContent(IrisGraphics guiGraphics, int mouseX, int mouseY, boolean isHovered, float tickDelta) {
			// Draw dividing line
			int x = getContentX();
			int y = getContentY();
			int entryWidth = getContentWidth();
			int entryHeight = getContentHeight();

			GuiUtil.bindIrisWidgetsTexture();
			GuiUtil.drawButton(guiGraphics, x - 2, y - 2, entryWidth, entryHeight + 2, isHovered, !allowEnableShadersButton);
			guiGraphics.centeredText(Minecraft.getInstance().textRenderer, getEnableDisableLabel(), (x + entryWidth / 2) - 2,
				y + (entryHeight - 11) / 2, 0xFFFFFFFF);
		}

		private Text getEnableDisableLabel() {
			return this.allowEnableShadersButton ? this.shadersEnabled ? SHADERS_ENABLED_LABEL : SHADERS_DISABLED_LABEL : NONE_PRESENT_LABEL;
		}

		@Override
		public boolean mouseClicked(GuiMouseEvent event, boolean bl2) {
			if (this.allowEnableShadersButton) {
				setShadersEnabled(!this.shadersEnabled);
				GuiUtil.playButtonClickSound();
				return true;
			}

			return false;
		}

		@Override
		public boolean keyPressed(GuiKeyEvent event) {
			if (event.isConfirmation()) {
				if (this.allowEnableShadersButton) {
					setShadersEnabled(!this.shadersEnabled);
					GuiUtil.playButtonClickSound();
					return true;
				}
			}

			return false;
		}

		public boolean isFocused() {
			return this.list.getFocused() == this;
		}

		// Renders the label at an offset as to not look misaligned with the rest of the menu
		public static class EnableShadersButtonElement extends IrisElementRow.TextButtonElement {
			private int centerX;

			public EnableShadersButtonElement(Text text, Function<IrisElementRow.TextButtonElement, Boolean> onClick) {
				super(text, onClick);
			}

			@Override
			public void renderLabel(IrisGraphics guiGraphics, int x, int y, int width, int height, int mouseX, int mouseY,
				float tickDelta, boolean hovered) {
				int textX = this.centerX - (int) (GuiUtil.width(this.text) * 0.5);
				int textY = y + (int) ((height - 8) * 0.5);

				guiGraphics.text(this.font, this.text, textX, textY, 0xFFFFFFFF);
			}
		}
	}

	private static class PinnedEntry extends BaseEntry {
		public final boolean allowPressButton = true;
		private final Text label;
		private final Runnable onClick;

		public PinnedEntry(Text label, Runnable onClick, ShaderPackSelectionList list) {
			this.label = label;
			this.onClick = onClick;
		}

		@Override
		public void extractContent(IrisGraphics guiGraphics, int mouseX, int mouseY, boolean isHovered, float tickDelta) {
			// Draw dividing line
			int x = getContentX();
			int y = getContentY();
			int entryWidth = getContentWidth();
			int entryHeight = getContentHeight();

			GuiUtil.bindIrisWidgetsTexture();
			GuiUtil.drawButton(guiGraphics, x - 2, y - 2, entryWidth, entryHeight + 2, isHovered, !allowPressButton);
			guiGraphics.centeredText(Minecraft.getInstance().textRenderer, label, (x + entryWidth / 2) - 2, y + (entryHeight - 11) / 2,
				0xFFFFFFFF);
		}

		@Override
		public boolean mouseClicked(GuiMouseEvent event, boolean repeat) {
			if (this.allowPressButton) {
				GuiUtil.playButtonClickSound();
				onClick.run();
				return false;
			}

			return false;
		}

		@Override
		public boolean keyPressed(GuiKeyEvent event) {
			if (event.isConfirmation()) {
				if (this.allowPressButton) {
					GuiUtil.playButtonClickSound();
					onClick.run();
					return false;
				}
			}

			return false;
		}
	}

	public class ShaderPackEntry extends BaseEntry {
		private final String packName;
		private final ShaderPackSelectionList list;
		private final int index;

		public ShaderPackEntry(int index, ShaderPackSelectionList list, String packName) {
			this.packName = packName;
			this.list = list;
			this.index = index;
		}

		public boolean isApplied() {
			return list.getApplied() == this;
		}

		public boolean isSelected() {
			return list.getSelected() == this;
		}

		public String getPackName() {
			return packName;
		}

		@Override
		public void extractContent(IrisGraphics guiGraphics, int mouseX, int mouseY, boolean isHovered, float tickDelta) {
			// Draw dividing line
			int x = getContentX();
			int y = getContentY();
			int entryWidth = getContentWidth();
			int entryHeight = getContentHeight();

			TextRenderer font = Minecraft.getInstance().textRenderer;
			int color = 0xFFFFFFFF;
			String name = packName;

			if (isHovered) {
				GuiUtil.bindIrisWidgetsTexture();
				GuiUtil.drawButton(guiGraphics, x - 2, y - 2, entryWidth + 4, entryHeight + 4, isHovered, false);
			}

			boolean shadersEnabled = list.getTopButtonRow().shadersEnabled;

			if (GuiUtil.width(GuiUtil.withStyle(GuiUtil.literal(name), Formatting.BOLD)) > this.list.getRowWidth() - 3) {
				name = font.trim(name, this.list.getRowWidth() - 8) + "...";
			}

			Text text = GuiUtil.literal(name);

			if (this.isMouseOver(mouseX, mouseY)) {
				text = GuiUtil.withStyle(text, Formatting.BOLD);
			}

			if (shadersEnabled && this.isApplied()) {
				color = 0xFFFFF263;
			}

			if (!shadersEnabled && !this.isMouseOver(mouseX, mouseY)) {
				color = 0xFFA2A2A2;
			}

			guiGraphics.centeredText(font, text, (x + entryWidth / 2) - 2, y + (entryHeight - 11) / 2, color);
		}

		@Override
		public boolean mouseClicked(GuiMouseEvent event, boolean repeat) {
			// Only do anything on left-click
			if (event.button() != GuiMouseEvent.LEFT) {
				return false;
			}

			return doThing();
		}

		@Override
		public boolean keyPressed(GuiKeyEvent event) {
			// Only do anything on key-press
			if (!event.isConfirmation()) {
				return false;
			}

			return doThing();
		}

		private boolean doThing() {
			boolean didAnything = false;

			// UX: If shaders are disabled, then clicking a shader in the list will also
			//     enable shaders on apply. Previously, it was not possible to select
			//     a pack when shaders were disabled, but this was a source of confusion
			//     - people did not realize that they needed to enable shaders before
			//     selecting a shader pack.
			if (!list.getTopButtonRow().shadersEnabled) {
				list.getTopButtonRow().setShadersEnabled(true);
				didAnything = true;
			}

			if (!this.isSelected()) {
				this.list.select(this.index);
				didAnything = true;
			}

			return didAnything;
		}

		public boolean isFocused() {
			return this.list.getFocused() == this;
		}
	}
}
