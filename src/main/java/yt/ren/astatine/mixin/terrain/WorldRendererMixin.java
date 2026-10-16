package yt.ren.astatine.mixin.terrain;

import java.util.Arrays;
import java.util.List;
import java.util.Set;
import net.minecraft.block.Blocks;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.client.render.Culler;
import net.minecraft.client.render.block.BlockLayer;
import net.minecraft.client.render.block.entity.BlockEntityRenderDispatcher;
import net.minecraft.client.render.entity.EntityRenderDispatcher;
import net.minecraft.client.render.platform.Lighting;
import net.minecraft.client.render.world.WorldRenderer;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.entity.Entity;
import net.minecraft.entity.living.LivingEntity;
import net.minecraft.entity.projectile.WitherSkullEntity;
import net.minecraft.util.TypeInstanceMultiMap;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.chunk.WorldChunk;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import yt.ren.astatine.Native;
import yt.ren.astatine.memory.Memory;
import yt.ren.astatine.terrain.TerrainRenderer;

@Mixin(WorldRenderer.class)
public abstract class WorldRendererMixin {
	@Shadow
	@Final
	private Minecraft minecraft;

	@Shadow
	private ClientWorld world;

	@Shadow
	@Final
	private Set<BlockEntity> globalBlockEntities;

	@Shadow
	private int lastViewDistance;

	@Shadow
	private int entityRenderCooldown;

	@Shadow
	private boolean viewChanged;

	@Shadow
	@Final
	private EntityRenderDispatcher entityRenderDispatcher;

	@Shadow
	private int renderedEntityCount;

	@Unique
	private WorldChunk[] astatine$columns;

	/**
	 * @author Ren
	 * @reason Astatine terrain
	 */
	@Overwrite
	public void reload() {
		if (this.world == null) {
			return;
		}
		this.viewChanged = true;
		Blocks.LEAVES.setCulling(this.minecraft.options.fancyGraphics);
		Blocks.LEAVES2.setCulling(this.minecraft.options.fancyGraphics);
		this.lastViewDistance = this.minecraft.options.viewDistance;
		synchronized (this.globalBlockEntities) {
			this.globalBlockEntities.clear();
		}
		TerrainRenderer.reload((WorldRenderer) (Object) this, this.world, this.lastViewDistance);
		this.entityRenderCooldown = 2;
	}

	@Inject(method = "setWorld", at = @At("HEAD"))
	private void astatine$forget(ClientWorld world, CallbackInfo callback) {
		if (this.world != null && world != this.world) {
			TerrainRenderer.clear();
		}
	}

	/**
	 * @author Ren
	 * @reason Astatine terrain
	 */
	@Overwrite
	public void resetDirtyChunks() {
	}

	/**
	 * @author Ren
	 * @reason Astatine terrain
	 */
	@Overwrite
	public void setupRender(Entity camera, double tickDelta, Culler culler, int frame, boolean loadChunks) {
		if (this.minecraft.options.viewDistance != this.lastViewDistance) {
			this.reload();
		}
		double x = camera.prevX + (camera.x - camera.prevX) * tickDelta;
		double y = camera.prevY + (camera.y - camera.prevY) * tickDelta;
		double z = camera.prevZ + (camera.z - camera.prevZ) * tickDelta;
		TerrainRenderer.setup(camera, x, y, z, tickDelta, frame, loadChunks);
	}

	/**
	 * @author Ren
	 * @reason Astatine terrain
	 */
	@Overwrite
	public int render(BlockLayer layer, double tickDelta, int anaglyphRenderPass, Entity camera) {
		Lighting.turnOff();
		if (layer == BlockLayer.TRANSLUCENT) {
			TerrainRenderer.sortTranslucent(camera);
		}
		this.minecraft.gameRenderer.enableLightMap();
		int drawn = Native.terrainDraw(layer.ordinal());
		this.minecraft.gameRenderer.disableLightMap();
		return drawn;
	}

	/**
	 * @author Ren
	 * @reason Astatine terrain
	 */
	@Overwrite
	public void compileChunksUntil(long time) {
		TerrainRenderer.accept();
	}

	/**
	 * @author Ren
	 * @reason Astatine terrain
	 */
	@Overwrite
	public String getChunkDebugInfo() {
		return TerrainRenderer.debug();
	}

	/**
	 * @author Ren
	 * @reason Astatine terrain
	 */
	@Overwrite
	private void markDirty(int minX, int minY, int minZ, int maxX, int maxY, int maxZ) {
		Native.terrainMarkDirty(minX, minY, minZ, maxX, maxY, maxZ, false);
	}

	/**
	 * @author Ren
	 * @reason Astatine terrain
	 */
	@Overwrite
	public void notifyBlockChanged(BlockPos pos) {
		int x = pos.getX();
		int y = pos.getY();
		int z = pos.getZ();
		Native.terrainMarkDirty(x - 1, y - 1, z - 1, x + 1, y + 1, z + 1, true);
		TerrainRenderer.markImportant(x, y, z);
	}

	@Inject(
		method = "renderEntities",
		at = @At(value = "INVOKE_STRING", target = "Lnet/minecraft/util/profiler/Profiler;swap(Ljava/lang/String;)V", args = "ldc=blockentities")
	)
	private void astatine$renderEntities(Entity camera, Culler culler, float tickDelta, CallbackInfo callback) {
		double d = camera.lastX + (camera.x - camera.lastX) * tickDelta;
		double e = camera.lastY + (camera.y - camera.lastY) * tickDelta;
		double f = camera.lastZ + (camera.z - camera.lastZ) * tickDelta;
		Entity view = this.minecraft.getCamera();
		boolean sleeping = view instanceof LivingEntity && ((LivingEntity) view).isSleeping();
		int count = TerrainRenderer.visibleCount();
		long address = TerrainRenderer.visibleAddress();
		int radius = this.lastViewDistance + 1;
		int span = radius * 2 + 1;
		if (this.astatine$columns == null || this.astatine$columns.length != span * span) {
			this.astatine$columns = new WorldChunk[span * span];
		} else {
			Arrays.fill(this.astatine$columns, null);
		}
		WorldChunk[] columns = this.astatine$columns;
		int originX = ((int) Math.floor(d) >> 4) - radius;
		int originZ = ((int) Math.floor(f) >> 4) - radius;
		for (int i = 0; i < count; i++) {
			long at = address + i * 12L;
			int sectionX = Memory.INSTANCE.getInt(at);
			int sectionY = Memory.INSTANCE.getInt(at + 4);
			int sectionZ = Memory.INSTANCE.getInt(at + 8);
			int columnX = sectionX - originX;
			int columnZ = sectionZ - originZ;
			WorldChunk chunk;
			if (columnX >= 0 && columnZ >= 0 && columnX < span && columnZ < span) {
				int column = columnZ * span + columnX;
				chunk = columns[column];
				if (chunk == null) {
					chunk = this.world.getChunkAt(sectionX, sectionZ);
					columns[column] = chunk;
				}
			} else {
				chunk = this.world.getChunkAt(sectionX, sectionZ);
			}
			TypeInstanceMultiMap<Entity> entities = chunk.getEntities()[sectionY];
			if (entities.isEmpty()) {
				continue;
			}
			for (Entity entity : entities) {
				boolean visible = this.entityRenderDispatcher.shouldRender(entity, culler, d, e, f) || entity.rider == this.minecraft.player;
				if (visible) {
					if (entity == view && this.minecraft.options.perspective == 0 && !sleeping
						|| entity.y >= 0.0 && entity.y < 256.0 && !this.world.isChunkLoaded(new BlockPos(entity))) {
						continue;
					}
					this.renderedEntityCount++;
					this.entityRenderDispatcher.render(entity, tickDelta);
				}
				if (!visible && entity instanceof WitherSkullEntity) {
					this.minecraft.getEntityRenderDispatcher().renderNameTag(entity, tickDelta);
				}
			}
		}
	}

	@Inject(
		method = "renderEntities",
		at = @At(value = "INVOKE", target = "Lnet/minecraft/client/render/platform/Lighting;turnOn()V", ordinal = 1, shift = At.Shift.AFTER)
	)
	private void astatine$renderBlockEntities(Entity camera, Culler culler, float tickDelta, CallbackInfo callback) {
		int count = TerrainRenderer.visibleCount();
		long address = TerrainRenderer.visibleAddress();
		for (int i = 0; i < count; i++) {
			long at = address + i * 12L;
			List<BlockEntity> blockEntities = TerrainRenderer.blockEntities(Memory.INSTANCE.getInt(at), Memory.INSTANCE.getInt(at + 4), Memory.INSTANCE.getInt(at + 8));
			if (blockEntities == null) {
				continue;
			}
			for (BlockEntity blockEntity : blockEntities) {
				BlockEntityRenderDispatcher.INSTANCE.render(blockEntity, tickDelta, -1);
			}
		}
	}
}
