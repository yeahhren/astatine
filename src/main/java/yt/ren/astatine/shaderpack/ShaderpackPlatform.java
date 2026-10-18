package yt.ren.astatine.shaderpack;

import com.mojang.renderpearl.api.textures.GpuTextureView;
import net.irisshaders.iris.pipeline.WorldRenderingPhase;
import net.minecraft.client.Minecraft;
import net.minecraft.client.render.Culler;
import net.minecraft.client.render.block.BlockLayer;
import net.minecraft.entity.Entity;
import net.minecraft.entity.living.LivingEntity;
import org.joml.Matrix4f;
import yt.ren.astatine.backend.MinecraftTextures;
import yt.ren.astatine.iris.IrisPlatform;
import yt.ren.astatine.mixin.shaderpack.GameRendererAccessor;

import java.util.OptionalInt;

public final class ShaderpackPlatform implements IrisPlatform {
	@Override
	public boolean isActive() {
		return Shaderpacks.isActive();
	}

	@Override
	public boolean isRenderingShadows() {
		return Shaderpacks.isRenderingShadows();
	}

	@Override
	public float sunPathRotation() {
		return Shaderpacks.sunPathRotation();
	}

	@Override
	public OptionalInt forcedShadowRenderDistanceChunks() {
		return Shaderpacks.forcedShadowRenderDistanceChunks();
	}

	@Override
	public void onReloaded() {
		Shaderpacks.onReloaded();
	}

	@Override
	public WorldRenderingPhase currentPhase() {
		return Shaderpacks.currentPhase();
	}

	@Override
	public long[] pbrCompanions(long texture) {
		return PbrTextures.companions(texture);
	}

	@Override
	public long defaultNormals() {
		return PbrTextures.defaultNormals();
	}

	@Override
	public long defaultSpecular() {
		return PbrTextures.defaultSpecular();
	}

	@Override
	public void beginShadowTerrain(Matrix4f modelView, Matrix4f projection, Culler frustum, double x, double y, double z) {
		ShadowTerrain.begin(modelView, projection, frustum, x, y, z);
	}

	@Override
	public int drawShadowTerrain(BlockLayer layer) {
		return ShadowTerrain.draw(layer);
	}

	@Override
	public void endShadowTerrain() {
		ShadowTerrain.end();
	}

	@Override
	public int entityId(Entity entity) {
		return DrawIds.entityId(entity);
	}

	@Override
	public float nightVisionScale(LivingEntity entity, float tickDelta) {
		return ((GameRendererAccessor) Minecraft.getInstance().gameRenderer).astatine$getNightVisionScale(entity, tickDelta);
	}

	@Override
	public boolean hasThickFog() {
		return ((GameRendererAccessor) Minecraft.getInstance().gameRenderer).astatine$hasThickFog();
	}

	@Override
	public GpuTextureView lightmap() {
		return MinecraftTextures.view(((GameRendererAccessor) Minecraft.getInstance().gameRenderer).astatine$lightmapTexture());
	}
}
