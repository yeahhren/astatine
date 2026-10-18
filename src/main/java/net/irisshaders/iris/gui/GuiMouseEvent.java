package net.irisshaders.iris.gui;

public record GuiMouseEvent(double x, double y, int button) {
	public static final int LEFT = 0;
	public static final int RIGHT = 1;
}
