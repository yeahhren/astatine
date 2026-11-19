package yt.ren.astatine.gui;

public final class Dim2i {
	private int x;
	private int y;
	private int width;
	private int height;
	private Dim2i anchor;

	public Dim2i(int x, int y, int width, int height) {
		this.x = x;
		this.y = y;
		this.width = width;
		this.height = height;
	}

	public int x() {
		return this.anchor != null ? this.x + this.anchor.x() : this.x;
	}

	public int y() {
		return this.anchor != null ? this.y + this.anchor.y() : this.y;
	}

	public int width() {
		return this.width;
	}

	public int height() {
		return this.height;
	}

	public int getLimitX() {
		return this.x() + this.width;
	}

	public int getLimitY() {
		return this.y() + this.height;
	}

	public int getCenterX() {
		return this.x() + this.width / 2;
	}

	public int getCenterY() {
		return this.y() + this.height / 2;
	}

	public boolean containsCursor(double x, double y) {
		return x >= this.x() && x < this.getLimitX() && y >= this.y() && y < this.getLimitY();
	}

	public void setX(int x) {
		this.x = x;
	}

	public void setY(int y) {
		this.y = y;
	}

	public void setWidth(int width) {
		this.width = width;
	}

	public void setHeight(int height) {
		this.height = height;
	}

	public void setAnchor(Dim2i anchor) {
		this.anchor = anchor;
	}

	public boolean canFitDimension(Dim2i other) {
		return this.x() <= other.x() && this.y() <= other.y() && this.getLimitX() >= other.getLimitX() && this.getLimitY() >= other.getLimitY();
	}

	public boolean overlapWith(Dim2i other) {
		return this.x() < other.getLimitX() && this.getLimitX() > other.x() && this.y() < other.getLimitY() && this.getLimitY() > other.y();
	}
}
