package yt.ren.astatine

import net.fabricmc.loader.api.FabricLoader
import java.nio.file.Files
import java.nio.file.StandardCopyOption

object NativeLoader {
    @Volatile
    private var loaded = false

    @JvmStatic
    @Synchronized
    fun load() {
        if (loaded) return
        val platform = "${os()}-${arch()}"
        val name = System.mapLibraryName("astatine")
        val resource = "/natives/$platform/$name"
        val bytes = NativeLoader::class.java.getResourceAsStream(resource)?.use { it.readBytes() }
            ?: throw UnsatisfiedLinkError("Astatine has no native renderer for $platform ($resource)")
        val directory = FabricLoader.getInstance().gameDir.resolve("astatine").resolve("natives")
        Files.createDirectories(directory)
        val target = directory.resolve(name)
        if (!Files.exists(target) || !Files.readAllBytes(target).contentEquals(bytes)) {
            val temporary = Files.createTempFile(directory, "astatine", ".tmp")
            Files.write(temporary, bytes)
            Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)
        }
        System.load(target.toAbsolutePath().toString())
        loaded = true
    }

    private fun os(): String {
        val name = System.getProperty("os.name").lowercase()
        return when {
            name.startsWith("windows") -> "windows"
            name.startsWith("mac") || name.contains("darwin") -> "macos"
            else -> "linux"
        }
    }

    private fun arch(): String = when (System.getProperty("os.arch").lowercase()) {
        "aarch64", "arm64" -> "arm64"
        else -> "x64"
    }
}
