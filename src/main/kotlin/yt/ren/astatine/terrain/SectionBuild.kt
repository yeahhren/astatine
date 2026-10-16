package yt.ren.astatine.terrain

import net.minecraft.block.Block
import net.minecraft.block.LiquidBlock
import net.minecraft.block.entity.BlockEntity
import net.minecraft.client.render.block.entity.BlockEntityRenderDispatcher
import net.minecraft.util.math.BlockPos
import net.minecraft.util.math.Direction
import net.minecraft.world.gen.WorldGeneratorType
import yt.ren.astatine.Native
import yt.ren.astatine.memory.Memory

class SectionBuild(
    val x: Int,
    val y: Int,
    val z: Int,
    val version: Int,
    val generation: Int,
    private val slice: WorldSlice,
) {
    val blockEntities = ArrayList<BlockEntity>()
    val globalBlockEntities = ArrayList<BlockEntity>()

    fun run() {
        val snapshot = slice.snapshot
        try {
            resolve(snapshot)
        } catch (throwable: Throwable) {
            Native.terrainRelease(snapshot)
            throw throwable
        }
        Native.terrainBuild(snapshot)
    }

    private fun resolve(snapshot: Long) {
        val flags = TerrainTables.javaFlags
        val models = TerrainTables.stateModels
        val tableModels = TerrainTables.tableModels
        val pos = BlockPos.Mutable()
        val neighbor = BlockPos.Mutable()
        val directions = Direction.entries.toTypedArray()
        val debug = slice.getGeneratorType() == WorldGeneratorType.DEBUG_ALL_BLOCK_STATES
        val originX = x shl 4
        val originY = y shl 4
        val originZ = z shl 4
        val states = snapshot + Snapshots.STATES + 13 * 8192L
        val present = Memory.getInt(snapshot + Snapshots.PRESENT_MASK) and (1 shl 13) != 0
        if (!present) return
        for (index in 0 until 4096) {
            val raw = Memory.getChar(states + index * 2L).code
            val java = flags[raw].toInt()
            if (java == 0) continue
            val state = Block.STATE_REGISTRY.get(raw) ?: continue
            val block = state.block
            pos.set(originX + (index and 15), originY + (index shr 8), originZ + ((index shr 4) and 15))
            var overrides = 0

            if (java and TerrainTables.ENTITY != 0) {
                val blockEntity = slice.getBlockEntity(pos)
                if (blockEntity != null) {
                    val renderer = BlockEntityRenderDispatcher.INSTANCE.getRenderer<BlockEntity>(blockEntity)
                    if (renderer != null) {
                        blockEntities += blockEntity
                        if (renderer.shouldRenderOffScreen()) globalBlockEntities += blockEntity
                    }
                }
            }

            if (java and TerrainTables.LIQUID != 0) {
                val liquid = block as LiquidBlock
                liquid.updateShape(slice, pos)
                var faces = 0
                for (direction in directions) {
                    neighbor.set(pos.x + direction.offsetX, pos.y + direction.offsetY, pos.z + direction.offsetZ)
                    if (liquid.shouldRenderFace(slice, neighbor, direction)) faces = faces or (1 shl direction.id)
                }
                neighbor.set(pos.x, pos.y + 1, pos.z)
                if (faces and (1 shl 1) != 0 && liquid.isNeighboringGap(slice, neighbor)) faces = faces or (1 shl 6)
                Memory.putByte(snapshot + Snapshots.FACES + index, faces.toByte())
                Memory.putFloat(snapshot + Snapshots.FLOWS + index * 4L, LiquidBlock.getFlowAngle(slice, pos, block.material).toFloat())
                Memory.putInt(snapshot + Snapshots.TINTS + index * 4L, liquid.getColorTint(slice, pos))
                overrides = Snapshots.OVERRIDE_FACES or Snapshots.OVERRIDE_FLOW or Snapshots.OVERRIDE_TINT
            } else {
                if (java and TerrainTables.VIRTUAL != 0) {
                    var actual = state
                    if (!debug) {
                        try {
                            actual = block.resolveVirtualProperties(state, slice, pos)
                        } catch (_: Exception) {
                        }
                    }
                    Memory.putInt(snapshot + Snapshots.MODELS + index * 4L, models[actual] ?: tableModels[raw])
                    overrides = 0 or Snapshots.OVERRIDE_MODEL
                }
                if (java and TerrainTables.FACES != 0) {
                    block.updateShape(slice, pos)
                    var faces = 0
                    for (direction in directions) {
                        neighbor.set(pos.x + direction.offsetX, pos.y + direction.offsetY, pos.z + direction.offsetZ)
                        if (block.shouldRenderFace(slice, neighbor, direction)) faces = faces or (1 shl direction.id)
                    }
                    Memory.putByte(snapshot + Snapshots.FACES + index, faces.toByte())
                    overrides = overrides or Snapshots.OVERRIDE_FACES
                }
                if (java and TerrainTables.TINT != 0) {
                    Memory.putInt(snapshot + Snapshots.TINTS + index * 4L, block.getColorTint(slice, pos, 0))
                    overrides = overrides or Snapshots.OVERRIDE_TINT
                }
            }
            if (overrides != 0) Memory.putByte(snapshot + Snapshots.OVERRIDES + index, overrides.toByte())
        }
    }
}
