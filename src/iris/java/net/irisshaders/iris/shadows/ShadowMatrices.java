package net.irisshaders.iris.shadows;

import net.irisshaders.iris.pipeline.GraphicsBackend;
import net.irisshaders.iris.shaderpack.properties.PackDirectives;
import net.irisshaders.iris.shaderpack.properties.PackShadowDirectives;
import net.irisshaders.iris.uniforms.CameraUniforms;
import net.irisshaders.iris.uniforms.CelestialUniforms;
import net.minecraft.client.Minecraft;
import org.joml.Matrix4f;
import org.joml.Vector3d;

public class ShadowMatrices {
	public static final float NEAR = -100.05f;
	public static final float FAR = 156.0f;

	public static float shadowNearPlane(PackShadowDirectives directives) {
		return directives.getNearPlane() == -1.0f ? -Minecraft.getInstance().options.viewDistance * 16 : directives.getNearPlane();
	}

	public static float shadowFarPlane(PackShadowDirectives directives) {
		return directives.getFarPlane() == -1.0f ? Minecraft.getInstance().options.viewDistance * 16 : directives.getFarPlane();
	}

	public static Matrix4f createShadowModelView(PackDirectives directives) {
		PackShadowDirectives shadow = directives.getShadowDirectives();
		Vector3d camera = CameraUniforms.getUnshiftedCameraPosition();
		Matrix4f modelView = new Matrix4f();
		createModelViewMatrix(modelView, CelestialUniforms.getShadowAngle(), shadow.getIntervalSize(), directives.getSunPathRotation(),
			camera.x, camera.y, camera.z, shadowNearPlane(shadow), shadowFarPlane(shadow));
		return modelView;
	}

	public static Matrix4f createShadowProjection(PackDirectives directives) {
		PackShadowDirectives shadow = directives.getShadowDirectives();

		if (shadow.getFov() != null) {
			return createPerspectiveMatrix(shadow.getFov(), false);
		}

		return createOrthoMatrix(shadow.getDistance(), shadowNearPlane(shadow), shadowFarPlane(shadow), false);
	}

	public static Matrix4f createOrthoMatrix(float halfPlaneLength, float nearPlane, float farPlane, boolean zZeroToOne) {
		return new Matrix4f().setOrthoSymmetric(halfPlaneLength * 2, halfPlaneLength * 2, nearPlane, farPlane, zZeroToOne);
	}

	public static Matrix4f createOrthoMatrix(float halfPlaneLength, float nearPlane, float farPlane) {
		//System.out.println("making a matrix with " + nearPlane + " / " + farPlane + " * " + halfPlaneLength);
		return new Matrix4f().setOrthoSymmetric(halfPlaneLength * 2, halfPlaneLength * 2, nearPlane, farPlane,
			GraphicsBackend.isZZeroToOne());
	}

	public static Matrix4f createPerspectiveMatrix(float fov) {
		return createPerspectiveMatrix(fov, GraphicsBackend.isZZeroToOne());
	}

	public static Matrix4f createPerspectiveMatrix(float fov, boolean zZeroToOne) {
		float yScale = (float) (1.0f / Math.tan(Math.toRadians(fov) * 0.5f));
		return new Matrix4f(
			// column 1
			yScale, 0f, 0f, 0f,
			// column 2
			0f, yScale, 0f, 0f,
			// column 3
			0f, 0f, (zZeroToOne ? FAR : (FAR + NEAR)) / (NEAR - FAR), -1.0F,
			// column 4
			0f, 0f, (zZeroToOne ? FAR : (2.0F * FAR)) * NEAR / (NEAR - FAR), 1f);
	}

	public static void createBaselineModelViewMatrix(Matrix4f target, float shadowAngle, float sunPathRotation, float nearPlane,
		float farPlane) {
		target.identity();

		float skyAngle;

		if (shadowAngle < 0.25f) {
			skyAngle = shadowAngle + 0.75f;
		} else {
			skyAngle = shadowAngle - 0.25f;
		}

		target.rotateX((float) Math.toRadians(90.0F));
		target.rotateZ((float) Math.toRadians(skyAngle * -360.0f));
		target.rotateX((float) Math.toRadians(sunPathRotation));
	}

	public static void snapModelViewToGrid(Matrix4f target, float shadowIntervalSize, double cameraX, double cameraY, double cameraZ) {
		if (Math.abs(shadowIntervalSize) == 0.0F) {
			// Avoid a division by zero - semantically, this just means that the snapping does not take place,
			// if the shadow interval (size of each grid "cell") is zero.
			return;
		}

		// Calculate where we are within each grid "cell"
		// These values will be in the range of (-shadowIntervalSize, shadowIntervalSize)
		//
		// It looks like it's intended for these to be within the range [0, shadowIntervalSize), however since the
		// expression (-2.0f % 32.0f) returns -2.0f, negative inputs will result in negative outputs.
		float offsetX = (float) cameraX % shadowIntervalSize;
		float offsetY = (float) cameraY % shadowIntervalSize;
		float offsetZ = (float) cameraZ % shadowIntervalSize;

		// Halve the size of each grid cell in order to move to the center of it.
		float halfIntervalSize = shadowIntervalSize / 2.0f;

		// Shift by -halfIntervalSize
		//
		// It's clear that the intent of the algorithm was to place the values into the range:
		// [-shadowIntervalSize/2, shadowIntervalSize), however due to the previously-mentioned behavior with negatives,
		// it's possible that values will lie in the range (-3shadowIntervalSize/2, shadowIntervalSize/2).
		offsetX -= halfIntervalSize;
		offsetY -= halfIntervalSize;
		offsetZ -= halfIntervalSize;

		target.translate(offsetX, offsetY, offsetZ);
	}

	public static void createModelViewMatrix(Matrix4f target, float shadowAngle, float shadowIntervalSize, float sunPathRotation,
		double cameraX, double cameraY, double cameraZ, float nearPlane, float farPlane) {
		createBaselineModelViewMatrix(target, shadowAngle, sunPathRotation, nearPlane, farPlane);
		snapModelViewToGrid(target, shadowIntervalSize, cameraX, cameraY, cameraZ);
	}
}
