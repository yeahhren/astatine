package yt.ren.astatine.mixin.terrain;

import net.minecraft.client.world.chunk.ClientChunkCache;
import net.minecraft.util.Long2ObjectHashMap;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.world.chunk.WorldChunk;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ClientChunkCache.class)
public abstract class ClientChunkCacheMixin {
	@Shadow
	private WorldChunk empty;

	@Shadow
	private Long2ObjectHashMap<WorldChunk> chunksByPos;

	@Unique
	private WorldChunk astatine$last;

	/**
	 * @author Ren
	 * @reason most lookups hit the same chunk as the previous one
	 */
	@Overwrite
	public WorldChunk getChunk(int chunkX, int chunkZ) {
		WorldChunk last = this.astatine$last;
		if (last != null && last.chunkX == chunkX && last.chunkZ == chunkZ) {
			return last;
		}
		WorldChunk chunk = this.chunksByPos.get(ChunkPos.toLong(chunkX, chunkZ));
		if (chunk == null) {
			return this.empty;
		}
		this.astatine$last = chunk;
		return chunk;
	}

	@Inject(method = "unloadChunk", at = @At("TAIL"))
	private void astatine$forgetUnloaded(int chunkX, int chunkZ, CallbackInfo callback) {
		this.astatine$last = null;
	}

	@Inject(method = "loadChunk", at = @At("TAIL"))
	private void astatine$forgetReplaced(int chunkX, int chunkZ, CallbackInfoReturnable<WorldChunk> callback) {
		this.astatine$last = null;
	}
}
