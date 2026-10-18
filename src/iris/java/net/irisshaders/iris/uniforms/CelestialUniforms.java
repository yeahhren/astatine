package net.irisshaders.iris.uniforms;

import net.irisshaders.iris.Iris;
import net.irisshaders.iris.gl.uniform.UniformHolder;
import net.irisshaders.iris.shaderpack.DimensionId;
import net.minecraft.client.Minecraft;
import net.minecraft.client.world.ClientWorld;
import org.joml.Matrix4f;
import org.joml.Vector4f;

import java.util.Objects;

import static net.irisshaders.iris.gl.uniform.UniformUpdateFrequency.PER_FRAME;

/**
 * @see <a href="https://github.com/IrisShaders/ShaderDoc/blob/master/uniforms.md#celestial-bodies">Uniforms: Celestial bodies</a>
 */
public final class CelestialUniforms {
	private static final Vector4f ZERO = new Vector4f();
	private final float sunPathRotation;

	public CelestialUniforms(float sunPathRotation) {
		this.sunPathRotation = sunPathRotation;
	}

	public static float getSunAngle(boolean sun) {
		float currentAngle = getCelestialAngle(sun);

		float c = currentAngle + 90.0f;

		if (c < 0) {
			c += 360;
		} else if (c > 360) {
			c -= 360;
		}

		return c;
	}

	public static float getCelestialAngle(boolean sun) {
		ClientWorld world = Minecraft.getInstance().world;
		float skyAngle = world == null ? 0.0f : world.getTimeOfDay(CapturedRenderingState.INSTANCE.getTickDelta()) * 360.0f;

		return sun ? skyAngle : skyAngle + 180.0f;
	}

	public static float getShadowAngle() {
		float shadowAngle = getSunAngle(isDay());

		return shadowAngle / 360.0f;
	}

	private static Vector4f getUpPosition() {
		Vector4f upVector = new Vector4f(0.0F, 100.0F, 0.0F, 0.0F);

		// Get the current GBuffer model view matrix, since that is the basis of the celestial model view matrix
		Matrix4f preCelestial = new Matrix4f(CapturedRenderingState.INSTANCE.getGbufferModelView());

		// Apply the fixed -90.0F degrees rotation to mirror the same transformation in renderSky.
		// But, notably, skip the rotation by the skyAngle.
		preCelestial.rotateY((float) Math.toRadians(-90.0F));

		// Use this matrix to transform the vector.
		upVector = preCelestial.transform(upVector);

		return upVector;
	}

	public static boolean isDay() {
		// Use same function as `sunAngle` uniform, no longer use hardcoded time values.
		float sunAngle = CelestialUniforms.getSunAngle(true);
		return sunAngle < 180;
	}

	private static ClientWorld getWorld() {
		return Objects.requireNonNull(Minecraft.getInstance().world);
	}


	public void addCelestialUniforms(UniformHolder uniforms) {
		uniforms.uniform1f(PER_FRAME, "sunAngle", () -> CelestialUniforms.getSunAngle(true) / 360.0f)
			.uniformTruncated3f(PER_FRAME, "sunPosition", this::getSunPosition)
			.uniformTruncated3f(PER_FRAME, "moonPosition", this::getMoonPosition)
			.uniform1f(PER_FRAME, "shadowAngle", CelestialUniforms::getShadowAngle)
			.uniformTruncated3f(PER_FRAME, "shadowLightPosition", this::getShadowLightPosition)
			.uniformTruncated3f(PER_FRAME, "endFlashPosition", () -> {
				if (Iris.getCurrentDimension() == DimensionId.END) {
					return getEndFlashPosition();
				} else {
					return ZERO;
				}
			}).uniformTruncated3f(PER_FRAME, "upPosition", CelestialUniforms::getUpPosition);
	}

	private Vector4f getSunPosition() {
		return getCelestialPosition(true, 100.0F);
	}

	private Vector4f getMoonPosition() {
		return getCelestialPosition(false, -100.0F);
	}

	private Vector4f getEndFlashPosition() {
		float h = 0.0F; // yaw around Y
		float g = 0.0F; // this feels silly

		Vector4f pos = new Vector4f(0f, 100f, 0f, 0f);

		Matrix4f m = new Matrix4f(CapturedRenderingState.INSTANCE.getGbufferModelView());
		m.rotateY((float) Math.toRadians(180.0F - h));
		m.rotateX((float) Math.toRadians(-90.0F - g));
		return m.transform(pos);
	}

	private Vector4f getEndFlashPositionInWorldSpace() {
		float h = 0.0F;
		float g = 0.0F;

		Vector4f pos = new Vector4f(0f, 100f, 0f, 0f);
		Matrix4f m = new Matrix4f();
		m.identity();
		m.rotateY((float) Math.toRadians(180.0F - h));
		m.rotateX((float) Math.toRadians(-90.0F - g));
		return m.transform(pos);
	}

	public Vector4f getShadowLightPosition() {
		if (Iris.getCurrentDimension() == DimensionId.END && supportsEndFlash()) {
			return getEndFlashPosition();
		}
		return isDay() ? getSunPosition() : getMoonPosition();
	}

	public Vector4f getShadowLightPositionInWorldSpace() {
		if (Iris.getCurrentDimension() == DimensionId.END && supportsEndFlash()) {
			return getEndFlashPositionInWorldSpace();
		}
		return isDay() ? getCelestialPositionInWorldSpace(true, 100.0F) : getCelestialPositionInWorldSpace(false, -100.0F);
	}

	private static boolean supportsEndFlash() {
		return false;
	}

	private Vector4f getCelestialPositionInWorldSpace(boolean sun, float y) {
		Vector4f position = new Vector4f(0.0F, 100.0f, 0.0F, 0.0F);

		// TODO: Deduplicate / remove this function.
		Matrix4f celestial = new Matrix4f();
		celestial.identity();

		// This is the same transformation applied by renderSky, however, it's been moved to here.
		// This is because we need the result of it before it's actually performed in vanilla.
		celestial.rotateY((float) Math.toRadians(-90.0F));
		celestial.rotateZ((float) Math.toRadians(sunPathRotation));
		float currentAngle = getCelestialAngle(sun);

		celestial.rotateX((float) Math.toRadians(currentAngle));
		celestial.transform(position);

		return position;
	}

	private Vector4f getCelestialPosition(boolean sun, float y) {
		Vector4f position = new Vector4f(0.0F, 100.0f, 0.0F, 0.0F);

		Matrix4f celestial = new Matrix4f(CapturedRenderingState.INSTANCE.getGbufferModelView());

		// This is the same transformation applied by renderSky, however, it's been moved to here.
		// This is because we need the result of it before it's actually performed in vanilla.
		celestial.rotateY((float) Math.toRadians(-90.0F));
		celestial.rotateZ((float) Math.toRadians(sunPathRotation));
		float currentAngle = getCelestialAngle(sun);

		celestial.rotateX((float) Math.toRadians(currentAngle));
		position = celestial.transform(position);

		return position;
	}
}
