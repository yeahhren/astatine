package net.irisshaders.iris.gui.element;

import net.minecraft.client.Minecraft;

public class IrisContainerObjectSelectionList<E extends IrisObjectSelectionList.Entry> extends IrisObjectSelectionList<E> {
	public IrisContainerObjectSelectionList(Minecraft client, int width, int height, int top, int bottom, int left, int right,
		int itemHeight) {
		super(client, width, height, top, bottom, left, right, itemHeight);
	}
}
