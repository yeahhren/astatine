package net.irisshaders.iris.gui.screen;

import net.irisshaders.iris.Iris;
import net.irisshaders.iris.api.v0.IrisApi;
import net.irisshaders.iris.gui.GuiKeyEvent;
import net.irisshaders.iris.gui.GuiUtil;
import net.irisshaders.iris.gui.IrisGraphics;
import net.irisshaders.iris.gui.NavigationController;
import net.irisshaders.iris.gui.OldImageButton;
import net.irisshaders.iris.gui.element.IrisObjectSelectionList;
import net.irisshaders.iris.gui.element.ShaderPackOptionList;
import net.irisshaders.iris.gui.element.ShaderPackSelectionList;
import net.irisshaders.iris.gui.element.screen.IrisButton;
import net.irisshaders.iris.gui.element.widget.AbstractElementWidget;
import net.irisshaders.iris.gui.element.widget.CommentedElementWidget;
import net.irisshaders.iris.platform.IrisPlatformHelpers;
import net.irisshaders.iris.shaderpack.ShaderPack;
import net.irisshaders.iris.uniforms.FrameUpdateNotifier;
import net.irisshaders.iris.uniforms.transforms.SmoothedFloat;
import net.minecraft.client.gui.screen.ConfirmScreen;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Formatting;
import net.minecraft.text.Text;
import org.jetbrains.annotations.Nullable;
import org.lwjgl.Sys;
import org.lwjgl.input.Keyboard;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Properties;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

public class ShaderPackScreen extends Screen implements HudHideable {
	/**
	 * Queue rendering to happen on top of all elements. Useful for tooltips or dialogs.
	 */
	public static final Set<Runnable> TOP_LAYER_RENDER_QUEUE = new HashSet<>();

	private static final Text SELECT_TITLE = GuiUtil.withStyle(GuiUtil.translatable("pack.iris.select.title"), Formatting.GRAY,
		Formatting.ITALIC);
	private static final Text CONFIGURE_TITLE = GuiUtil.withStyle(GuiUtil.translatable("pack.iris.configure.title"), Formatting.GRAY,
		Formatting.ITALIC);
	private static final int COMMENT_PANEL_WIDTH = 314;
	private static final int DEBUG_CONFIRMATION = 7032;
	private static final int UNKNOWN_CONFIRMATION = 7033;
	private static final int UPDATE_CONFIRMATION = 7034;
	private static final String development = "Development Environment";
	private final Screen parent;
	private final Text title;
	private final Text irisTextComponent;
	private boolean guiHidden = false;
	private float backgroundInit = 0.0f;

	private final FrameUpdateNotifier notifier = new FrameUpdateNotifier();
	public final SmoothedFloat listTransition = new SmoothedFloat(1, 1, () -> {
		if (guiHidden || this.optionMenuOpen) {
			return 0.0f;
		} else {
			return backgroundInit;
		}
	}, notifier);
	public final SmoothedFloat buttonTransition = new SmoothedFloat(1, 1, () -> {
		if (guiHidden) {
			return 0.0f;
		} else {
			return backgroundInit;
		}
	}, notifier);
	private ShaderPackSelectionList shaderPackList;
	private @Nullable ShaderPackOptionList shaderOptionList = null;
	private @Nullable NavigationController navigation = null;
	private IrisButton screenSwitchButton;
	private Text notificationDialog = null;
	private int notificationDialogTimer = 0;
	private @Nullable AbstractElementWidget<?> hoveredElement = null;
	private Optional<Text> hoveredElementCommentTitle = Optional.empty();
	private List<String> hoveredElementCommentBody = new ArrayList<>();
	private int hoveredElementCommentTimer = 0;
	private boolean optionMenuOpen = false;
	private boolean dropChanges = false;
	private Text developmentComponent;
	private Text updateComponent;
	private float guiButtonHoverTimer = 0.0f;
	private IrisButton openFolderButton;
	private OldImageButton showHideButton;

	public ShaderPackScreen(Screen parent) {
		this.title = GuiUtil.translatable("options.iris.shaderPackSelection.title");

		this.parent = parent;

		String irisName = Iris.MODNAME + " " + Iris.getVersion();

		if (IrisPlatformHelpers.getInstance().isDevelopmentEnvironment()) {
			this.developmentComponent = GuiUtil.withStyle(GuiUtil.literal(development), Formatting.GOLD);
		}

		this.irisTextComponent = GuiUtil.withStyle(GuiUtil.literal(irisName), Formatting.GRAY);

		refreshForChangedPack();
	}

	private IrisObjectSelectionList<?> activeList() {
		return optionMenuOpen && shaderOptionList != null ? shaderOptionList : shaderPackList;
	}

	@Override
	public void render(int mouseX, int mouseY, float delta) {
		notifier.onNewFrame();
		backgroundInit = 1.0f;
		IrisGraphics guiGraphics = IrisGraphics.INSTANCE;

		if (Screen.isControlDown() && Keyboard.isKeyDown(Keyboard.KEY_D)) {
			this.minecraft.openScreen(new ConfirmScreen(this, "Shader debug mode toggle",
				"Debug mode helps investigate problems and shows shader errors. Would you like to enable it?", "Yes", "No",
				DEBUG_CONFIRMATION));
		}

		if (Screen.isControlDown() && Keyboard.isKeyDown(Keyboard.KEY_G)) {
			this.minecraft.openScreen(new ConfirmScreen(this, "Unknown shader toggle", "This allows unknown shaders to load in.", "Enable",
				"Disable", UNKNOWN_CONFIRMATION));
		}

		if (!this.guiHidden) {
			this.renderBackground();
			this.activeList().render(mouseX, mouseY, delta);
			super.render(mouseX, mouseY, delta);
		} else {
			this.showHideButton.render(this.minecraft, mouseX, mouseY);
		}

		float previousHoverTimer = this.guiButtonHoverTimer;
		if (previousHoverTimer == this.guiButtonHoverTimer) {
			this.guiButtonHoverTimer = 0.0f;
		}

		if (!this.guiHidden) {
			guiGraphics.centeredText(this.textRenderer, this.title, (int) (this.width * 0.5), 8, 0xFFFFFFFF);

			if (notificationDialog != null && notificationDialogTimer > 0) {
				guiGraphics.centeredText(this.textRenderer, notificationDialog, (int) (this.width * 0.5), 21, 0xFFFFFFFF);
			} else {
				if (optionMenuOpen) {
					guiGraphics.centeredText(this.textRenderer, CONFIGURE_TITLE, (int) (this.width * 0.5), 21, 0xFFFFFFFF);
				} else {
					guiGraphics.centeredText(this.textRenderer, SELECT_TITLE, (int) (this.width * 0.5), 21, 0xFFFFFFFF);
				}
			}

			// Draw the comment panel
			if (this.isDisplayingComment()) {
				// Determine panel height and position
				int panelHeight = Math.max(50, 18 + (this.hoveredElementCommentBody.size() * 10));
				int x = (int) (0.5 * this.width) - 157;
				int y = this.height - (panelHeight + 4);
				// Draw panel
				GuiUtil.drawPanel(guiGraphics, x, y, COMMENT_PANEL_WIDTH, panelHeight);
				// Draw text
				guiGraphics.text(textRenderer, this.hoveredElementCommentTitle.orElse(GuiUtil.empty()), x + 4, y + 4, 0xFFFFFFFF);
				for (int i = 0; i < this.hoveredElementCommentBody.size(); i++) {
					guiGraphics.text(textRenderer, this.hoveredElementCommentBody.get(i), x + 4, (y + 16) + (i * 10), 0xFFFFFFFF);
				}
			}
		}

		// Render everything queued to render last
		for (Runnable render : TOP_LAYER_RENDER_QUEUE) {
			render.run();
		}
		TOP_LAYER_RENDER_QUEUE.clear();

		if (this.developmentComponent != null) {
			guiGraphics.text(textRenderer, developmentComponent, 2, this.height - 10, 0xFFFFFFFF);
			guiGraphics.text(textRenderer, irisTextComponent, 2, this.height - 20, 0xFFFFFFFF);
		} else if (this.updateComponent != null) {
			guiGraphics.text(textRenderer, updateComponent, 2, this.height - 10, 0xFFFFFFFF);
			guiGraphics.text(textRenderer, irisTextComponent, 2, this.height - 20, 0xFFFFFFFF);
		} else {
			guiGraphics.text(textRenderer, irisTextComponent, 2, this.height - 10, 0xFFFFFFFF);
		}
	}

	@Override
	protected void mouseClicked(int mouseX, int mouseY, int button) {
		if (!this.guiHidden) {
			this.activeList().mouseClicked(mouseX, mouseY, button);
		}

		super.mouseClicked(mouseX, mouseY, button);
	}

	@Override
	protected void mouseReleased(int mouseX, int mouseY, int button) {
		if (!this.guiHidden) {
			this.activeList().mouseReleased(mouseX, mouseY, button);
		}

		super.mouseReleased(mouseX, mouseY, button);
	}

	@Override
	public void handleMouse() {
		super.handleMouse();

		if (!this.guiHidden && this.activeList() != null) {
			this.activeList().handleMouse();
		}
	}

	@Override
	protected void buttonClicked(ButtonWidget button) {
		if (button instanceof IrisButton irisButton) {
			irisButton.onPress();
		}
	}

	@Override
	public void confirmResult(boolean result, int id) {
		if (id == DEBUG_CONFIRMATION) {
			Iris.setDebug(result);
		} else if (id == UNKNOWN_CONFIRMATION) {
			try {
				Iris.getIrisConfig().setUnknown(result);
			} catch (IOException e) {
				throw new RuntimeException(e);
			}
		} else if (id == ShaderPackSelectionList.DOWNLOAD_CONFIRMATION && result) {
			Sys.openURL(ShaderPackSelectionList.DOWNLOAD_LINK);
		}
		this.minecraft.openScreen(this);
	}

	@Override
	public void init() {
		super.init();
		Keyboard.enableRepeatEvents(true);
		int bottomCenter = this.width / 2 - 50;
		int topCenter = this.width / 2 - 76;
		boolean inWorld = this.minecraft.world != null;

		this.shaderPackList = new ShaderPackSelectionList(this, this.minecraft, this.width, this.height, 32, this.height - 58 - 36, 0,
			this.width);

		if (Iris.getCurrentPack().isPresent() && this.navigation != null) {
			ShaderPack currentPack = Iris.getCurrentPack().get();

			this.shaderOptionList = new ShaderPackOptionList(this, this.navigation, currentPack, this.minecraft, this.width, this.height,
				32, this.height - 58 - 36, 0, this.width);
			this.navigation.setActiveOptionList(this.shaderOptionList);

			this.shaderOptionList.rebuild();
		} else {
			optionMenuOpen = false;
			this.shaderOptionList = null;
		}

		this.buttons.clear();

		if (!this.guiHidden) {
			this.buttons.add(IrisButton.iris$builder(GuiUtil.translatable("gui.done"), button -> onClose(), buttonTransition)
				.bounds(bottomCenter + 104, this.height - 27, 100, 20).build());

			this.buttons.add(
				IrisButton.iris$builder(GuiUtil.translatable("options.iris.apply"), button -> this.applyChanges(), buttonTransition)
					.bounds(bottomCenter, this.height - 27, 100, 20).build());

			this.buttons.add(IrisButton.iris$builder(GuiUtil.translatable("gui.cancel"), button -> this.dropChangesAndClose(), buttonTransition)
				.bounds(bottomCenter - 104, this.height - 27, 100, 20).build());

			this.openFolderButton = IrisButton.iris$builder(GuiUtil.translatable("options.iris.openShaderPackFolder"),
				button -> openShaderPackFolder(), buttonTransition).bounds(topCenter - 78, this.height - 51, 152, 20).build();
			this.buttons.add(openFolderButton);

			this.screenSwitchButton = IrisButton.iris$builder(GuiUtil.translatable("options.iris.shaderPackList"), button -> {
				this.optionMenuOpen = !this.optionMenuOpen;

				// UX: Apply changes before switching screens to avoid unintuitive behavior
				//
				// Not doing this leads to unintuitive behavior, since selecting a pack in the
				// list (but not applying) would open the settings for the previous pack, rather
				// than opening the settings for the selected (but not applied) pack.
				this.applyChanges();
				this.init();
			}, buttonTransition).bounds(topCenter + 78, this.height - 51, 152, 20).build();
			this.buttons.add(this.screenSwitchButton);

			refreshScreenSwitchButton();
		}

		if (inWorld) {
			Text showOrHide = this.guiHidden ? GuiUtil.translatable("options.iris.gui.show") : GuiUtil.translatable("options.iris.gui.hide");

			float endOfLastButton = this.width / 2.0f + 154.0f;
			float freeSpace = this.width - endOfLastButton;
			int x;
			if (freeSpace > 100.0f) {
				x = this.width - 50;
			} else if (freeSpace < 20.0f) {
				x = this.width - 20;
			} else {
				x = (int) (endOfLastButton + (freeSpace / 2.0f)) - 10;
			}

			this.showHideButton = new OldImageButton(x, this.height - 39, 20, 20, this.guiHidden ? 20 : 0, 146, 20,
				GuiUtil.IRIS_WIDGETS_TEX, 256, 256, (button) -> {
				this.guiHidden = !this.guiHidden;
				this.init();
			}, showOrHide);

			this.buttons.add(showHideButton);
		}

		// NB: Don't let comment remain when exiting options screen
		// https://github.com/IrisShaders/Iris/issues/1494
		this.hoveredElement = null;
		this.hoveredElementCommentTimer = 0;
	}

	@Override
	public void removed() {
		Keyboard.enableRepeatEvents(false);
	}

	public void refreshForChangedPack() {
		if (Iris.getCurrentPack().isPresent()) {
			ShaderPack currentPack = Iris.getCurrentPack().get();

			this.navigation = new NavigationController(currentPack.getMenuContainer());

			if (this.shaderOptionList != null) {
				this.shaderOptionList.applyShaderPack(currentPack);
				this.shaderOptionList.rebuild();
			}
		} else {
			this.navigation = null;
		}

		refreshScreenSwitchButton();
	}

	public void refreshScreenSwitchButton() {
		if (this.screenSwitchButton != null) {
			this.screenSwitchButton.setMessage(
				optionMenuOpen ? GuiUtil.translatable("options.iris.shaderPackList") : GuiUtil.translatable(
					"options.iris.shaderPackSettings"));
			this.screenSwitchButton.active = optionMenuOpen || (shaderPackList.getTopButtonRow().shadersEnabled && Iris.getCurrentPack()
				.map(p -> !p.getMenuContainer().mainScreen.elements.isEmpty()).orElse(true));
		}
	}

	@Override
	public void tick() {
		super.tick();

		if (this.notificationDialogTimer > 0) {
			this.notificationDialogTimer--;
		}

		if (this.hoveredElement != null) {
			this.hoveredElementCommentTimer++;
		} else {
			this.hoveredElementCommentTimer = 0;
		}
	}

	@Override
	protected void keyPressed(char character, int key) {
		GuiKeyEvent event = new GuiKeyEvent(key);

		if (event.isEscape()) {
			if (this.guiHidden) {
				this.guiHidden = false;
				this.init();

				return;
			} else if (this.navigation != null && this.navigation.hasHistory()) {
				this.navigation.back();

				return;
			} else if (this.optionMenuOpen) {
				this.optionMenuOpen = false;
				this.init();

				return;
			}

			this.onClose();
			return;
		} else if (event.isCycleFocus()) {
			if (!optionMenuOpen) {
				shaderPackList.keyPressed(new GuiKeyEvent(Keyboard.KEY_RETURN));
			}

			this.optionMenuOpen = !this.optionMenuOpen;

			// UX: Apply changes before switching screens to avoid unintuitive behavior
			//
			// Not doing this leads to unintuitive behavior, since selecting a pack in the
			// list (but not applying) would open the settings for the previous pack, rather
			// than opening the settings for the selected (but not applied) pack.
			this.applyChanges();

			this.init();

			return;
		} else if (key == Keyboard.KEY_F1 && this.showHideButton != null) {
			this.guiHidden = !guiHidden;
			this.init();
			return;
		}

		if (!this.guiHidden) {
			this.activeList().keyPressed(event);
		}
	}

	public void onFilesDrop(List<Path> paths) {
		if (this.optionMenuOpen) {
			onOptionMenuFilesDrop(paths);
		} else {
			onPackListFilesDrop(paths);
		}
	}

	public void onPackListFilesDrop(List<Path> paths) {
		List<Path> packs = paths.stream().filter(Iris::isValidShaderpack).toList();

		for (Path pack : packs) {
			String fileName = pack.getFileName().toString();

			try {
				Iris.getShaderpacksDirectoryManager().copyPackIntoDirectory(fileName, pack);
			} catch (FileAlreadyExistsException e) {
				this.notificationDialog = GuiUtil.withStyle(
					GuiUtil.translatable("options.iris.shaderPackSelection.copyErrorAlreadyExists", fileName), Formatting.ITALIC,
					Formatting.RED);

				this.notificationDialogTimer = 100;
				this.shaderPackList.refresh();

				return;
			} catch (IOException e) {
				Iris.logger.warn("Error copying dragged shader pack", e);

				this.notificationDialog = GuiUtil.withStyle(GuiUtil.translatable("options.iris.shaderPackSelection.copyError", fileName),
					Formatting.ITALIC, Formatting.RED);

				this.notificationDialogTimer = 100;
				this.shaderPackList.refresh();

				return;
			}
		}

		// After copying the relevant files over to the folder, make sure to refresh the shader pack list.
		this.shaderPackList.refresh();

		if (packs.isEmpty()) {
			// If zero packs were added, then notify the user that the files that they added weren't actually shader
			// packs.

			if (paths.size() == 1) {
				// If a single pack could not be added, provide a message with that pack in the file name
				String fileName = paths.getFirst().getFileName().toString();

				this.notificationDialog = GuiUtil.withStyle(GuiUtil.translatable("options.iris.shaderPackSelection.failedAddSingle", fileName),
					Formatting.ITALIC, Formatting.RED);
			} else {
				// Otherwise, show a generic message.

				this.notificationDialog = GuiUtil.withStyle(GuiUtil.translatable("options.iris.shaderPackSelection.failedAdd"),
					Formatting.ITALIC, Formatting.RED);
			}

		} else if (packs.size() == 1) {
			// In most cases, users will drag a single pack into the selection menu. So, let's special case it.
			String packName = packs.getFirst().getFileName().toString();

			this.notificationDialog = GuiUtil.withStyle(GuiUtil.translatable("options.iris.shaderPackSelection.addedPack", packName),
				Formatting.ITALIC, Formatting.YELLOW);

			// Select the pack that the user just added, since if a user just dragged a pack in, they'll probably want
			// to actually use that pack afterwards.
			this.shaderPackList.select(packName);
		} else {
			// We also support multiple packs being dragged and dropped at a time. Just show a generic success message
			// in that case.
			this.notificationDialog = GuiUtil.withStyle(GuiUtil.translatable("options.iris.shaderPackSelection.addedPacks", packs.size()),
				Formatting.ITALIC, Formatting.YELLOW);
		}

		// Show the relevant message for 5 seconds (100 ticks)
		this.notificationDialogTimer = 100;
	}

	public void displayNotification(Text component) {
		this.notificationDialog = component;
		this.notificationDialogTimer = 100;
	}

	public void onOptionMenuFilesDrop(List<Path> paths) {
		// If more than one option file has been dragged, display an error
		// as only one option file should be imported at a time
		if (paths.size() != 1) {
			this.notificationDialog = GuiUtil.withStyle(GuiUtil.translatable("options.iris.shaderPackOptions.tooManyFiles"),
				Formatting.ITALIC, Formatting.RED);
			this.notificationDialogTimer = 100; // 5 seconds (100 ticks)

			return;
		}

		this.importPackOptions(paths.getFirst());
	}

	public void importPackOptions(Path settingFile) {
		try (InputStream in = Files.newInputStream(settingFile)) {
			Properties properties = new Properties();
			properties.load(in);

			Iris.queueShaderPackOptionsFromProperties(properties);

			this.notificationDialog = GuiUtil.withStyle(
				GuiUtil.translatable("options.iris.shaderPackOptions.importedSettings", settingFile.getFileName().toString()),
				Formatting.ITALIC, Formatting.YELLOW);
			this.notificationDialogTimer = 100; // 5 seconds (100 ticks)

			if (this.navigation != null) {
				this.navigation.refresh();
			}
		} catch (Exception e) {
			// If the file could not be properly parsed or loaded,
			// log the error and display a message to the user
			Iris.logger.error("Error importing shader settings file \"" + settingFile.toString() + "\"", e);

			this.notificationDialog = GuiUtil.withStyle(
				GuiUtil.translatable("options.iris.shaderPackOptions.failedImport", settingFile.getFileName().toString()),
				Formatting.ITALIC, Formatting.RED);
			this.notificationDialogTimer = 100; // 5 seconds (100 ticks)
		}
	}

	public void onClose() {
		if (!dropChanges) {
			applyChanges();
		} else {
			discardChanges();
		}

		try {
			shaderPackList.close();
		} catch (IOException e) {
			Iris.logger.error("Failed to safely close shaderpack selection!", e);
		}

		this.minecraft.openScreen(parent);
	}

	private void dropChangesAndClose() {
		dropChanges = true;
		onClose();
	}

	public void applyChanges() {
		ShaderPackSelectionList.BaseEntry base = this.shaderPackList.getSelected();
		boolean enabled = this.shaderPackList.getTopButtonRow().shadersEnabled;
		boolean previousShadersEnabled = Iris.getIrisConfig().areShadersEnabled();

		if (enabled != previousShadersEnabled) {
			IrisApi.getInstance().getConfig().setShadersEnabledAndApply(enabled);
		}

		if (!(base instanceof ShaderPackSelectionList.ShaderPackEntry entry)) {
			return;
		}

		this.shaderPackList.setApplied(entry);

		String name = entry.getPackName();

		// If the pack is being changed, clear pending options from the previous pack to
		// avoid possible undefined behavior from applying one pack's options to another pack
		if (!name.equals(Iris.getCurrentPackName())) {
			Iris.clearShaderPackOptionQueue();
		}

		String previousPackName = Iris.getIrisConfig().getShaderPackName().orElse(null);

		// Only reload if the pack would be different from before, or shaders were toggled, or options were changed, or if we're about to reset options.
		if (!name.equals(previousPackName) || !Iris.getShaderPackOptionQueue()
			.isEmpty() || Iris.shouldResetShaderPackOptionsOnNextReload()) {
			Iris.getIrisConfig().setShaderPackName(name);
			IrisApi.getInstance().getConfig().setShadersEnabledAndApply(enabled);
		}

		refreshForChangedPack();
	}

	private void discardChanges() {
		Iris.clearShaderPackOptionQueue();
	}

	private void openShaderPackFolder() {
		CompletableFuture.runAsync(() -> Sys.openURL(Iris.getShaderpacksDirectoryManager().getDirectoryUri().toString()));
	}

	// Let the screen know if an element is hovered or not, allowing for accurately updating which element is hovered
	public void setElementHoveredStatus(AbstractElementWidget<?> widget, boolean hovered) {
		if (hovered && widget != this.hoveredElement) {
			this.hoveredElement = widget;

			if (widget instanceof CommentedElementWidget) {
				this.hoveredElementCommentTitle = ((CommentedElementWidget<?>) widget).getCommentTitle();

				Optional<Text> commentBody = ((CommentedElementWidget<?>) widget).getCommentBody();
				if (commentBody.isEmpty()) {
					this.hoveredElementCommentBody.clear();
				} else {
					String rawCommentBody = commentBody.get().getString();

					// Strip any trailing "."s
					if (rawCommentBody.endsWith(".")) {
						rawCommentBody = rawCommentBody.substring(0, rawCommentBody.length() - 1);
					}
					// Split comment body into lines by separator ". "
					List<String> splitByPeriods = Arrays.stream(rawCommentBody.split("\\. [ ]*")).toList();
					// Line wrap
					this.hoveredElementCommentBody = new ArrayList<>();
					for (String text : splitByPeriods) {
						this.hoveredElementCommentBody.addAll(this.textRenderer.split(text, COMMENT_PANEL_WIDTH - 8));
					}
				}
			} else {
				this.hoveredElementCommentTitle = Optional.empty();
				this.hoveredElementCommentBody.clear();
			}

			this.hoveredElementCommentTimer = 0;
		} else if (!hovered && widget == this.hoveredElement) {
			this.hoveredElement = null;
			this.hoveredElementCommentTitle = Optional.empty();
			this.hoveredElementCommentBody.clear();
			this.hoveredElementCommentTimer = 0;
		}
	}

	public boolean isDisplayingComment() {
		return this.hoveredElementCommentTimer > 10 && this.hoveredElementCommentTitle.isPresent() && !this.hoveredElementCommentBody.isEmpty();
	}

	public IrisButton getBottomRowOption() {
		return openFolderButton;
	}
}
