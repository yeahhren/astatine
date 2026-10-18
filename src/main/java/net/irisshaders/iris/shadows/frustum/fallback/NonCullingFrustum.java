package net.irisshaders.iris.shadows.frustum.fallback;

import net.minecraft.client.render.Culler;
import net.minecraft.util.math.Box;
import org.joml.FrustumIntersection;
import org.joml.Vector3d;

public class NonCullingFrustum implements Culler {
	private final Vector3d position = new Vector3d();

	public NonCullingFrustum() {
	}

	// For Immersive Portals
	// NB: The shadow culling in Immersive Portals must be disabled, because when Advanced Shadow Frustum Culling
	//     is not active, we are at a point where we can make no assumptions how the shader pack uses the shadow
	//     pass beyond what it already tells us. So we cannot use any extra fancy culling methods.
	public boolean canDetermineInvisible(double minX, double minY, double minZ, double maxX, double maxY, double maxZ) {
		return false;
	}

	@Override
	public boolean isVisible(Box box) {
		return true;
	}

	@Override
	public void prepare(double d, double e, double f) {
		this.position.set(d, e, f);
	}

	public boolean testAab(float v, float v1, float v2, float v3, float v4, float v5) {
		return true;
	}

	public int intersectAab(float v, float v1, float v2, float v3, float v4, float v5) {
		return FrustumIntersection.INSIDE;
	}

	public boolean testSection(float v, float v1, float v2) {
		return true;
	}

	public boolean testSectionExpanded(float v, float v1, float v2, float v3) {
		return true;
	}
}
