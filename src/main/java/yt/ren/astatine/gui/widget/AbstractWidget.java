package yt.ren.astatine.gui.widget;

import net.minecraft.client.Minecraft;
import net.minecraft.client.render.TextRenderer;
import net.minecraft.client.render.platform.GlStateManager;
import net.minecraft.client.sound.instance.SimpleSoundInstance;
import net.minecraft.resource.Identifier;
import yt.ren.astatine.gui.Clip;

public abstract class AbstractWidget {
	protected final TextRenderer font;
	protected boolean focused;
	protected boolean hovered;

	protected AbstractWidget() {
		this.font = Minecraft.getInstance().textRenderer;
	}

	public abstract void render(int mouseX, int mouseY, float delta);

	public boolean mouseClicked(double mouseX, double mouseY, int button) {
		return false;
	}

	public boolean mouseReleased(double mouseX, double mouseY, int button) {
		return false;
	}

	public boolean mouseDragged(double mouseX, double mouseY, int button, double deltaX, double deltaY) {
		return false;
	}

	public boolean mouseScrolled(double mouseX, double mouseY, double amount) {
		return false;
	}

	/**
	 * @param keyCode an LWJGL 2 key code
	 */
	public boolean keyPressed(int keyCode, char character) {
		return false;
	}

	public boolean charTyped(char character) {
		return false;
	}

	public boolean isMouseOver(double mouseX, double mouseY) {
		return false;
	}

	/**
	 * Whether keyboard navigation can move focus to this widget.
	 */
	public boolean isFocusable() {
		return false;
	}

	protected void drawString(String str, int x, int y, int color) {
		if (Clip.containsText(x, y, this.font.getWidth(str))) {
			// Modern GuiGraphics blends text; 1.8.9's fill() leaves blending off, which would make dimmed text opaque.
			GlStateManager.enableBlend();
			GlStateManager.blendFuncSeparate(770, 771, 1, 0);
			this.font.drawWithShadow(str, x, y, color);
			GlStateManager.disableBlend();
		}
	}

	public boolean isHovered() {
		return this.hovered;
	}

	protected void drawRect(int x1, int y1, int x2, int y2, int color) {
		Clip.fill(x1, y1, x2, y2, color);
	}

	protected void playClickSound() {
		Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.of(new Identifier("gui.button.press"), 1.0F));
	}

	protected int getStringWidth(String text) {
		return this.font.getWidth(text);
	}

	public boolean isFocused() {
		return this.focused;
	}

	public void setFocused(boolean focused) {
		this.focused = focused;
	}

	protected void drawBorder(int x1, int y1, int x2, int y2, int color) {
		Clip.fill(x1, y1, x2, y1 + 1, color);
		Clip.fill(x1, y2 - 1, x2, y2, color);
		Clip.fill(x1, y1, x1 + 1, y2, color);
		Clip.fill(x2 - 1, y1, x2, y2, color);
	}
}
