package net.irisshaders.iris.gui.element.screen;

import net.irisshaders.iris.gl.uniform.FloatSupplier;
import net.irisshaders.iris.gui.GuiUtil;
import net.irisshaders.iris.gui.IrisGraphics;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;

public class IrisButton extends ButtonWidget {
	private final FloatSupplier alphaSupplier;
	private final OnPress onPress;

	public IrisButton(int pButton0, int pInt1, int pInt2, int pInt3, Text pComponent4, OnPress pButton$OnPress5, FloatSupplier alpha) {
		super(0, pButton0, pInt1, pInt2, pInt3, pComponent4.getFormattedString());
		this.alphaSupplier = alpha;
		this.onPress = pButton$OnPress5;
	}

	public static IrisButton.Builder iris$builder(Text pComponent0, OnPress pButton$OnPress1, FloatSupplier alpha) {
		return new IrisButton.Builder(pComponent0, pButton$OnPress1, alpha);
	}

	public float getAlpha() {
		return this.alphaSupplier.getAsFloat();
	}

	public void onPress() {
		this.onPress.onPress(this);
	}

	public void setMessage(Text message) {
		this.message = message.getFormattedString();
	}

	@Override
	public void render(Minecraft minecraft, int mouseX, int mouseY) {
		if (!this.visible) {
			return;
		}

		this.hovered = mouseX >= this.x && mouseY >= this.y && mouseX < this.x + this.width && mouseY < this.y + this.height;
		IrisGraphics guiGraphics = IrisGraphics.INSTANCE;
		guiGraphics.setAlpha(this.isHovered() ? Math.min(1.0f, this.alphaSupplier.getAsFloat() * 1.8f) : this.alphaSupplier.getAsFloat());
		GuiUtil.bindIrisWidgetsTexture();
		GuiUtil.drawButton(guiGraphics, this.x, this.y, this.width, this.height, this.isHovered(), !this.active);
		guiGraphics.setAlpha(1.0f);
		int lvInt6 = this.active ? 16777215 : 10526880;
		this.drawCenteredString(minecraft.textRenderer, this.message, this.x + this.width / 2, this.y + (this.height - 8) / 2, lvInt6);
	}

	public interface OnPress {
		void onPress(IrisButton button);
	}

	public static class Builder {
		private final Text message;
		private final OnPress onPress;
		private final FloatSupplier alpha;
		private int x;
		private int y;
		private int width = 150;
		private int height = 20;

		public Builder(Text pButton$Builder0, OnPress pButton$OnPress1, FloatSupplier alpha) {
			this.message = pButton$Builder0;
			this.onPress = pButton$OnPress1;
			this.alpha = alpha;
		}

		public IrisButton.Builder pos(int pButton$Builder0, int pInt1) {
			this.x = pButton$Builder0;
			this.y = pInt1;
			return this;
		}

		public IrisButton.Builder width(int pButton$Builder0) {
			this.width = pButton$Builder0;
			return this;
		}

		public IrisButton.Builder size(int pButton$Builder0, int pInt1) {
			this.width = pButton$Builder0;
			this.height = pInt1;
			return this;
		}

		public IrisButton.Builder bounds(int pButton$Builder0, int pInt1, int pInt2, int pInt3) {
			return this.pos(pButton$Builder0, pInt1).size(pInt2, pInt3);
		}

		public IrisButton build() {
			IrisButton lvButton1 = new IrisButton(this.x, this.y, this.width, this.height, this.message, this.onPress, this.alpha);
			lvButton1.active = true;
			return lvButton1;
		}
	}
}
