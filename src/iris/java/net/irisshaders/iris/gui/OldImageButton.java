package net.irisshaders.iris.gui;

import net.irisshaders.iris.gui.element.screen.IrisButton;
import net.minecraft.client.Minecraft;
import net.minecraft.resource.Identifier;
import net.minecraft.text.Text;

public class OldImageButton extends IrisButton {
	protected final Identifier Identifier;
	protected final int xTexStart;
	protected final int yTexStart;
	protected final int yDiffTex;
	protected final int textureWidth;
	protected final int textureHeight;

	public OldImageButton(int pImageButton0, int pInt1, int pInt2, int pInt3, int pInt4, int pInt5, Identifier pIdentifier6,
		IrisButton.OnPress pButton$OnPress7) {
		this(pImageButton0, pInt1, pInt2, pInt3, pInt4, pInt5, pInt3, pIdentifier6, 256, 256, pButton$OnPress7);
	}

	public OldImageButton(int pImageButton0, int pInt1, int pInt2, int pInt3, int pInt4, int pInt5, int pInt6, Identifier pIdentifier7,
		IrisButton.OnPress pButton$OnPress8) {
		this(pImageButton0, pInt1, pInt2, pInt3, pInt4, pInt5, pInt6, pIdentifier7, 256, 256, pButton$OnPress8);
	}

	public OldImageButton(int pImageButton0, int pInt1, int pInt2, int pInt3, int pInt4, int pInt5, int pInt6, Identifier pIdentifier7,
		int pInt8, int pInt9, IrisButton.OnPress pButton$OnPress10) {
		this(pImageButton0, pInt1, pInt2, pInt3, pInt4, pInt5, pInt6, pIdentifier7, pInt8, pInt9, pButton$OnPress10, GuiUtil.empty());
	}

	public OldImageButton(int pImageButton0, int pInt1, int pInt2, int pInt3, int pInt4, int pInt5, int pInt6, Identifier pIdentifier7,
		int pInt8, int pInt9, IrisButton.OnPress pButton$OnPress10, Text pComponent11) {
		super(pImageButton0, pInt1, pInt2, pInt3, pComponent11, pButton$OnPress10, () -> 1.0f);
		this.textureWidth = pInt8;
		this.textureHeight = pInt9;
		this.xTexStart = pInt4;
		this.yTexStart = pInt5;
		this.yDiffTex = pInt6;
		this.Identifier = pIdentifier7;
	}

	@Override
	public void render(Minecraft minecraft, int mouseX, int mouseY) {
		if (!this.visible) {
			return;
		}

		this.hovered = mouseX >= this.x && mouseY >= this.y && mouseX < this.x + this.width && mouseY < this.y + this.height;
		this.renderTexture(IrisGraphics.INSTANCE, this.Identifier, this.x, this.y, this.xTexStart, this.yTexStart, this.yDiffTex,
			this.width, this.height, this.textureWidth, this.textureHeight);
	}

	public void renderTexture(IrisGraphics pAbstractWidget0, Identifier pIdentifier1, int pInt2, int pInt3, int pInt4, int pInt5,
		int pInt6, int pInt7, int pInt8, int pInt9, int pInt10) {
		int lvInt12 = pInt5;
		if (!this.active) {
			lvInt12 = pInt5 + pInt6 * 2;
		} else if (this.isHovered()) {
			lvInt12 = pInt5 + pInt6;
		}

		pAbstractWidget0.blit(pIdentifier1, pInt2, pInt3, (float) pInt4, (float) lvInt12, pInt7, pInt8, pInt9, pInt10);
	}
}
