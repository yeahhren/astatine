package net.irisshaders.iris.gui.element;

import net.irisshaders.iris.Iris;
import net.irisshaders.iris.gui.FileDialogUtil;
import net.irisshaders.iris.gui.GuiBounds;
import net.irisshaders.iris.gui.GuiKeyEvent;
import net.irisshaders.iris.gui.GuiMouseEvent;
import net.irisshaders.iris.gui.GuiUtil;
import net.irisshaders.iris.gui.IrisGraphics;
import net.irisshaders.iris.gui.NavigationController;
import net.irisshaders.iris.gui.element.widget.AbstractElementWidget;
import net.irisshaders.iris.gui.element.widget.OptionMenuConstructor;
import net.irisshaders.iris.gui.screen.ShaderPackScreen;
import net.irisshaders.iris.shaderpack.ShaderPack;
import net.irisshaders.iris.shaderpack.option.menu.OptionMenuContainer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.render.TextRenderer;
import net.minecraft.text.Formatting;
import net.minecraft.text.Text;
import net.minecraft.util.math.MathHelper;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

public class ShaderPackOptionList extends IrisContainerObjectSelectionList<ShaderPackOptionList.BaseEntry> {
	private final List<AbstractElementWidget<?>> elementWidgets = new ArrayList<>();
	private final ShaderPackScreen screen;
	private final NavigationController navigation;
	private OptionMenuContainer container;

	public ShaderPackOptionList(ShaderPackScreen screen, NavigationController navigation, ShaderPack pack, Minecraft client, int width,
		int height, int top, int bottom, int left, int right) {
		super(client, width, height, top + 4, bottom, left, right, 24);
		this.navigation = navigation;
		this.screen = screen;

		applyShaderPack(pack);
	}

	public void applyShaderPack(ShaderPack pack) {
		this.container = pack.getMenuContainer();
	}

	public void rebuild() {
		this.clearEntries();
		this.setScrollAmount(0);
		OptionMenuConstructor.constructAndApplyToScreen(this.container, this.screen, this, navigation);
	}

	public void refresh() {
		this.elementWidgets.forEach(widget -> widget.init(this.screen, this.navigation));
	}

	@Override
	public int getRowWidth() {
		return Math.min(400, width - 12);
	}

	public void addHeader(Text text, boolean backButton) {
		this.addEntry(new HeaderEntry(this.screen, this.navigation, text, backButton));
	}

	public void addWidgets(int columns, List<AbstractElementWidget<?>> elements) {
		this.elementWidgets.addAll(elements);

		List<AbstractElementWidget<?>> row = new ArrayList<>();
		for (AbstractElementWidget<?> element : elements) {
			row.add(element);

			if (row.size() >= columns) {
				this.addEntry(new ElementRowEntry(screen, this.navigation, row));
				row = new ArrayList<>(); // Clearing the list would affect the row entry created above
			}
		}

		if (!row.isEmpty()) {
			while (row.size() < columns) {
				row.add(AbstractElementWidget.EMPTY);
			}

			this.addEntry(new ElementRowEntry(screen, this.navigation, row));
		}
	}

	public NavigationController getNavigation() {
		return navigation;
	}

	@Override
	public boolean mouseReleased(int mouseX, int mouseY, int button) {
		boolean released = false;

		for (BaseEntry entry : this.children()) {
			released |= entry.mouseReleased(new GuiMouseEvent(mouseX, mouseY, button));
		}

		return released || super.mouseReleased(mouseX, mouseY, button);
	}

	public abstract static class BaseEntry extends IrisObjectSelectionList.Entry {
		protected final NavigationController navigation;

		protected BaseEntry(NavigationController navigation) {
			this.navigation = navigation;
		}
	}

	public static class ElementRowEntry extends BaseEntry {
		private final List<AbstractElementWidget<?>> widgets;
		private final ShaderPackScreen screen;

		private int cachedWidth;
		private int cachedPosX;
		private @Nullable AbstractElementWidget<?> focused;

		public ElementRowEntry(ShaderPackScreen screen, NavigationController navigation, List<AbstractElementWidget<?>> widgets) {
			super(navigation);

			this.screen = screen;
			this.widgets = widgets;
		}

		@Override
		public void extractContent(IrisGraphics guiGraphics, int mouseX, int mouseY, boolean isHovered, float tickDelta) {
			this.cachedWidth = getContentWidth();
			this.cachedPosX = getContentX();

			// The amount of space widgets will occupy, excluding margins. Will be divided up between widgets.
			int totalWidthWithoutMargins = getContentWidth() - (2 * (widgets.size() - 1));

			totalWidthWithoutMargins -= 3; // Centers it for some reason

			// Width of a single widget
			float singleWidgetWidth = (float) totalWidthWithoutMargins / widgets.size();

			for (int i = 0; i < widgets.size(); i++) {
				AbstractElementWidget<?> widget = widgets.get(i);
				boolean widgetHovered = (isMouseOver(mouseX, mouseY) && (getHoveredWidget(mouseX) == i)) || focused == widget;

				widget.bounds = new GuiBounds(getContentX() + (int) ((singleWidgetWidth + 2) * i), getContentY(), (int) singleWidgetWidth,
					getContentHeight() + 2);
				widget.render(guiGraphics, mouseX, mouseY, tickDelta, widgetHovered);

				screen.setElementHoveredStatus(widget, widgetHovered);
			}
		}

		public int getHoveredWidget(int mouseX) {
			float positionAcrossWidget = ((float) MathHelper.clamp(mouseX - cachedPosX, 0, cachedWidth)) / cachedWidth;

			return MathHelper.clamp((int) Math.floor(widgets.size() * positionAcrossWidget), 0, widgets.size() - 1);
		}

		@Override
		public boolean mouseClicked(GuiMouseEvent event, boolean bl2) {
			return this.widgets.get(getHoveredWidget((int) event.x())).mouseClicked(event, bl2);
		}

		@Override
		public boolean mouseReleased(GuiMouseEvent event) {
			return this.widgets.get(getHoveredWidget((int) event.x())).mouseReleased(event);
		}

		@Override
		public boolean keyPressed(GuiKeyEvent event) {
			if (event.isLeft() || event.isRight()) {
				int index = focused == null ? 0 : widgets.indexOf(focused) + (event.isRight() ? 1 : -1);
				AbstractElementWidget<?> next = widgets.get(MathHelper.clamp(index, 0, widgets.size() - 1));

				if (focused == null || !focused.keyPressed(event)) {
					if (focused != null) {
						focused.setFocused(false);
					}

					focused = next;
					focused.setFocused(true);
				}

				return true;
			}

			if (focused == null) {
				focused = widgets.getFirst();
				focused.setFocused(true);
			}

			return focused.keyPressed(event);
		}

		public List<AbstractElementWidget<?>> children() {
			return widgets;
		}
	}

	public class HeaderEntry extends BaseEntry {
		public static final Text BACK_BUTTON_TEXT = GuiUtil.literal("< ")
			.append(GuiUtil.withStyle(GuiUtil.translatable("options.iris.back"), Formatting.ITALIC));
		public static final Text RESET_BUTTON_TEXT_INACTIVE = GuiUtil.withStyle(GuiUtil.translatable("options.iris.reset"), Formatting.GRAY);
		public static final Text RESET_BUTTON_TEXT_ACTIVE = GuiUtil.withStyle(GuiUtil.translatable("options.iris.reset"), Formatting.YELLOW);

		public static final Text RESET_HOLD_SHIFT_TOOLTIP = GuiUtil.withStyle(GuiUtil.translatable("options.iris.reset.tooltip.holdShift"),
			Formatting.GOLD);
		public static final Text RESET_TOOLTIP = GuiUtil.withStyle(GuiUtil.translatable("options.iris.reset.tooltip"), Formatting.RED);
		public static final Text IMPORT_TOOLTIP = GuiUtil.withColor(GuiUtil.translatable("options.iris.importSettings.tooltip"), 0x4da6ff);
		public static final Text EXPORT_TOOLTIP = GuiUtil.withColor(GuiUtil.translatable("options.iris.exportSettings.tooltip"), 0xfc7d3d);

		private static final int MIN_SIDE_BUTTON_WIDTH = 42;
		private static final int BUTTON_HEIGHT = 16;

		private final ShaderPackScreen screen;
		private final @Nullable IrisElementRow backButton;
		private final IrisElementRow utilityButtons = new IrisElementRow();
		private final IrisElementRow.TextButtonElement resetButton;
		private final IrisElementRow.IconButtonElement importButton;
		private final IrisElementRow.IconButtonElement exportButton;
		private final Text text;

		public HeaderEntry(ShaderPackScreen screen, NavigationController navigation, Text text, boolean hasBackButton) {
			super(navigation);

			if (hasBackButton) {
				this.backButton = new IrisElementRow().add(new IrisElementRow.TextButtonElement(BACK_BUTTON_TEXT, this::backButtonClicked),
					Math.max(MIN_SIDE_BUTTON_WIDTH, GuiUtil.width(BACK_BUTTON_TEXT) + 8));
			} else {
				this.backButton = null;
			}

			this.resetButton = new IrisElementRow.TextButtonElement(RESET_BUTTON_TEXT_INACTIVE, this::resetButtonClicked);
			this.importButton = new IrisElementRow.IconButtonElement(GuiUtil.Icon.IMPORT, GuiUtil.Icon.IMPORT_COLORED,
				this::importSettingsButtonClicked);
			this.exportButton = new IrisElementRow.IconButtonElement(GuiUtil.Icon.EXPORT, GuiUtil.Icon.EXPORT_COLORED,
				this::exportSettingsButtonClicked);

			this.utilityButtons.add(this.importButton, 15).add(this.exportButton, 15)
				.add(this.resetButton, Math.max(MIN_SIDE_BUTTON_WIDTH, GuiUtil.width(RESET_BUTTON_TEXT_INACTIVE) + 8));

			this.screen = screen;
			this.text = text;
		}

		@Override
		public void extractContent(IrisGraphics guiGraphics, int mouseX, int mouseY, boolean isHovered, float tickDelta) {
			// Draw dividing line
			int x = getX();
			int y = getY();
			int entryWidth = getWidth();
			int entryHeight = getHeight();
			guiGraphics.fill(x - 3, (y + entryHeight) - 2, x + entryWidth, (y + entryHeight) - 1, 0x66BEBEBE);

			TextRenderer font = Minecraft.getInstance().textRenderer;

			// Draw header text
			guiGraphics.scrollingText(font, text, x + (int) (entryWidth * 0.5), x + 5, ((x + entryWidth) - 10) - utilityButtons.getWidth(),
				y + 5, y + 15);

			GuiUtil.bindIrisWidgetsTexture();

			// Draw back button if present
			if (this.backButton != null) {
				backButton.render(guiGraphics, x, y, BUTTON_HEIGHT, mouseX, mouseY, tickDelta, isHovered);
			}

			boolean shiftDown = Screen.isShiftDown();

			// Set the appearance of the reset button
			this.resetButton.disabled = !shiftDown && !resetButton.isFocused();
			this.resetButton.text = !resetButton.disabled ? RESET_BUTTON_TEXT_ACTIVE : RESET_BUTTON_TEXT_INACTIVE;

			// Draw the utility buttons
			this.utilityButtons.renderRightAligned(guiGraphics, (x + entryWidth) - 3, y, BUTTON_HEIGHT, mouseX, mouseY, tickDelta,
				isHovered);

			// Draw the reset button's tooltip
			if (this.resetButton.isHovered() || this.resetButton.isFocused()) {
				Text tooltip = !resetButton.disabled ? RESET_TOOLTIP : RESET_HOLD_SHIFT_TOOLTIP;
				queueBottomRightAnchoredTooltip(guiGraphics, this.resetButton.getRectangle().right(), this.resetButton.getRectangle().y(),
					font, tooltip);
			}
			// Draw the import/export button tooltips
			if (this.importButton.isHovered() || this.importButton.isFocused()) {
				queueBottomRightAnchoredTooltip(guiGraphics, this.importButton.getRectangle().right(), this.importButton.getRectangle().y(),
					font, IMPORT_TOOLTIP);
			}
			if (this.exportButton.isHovered() || this.exportButton.isFocused()) {
				queueBottomRightAnchoredTooltip(guiGraphics, this.exportButton.getRectangle().right(), this.exportButton.getRectangle().y(),
					font, EXPORT_TOOLTIP);
			}
		}

		private void queueBottomRightAnchoredTooltip(IrisGraphics guiGraphics, int x, int y, TextRenderer font, Text text) {
			ShaderPackScreen.TOP_LAYER_RENDER_QUEUE.add(
				() -> GuiUtil.drawTextPanel(font, guiGraphics, text, x - (GuiUtil.width(text) + 10), y - 16));
		}

		public List<IrisElementRow.Element> children() {
			List<IrisElementRow.Element> children = new ArrayList<>(utilityButtons.children());
			if (backButton != null)
				children.addAll(backButton.children());
			return children;
		}

		@Override
		public boolean mouseClicked(GuiMouseEvent event, boolean bl2) {
			boolean backButtonResult = backButton != null && backButton.mouseClicked(event, bl2);
			boolean utilButtonResult = utilityButtons.mouseClicked(event, bl2);

			return backButtonResult || utilButtonResult;
		}

		@Override
		public boolean keyPressed(GuiKeyEvent event) {
			if (backButton != null && backButton.keyPressed(event)) {
				return true;
			}

			return utilityButtons.keyPressed(event);
		}

		private boolean backButtonClicked(IrisElementRow.TextButtonElement button) {
			this.navigation.back();
			GuiUtil.playButtonClickSound();

			return true;
		}

		private boolean resetButtonClicked(IrisElementRow.TextButtonElement button) {
			if (Screen.isShiftDown()) {
				Iris.resetShaderPackOptionsOnNextReload();
				this.screen.applyChanges();
				GuiUtil.playButtonClickSound();

				return true;
			}

			return false;
		}

		private boolean importSettingsButtonClicked(IrisElementRow.IconButtonElement button) {
			GuiUtil.playButtonClickSound();

			// Invalid state to be in
			if (Iris.getCurrentPack().isEmpty()) {
				return false;
			}

			// Displaying a dialog when the game is full-screened can cause severe issues
			// https://github.com/IrisShaders/Iris/issues/1258
			if (Minecraft.getInstance().isFullscreen()) {
				this.screen.displayNotification(
					GuiUtil.withStyle(GuiUtil.translatable("options.iris.mustDisableFullscreen"), Formatting.RED, Formatting.BOLD));
				return false;
			}

			final ShaderPackScreen originalScreen = this.screen; // Also used to prevent invalid state

			FileDialogUtil.fileSelectDialog(FileDialogUtil.DialogType.OPEN, "Import Shader Settings from File",
					Iris.getShaderpacksDirectory().resolve(Iris.getCurrentPackName() + ".txt"), "Shader Pack Settings (.txt)", "*.txt")
				.whenComplete((path, err) -> {
					if (err != null) {
						Iris.logger.error("Error selecting shader settings from file", err);

						return;
					}

					Minecraft.getInstance().executeTask(() -> {
						if (Minecraft.getInstance().screen == originalScreen) {
							path.ifPresent(originalScreen::importPackOptions);
						}
					});
				});

			return true;
		}

		private boolean exportSettingsButtonClicked(IrisElementRow.IconButtonElement button) {
			GuiUtil.playButtonClickSound();

			// Invalid state to be in
			if (Iris.getCurrentPack().isEmpty()) {
				return false;
			}

			// Displaying a dialog when the game is full-screened can cause severe issues
			// https://github.com/IrisShaders/Iris/issues/1258
			if (Minecraft.getInstance().isFullscreen()) {
				this.screen.displayNotification(
					GuiUtil.withStyle(GuiUtil.translatable("options.iris.mustDisableFullscreen"), Formatting.RED, Formatting.BOLD));
				return false;
			}

			FileDialogUtil.fileSelectDialog(FileDialogUtil.DialogType.SAVE, "Export Shader Settings to File",
					Iris.getShaderpacksDirectory().resolve(Iris.getCurrentPackName() + ".txt"), "Shader Pack Settings (.txt)", "*.txt")
				.whenComplete((path, err) -> {
					if (err != null) {
						Iris.logger.error("Error selecting file to export shader settings", err);

						return;
					}

					path.ifPresent(p -> {
						Properties toSave = new Properties();

						// Dirty way of getting the currently applied settings as a Properties, directly
						// opens and copies out of the saved settings file if it is present
						Path sourceTxtPath = Iris.getShaderpacksDirectory().resolve(Iris.getCurrentPackName() + ".txt");
						if (Files.exists(sourceTxtPath)) {
							try (InputStream in = Files.newInputStream(sourceTxtPath)) {
								toSave.load(in);
							} catch (IOException ignored) {
							}
						}

						// Save properties to user determined file
						try (OutputStream out = Files.newOutputStream(p)) {
							toSave.store(out, null);
						} catch (IOException e) {
							Iris.logger.error("Error saving properties to \"" + p + "\"", e);
						}
					});
				});

			return true;
		}
	}
}
