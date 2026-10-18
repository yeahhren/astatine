package net.irisshaders.iris.compat.dh;

import net.irisshaders.iris.gl.sampler.GlSampler;
import net.irisshaders.iris.gl.sampler.SamplerHolder;
import net.irisshaders.iris.pipeline.WorldRenderingPipeline;
import net.irisshaders.iris.uniforms.CapturedRenderingState;
import net.minecraft.client.Minecraft;
import yt.ren.astatine.vulkan.MainTarget;
import org.joml.Matrix4f;

import java.util.function.Supplier;

public class DHCompat {
	private static boolean dhPresent;
	private final Object compat;

	public DHCompat(WorldRenderingPipeline pipeline, boolean renderDHShadow) {
		this.compat = null;
	}

	public static void run() {
		dhPresent = false;
	}

	public static Matrix4f getProjection() {
		return dhPresent ? getProjection(getNearPlane(), getFarPlane()) : new Matrix4f(
			CapturedRenderingState.INSTANCE.getGbufferProjection());
	}

	public static Matrix4f getProjection(float nearPlane, float farPlane) {
		var projection = CapturedRenderingState.INSTANCE.getGbufferProjection();
		return new Matrix4f().setPerspective(projection.perspectiveFov(), projection.m11() / projection.m00(), nearPlane, farPlane);
	}

	public static boolean lastPackIncompatible() {
		return false;
	}

	public static float getFarPlane() {
		return 0.01f;
	}

	public static float getNearPlane() {
		return 0.01f;
	}

	public static int getRenderDistance() {
		return Minecraft.getInstance().options.viewDistance;
	}

	public static boolean checkFrame() {
		return hasRenderingEnabled();
	}

	public static boolean hasRenderingEnabled() {
		return false;
	}

	public static void addDepthSamplers(SamplerHolder samplers, Supplier<DHCompat> compat) {
		if (!dhPresent) {
			return;
		}

		samplers.addTextureSampler(depthBinding("dhDepthTex", compat, false), false, "dhDepthTex", "dhDepthTex0");
		samplers.addTextureSampler(depthBinding("dhDepthTex1", compat, true), false, "dhDepthTex1");
	}

	private static SamplerHolder.TextureBinding depthBinding(String name, Supplier<DHCompat> compat, boolean opaque) {
		return new SamplerHolder.TextureBinding(name, () -> MainTarget.get().getDepthTextureView(), () -> GlSampler.NEAREST);
	}

	public static void renderLods(boolean translucent) {
	}

	public void prepare() {
	}

	public void clearPipeline() {
	}

	public Object getInstance() {
		return this.compat;
	}
}
