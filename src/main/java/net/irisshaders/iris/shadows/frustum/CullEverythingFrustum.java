package net.irisshaders.iris.shadows.frustum;

import net.minecraft.client.render.Culler;
import net.minecraft.util.math.Box;
import org.joml.FrustumIntersection;
import org.joml.Vector3d;

public class CullEverythingFrustum implements Culler {
	private final Vector3d position = new Vector3d();

	public CullEverythingFrustum() {
	}

	// For Immersive Portals
	// We return false here since isVisible is going to return false anyways.
	public boolean canDetermineInvisible(double minX, double minY, double minZ, double maxX, double maxY, double maxZ) {
		return false;
	}

	public boolean isVisible(Box box) {
		return false;
	}

	@Override
	public void prepare(double d, double e, double f) {
		this.position.set(d, e, f);
	}

	public boolean testAab(float v, float v1, float v2, float v3, float v4, float v5) {
		return false;
	}

	public int intersectAab(float v, float v1, float v2, float v3, float v4, float v5) {
		return FrustumIntersection.OUTSIDE;
	}

	public boolean testSection(float v, float v1, float v2) {
		return false;
	}

	public boolean testSectionExpanded(float v, float v1, float v2, float v3) {
		return false;
	}
}
