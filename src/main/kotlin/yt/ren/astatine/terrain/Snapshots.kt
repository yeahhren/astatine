package yt.ren.astatine.terrain

import net.minecraft.block.entity.BlockEntity
import net.minecraft.client.world.ClientWorld
import net.minecraft.util.math.BlockPos
import yt.ren.astatine.Native
import yt.ren.astatine.memory.Memory

object Snapshots {
    const val SECTION_X = 0L
    const val SECTION_Y = 4L
    const val SECTION_Z = 8L
    const val VERSION = 12L
    const val FLAGS = 16L
    const val SEQUENCE = 32L
    const val PRESENT_MASK = 20L
    const val SKY_MASK = 24L
    const val LOADED_MASK = 28L
    const val STATES = 64L
    const val BLOCK_LIGHT = 221248L
    const val SKY_LIGHT = 276544L
    const val HEIGHT_MAPS = 331840L
    const val OVERRIDES = 341056L
    const val FACES = 345152L
    const val MODELS = 349248L
    const val TINTS = 365632L
    const val FLOWS = 382016L

    const val OVERRIDE_MODEL = 1
    const val OVERRIDE_FACES = 2
    const val OVERRIDE_TINT = 4
    const val OVERRIDE_FLOW = 8

    private const val SKY = 1
    private const val URGENT = 2

    fun fill(snapshot: Long, world: ClientWorld, x: Int, y: Int, z: Int, version: Int, urgent: Boolean = false): WorldSlice {
        val sky = !world.dimension.hasNoSky()
        Memory.putInt(snapshot + SECTION_X, x)
        Memory.putInt(snapshot + SECTION_Y, y)
        Memory.putInt(snapshot + SECTION_Z, z)
        Memory.putInt(snapshot + VERSION, version)
        Memory.putInt(snapshot + FLAGS, (if (sky) SKY else 0) or (if (urgent) URGENT else 0))
        Memory.putLong(snapshot + SEQUENCE, if (urgent) Native.terrainDirtySequence() else 0L)
        Memory.fill(snapshot + OVERRIDES, 4096, 0)
        var present = 0
        var skyMask = 0
        var loaded = 0
        val biomes = arrayOfNulls<ByteArray>(9)
        val blockEntities = HashMap<BlockPos, BlockEntity>()
        val minX = (x - 1) shl 4
        val minY = (y - 1) shl 4
        val minZ = (z - 1) shl 4
        for (dz in -1..1) {
            for (dx in -1..1) {
                val column = (dz + 1) * 3 + dx + 1
                val chunk = world.getChunkAt(x + dx, z + dz)
                if (chunk.isEmpty) continue
                val sections = chunk.sections
                for (dy in -1..1) {
                    val sectionY = y + dy
                    if (sectionY !in 0..15) continue
                    val storage = sections[sectionY] ?: continue
                    val index = (dy + 1) * 9 + column
                    present = present or (1 shl index)
                    if (storage.skyLightStorage != null) skyMask = skyMask or (1 shl index)
                }
            }
        }
        val cached = Native.terrainSnapshotClones(snapshot, x, y, z, present)
        for (dz in -1..1) {
            for (dx in -1..1) {
                val column = (dz + 1) * 3 + dx + 1
                val chunk = world.getChunkAt(x + dx, z + dz)
                if (chunk.isEmpty) continue
                loaded = loaded or (1 shl column)
                Memory.copy(chunk.heightMap, 0, snapshot + HEIGHT_MAPS + column * 1024L, 256)
                biomes[column] = chunk.biomes.copyOf()
                val sections = chunk.sections
                for (dy in -1..1) {
                    val sectionY = y + dy
                    if (sectionY !in 0..15) continue
                    val index = (dy + 1) * 9 + column
                    if (cached and (1 shl index) != 0) continue
                    val storage = sections[sectionY] ?: continue
                    Memory.copy(storage.blockStates, snapshot + STATES + index * 8192L)
                    Memory.copy(storage.blockLightStorage.data, snapshot + BLOCK_LIGHT + index * 2048L)
                    val skyLight = storage.skyLightStorage
                    if (skyLight != null) Memory.copy(skyLight.data, snapshot + SKY_LIGHT + index * 2048L)
                }
                for ((pos, blockEntity) in chunk.blockEntities) {
                    val ly = pos.y - minY
                    if (ly !in 0..<48 || pos.x - minX !in 0..47 || pos.z - minZ !in 0..47) continue
                    blockEntities[pos] = blockEntity
                }
            }
        }
        Native.terrainSnapshotCommit(snapshot, present and cached.inv(), skyMask)
        Memory.putInt(snapshot + PRESENT_MASK, present)
        Memory.putInt(snapshot + SKY_MASK, skyMask)
        Memory.putInt(snapshot + LOADED_MASK, loaded)
        return WorldSlice(snapshot, x shl 4, y shl 4, z shl 4, biomes, blockEntities, world.generatorType, sky)
    }
}
