package yt.ren.astatine.gui;

import net.minecraft.client.gui.GuiElement;

import java.util.ArrayDeque;
import java.util.Deque;

public final class Clip {
	private static final Deque<int[]> STACK = new ArrayDeque<>();

	private Clip() {
	}

	public static void push(int x, int y, int width, int height) {
		int x1 = x, y1 = y, x2 = x + width, y2 = y + height;
		int[] parent = STACK.peek();

		if (parent != null) {
			x1 = Math.max(x1, parent[0]);
			y1 = Math.max(y1, parent[1]);
			x2 = Math.min(x2, parent[2]);
			y2 = Math.min(y2, parent[3]);
		}

		STACK.push(new int[]{x1, y1, x2, y2});
	}

	public static void pop() {
		STACK.pop();
	}

	public static void run(int x, int y, int width, int height, Runnable action) {
		push(x, y, width, height);

		try {
			action.run();
		} finally {
			pop();
		}
	}

	public static void reset() {
		STACK.clear();
	}

	public static void fill(int x1, int y1, int x2, int y2, int color) {
		int[] clip = STACK.peek();

		if (clip != null) {
			x1 = Math.max(x1, clip[0]);
			y1 = Math.max(y1, clip[1]);
			x2 = Math.min(x2, clip[2]);
			y2 = Math.min(y2, clip[3]);
		}

		if (x1 < x2 && y1 < y2) {
			GuiElement.fill(x1, y1, x2, y2, color);
		}
	}

	public static boolean containsText(int x, int y, int width) {
		int[] clip = STACK.peek();
		return clip == null || (y >= clip[1] && y + 8 <= clip[3] && x < clip[2] && x + width > clip[0]);
	}
}
