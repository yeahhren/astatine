package yt.ren.astatine

object Native {
    @JvmStatic
    external fun initialize(
        window: Long,
        getInstanceExtensions: Long,
        createSurface: Long,
        destroySurface: Long,
        getInstanceProcAddr: Long,
        windowSize: Long,
        nativeWindow: Long,
        validation: Boolean,
        cacheFile: String,
    )

    @JvmStatic
    external fun shutdown()

    @JvmStatic
    external fun streamAddress(): Long

    @JvmStatic
    external fun flush()

    @JvmStatic
    external fun present()

    @JvmStatic
    external fun setVsync(enabled: Boolean)

    @JvmStatic
    external fun deviceName(): String

    @JvmStatic
    external fun vendorId(): Int

    @JvmStatic
    external fun apiVersion(): Int

    @JvmStatic
    external fun maxTextureSize(): Int

    @JvmStatic
    external fun driverVersion(): Int

    @JvmStatic
    external fun gpuTimestamps(): Boolean

    @JvmStatic
    external fun gpuTime(): Long

    @JvmStatic
    external fun registerLayout(elements: Long, count: Int, vertexSize: Int): Int

    @JvmStatic
    external fun draw(layout: Int, mode: Int, source: Long, count: Int)

    @JvmStatic
    external fun drawMesh(mesh: Long, mode: Int)

    @JvmStatic
    external fun uploadMesh(layout: Int, source: Long, count: Int): Long

    @JvmStatic
    external fun textureCreate(): Long

    @JvmStatic
    external fun textureAllocate(texture: Long, width: Int, height: Int)

    @JvmStatic
    external fun textureUpload(texture: Long, level: Int, x: Int, y: Int, width: Int, height: Int, source: Long)

    @JvmStatic
    external fun textureAllocateActive(width: Int, height: Int)

    @JvmStatic
    external fun textureUploadActive(level: Int, x: Int, y: Int, width: Int, height: Int, source: Long)

    @JvmStatic
    external fun textureDetach(texture: Long)

    @JvmStatic
    external fun textureWidth(texture: Long): Int

    @JvmStatic
    external fun textureHeight(texture: Long): Int

    @JvmStatic
    external fun activeTexture(): Long

    @JvmStatic
    external fun readTexture(texture: Long, destination: Long)

    @JvmStatic
    external fun copyFromTarget(level: Int, destinationX: Int, destinationY: Int, x: Int, y: Int, width: Int, height: Int)

    @JvmStatic
    external fun framebufferCreate(width: Int, height: Int, depth: Boolean): Long

    @JvmStatic
    external fun framebufferRelease(framebuffer: Long)

    @JvmStatic
    external fun framebufferBlit(framebuffer: Long, width: Int, height: Int): Boolean

    @JvmStatic
    external fun framebufferTexture(framebuffer: Long): Long

    @JvmStatic
    external fun getMatrix(name: Int, destination: Long)

    @JvmStatic
    external fun getViewport(destination: Long)

    @JvmStatic
    external fun terrainTables(
        blocks: Long,
        models: Long,
        modelWords: Int,
        sprites: Long,
        ambientOcclusion: Boolean,
        alternatives: Boolean,
        sky: Boolean,
    )

    @JvmStatic
    external fun terrainConfigure(viewDistance: Int)

    @JvmStatic
    external fun terrainClear()

    @JvmStatic
    external fun terrainMarkDirty(minX: Int, minY: Int, minZ: Int, maxX: Int, maxY: Int, maxZ: Int, important: Boolean)

    @JvmStatic
    external fun terrainSetup(
        x: Double,
        y: Double,
        z: Double,
        eyeHeight: Double,
        frame: Int,
        lookFace: Int,
        smartCull: Boolean,
        spectator: Boolean,
        cameraInOpaque: Boolean,
    )

    @JvmStatic
    external fun terrainVisibleCount(): Int

    @JvmStatic
    external fun terrainVisibleAddress(): Long

    @JvmStatic
    external fun terrainSchedule(out: Long, max: Int): Int

    @JvmStatic
    external fun terrainAbort(x: Int, y: Int, z: Int, version: Int)

    @JvmStatic
    external fun terrainSnapshot(): Long

    @JvmStatic
    external fun terrainRelease(snapshot: Long)

    @JvmStatic
    external fun terrainSnapshotClones(snapshot: Long, x: Int, y: Int, z: Int, present: Int): Int

    @JvmStatic
    external fun terrainSnapshotCommit(snapshot: Long, copied: Int, sky: Int)

    @JvmStatic
    external fun terrainSnapshotCopyClones(snapshot: Long)

    @JvmStatic
    external fun terrainBuild(snapshot: Long)

    @JvmStatic
    external fun terrainUpload(budget: Long, budgetNanos: Long): Int

    @JvmStatic
    external fun terrainSort(limit: Int): Int

    @JvmStatic
    external fun terrainDraw(layer: Int): Int

    @JvmStatic
    external fun terrainDebug(): String
}
