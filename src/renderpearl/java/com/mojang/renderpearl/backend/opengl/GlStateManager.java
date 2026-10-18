package com.mojang.renderpearl.backend.opengl;

import org.lwjgl.opengl.GL11C;
import org.lwjgl.opengl.GL13C;
import org.lwjgl.opengl.GL15C;
import org.lwjgl.opengl.GL20C;
import org.lwjgl.opengl.GL30C;
import org.lwjgl.opengl.GL32C;

public final class GlStateManager {
	private GlStateManager() {
	}

	public static int _getInteger(int name) {
		return GL11C.glGetInteger(name);
	}

	public static void _disableBlend(int buffer) {
		GL30C.glDisablei(GL11C.GL_BLEND, buffer);
	}

	public static void _enableBlend(int buffer) {
		GL30C.glEnablei(GL11C.GL_BLEND, buffer);
	}

	public static void _bindTexture(int texture) {
		GL11C.glBindTexture(GL11C.GL_TEXTURE_2D, texture);
	}

	public static void _activeTexture(int unit) {
		GL13C.glActiveTexture(unit);
	}

	public static void _glBindFramebuffer(int target, int framebuffer) {
		GL30C.glBindFramebuffer(target, framebuffer);
	}

	public static int _glGenBuffers() {
		return GL15C.glGenBuffers();
	}

	public static void _glBindBuffer(int target, int buffer) {
		GL15C.glBindBuffer(target, buffer);
	}

	public static int glGenFramebuffers() {
		return GL30C.glGenFramebuffers();
	}

	public static int _genTexture() {
		return GL11C.glGenTextures();
	}

	public static void _pixelStore(int name, int value) {
		GL11C.glPixelStorei(name, value);
	}

	public static int _getTexLevelParameter(int target, int level, int name) {
		return GL11C.glGetTexLevelParameteri(target, level, name);
	}

	public static void _viewport(int x, int y, int width, int height) {
		GL11C.glViewport(x, y, width, height);
	}

	public static void _glFramebufferTexture2D(int target, int attachment, int textureTarget, int texture, int level) {
		GL30C.glFramebufferTexture2D(target, attachment, textureTarget, texture, level);
	}

	public static void _clear(int mask) {
		GL11C.glClear(mask);
	}

	public static void _glUseProgram(int program) {
		GL20C.glUseProgram(program);
	}

	public static int _glGetUniformLocation(int program, CharSequence name) {
		return GL20C.glGetUniformLocation(program, name);
	}

	public static void _glUniform1i(int location, int value) {
		GL20C.glUniform1i(location, value);
	}

	public static void _glFramebufferTexture(int target, int attachment, int texture, int level) {
		GL32C.glFramebufferTexture(target, attachment, texture, level);
	}
}
