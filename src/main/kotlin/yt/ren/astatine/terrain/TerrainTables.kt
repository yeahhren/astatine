package yt.ren.astatine.terrain

import net.minecraft.block.Block
import net.minecraft.block.LiquidBlock
import net.minecraft.block.SlabBlock
import net.minecraft.block.material.Material
import net.minecraft.block.state.BlockState
import net.minecraft.client.Minecraft
import net.minecraft.client.render.model.block.BakedModel
import net.minecraft.client.render.model.block.WeightedBakedModel
import net.minecraft.client.world.ClientWorld
import net.minecraft.util.math.Direction
import yt.ren.astatine.Native
import yt.ren.astatine.memory.Memory
import yt.ren.astatine.mixin.terrain.ModelEntryAccessor
import yt.ren.astatine.mixin.terrain.WeightedBakedModelAccessor
import yt.ren.astatine.mixin.terrain.WeightedEntryAccessor
import yt.ren.astatine.shaderpack.Shaderpacks
import java.util.IdentityHashMap

object TerrainTables {
    const val STATES = 65536

    const val VIRTUAL = 1
    const val FACES = 2
    const val TINT = 4
    const val ENTITY = 8
    const val LIQUID = 16

    private const val OPAQUE = 1
    private const val FULL_CUBE = 2
    private const val AO_BLOCKING = 4
    private const val TRANSLUCENT = 8
    private const val NEIGHBOR_LIGHT = 16
    private const val SLAB = 32
    private const val AIR = 64
    private const val SOLID_MATERIAL = 128

    private const val BLOCK_INFO_SIZE = 16L

    var javaFlags = ByteArray(STATES)
    var tableModels = IntArray(STATES)
    var stateModels = IdentityHashMap<BlockState, Int>()

    fun export(minecraft: Minecraft, world: ClientWorld) {
        val shaper = minecraft.blockRenderDispatcher.modelShaper
        val ids = IdentityHashMap<BakedModel, Int>()
        val models = ArrayList<BakedModel>()

        fun id(model: BakedModel): Int {
            ids[model]?.let { return it }
            val id = models.size
            ids[model] = id
            models += model
            if (model is WeightedBakedModel) {
                for (entry in (model as WeightedBakedModelAccessor).astatineEntries) id((entry as ModelEntryAccessor).astatineModel)
            }
            return id
        }

        val states = IdentityHashMap<BlockState, Int>()
        val tintedBlocks = IdentityHashMap<Block, Boolean>()
        for (block in Block.REGISTRY) {
            var tinted = false
            for (state in block.stateDefinition().all()) {
                val model = shaper.getModel(state) ?: continue
                states[state] = id(model)
                tinted = tinted || hasTint(model)
            }
            tintedBlocks[block] = tinted
        }

        val flags = ByteArray(STATES)
        val modelsByState = IntArray(STATES)
        val table = Memory.allocate(STATES * BLOCK_INFO_SIZE)
        try {
            Memory.fill(table, STATES * BLOCK_INFO_SIZE, 0)
            val blockFlags = IdentityHashMap<Block, Int>()
            for (raw in 0 until STATES) {
                val at = table + raw * BLOCK_INFO_SIZE
                val state = Block.STATE_REGISTRY.get(raw)
                if (state == null) {
                    Memory.putInt(at, AIR)
                    continue
                }
                val block = state.block
                val material = block.material
                val renderType = when (block.renderType) {
                    1 -> 1
                    2 -> 2
                    3 -> 3
                    else -> 0
                }
                var native = 0
                if (block.isSolidRender) native = native or OPAQUE
                if (block.isCube) native = native or FULL_CUBE
                if (block.ambientOcclusionLight < 0.5f) native = native or AO_BLOCKING
                if (block.isTranslucent) native = native or TRANSLUCENT
                if (block.usesNeighborLight()) native = native or NEIGHBOR_LIGHT
                if (block is SlabBlock) native = native or SLAB
                if (material == Material.AIR) native = native or AIR
                if (material.isSolid) native = native or SOLID_MATERIAL
                val model = states[state] ?: 0
                Memory.putInt(at, native)
                Memory.putInt(at + 4, model)
                Memory.putByte(at + 8, block.light.toByte())
                Memory.putByte(at + 9, block.renderLayer.ordinal.toByte())
                Memory.putByte(at + 10, renderType.toByte())
                Memory.putByte(at + 11, block.offsetType.ordinal.toByte())
                Memory.putByte(at + 12, insetFaces(block).toByte())
                Memory.putByte(at + 13, (if (material == Material.WATER) 1 else if (material == Material.LAVA) 2 else 0).toByte())
                Memory.putByte(at + 14, (if (block is LiquidBlock) state.get(LiquidBlock.LEVEL) else 0).toByte())
                modelsByState[raw] = model
                flags[raw] = blockFlags.getOrPut(block) { javaFlags(block, renderType, tintedBlocks[block] == true) }.toByte()
            }
            val stream = serialize(models, ids)
            val modelData = Memory.allocate(stream.size * 4L)
            val sprites = Memory.allocate(16 * 4L)
            try {
                Memory.copy(stream.values, 0, modelData, stream.size)
                val atlas = minecraft.blocksAtlas
                var cursor = sprites
                for (name in listOf("water_still", "water_flow", "lava_still", "lava_flow")) {
                    val sprite = atlas.getSprite("minecraft:blocks/$name")
                    Memory.putFloat(cursor, sprite.uMin)
                    Memory.putFloat(cursor + 4, sprite.uMax)
                    Memory.putFloat(cursor + 8, sprite.vMin)
                    Memory.putFloat(cursor + 12, sprite.vMax)
                    cursor += 16
                }
                Native.terrainTables(
                    table,
                    modelData,
                    stream.size,
                    sprites,
                    Minecraft.isAmbientOcclusionEnabled(),
                    minecraft.options.allowBlockAlternatives,
                    !world.dimension.hasNoSky(),
                    Shaderpacks.terrainBlockIds(),
                    Shaderpacks.ambientOcclusionLevel(),
                    Shaderpacks.directionalShading(),
                    Shaderpacks.separateAo(),
                )
            } finally {
                Memory.free(modelData)
                Memory.free(sprites)
            }
        } finally {
            Memory.free(table)
        }
        javaFlags = flags
        tableModels = modelsByState
        stateModels = states
    }

    private fun javaFlags(block: Block, renderType: Int, tinted: Boolean): Int {
        var flags = 0
        if (block.hasBlockEntity()) flags = flags or ENTITY
        when (renderType) {
            1 -> flags = flags or LIQUID
            3 -> {
                if (Overrides.resolvesVirtualProperties(block)) flags = flags or VIRTUAL
                if (Overrides.cullsFaces(block)) flags = flags or FACES
                if (tinted && Overrides.tints(block)) flags = flags or TINT
            }
        }
        return flags
    }

    private fun insetFaces(block: Block): Int {
        var faces = 0
        if (block.minY > 0.0) faces = faces or (1 shl 0)
        if (block.maxY < 1.0) faces = faces or (1 shl 1)
        if (block.minZ > 0.0) faces = faces or (1 shl 2)
        if (block.maxZ < 1.0) faces = faces or (1 shl 3)
        if (block.minX > 0.0) faces = faces or (1 shl 4)
        if (block.maxX < 1.0) faces = faces or (1 shl 5)
        return faces
    }

    private fun hasTint(model: BakedModel): Boolean {
        if (model is WeightedBakedModel) {
            return (model as WeightedBakedModelAccessor).astatineEntries.any { hasTint((it as ModelEntryAccessor).astatineModel) }
        }
        for (direction in Direction.entries) if (model.getQuads(direction).any { it.hasTint() }) return true
        return model.quads.any { it.hasTint() }
    }

    private class IntStream {
        var values = IntArray(1 shl 16)
        var size = 0

        fun put(value: Int) {
            if (size == values.size) values = values.copyOf(size * 2)
            values[size++] = value
        }
    }

    private fun serialize(models: List<BakedModel>, ids: IdentityHashMap<BakedModel, Int>): IntStream {
        val stream = IntStream()
        stream.put(models.size)
        val directions = Direction.entries.toTypedArray()
        for (model in models) {
            val weighted = model is WeightedBakedModel
            stream.put((if (model.useAmbientOcclusion()) 1 else 0) or (if (weighted) 2 else 0))
            if (weighted) {
                val entries = (model as WeightedBakedModelAccessor).astatineEntries
                stream.put(entries.size)
                for (entry in entries) {
                    stream.put(ids.getValue((entry as ModelEntryAccessor).astatineModel))
                    stream.put((entry as WeightedEntryAccessor).astatineWeight)
                }
                repeat(7) { stream.put(0) }
                continue
            }
            for (direction in directions) stream.put(model.getQuads(direction).size)
            stream.put(model.quads.size)
            for (face in 0..6) {
                val quads = if (face < 6) model.getQuads(directions[face]) else model.quads
                for (quad in quads) {
                    val vertices = quad.vertices
                    for (i in 0 until 28) stream.put(if (i < vertices.size) vertices[i] else 0)
                    stream.put(quad.face.id)
                    stream.put(quad.tintIndex)
                }
            }
        }
        return stream
    }
}
