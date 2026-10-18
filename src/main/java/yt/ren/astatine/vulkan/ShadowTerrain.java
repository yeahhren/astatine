package yt.ren.astatine.vulkan;

import net.irisshaders.iris.shadows.frustum.CullEverythingFrustum;
import net.irisshaders.iris.shadows.frustum.advanced.AdvancedShadowCullingFrustum;
import net.irisshaders.iris.shadows.frustum.advanced.SafeZoneCullingFrustum;
import net.irisshaders.iris.shadows.frustum.fallback.BoxCullingFrustum;
import net.minecraft.client.render.Culler;
import net.minecraft.client.render.block.BlockLayer;
import org.joml.Matrix4f;
import yt.ren.astatine.Native;
import yt.ren.astatine.memory.Memory;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

public final class ShadowTerrain {
	private static final ByteBuffer VIEW = ByteBuffer.allocateDirect(32 * 4 + 16 + 13 * 16).order(ByteOrder.nativeOrder());

	private ShadowTerrain() {
	}

	public static void begin(Matrix4f modelView, Matrix4f projection, Culler frustum, double x, double y, double z) {
		modelView.get(0, VIEW);
		projection.get(16 * 4, VIEW);
		int cullEverything = 0;
		float distance = -1.0f;
		float safeDistance = -1.0f;
		float[][] planes = null;
		int planeCount = 0;

		if (frustum instanceof CullEverythingFrustum) {
			cullEverything = 1;
		} else if (frustum instanceof BoxCullingFrustum box) {
			distance = (float) box.getBoxCuller().getMaxDistance();
		} else if (frustum instanceof SafeZoneCullingFrustum safeZone) {
			distance = safeZone.getDistanceCuller() == null ? -1.0f : (float) safeZone.getDistanceCuller().getMaxDistance();
			safeDistance = safeZone.getBoxCuller() == null ? -1.0f : (float) safeZone.getBoxCuller().getMaxDistance();
			planes = safeZone.getPlanes();
			planeCount = safeZone.getPlaneCount();
		} else if (frustum instanceof AdvancedShadowCullingFrustum advanced) {
			distance = advanced.getBoxCuller() == null ? -1.0f : (float) advanced.getBoxCuller().getMaxDistance();
			planes = advanced.getPlanes();
			planeCount = advanced.getPlaneCount();
		}

		VIEW.putInt(128, cullEverything);
		VIEW.putInt(132, planeCount);
		VIEW.putFloat(136, distance);
		VIEW.putFloat(140, safeDistance);

		for (int plane = 0; plane < 13; plane++) {
			for (int component = 0; component < 4; component++) {
				float value = planes != null && plane < planeCount ? planes[plane][component] : 0.0f;
				VIEW.putFloat(144 + (plane * 4 + component) * 4, value);
			}
		}

		Native.terrainShadowView(Memory.INSTANCE.address(VIEW), x, y, z);
	}

	public static int draw(BlockLayer layer) {
		return WorldDraws.terrain(layer);
	}

	public static void end() {
		Native.terrainShadowViewEnd();
	}
}
