package yt.ren.astatine.terrain

import net.minecraft.block.Block
import net.minecraft.block.Blocks
import net.minecraft.block.entity.BlockEntity
import net.minecraft.block.material.Material
import net.minecraft.block.state.BlockState
import net.minecraft.util.math.BlockPos
import net.minecraft.util.math.Direction
import net.minecraft.world.WorldView
import net.minecraft.world.biome.Biome
import net.minecraft.world.gen.WorldGeneratorType
import yt.ren.astatine.memory.Memory

class WorldSlice(
    val snapshot: Long,
    private val originX: Int,
    private val originY: Int,
    private val originZ: Int,
    private val biomes: Array<ByteArray?>,
    private val blockEntities: Map<BlockPos, BlockEntity>,
    private val worldGeneratorType: WorldGeneratorType,
    private val sky: Boolean,
) : WorldView {
    private val present = Memory.getInt(snapshot + Snapshots.PRESENT_MASK)
    private val skyMask = Memory.getInt(snapshot + Snapshots.SKY_MASK)
    private val loaded = Memory.getInt(snapshot + Snapshots.LOADED_MASK)

    fun stateId(x: Int, y: Int, z: Int): Int {
        if (y !in 0..<256) return 0
        val lx = x - originX + 16
        val ly = y - originY + 16
        val lz = z - originZ + 16
        if (lx < 0 || ly < 0 || lz < 0 || lx >= 48 || ly >= 48 || lz >= 48) return 0
        val section = (ly shr 4) * 9 + (lz shr 4) * 3 + (lx shr 4)
        if (present and (1 shl section) == 0) return 0
        val index = ((ly and 15) shl 8) or ((lz and 15) shl 4) or (lx and 15)
        return Memory.getChar(snapshot + Snapshots.STATES + (section * 4096L + index) * 2).code
    }

    override fun getBlockState(pos: BlockPos): BlockState =
        Block.STATE_REGISTRY.get(stateId(pos.x, pos.y, pos.z)) ?: AIR

    override fun getBlockEntity(pos: BlockPos): BlockEntity? = blockEntities[pos]

    override fun isAir(pos: BlockPos): Boolean = getBlockState(pos).block.material == Material.AIR

    override fun getBiome(pos: BlockPos): Biome {
        val column = columnIndex(pos.x, pos.z)
        val data = if (column >= 0) biomes[column] else null
        if (data == null) return Biome.PLAINS
        val id = data[((pos.z and 15) shl 4) or (pos.x and 15)].toInt() and 255
        if (id == 255) return Biome.PLAINS
        return Biome.byId(id) ?: Biome.PLAINS
    }

    override fun isEmpty(): Boolean = false

    override fun getDirectSignal(pos: BlockPos, dir: Direction): Int {
        val state = getBlockState(pos)
        return state.block.getDirectSignal(this, pos, state, dir)
    }

    override fun getGeneratorType(): WorldGeneratorType = worldGeneratorType

    override fun getLightColor(pos: BlockPos, blockLight: Int): Int {
        val skyLight = brightness(true, pos.x, pos.y, pos.z)
        val light = maxOf(brightness(false, pos.x, pos.y, pos.z), blockLight)
        return (skyLight shl 20) or (light shl 4)
    }

    private fun columnIndex(x: Int, z: Int): Int {
        val cx = (x - originX + 16) shr 4
        val cz = (z - originZ + 16) shr 4
        if (cx < 0 || cz < 0 || cx > 2 || cz > 2) return -1
        return cz * 3 + cx
    }

    private fun brightness(skyType: Boolean, x: Int, y: Int, z: Int): Int {
        if (skyType && !sky) return 0
        if (y !in 0..<256) return if (skyType) 15 else 0
        if (getBlockState(BlockPos(x, y, z)).block.usesNeighborLight()) {
            var brightest = 0
            for (direction in Direction.entries) {
                val light = rawLight(skyType, x + direction.offsetX, y + direction.offsetY, z + direction.offsetZ)
                if (light > brightest) brightest = light
                if (brightest >= 15) return brightest
            }
            return brightest
        }
        return rawLight(skyType, x, y, z)
    }

    private fun rawLight(skyType: Boolean, x: Int, y: Int, z: Int): Int {
        val fallback = if (skyType) 15 else 0
        if (y !in 0..<256) return fallback
        val column = columnIndex(x, z)
        if (column < 0 || loaded and (1 shl column) == 0) return fallback
        val ly = y - originY + 16
        if (ly !in 0..<48) return fallback
        val section = (ly shr 4) * 9 + column
        val index = ((ly and 15) shl 8) or ((z and 15) shl 4) or (x and 15)
        if (present and (1 shl section) == 0) {
            val height = Memory.getInt(snapshot + Snapshots.HEIGHT_MAPS + (column * 256L + ((z and 15) shl 4) + (x and 15)) * 4)
            return if (y >= height) fallback else 0
        }
        val base = if (skyType) {
            if (!sky || skyMask and (1 shl section) == 0) return 0
            Snapshots.SKY_LIGHT
        } else {
            Snapshots.BLOCK_LIGHT
        }
        val packed = Memory.getByte(snapshot + base + section * 2048L + (index shr 1)).toInt()
        return if (index and 1 != 0) (packed shr 4) and 15 else packed and 15
    }

    private companion object {
        val AIR: BlockState = Blocks.AIR.defaultState()
    }
}
