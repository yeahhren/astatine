package yt.ren.astatine.terrain

import net.minecraft.block.entity.BlockEntity
import net.minecraft.client.Minecraft
import net.minecraft.client.render.world.RenderChunk
import net.minecraft.client.render.world.WorldRenderer
import net.minecraft.client.world.ClientWorld
import net.minecraft.entity.Entity
import net.minecraft.util.math.BlockPos
import net.minecraft.util.math.Direction
import net.minecraft.util.math.MathHelper
import org.apache.logging.log4j.LogManager
import yt.ren.astatine.Native
import yt.ren.astatine.memory.LongObjectMap
import yt.ren.astatine.memory.Memory
import yt.ren.astatine.shaderpack.Shaderpacks
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import kotlin.math.abs

object TerrainRenderer {
    private val logger = LogManager.getLogger("Astatine/Terrain")
    private const val UPLOAD_BUDGET = 24L shl 20
    private const val BUDGET_SHARE = 0.15
    private const val MIN_BUDGET_NANOS = 50_000L
    private const val MAX_BUDGET_NANOS = 2_000_000L
    private const val SCHEDULE_ENTRIES = 256
    private const val TRANSLUCENT_SORTS = 15

    private val threads = (Runtime.getRuntime().availableProcessors() / 2).coerceIn(1, 8)
    private val maxInFlight = threads * 3
    private val executor = ThreadPoolExecutor(threads, threads, 30L, TimeUnit.SECONDS, LinkedBlockingQueue()) { task ->
        Thread(task, "Astatine Chunk Builder").apply {
            isDaemon = true
            priority = Thread.NORM_PRIORITY - 1
        }
    }.apply { allowCoreThreadTimeOut(true) }
    private val inFlight = AtomicInteger()
    private val completed = ConcurrentLinkedQueue<SectionBuild>()
    private val failed = ConcurrentLinkedQueue<SectionBuild>()
    private val scheduled = Memory.allocate(SCHEDULE_ENTRIES * 5 * 4L)

    @Volatile
    private var generation = 0
    private var world: ClientWorld? = null
    private var renderer: WorldRenderer? = null
    private val blockEntities = LongObjectMap<List<BlockEntity>>()
    private val globalBlockEntities = LongObjectMap<List<BlockEntity>>()
    private val important = LinkedHashSet<Long>()
    private var lastFrameNanos = 0L
    private var frameNanos = 4_000_000.0
    private var snapshotNanos = 100_000.0

    private fun budgetNanos(): Long = (frameNanos * BUDGET_SHARE).toLong().coerceIn(MIN_BUDGET_NANOS, MAX_BUDGET_NANOS)

    @JvmStatic
    fun key(x: Int, y: Int, z: Int): Long =
        ((x.toLong() and 0x3FFFFF) shl 26) or ((z.toLong() and 0x3FFFFF) shl 4) or (y.toLong() and 15)

    @JvmStatic
    fun reload(renderer: WorldRenderer, world: ClientWorld, viewDistance: Int) {
        settle()
        this.renderer = renderer
        this.world = world
        val started = System.nanoTime()
        TerrainTables.export(Minecraft.getInstance(), world)
        Native.terrainConfigure(viewDistance)
        blockEntities.clear()
        globalBlockEntities.clear()
        logger.info(
            "Terrain reloaded with view distance {} and {} build threads in {} ms",
            viewDistance,
            threads,
            (System.nanoTime() - started) / 1_000_000
        )
    }

    @JvmStatic
    fun clear() {
        settle()
        Native.terrainClear()
        blockEntities.clear()
        globalBlockEntities.clear()
        world = null
    }

    private fun settle() {
        generation++
        important.clear()
        val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10)
        while (inFlight.get() > 0 && System.nanoTime() < deadline) Thread.sleep(1)
        completed.clear()
        failed.clear()
    }

    @JvmStatic
    fun setup(camera: Entity, x: Double, y: Double, z: Double, tickDelta: Double, frame: Int, spectator: Boolean) {
        val world = world ?: return
        val now = System.nanoTime()
        if (lastFrameNanos != 0L) frameNanos += ((now - lastFrameNanos).coerceAtMost(100_000_000L) - frameNanos) * 0.05
        lastFrameNanos = now
        val minecraft = Minecraft.getInstance()
        val eyeHeight = camera.eyeHeight.toDouble()
        val inOpaque = spectator && world.getBlockState(BlockPos(x, y + eyeHeight, z)).block.isSolidRender
        Native.terrainSetup(
            x,
            y,
            z,
            eyeHeight,
            frame,
            lookFace(camera, tickDelta, minecraft),
            minecraft.smartCull && Shaderpacks.occlusionCulling(),
            spectator,
            inOpaque,
            Shaderpacks.frustumCulling(),
        )
        buildImportant(world, x, y + eyeHeight, z)
        schedule(world)
    }

    private var playerChanges = 0

    @JvmStatic
    fun beginPlayerChange() {
        playerChanges++
    }

    @JvmStatic
    fun endPlayerChange() {
        if (playerChanges > 0) playerChanges--
    }

    @JvmStatic
    fun markImportant(x: Int, y: Int, z: Int) {
        if (playerChanges == 0) return
        for (sx in ((x - 1) shr 4)..((x + 1) shr 4)) {
            for (sz in ((z - 1) shr 4)..((z + 1) shr 4)) {
                for (sy in ((y - 1) shr 4)..((y + 1) shr 4)) {
                    if (sy in 0..15) important += key(sx, sy, sz)
                }
            }
        }
    }

    private fun buildImportant(world: ClientWorld, x: Double, y: Double, z: Double) {
        if (important.isEmpty()) return
        val cameraX = MathHelper.floor(x) shr 4
        val cameraY = MathHelper.floor(y) shr 4
        val cameraZ = MathHelper.floor(z) shr 4
        var built = false
        for (key in important) {
            val sectionX = (key shr 26).toInt() shl 10 shr 10
            val sectionZ = ((key shr 4) and 0x3FFFFF).toInt() shl 10 shr 10
            val sectionY = (key and 15).toInt()
            if (abs(sectionX - cameraX) > 1 || abs(sectionY - cameraY) > 1 || abs(sectionZ - cameraZ) > 1) continue
            val snapshot = Native.terrainSnapshot()
            runNow(SectionBuild(sectionX, sectionY, sectionZ, 0, generation, Snapshots.fill(snapshot, world, sectionX, sectionY, sectionZ, 0, true)))
            built = true
        }
        important.clear()
        if (built) accept()
    }

    private fun lookFace(camera: Entity, tickDelta: Double, minecraft: Minecraft): Int {
        var pitch = (camera.lastPitch + (camera.pitch - camera.lastPitch) * tickDelta).toFloat()
        val yaw = (camera.lastYaw + (camera.yaw - camera.lastYaw) * tickDelta).toFloat()
        if (minecraft.options.perspective == 2) pitch += 180.0f
        val h = MathHelper.cos(-yaw * (Math.PI.toFloat() / 180.0f) - Math.PI.toFloat())
        val i = MathHelper.sin(-yaw * (Math.PI.toFloat() / 180.0f) - Math.PI.toFloat())
        val j = -MathHelper.cos(-pitch * (Math.PI.toFloat() / 180.0f))
        val k = MathHelper.sin(-pitch * (Math.PI.toFloat() / 180.0f))
        return Direction.getNearest(i * j, k, h * j).opposite.id
    }

    private fun schedule(world: ClientWorld) {
        val room = maxInFlight - inFlight.get()
        val affordable = (budgetNanos() / snapshotNanos).toInt().coerceAtLeast(1)
        val count = Native.terrainSchedule(scheduled, minOf(maxOf(room, 2), affordable).coerceAtMost(SCHEDULE_ENTRIES))
        var urgent = false
        val deadline = System.nanoTime() + budgetNanos()
        for (i in 0 until count) {
            val at = scheduled + i * 20L
            val x = Memory.getInt(at)
            val y = Memory.getInt(at + 4)
            val z = Memory.getInt(at + 8)
            val version = Memory.getInt(at + 12)
            val now = Memory.getInt(at + 16) != 0
            val started = System.nanoTime()
            if (!now && i > 0 && started >= deadline) {
                Native.terrainAbort(x, y, z, version)
                continue
            }
            val snapshot = Native.terrainSnapshot()
            val build = SectionBuild(x, y, z, version, generation, Snapshots.fill(snapshot, world, x, y, z, version))
            snapshotNanos += ((System.nanoTime() - started) - snapshotNanos) * 0.1
            if (now) {
                runNow(build)
                urgent = true
            } else {
                submit(build)
            }
        }
        if (urgent) accept()
    }

    private fun runNow(build: SectionBuild) {
        try {
            build.run()
            completed += build
        } catch (throwable: Throwable) {
            logger.error("Unable to build section {} {} {}", build.x, build.y, build.z, throwable)
            failed += build
        }
    }

    private fun submit(build: SectionBuild) {
        inFlight.incrementAndGet()
        executor.execute {
            try {
                build.run()
                completed += build
            } catch (throwable: Throwable) {
                logger.error("Unable to build section {} {} {}", build.x, build.y, build.z, throwable)
                failed += build
            } finally {
                inFlight.decrementAndGet()
            }
        }
    }

    @JvmStatic
    fun accept() {
        var built = 0
        while (true) {
            val build = completed.poll() ?: break
            if (build.generation != generation) continue
            built++
            val key = key(build.x, build.y, build.z)
            if (build.blockEntities.isEmpty()) blockEntities.remove(key) else blockEntities[key] = build.blockEntities
            val previous = globalBlockEntities[key] ?: emptyList()
            if (previous.isNotEmpty() || build.globalBlockEntities.isNotEmpty()) {
                val added = build.globalBlockEntities.filter { it !in previous }
                val removed = previous.filter { it !in build.globalBlockEntities }
                renderer?.updateGlobalBlockEntities(removed, added)
                if (build.globalBlockEntities.isEmpty()) globalBlockEntities.remove(key) else globalBlockEntities[key] = build.globalBlockEntities
            }
        }
        while (true) {
            val build = failed.poll() ?: break
            if (build.generation == generation) Native.terrainAbort(build.x, build.y, build.z, build.version)
        }
        RenderChunk.updateCounter += built
        Native.terrainUpload(UPLOAD_BUDGET, budgetNanos())
    }

    @JvmStatic
    fun sortTranslucent() {
        Native.terrainSort(TRANSLUCENT_SORTS)
    }

    @JvmStatic
    fun visibleCount(): Int = Native.terrainVisibleCount()

    @JvmStatic
    fun visibleAddress(): Long = Native.terrainVisibleAddress()

    @JvmStatic
    fun blockEntities(x: Int, y: Int, z: Int): List<BlockEntity>? = blockEntities[key(x, y, z)]

    @JvmStatic
    fun debug(): String = Native.terrainDebug()
}
