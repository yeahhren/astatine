package net.irisshaders.iris.gui.option;

import net.irisshaders.iris.Iris;
import net.minecraft.client.Minecraft;
import net.minecraft.client.render.platform.GlStateManager;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.util.math.MathHelper;

import java.io.IOException;

public class ShadowDistanceOption extends ButtonWidget {
	private static final int MAX_DISTANCE = 32;
	private float value;
	private boolean dragging;

	public ShadowDistanceOption(int id, int x, int y, int width) {
		super(id, x, y, width, 20, "");
		this.value = IrisVideoSettings.getOverriddenShadowDistance(IrisVideoSettings.shadowDistance) / (float) MAX_DISTANCE;
		this.active = IrisVideoSettings.isShadowDistanceSliderEnabled();
		this.updateMessage();
	}

	public String getTooltip() {
		return IrisVideoSettings.getTooltip();
	}

	private void updateMessage() {
		this.message = IrisVideoSettings.getMessage(Math.round(this.value * MAX_DISTANCE));
	}

	private void setFromMouse(int mouseX) {
		this.value = MathHelper.clamp((mouseX - (this.x + 4)) / (float) (this.width - 8), 0.0f, 1.0f);
		int distance = Math.round(this.value * MAX_DISTANCE);
		this.value = distance / (float) MAX_DISTANCE;

		if (distance != IrisVideoSettings.shadowDistance) {
			IrisVideoSettings.shadowDistance = distance;

			try {
				Iris.getIrisConfig().save();
			} catch (IOException e) {
				Iris.logger.fatal("Failed to save config!", e);
			}
		}

		this.updateMessage();
	}

	@Override
	protected int getYImage(boolean hovered) {
		return 0;
	}

	@Override
	protected void renderBackground(Minecraft minecraft, int mouseX, int mouseY) {
		if (!this.visible) {
			return;
		}

		if (this.dragging) {
			this.setFromMouse(mouseX);
		}

		minecraft.getTextureManager().bind(WIDGETS_LOCATION);
		GlStateManager.color4f(1.0f, 1.0f, 1.0f, 1.0f);
		int knob = this.x + (int) (this.value * (this.width - 8));
		this.drawTexture(knob, this.y, 0, 66, 4, 20);
		this.drawTexture(knob + 4, this.y, 196, 66, 4, 20);
	}

	@Override
	public boolean mouseClicked(Minecraft minecraft, int mouseX, int mouseY) {
		if (super.mouseClicked(minecraft, mouseX, mouseY)) {
			this.setFromMouse(mouseX);
			this.dragging = true;
			return true;
		}

		return false;
	}

	@Override
	public void mouseReleased(int mouseX, int mouseY) {
		this.dragging = false;
	}
}
