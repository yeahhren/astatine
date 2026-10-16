package yt.ren.astatine.mixin.state;

import java.nio.ByteBuffer;
import net.minecraft.client.render.platform.GLX;
import org.lwjgl.opengl.ContextCapabilities;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;
import yt.ren.astatine.render.GL;
import yt.ren.astatine.render.GLCapabilities;

@Mixin(GLX.class)
public abstract class GLXMixin {
	@Redirect(method = "init", at = @At(value = "INVOKE", target = "Lorg/lwjgl/opengl/GLContext;getCapabilities()Lorg/lwjgl/opengl/ContextCapabilities;"))
	private static ContextCapabilities astatine$capabilities() {
		return GLCapabilities.getCapabilities();
	}

	@Redirect(method = "init", at = @At(value = "INVOKE", target = "Lorg/lwjgl/opengl/GL11;glGetString(I)Ljava/lang/String;"))
	private static String astatine$string(int name) {
		return GLCapabilities.string(name);
	}

	/**
	 * @author Ren
	 * @reason While this could be an inject, that is too much overhead
	 */
	@Overwrite
	public static boolean useVbo() {
		return true;
	}

	/**
	 * @author Ren
	 * @reason Same as above
	 */
	@Overwrite
	public static boolean useFbo() {
		return true;
	}

	@Redirect(method = "genBuffers", at = @At(value = "INVOKE", target = "Lorg/lwjgl/opengl/GL15;glGenBuffers()I"))
	private static int astatine$genBuffers() {
		return 0;
	}

	@Redirect(method = "bindBuffer", at = @At(value = "INVOKE", target = "Lorg/lwjgl/opengl/GL15;glBindBuffer(II)V"))
	private static void astatine$bindBuffer(int target, int buffer) {
	}

	@Redirect(method = "bufferData", at = @At(value = "INVOKE", target = "Lorg/lwjgl/opengl/GL15;glBufferData(ILjava/nio/ByteBuffer;I)V"))
	private static void astatine$bufferData(int target, ByteBuffer data, int usage) {
	}

	@Redirect(method = "deleteBuffers", at = @At(value = "INVOKE", target = "Lorg/lwjgl/opengl/GL15;glDeleteBuffers(I)V"))
	private static void astatine$deleteBuffers(int buffer) {
	}

	@Redirect(method = "bindFramebuffer", at = @At(value = "INVOKE", target = "Lorg/lwjgl/opengl/GL30;glBindFramebuffer(II)V"))
	private static void astatine$bindFramebuffer(int target, int framebuffer) {
		if (framebuffer == 0) {
			GL.bindFramebuffer(0L);
		}
	}

	@Redirect(method = "activeTexture", at = @At(value = "INVOKE", target = "Lorg/lwjgl/opengl/GL13;glActiveTexture(I)V"))
	private static void astatine$activeTexture(int texture) {
		GL.activeTexture(texture - GLX.GL_TEXTURE0);
	}

	@Redirect(method = "clientActiveTexture", at = @At(value = "INVOKE", target = "Lorg/lwjgl/opengl/GL13;glClientActiveTexture(I)V"))
	private static void astatine$clientActiveTexture(int texture) {
	}

	@Redirect(method = "multiTexCoord2f", at = @At(value = "INVOKE", target = "Lorg/lwjgl/opengl/GL13;glMultiTexCoord2f(IFF)V"))
	private static void astatine$multiTexCoord(int texture, float s, float t) {
		GL.texCoord(texture - GLX.GL_TEXTURE0, s, t);
	}

	@Redirect(method = "blendFuncSeparate", at = @At(value = "INVOKE", target = "Lorg/lwjgl/opengl/GL14;glBlendFuncSeparate(IIII)V"))
	private static void astatine$blendFuncSeparate(int sourceColor, int destinationColor, int sourceAlpha, int destinationAlpha) {
		GL.blendFunc(sourceColor, destinationColor, sourceAlpha, destinationAlpha);
	}
}
