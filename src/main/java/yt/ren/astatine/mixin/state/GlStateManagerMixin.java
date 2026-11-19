package yt.ren.astatine.mixin.state;

import java.nio.FloatBuffer;
import net.irisshaders.iris.uniforms.CapturedRenderingState;
import net.minecraft.client.render.platform.GlStateManager;
import org.lwjgl.opengl.GL11;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;
import yt.ren.astatine.Native;
import yt.ren.astatine.memory.Memory;
import yt.ren.astatine.render.GL;
import yt.ren.astatine.render.GLState;
import yt.ren.astatine.render.TextureBinding;
import yt.ren.astatine.shaderpack.Shaderpacks;

@Mixin(GlStateManager.class)
public abstract class GlStateManagerMixin {
	@Unique
	private static int astatine$names;

	@Redirect(method = "alphaFunc", at = @At(value = "INVOKE", target = "Lorg/lwjgl/opengl/GL11;glAlphaFunc(IF)V"))
	private static void astatine$alphaFunc(int function, float reference) {
		GL.alphaFunc(function, reference);
	}

	@Redirect(method = "colorMaterial", at = @At(value = "INVOKE", target = "Lorg/lwjgl/opengl/GL11;glColorMaterial(II)V"))
	private static void astatine$colorMaterial(int face, int mode) {
		GL.colorMaterial(mode);
	}

	@Redirect(method = "depthFunc", at = @At(value = "INVOKE", target = "Lorg/lwjgl/opengl/GL11;glDepthFunc(I)V"))
	private static void astatine$depthFunc(int function) {
		GL.depthFunc(function);
	}

	@Redirect(method = "depthMask", at = @At(value = "INVOKE", target = "Lorg/lwjgl/opengl/GL11;glDepthMask(Z)V"))
	private static void astatine$depthMask(boolean mask) {
		GL.depthMask(mask);
	}

	@Redirect(method = "blendFunc", at = @At(value = "INVOKE", target = "Lorg/lwjgl/opengl/GL11;glBlendFunc(II)V"))
	private static void astatine$blendFunc(int source, int destination) {
		GL.blendFunc(source, destination, source, destination);
	}

	@Redirect(method = "fogMode", at = @At(value = "INVOKE", target = "Lorg/lwjgl/opengl/GL11;glFogi(II)V"))
	private static void astatine$fogMode(int name, int mode) {
		GL.fogMode(mode);
		Shaderpacks.fogMode(mode);
	}

	@Redirect(method = {"fogDensity", "fogStart", "fogEnd"}, at = @At(value = "INVOKE", target = "Lorg/lwjgl/opengl/GL11;glFogf(IF)V"))
	private static void astatine$fog(int name, float value) {
		switch (name) {
			case GL11.GL_FOG_DENSITY:
				GL.fogDensity(value);
				Shaderpacks.fogDensity(value);
				break;
			case GL11.GL_FOG_START:
				GL.fogStart(value);
				CapturedRenderingState.INSTANCE.setFogStart(value);
				break;
			case GL11.GL_FOG_END:
				GL.fogEnd(value);
				CapturedRenderingState.INSTANCE.setFogEnd(value);
				break;
			default:
				break;
		}
	}

	@Redirect(method = "cullFace", at = @At(value = "INVOKE", target = "Lorg/lwjgl/opengl/GL11;glCullFace(I)V"))
	private static void astatine$cullFace(int face) {
		GL.cullFace(face);
	}

	@Redirect(method = "polygonOffset", at = @At(value = "INVOKE", target = "Lorg/lwjgl/opengl/GL11;glPolygonOffset(FF)V"))
	private static void astatine$polygonOffset(float factor, float units) {
		GL.polygonOffset(factor, units);
	}

	@Redirect(method = "logicOp", at = @At(value = "INVOKE", target = "Lorg/lwjgl/opengl/GL11;glLogicOp(I)V"))
	private static void astatine$logicOp(int operation) {
		GL.logicOp(operation);
	}

	@Redirect(method = "texGenMode", at = @At(value = "INVOKE", target = "Lorg/lwjgl/opengl/GL11;glTexGeni(III)V"))
	private static void astatine$texGenMode(int coordinate, int name, int mode) {
		GL.texGenMode(coordinate - GL11.GL_S, mode);
	}

	@Redirect(method = "texGenParam", at = @At(value = "INVOKE", target = "Lorg/lwjgl/opengl/GL11;glTexGen(IILjava/nio/FloatBuffer;)V"))
	private static void astatine$texGenParam(int coordinate, int name, FloatBuffer plane) {
		int at = plane.position();
		GL.texGenPlane(coordinate - GL11.GL_S, name, plane.get(at), plane.get(at + 1), plane.get(at + 2), plane.get(at + 3));
	}

	@Redirect(method = "genTextures", at = @At(value = "INVOKE", target = "Lorg/lwjgl/opengl/GL11;glGenTextures()I"))
	private static int astatine$genTextures() {
		return ++astatine$names;
	}

	@Redirect(method = "deleteTextures", at = @At(value = "INVOKE", target = "Lorg/lwjgl/opengl/GL11;glDeleteTextures(I)V"))
	private static void astatine$deleteTextures(int name) {
	}

	@Redirect(method = "bindTexture", at = @At(value = "INVOKE", target = "Lorg/lwjgl/opengl/GL11;glBindTexture(II)V"))
	private static void astatine$bindTexture(int target, int name) {
		if (name != TextureBinding.OBJECT) {
			GL.bindTexture(0L);
		}
	}

	@Redirect(method = "shadeModel", at = @At(value = "INVOKE", target = "Lorg/lwjgl/opengl/GL11;glShadeModel(I)V"))
	private static void astatine$shadeModel(int model) {
		GL.shadeModel(model);
	}

	@Redirect(method = "viewport", at = @At(value = "INVOKE", target = "Lorg/lwjgl/opengl/GL11;glViewport(IIII)V"))
	private static void astatine$viewport(int x, int y, int width, int height) {
		GL.viewport(x, y, width, height);
	}

	@Redirect(method = "colorMask", at = @At(value = "INVOKE", target = "Lorg/lwjgl/opengl/GL11;glColorMask(ZZZZ)V"))
	private static void astatine$colorMask(boolean red, boolean green, boolean blue, boolean alpha) {
		GL.colorMask(red, green, blue, alpha);
	}

	@Redirect(method = "clearDepth", at = @At(value = "INVOKE", target = "Lorg/lwjgl/opengl/GL11;glClearDepth(D)V"))
	private static void astatine$clearDepth(double depth) {
		GL.clearDepth((float) depth);
	}

	@Redirect(method = "clearColor(FFFF)V", at = @At(value = "INVOKE", target = "Lorg/lwjgl/opengl/GL11;glClearColor(FFFF)V"))
	private static void astatine$clearColor(float red, float green, float blue, float alpha) {
		GL.clearColor(red, green, blue, alpha);
	}

	@Redirect(method = "clear", at = @At(value = "INVOKE", target = "Lorg/lwjgl/opengl/GL11;glClear(I)V"))
	private static void astatine$clear(int mask) {
		GL.clear(mask);
	}

	@Redirect(method = "matrixMode", at = @At(value = "INVOKE", target = "Lorg/lwjgl/opengl/GL11;glMatrixMode(I)V"))
	private static void astatine$matrixMode(int mode) {
		GL.matrixMode(mode);
	}

	@Redirect(method = "loadIdentity", at = @At(value = "INVOKE", target = "Lorg/lwjgl/opengl/GL11;glLoadIdentity()V"))
	private static void astatine$loadIdentity() {
		GL.loadIdentity();
	}

	@Redirect(method = "pushMatrix", at = @At(value = "INVOKE", target = "Lorg/lwjgl/opengl/GL11;glPushMatrix()V"))
	private static void astatine$pushMatrix() {
		GL.pushMatrix();
	}

	@Redirect(method = "popMatrix", at = @At(value = "INVOKE", target = "Lorg/lwjgl/opengl/GL11;glPopMatrix()V"))
	private static void astatine$popMatrix() {
		GL.popMatrix();
	}

	@Redirect(method = "getFloat", at = @At(value = "INVOKE", target = "Lorg/lwjgl/opengl/GL11;glGetFloat(ILjava/nio/FloatBuffer;)V"))
	private static void astatine$getFloat(int name, FloatBuffer buffer) {
		GLState.readMatrix(name, Memory.INSTANCE.address(buffer) + buffer.position() * 4L);
	}

	@Redirect(method = "ortho", at = @At(value = "INVOKE", target = "Lorg/lwjgl/opengl/GL11;glOrtho(DDDDDD)V"))
	private static void astatine$ortho(double left, double right, double bottom, double top, double near, double far) {
		GL.ortho((float) left, (float) right, (float) bottom, (float) top, (float) near, (float) far);
	}

	@Redirect(method = "rotatef", at = @At(value = "INVOKE", target = "Lorg/lwjgl/opengl/GL11;glRotatef(FFFF)V"))
	private static void astatine$rotate(float angle, float x, float y, float z) {
		GL.rotate(angle, x, y, z);
	}

	@Redirect(method = "scalef", at = @At(value = "INVOKE", target = "Lorg/lwjgl/opengl/GL11;glScalef(FFF)V"))
	private static void astatine$scale(float x, float y, float z) {
		GL.scale(x, y, z);
	}

	@Redirect(method = "scaled", at = @At(value = "INVOKE", target = "Lorg/lwjgl/opengl/GL11;glScaled(DDD)V"))
	private static void astatine$scale(double x, double y, double z) {
		GL.scale((float) x, (float) y, (float) z);
	}

	@Redirect(method = "translatef", at = @At(value = "INVOKE", target = "Lorg/lwjgl/opengl/GL11;glTranslatef(FFF)V"))
	private static void astatine$translate(float x, float y, float z) {
		GL.translate(x, y, z);
	}

	@Redirect(method = "translated", at = @At(value = "INVOKE", target = "Lorg/lwjgl/opengl/GL11;glTranslated(DDD)V"))
	private static void astatine$translate(double x, double y, double z) {
		GL.translate((float) x, (float) y, (float) z);
	}

	@Redirect(method = "multMatrix", at = @At(value = "INVOKE", target = "Lorg/lwjgl/opengl/GL11;glMultMatrix(Ljava/nio/FloatBuffer;)V"))
	private static void astatine$multMatrix(FloatBuffer matrix) {
		GL.multMatrix(Memory.INSTANCE.address(matrix) + matrix.position() * 4L);
	}

	@Redirect(method = "color4f", at = @At(value = "INVOKE", target = "Lorg/lwjgl/opengl/GL11;glColor4f(FFFF)V"))
	private static void astatine$color(float red, float green, float blue, float alpha) {
		GL.color(red, green, blue, alpha);
	}

	@Redirect(method = "callList", at = @At(value = "INVOKE", target = "Lorg/lwjgl/opengl/GL11;glCallList(I)V"))
	private static void astatine$callList(int list) {
	}

	@Redirect(method = "pushLightingAttributes", at = @At(value = "INVOKE", target = "Lorg/lwjgl/opengl/GL11;glPushAttrib(I)V"))
	private static void astatine$pushAttributes(int mask) {
		GL.pushAttributes();
	}

	@Redirect(method = "popAttributes", at = @At(value = "INVOKE", target = "Lorg/lwjgl/opengl/GL11;glPopAttrib()V"))
	private static void astatine$popAttributes() {
		GL.popAttributes();
	}
}
