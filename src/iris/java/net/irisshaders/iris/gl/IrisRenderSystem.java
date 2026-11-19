package net.irisshaders.iris.gl;

import com.mojang.blaze3d.systems.RenderSystem;
import net.irisshaders.iris.pipeline.GraphicsBackend;
import yt.ren.astatine.backend.AstatineDevice;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix4f;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL30C;
import org.lwjgl.opengl.GL32C;
import org.lwjgl.opengl.GL33C;
import org.lwjgl.opengl.GL46C;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.system.MemoryUtil;
import yt.ren.astatine.render.GL;

import java.nio.ByteBuffer;

/**
 * This class is responsible for abstracting calls to OpenGL and asserting that calls are run on the render thread.
 */
public class IrisRenderSystem {
	private static int lastTex = -1;

	public static void texImage1D(int texture, int target, int level, int internalformat, int width, int border, int format, int type,
		@Nullable ByteBuffer pixels) {
		RenderSystem.assertOnRenderThread();
		IrisRenderSystem.bindTextureForSetup(target, texture);
		GL30C.glTexImage1D(target, level, internalformat, width, border, format, type, pixels);
	}

	public static void texImage2D(int texture, int target, int level, int internalformat, int width, int height, int border, int format,
		int type, @Nullable ByteBuffer pixels) {
		RenderSystem.assertOnRenderThread();
		IrisRenderSystem.bindTextureForSetup(target, texture);
		GL32C.glTexImage2D(target, level, internalformat, width, height, border, format, type, pixels);
	}

	public static void texImage3D(int texture, int target, int level, int internalformat, int width, int height, int depth, int border,
		int format, int type, @Nullable ByteBuffer pixels) {
		RenderSystem.assertOnRenderThread();
		IrisRenderSystem.bindTextureForSetup(target, texture);
		GL30C.glTexImage3D(target, level, internalformat, width, height, depth, border, format, type, pixels);
	}

	public static void setShadowProjection(Matrix4f shadowProjection) {
		GL.matrixMode(GL11.GL_PROJECTION);
		GL.pushMatrix();
		GL.loadIdentity();

		try (var stack = MemoryStack.stackPush()) {
			var values = stack.mallocFloat(16);
			shadowProjection.get(values);
			GL.multMatrix(MemoryUtil.memAddress(values));
		}

		GL.matrixMode(GL11.GL_MODELVIEW);
	}

	public static void restorePlayerProjection() {
		GL.matrixMode(GL11.GL_PROJECTION);
		GL.popMatrix();
		GL.matrixMode(GL11.GL_MODELVIEW);
	}

	public static void bindTextureForSetup(int glType, int glId) {
		if (glType == GL46C.GL_TEXTURE_2D) {
			lastTex = GL30C.glGetInteger(GL30C.GL_TEXTURE_BINDING_2D);
		}
		GL30C.glBindTexture(glType, glId);
	}

	public static void restoreTexture() {
		if (lastTex != -1) {
			GL30C.glBindTexture(GL46C.GL_TEXTURE_2D, lastTex);
			lastTex = -1;
		}
	}

	public static boolean supportsCompute() {
		return GraphicsBackend.isVulkan();
	}

	public static boolean supportsTesselation() {
		return AstatineDevice.get().supportsTessellationShaders();
	}

	public static int genSampler() {
		return GL33C.glGenSamplers();
	}

	public static void destroySampler(int glId) {
		GL33C.glDeleteSamplers(glId);
	}

	public static void samplerParameteri(int sampler, int pname, int param) {
		GL33C.glSamplerParameteri(sampler, pname, param);
	}

	public static void samplerParameterf(int sampler, int pname, float param) {
		GL33C.glSamplerParameterf(sampler, pname, param);
	}

	public static boolean supportsSSBO() {
		return GraphicsBackend.isVulkan() || org.lwjgl.opengl.GL.getCapabilities().OpenGL44;
	}

	public static boolean supportsImageLoadStore() {
		return GraphicsBackend.isVulkan();
	}
}
