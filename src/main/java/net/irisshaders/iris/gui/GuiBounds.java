package net.irisshaders.iris.gui;

public record GuiBounds(int x, int y, int width, int height) {
	public static final GuiBounds EMPTY = new GuiBounds(0, 0, 0, 0);

	public int right() {
		return this.x + this.width;
	}

	public int bottom() {
		return this.y + this.height;
	}

	public int centerX() {
		return this.x + this.width / 2;
	}

	public boolean contains(double mouseX, double mouseY) {
		return mouseX >= this.x && mouseX < this.right() && mouseY >= this.y && mouseY < this.bottom();
	}
}
