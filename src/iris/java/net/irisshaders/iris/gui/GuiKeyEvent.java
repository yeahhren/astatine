package net.irisshaders.iris.gui;

import org.lwjgl.input.Keyboard;

public record GuiKeyEvent(int key) {
	public boolean isEscape() {
		return this.key == Keyboard.KEY_ESCAPE;
	}

	public boolean isConfirmation() {
		return this.key == Keyboard.KEY_RETURN || this.key == Keyboard.KEY_NUMPADENTER || this.key == Keyboard.KEY_SPACE;
	}

	public boolean isCycleFocus() {
		return this.key == Keyboard.KEY_TAB;
	}

	public boolean isUp() {
		return this.key == Keyboard.KEY_UP;
	}

	public boolean isDown() {
		return this.key == Keyboard.KEY_DOWN;
	}

	public boolean isLeft() {
		return this.key == Keyboard.KEY_LEFT;
	}

	public boolean isRight() {
		return this.key == Keyboard.KEY_RIGHT;
	}
}
