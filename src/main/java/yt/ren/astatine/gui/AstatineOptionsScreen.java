package yt.ren.astatine.gui;

import net.irisshaders.iris.Iris;
import net.irisshaders.iris.gui.screen.ShaderPackScreen;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.render.Window;
import net.minecraft.client.resource.language.I18n;
import org.apache.logging.log4j.LogManager;
import org.lwjgl.input.Keyboard;
import org.lwjgl.input.Mouse;
import yt.ren.astatine.config.ConfigStore;
import yt.ren.astatine.gui.frame.AbstractFrame;
import yt.ren.astatine.gui.frame.BasicFrame;
import yt.ren.astatine.gui.frame.components.SearchTextFieldComponent;
import yt.ren.astatine.gui.frame.tab.Tab;
import yt.ren.astatine.gui.frame.tab.TabFrame;
import yt.ren.astatine.gui.options.Option;
import yt.ren.astatine.gui.options.OptionFlag;
import yt.ren.astatine.gui.options.OptionPage;
import yt.ren.astatine.gui.widget.AbstractWidget;
import yt.ren.astatine.gui.widget.FlatButtonWidget;
import yt.ren.astatine.shaderpack.Shaderpacks;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Stream;

public class AstatineOptionsScreen extends Screen {
	private static final AtomicReference<String> tabFrameSelectedTab = new AtomicReference<>(null);
	private static final AtomicReference<Integer> tabFrameScrollBarOffset = new AtomicReference<>(0);
	private static final AtomicReference<Integer> optionPageScrollBarOffset = new AtomicReference<>(0);

	private static final AtomicReference<String> lastSearch = new AtomicReference<>("");
	private static final AtomicReference<Integer> lastSearchIndex = new AtomicReference<>(0);

	private final Screen prevScreen;
	private final List<OptionPage> pages = new ArrayList<>();
	private AbstractFrame frame;
	private FlatButtonWidget applyButton, closeButton, undoButton;
	private boolean hasPendingChanges;

	private SearchTextFieldComponent searchTextField;

	private int lastMouseX, lastMouseY;

	public AstatineOptionsScreen(Screen prev) {
		this.prevScreen = prev;
		this.pages.addAll(AstatineOptionPages.create());
	}

	public void rebuildUI() {
		this.init();
	}

	@Override
	public void init() {
		Keyboard.enableRepeatEvents(true);

		// Pick up values changed elsewhere (e.g. the shader pack screen) without discarding pending edits.
		this.getAllOptions().filter(option -> !option.hasChanged()).forEach(Option::reset);

		this.frame = this.parentFrameBuilder().build();

		this.searchTextField.setFocused(!lastSearch.get().trim().isEmpty());
	}

	@Override
	public void removed() {
		Keyboard.enableRepeatEvents(false);
	}

	protected BasicFrame.Builder parentFrameBuilder() {
		BasicFrame.Builder basicFrameBuilder;

		// Calculates if resolution exceeds 16:9 ratio, force 16:9
		int newWidth = this.width;
		if ((float) this.width / (float) this.height > 1.77777777778) {
			newWidth = (int) (this.height * 1.77777777778);
		}

		Dim2i basicFrameDim = new Dim2i((this.width - newWidth) / 2, 0, newWidth, this.height);
		Dim2i tabFrameDim = new Dim2i(basicFrameDim.x() + basicFrameDim.width() / 20 / 2, basicFrameDim.y() + basicFrameDim.height() / 4 / 2, basicFrameDim.width() - (basicFrameDim.width() / 20), basicFrameDim.height() / 4 * 3);

		Dim2i undoButtonDim = new Dim2i(tabFrameDim.getLimitX() - 203, tabFrameDim.getLimitY() + 5, 65, 20);
		Dim2i applyButtonDim = new Dim2i(tabFrameDim.getLimitX() - 134, tabFrameDim.getLimitY() + 5, 65, 20);
		Dim2i closeButtonDim = new Dim2i(tabFrameDim.getLimitX() - 65, tabFrameDim.getLimitY() + 5, 65, 20);

		this.undoButton = new FlatButtonWidget(undoButtonDim, I18n.translate("astatine.options.buttons.undo"), this::undoChanges);
		this.applyButton = new FlatButtonWidget(applyButtonDim, I18n.translate("astatine.options.buttons.apply"), this::applyChanges);
		this.closeButton = new FlatButtonWidget(closeButtonDim, I18n.translate("gui.done"), this::onClose);

		Dim2i searchTextFieldDim = new Dim2i(tabFrameDim.x(), tabFrameDim.y() - 26, tabFrameDim.width(), 20);

		basicFrameBuilder = this.parentBasicFrameBuilder(basicFrameDim, tabFrameDim);

		if (Shaderpacks.isInitialized()) {
			String label = I18n.translate("options.iris.shaderPackSelection");
			int size = this.textRenderer.getWidth(label);
			Dim2i shaderPackButtonDim = new Dim2i(tabFrameDim.getLimitX() - size - 10, tabFrameDim.y() - 26, 10 + size, 20);
			searchTextFieldDim = new Dim2i(tabFrameDim.x(), tabFrameDim.y() - 26, tabFrameDim.width() - (tabFrameDim.getLimitX() - shaderPackButtonDim.x()) - 2, 20);

			FlatButtonWidget shaderPackButton = new FlatButtonWidget(shaderPackButtonDim, label, () -> this.minecraft.openScreen(new ShaderPackScreen(this)));
			basicFrameBuilder.addChild(dim -> shaderPackButton);
		}

		this.searchTextField = new SearchTextFieldComponent(searchTextFieldDim, this.pages, tabFrameSelectedTab,
			tabFrameScrollBarOffset, optionPageScrollBarOffset, tabFrameDim.height(), this, lastSearch, lastSearchIndex);

		basicFrameBuilder.addChild(dim -> this.searchTextField);

		return basicFrameBuilder;
	}

	public BasicFrame.Builder parentBasicFrameBuilder(Dim2i parentBasicFrameDim, Dim2i tabFrameDim) {
		return BasicFrame.createBuilder()
			.setDimension(parentBasicFrameDim)
			.shouldRenderOutline(false)
			.addChild(parentDim -> TabFrame.createBuilder()
				.setDimension(tabFrameDim)
				.shouldRenderOutline(false)
				.setTabSectionScrollBarOffset(tabFrameScrollBarOffset)
				.setTabSectionSelectedTab(tabFrameSelectedTab)
				.addTabs(tabs -> this.pages
					.stream()
					.filter(page -> !page.getGroups().isEmpty())
					.forEach(page -> tabs.add(Tab.createBuilder().from(page, optionPageScrollBarOffset)))
				)
				.onSetTab(() -> optionPageScrollBarOffset.set(0))
				.build()
			)
			.addChild(dim -> this.undoButton)
			.addChild(dim -> this.applyButton)
			.addChild(dim -> this.closeButton);
	}

	@Override
	public void render(int mouseX, int mouseY, float delta) {
		Clip.reset();
		this.updateControls();
		this.renderBackground();
		this.frame.render(mouseX, mouseY, delta);
	}

	private void updateControls() {
		boolean hasChanges = this.getAllOptions().anyMatch(Option::hasChanged);

		this.applyButton.setEnabled(hasChanges);
		this.undoButton.setVisible(hasChanges);
		this.closeButton.setEnabled(!hasChanges);

		this.hasPendingChanges = hasChanges;
	}

	private Stream<Option<?>> getAllOptions() {
		return this.pages.stream().flatMap(s -> s.getOptions().stream());
	}

	private void applyChanges() {
		final Set<ConfigStore> dirtyStorages = new LinkedHashSet<>();
		final EnumSet<OptionFlag> flags = EnumSet.noneOf(OptionFlag.class);
		final int guiScale = this.minecraft.options.guiScale;

		this.getAllOptions().forEach(option -> {
			if (!option.hasChanged()) {
				return;
			}

			option.applyChanges();

			flags.addAll(option.getFlags());
			dirtyStorages.add(option.getStorage());
		});

		if (flags.contains(OptionFlag.REQUIRES_RENDERER_RELOAD) && this.minecraft.worldRenderer != null) {
			this.minecraft.worldRenderer.reload();
		}

		if (flags.contains(OptionFlag.REQUIRES_ASSET_RELOAD)) {
			this.minecraft.reloadResources();
		}

		for (ConfigStore storage : dirtyStorages) {
			storage.save();
		}

		if (flags.contains(OptionFlag.REQUIRES_SHADER_RELOAD) && Shaderpacks.isInitialized()) {
			try {
				Iris.reload();
			} catch (Exception e) {
				LogManager.getLogger("Astatine/Options").error("Failed to reload shaders", e);
			}
		}

		if (this.minecraft.options.guiScale != guiScale) {
			Window window = new Window(this.minecraft);
			this.init(this.minecraft, (int) window.getScaledWidth(), (int) window.getScaledHeight());
		}
	}

	private void undoChanges() {
		this.getAllOptions().forEach(Option::reset);
	}

	private void onClose() {
		lastSearch.set("");
		lastSearchIndex.set(0);
		this.minecraft.openScreen(this.prevScreen);
	}

	@Override
	protected void mouseClicked(int mouseX, int mouseY, int button) {
		this.lastMouseX = mouseX;
		this.lastMouseY = mouseY;
		this.frame.clearFocus();
		this.frame.mouseClicked(mouseX, mouseY, button);
	}

	@Override
	protected void mouseReleased(int mouseX, int mouseY, int button) {
		this.frame.mouseReleased(mouseX, mouseY, button);
	}

	@Override
	protected void mouseDragged(int mouseX, int mouseY, int button, long time) {
		this.frame.mouseDragged(mouseX, mouseY, button, mouseX - this.lastMouseX, mouseY - this.lastMouseY);
		this.lastMouseX = mouseX;
		this.lastMouseY = mouseY;
	}

	@Override
	public void handleMouse() {
		super.handleMouse();

		int wheel = Mouse.getEventDWheel();

		if (wheel != 0) {
			int mouseX = Mouse.getEventX() * this.width / this.minecraft.width;
			int mouseY = this.height - Mouse.getEventY() * this.height / this.minecraft.height - 1;
			this.frame.mouseScrolled(mouseX, mouseY, Math.signum(wheel));
		}
	}

	@Override
	protected void keyPressed(char character, int keyCode) {
		if (keyCode == Keyboard.KEY_ESCAPE) {
			if (!this.hasPendingChanges) {
				this.onClose();
			}

			return;
		}

		if (this.frame.keyPressed(keyCode, character)) {
			return;
		}

		if (character >= ' ' && this.frame.charTyped(character)) {
			return;
		}

		if (keyCode == Keyboard.KEY_TAB) {
			this.moveFocus(isShiftDown() ? -1 : 1);
		} else if (keyCode == Keyboard.KEY_DOWN) {
			this.moveFocus(1);
		} else if (keyCode == Keyboard.KEY_UP) {
			this.moveFocus(-1);
		}
	}

	private void moveFocus(int direction) {
		List<AbstractWidget> focusables = new ArrayList<>();
		this.frame.collectFocusables(focusables);

		if (focusables.isEmpty()) {
			return;
		}

		int current = -1;

		for (int i = 0; i < focusables.size(); i++) {
			if (focusables.get(i).isFocused()) {
				current = i;
				break;
			}
		}

		int next = current == -1 ? (direction > 0 ? 0 : focusables.size() - 1) : Math.floorMod(current + direction, focusables.size());
		AbstractWidget widget = focusables.get(next);

		this.frame.clearFocus();
		widget.setFocused(true);
		this.frame.onKeyboardFocus(widget);
	}
}
