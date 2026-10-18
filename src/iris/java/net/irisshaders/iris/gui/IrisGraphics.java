package net.irisshaders.iris.gui;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiElement;
import net.minecraft.client.render.TextRenderer;
import net.minecraft.client.render.platform.GlStateManager;
import net.minecraft.resource.Identifier;
import net.minecraft.text.Text;

public final class IrisGraphics {
	public static final IrisGraphics INSTANCE = new IrisGraphics();

	private float alpha = 1.0f;

	private IrisGraphics() {
	}

	public void setAlpha(float alpha) {
		this.alpha = alpha;
	}

	public void text(TextRenderer font, Text text, int x, int y, int color) {
		this.text(font, text.getFormattedString(), x, y, color);
	}

	public void text(TextRenderer font, String text, int x, int y, int color) {
		font.drawWithShadow(text, x, y, color);
	}

	public void centeredText(TextRenderer font, Text text, int x, int y, int color) {
		String formatted = text.getFormattedString();
		this.text(font, formatted, x - font.getWidth(formatted) / 2, y, color);
	}

	public void scrollingText(TextRenderer font, Text text, int centerX, int left, int right, int top, int bottom) {
		String formatted = text.getFormattedString();
		int width = font.getWidth(formatted);
		int x = width > right - left ? left : centerX - width / 2;
		this.text(font, font.trim(formatted, right - left), x, top + (bottom - top - 8) / 2, 0xFFFFFFFF);
	}

	public void fill(int x1, int y1, int x2, int y2, int color) {
		GuiElement.fill(x1, y1, x2, y2, color);
	}

	public void blit(Identifier texture, int x, int y, float u, float v, int width, int height, int textureWidth, int textureHeight) {
		Minecraft.getInstance().getTextureManager().bind(texture);
		GlStateManager.enableBlend();
		GlStateManager.color4f(1.0f, 1.0f, 1.0f, this.alpha);
		GuiElement.drawTexture(x, y, u, v, width, height, textureWidth, textureHeight);
		GlStateManager.color4f(1.0f, 1.0f, 1.0f, 1.0f);
	}
}
