package net.irisshaders.iris.gui.element.widget;

import net.irisshaders.iris.gui.GuiKeyEvent;
import net.irisshaders.iris.gui.GuiMouseEvent;
import net.irisshaders.iris.gui.GuiUtil;
import net.irisshaders.iris.gui.IrisGraphics;
import net.irisshaders.iris.gui.NavigationController;
import net.irisshaders.iris.gui.screen.ShaderPackScreen;
import net.irisshaders.iris.shaderpack.option.menu.OptionMenuLinkElement;
import net.minecraft.client.Minecraft;
import net.minecraft.client.render.TextRenderer;
import net.minecraft.text.Text;

import java.util.Optional;

public class LinkElementWidget extends CommentedElementWidget<OptionMenuLinkElement> {
	private static final Text ARROW = GuiUtil.literal(">");

	private final String targetScreenId;
	private final Text label;

	private NavigationController navigation;
	private Text trimmedLabel = null;
	private boolean isLabelTrimmed = false;

	public LinkElementWidget(OptionMenuLinkElement element) {
		super(element);

		this.targetScreenId = element.targetScreenId;
		this.label = GuiUtil.translateOrDefault(GuiUtil.literal(element.targetScreenId), "screen." + element.targetScreenId);
	}

	@Override
	public void init(ShaderPackScreen screen, NavigationController navigation) {
		this.navigation = navigation;
	}

	@Override
	public void render(IrisGraphics guiGraphics, int mouseX, int mouseY, float tickDelta, boolean hovered) {
		GuiUtil.bindIrisWidgetsTexture();
		GuiUtil.drawButton(guiGraphics, bounds.x(), bounds.y(), bounds.width(), bounds.height(),
			hovered || isFocused(), false);

		TextRenderer font = Minecraft.getInstance().textRenderer;

		int maxLabelWidth = bounds.width() - 9;

		if (GuiUtil.width(this.label) > maxLabelWidth) {
			this.isLabelTrimmed = true;
		}

		if (this.trimmedLabel == null) {
			this.trimmedLabel = GuiUtil.shortenText(font, this.label, maxLabelWidth);
		}

		int labelWidth = GuiUtil.width(this.trimmedLabel);

		guiGraphics.text(font, this.trimmedLabel,
			bounds.centerX() - (int) (labelWidth * 0.5) - (int) (0.5 * Math.max(
				labelWidth - (bounds.width() - 18), 0)), bounds.y() + 7, 0xFFFFFFFF);
		guiGraphics.text(font, ARROW, bounds.right() - 9, bounds.y() + 7, 0xFFFFFFF);

		if (hovered && this.isLabelTrimmed) {
			// To prevent other elements from being drawn on top of the tooltip
			ShaderPackScreen.TOP_LAYER_RENDER_QUEUE.add(
				() -> GuiUtil.drawTextPanel(font, guiGraphics, this.label, mouseX + 2, mouseY - 16));
		}
	}

	@Override
	public boolean mouseClicked(GuiMouseEvent event, boolean bl2) {
		if (event.button() == GuiMouseEvent.LEFT) {
			this.navigation.open(targetScreenId);
			GuiUtil.playButtonClickSound();

			return true;
		}
		return super.mouseClicked(event, bl2);
	}

	@Override
	public boolean keyPressed(GuiKeyEvent event) {
		if (event.isConfirmation()) {
			this.navigation.open(targetScreenId);
			GuiUtil.playButtonClickSound();

			return true;
		}

		return super.keyPressed(event);
	}

	@Override
	public Optional<Text> getCommentTitle() {
		return Optional.of(this.label);
	}

	@Override
	public Optional<Text> getCommentBody() {
		String translation = "screen." + this.targetScreenId + ".comment";
		return Optional.ofNullable(GuiUtil.hasTranslation(translation) ? GuiUtil.translatable(translation) : null);
	}
}
