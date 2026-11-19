package yt.ren.astatine.iris;

import com.mojang.renderpearl.api.textures.GpuTextureView;
import net.irisshaders.iris.pipeline.WorldRenderingPhase;
import net.minecraft.client.render.Culler;
import net.minecraft.client.render.block.BlockLayer;
import net.minecraft.entity.Entity;
import net.minecraft.entity.living.LivingEntity;
import org.joml.Matrix4f;

import java.util.OptionalInt;

public interface IrisPlatform {
	static IrisPlatform get() {
		var platform = Holder.platform;

		if (platform == null) {
			throw new IllegalStateException("the Iris platform has not been registered");
		}

		return platform;
	}

	static void set(IrisPlatform platform) {
		Holder.platform = platform;
	}

	boolean isActive();

	boolean isRenderingShadows();

	float sunPathRotation();

	OptionalInt forcedShadowRenderDistanceChunks();

	void onReloaded();

	WorldRenderingPhase currentPhase();

	long[] pbrCompanions(long texture);

	long defaultNormals();

	long defaultSpecular();

	void beginShadowTerrain(Matrix4f modelView, Matrix4f projection, Culler frustum, double x, double y, double z);

	int drawShadowTerrain(BlockLayer layer);

	void endShadowTerrain();

	int entityId(Entity entity);

	float nightVisionScale(LivingEntity entity, float tickDelta);

	boolean hasThickFog();

	GpuTextureView lightmap();

	final class Holder {
		private static volatile IrisPlatform platform;

		private Holder() {
		}
	}
}
