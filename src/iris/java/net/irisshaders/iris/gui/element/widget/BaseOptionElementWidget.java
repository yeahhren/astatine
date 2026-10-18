package net.irisshaders.iris.gui.element.widget;

import net.irisshaders.iris.gui.GuiKeyEvent;
import net.irisshaders.iris.gui.GuiMouseEvent;
import net.irisshaders.iris.gui.GuiUtil;
import net.irisshaders.iris.gui.IrisGraphics;
import net.irisshaders.iris.gui.NavigationController;
import net.irisshaders.iris.gui.screen.ShaderPackScreen;
import net.irisshaders.iris.shaderpack.option.menu.OptionMenuElement;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.render.TextRenderer;
import net.minecraft.text.Formatting;
import net.minecraft.text.Text;
import org.jetbrains.annotations.Nullable;

import java.util.Optional;

public abstract class BaseOptionElementWidget<T extends OptionMenuElement> extends CommentedElementWidget<T> {
	protected static final Text SET_TO_DEFAULT = GuiUtil.withStyle(GuiUtil.translatable("options.iris.setToDefault"), Formatting.GREEN);
	protected static final Text DIVIDER = GuiUtil.literal(": ");

	protected Text unmodifiedLabel;
	protected ShaderPackScreen screen;
	protected NavigationController navigation;
	protected Text trimmedLabel;
	protected Text valueLabel;
	protected boolean usedKeyboard;
	private Text label;
	private boolean isLabelTrimmed;
	private int maxLabelWidth;
	private int valueSectionWidth;

	public BaseOptionElementWidget(T element) {
		super(element);
	}

	@Override
	public void init(ShaderPackScreen screen, NavigationController navigation) {
		this.screen = screen;
		this.navigation = navigation;
		this.valueLabel = null;
		this.trimmedLabel = null;
	}

	protected final void setLabel(Text label) {
		this.label = label.copy().append(DIVIDER.copy());
		this.unmodifiedLabel = label;
	}

	protected final void updateRenderParams(int minValueSectionWidth) {
		// Check if we used the keyboard to access this value
		usedKeyboard = isFocused();

		// Lazy init of value label
		if (this.valueLabel == null) {
			this.valueLabel = createValueLabel();
		}

		// Determine the width of the value box
		TextRenderer font = Minecraft.getInstance().textRenderer;
		this.valueSectionWidth = Math.max(minValueSectionWidth, GuiUtil.width(this.valueLabel) + 8);

		// Determine maximum width of trimmed label
		this.maxLabelWidth = (bounds.width() - 8) - this.valueSectionWidth;

		// Lazy init of trimmed label, and make sure it is only trimmed when necessary
		if (this.trimmedLabel == null || GuiUtil.width(this.label) > this.maxLabelWidth != isLabelTrimmed) {
			updateLabels();
		}

		// Set whether the label has been trimmed (used when updating label and determining whether to render tooltips)
		this.isLabelTrimmed = GuiUtil.width(this.label) > this.maxLabelWidth;
	}

	protected final void renderOptionWithValue(IrisGraphics guiGraphics, boolean hovered, float sliderPosition, int sliderWidth) {
		GuiUtil.bindIrisWidgetsTexture();

		// Draw button background
		GuiUtil.drawButton(guiGraphics, bounds.x(), bounds.y(), bounds.width(), bounds.height(), hovered, false);

		// Draw the value box
		GuiUtil.drawButton(guiGraphics, bounds.right() - (this.valueSectionWidth + 2),
			bounds.y() + 2, this.valueSectionWidth, bounds.height() - 4, false, true);

		// Draw the preview slider
		if (sliderPosition >= 0) {
			// Range of x values the slider can occupy
			int sliderSpace = (this.valueSectionWidth - 4) - sliderWidth;

			// Position of slider
			int sliderPos = (bounds.right() - this.valueSectionWidth) + (int) (sliderPosition * sliderSpace);

			GuiUtil.drawButton(guiGraphics, sliderPos, bounds.y() + 4, sliderWidth, bounds.height() - 8, false, false);
		}

		TextRenderer font = Minecraft.getInstance().textRenderer;

		// Draw the label
		guiGraphics.text(font, this.trimmedLabel, bounds.x() + 6, bounds.y() + 7, 0xFFFFFFFF);
		// Draw the value label
		guiGraphics.text(font, this.valueLabel,
			(bounds.right() - 2) - (int) (this.valueSectionWidth * 0.5) - (int) (GuiUtil.width(
				this.valueLabel) * 0.5), bounds.y() + 7, 0xFFFFFFFF);
	}

	protected final void renderOptionWithValue(IrisGraphics guiGraphics, boolean hovered) {
		this.renderOptionWithValue(guiGraphics, hovered, -1, 0);
	}

	protected final void tryRenderTooltip(IrisGraphics guiGraphics, int mouseX, int mouseY, boolean hovered) {
		if (Screen.isShiftDown()) {
			renderTooltip(guiGraphics, SET_TO_DEFAULT, mouseX, mouseY, hovered);
		} else if (this.isLabelTrimmed && !this.screen.isDisplayingComment()) {
			renderTooltip(guiGraphics, this.unmodifiedLabel, mouseX, mouseY, hovered);
		}
	}

	protected final void renderTooltip(IrisGraphics guiGraphics, Text text, int mouseX, int mouseY, boolean hovered) {
		if (hovered) {
			ShaderPackScreen.TOP_LAYER_RENDER_QUEUE.add(
				() -> GuiUtil.drawTextPanel(Minecraft.getInstance().textRenderer, guiGraphics, text, mouseX + 2, mouseY - 16));
		}
	}

	protected final void updateLabels() {
		this.trimmedLabel = createTrimmedLabel();
		this.valueLabel = createValueLabel();
	}

	protected final Text createTrimmedLabel() {
		Text label = GuiUtil.shortenText(Minecraft.getInstance().textRenderer, this.label.copy(), this.maxLabelWidth);

		if (this.isValueModified()) {
			label = GuiUtil.withColor(label, 0xffc94a);
		}

		return label;
	}

	protected abstract Text createValueLabel();

	public abstract boolean applyNextValue();

	public abstract boolean applyPreviousValue();

	public abstract boolean applyOriginalValue();

	public abstract boolean isValueModified();

	public abstract @Nullable String getCommentKey();

	@Override
	public Optional<Text> getCommentTitle() {
		return Optional.of(this.unmodifiedLabel);
	}

	@Override
	public Optional<Text> getCommentBody() {
		return Optional.ofNullable(getCommentKey()).map(key -> GuiUtil.hasTranslation(key) ? GuiUtil.translatable(key) : null);
	}

	@Override
	public boolean mouseClicked(GuiMouseEvent event, boolean bl2) {
		if (event.button() == GuiMouseEvent.LEFT || event.button() == GuiMouseEvent.RIGHT) {
			boolean refresh = false;

			if (Screen.isShiftDown()) {
				refresh = applyOriginalValue();
			}
			if (!refresh) {
				if (event.button() == GuiMouseEvent.LEFT) {
					refresh = applyNextValue();
				} else {
					refresh = applyPreviousValue();
				}
			}

			if (refresh) {
				this.navigation.refresh();
			}

			GuiUtil.playButtonClickSound();

			return true;
		}
		return super.mouseClicked(event, bl2);
	}

	@Override
	public boolean keyPressed(GuiKeyEvent event) {
		if (event.isConfirmation()) {
			boolean refresh = Screen.isControlDown() ? applyOriginalValue() : (Screen.isShiftDown() ? applyPreviousValue() : applyNextValue());

			if (refresh) {
				this.navigation.refresh();
			}

			GuiUtil.playButtonClickSound();

			return true;
		}

		return false;
	}
}
