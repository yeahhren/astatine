package yt.ren.astatine.window

import net.fabricmc.loader.api.FabricLoader
import org.lwjgl.sdl.SDL
import org.lwjgl.sdl.SDLError.SDL_GetError
import org.lwjgl.sdl.SDLProperties.*
import org.lwjgl.sdl.SDLVideo.*
import pl.tomgirl.pylon.window.DisplaySdl
import pl.tomgirl.pylon.window.GpuSurface
import pl.tomgirl.pylon.window.MainThread
import yt.ren.astatine.Native
import yt.ren.astatine.NativeLoader
import yt.ren.astatine.render.GL

class VulkanSurface : GpuSurface {
    private var window = 0L
    private var ready = false

    override fun createWindow(title: String, width: Int, height: Int, resizable: Boolean): Long {
        check(window == 0L) { "Surface has already been created" }
        NativeLoader.load()
        MainThread.run { window = createSdlWindow(title, width, height, resizable) }
        val sdl = SDL.getLibrary()
        val cache = FabricLoader.getInstance().gameDir.resolve("astatine").resolve("pipelines.bin")
        Native.initialize(
            window,
            sdl.getFunctionAddress("SDL_Vulkan_GetInstanceExtensions"),
            sdl.getFunctionAddress("SDL_Vulkan_CreateSurface"),
            sdl.getFunctionAddress("SDL_Vulkan_DestroySurface"),
            sdl.getFunctionAddress("SDL_Vulkan_GetVkGetInstanceProcAddr"),
            sdl.getFunctionAddress("SDL_GetWindowSizeInPixels"),
            java.lang.Boolean.getBoolean("astatine.validation"),
            cache.toAbsolutePath().toString(),
        )
        GL.attach()
        ready = true
        return window
    }

    override fun makeCurrent() {}

    override fun releaseCurrent() {}

    override fun swapBuffers() {
        Native.present()
    }

    override fun setVSyncEnabled(enabled: Boolean) {
        if (ready) Native.setVsync(enabled)
    }

    override fun destroy() {
        if (ready) Native.shutdown()
        ready = false
        if (window != 0L) {
            val handle = window
            MainThread.run { SDL_DestroyWindow(handle) }
            window = 0L
        }
    }

    private fun createSdlWindow(title: String, width: Int, height: Int, resizable: Boolean): Long {
        val properties = SDL_CreateProperties()
        try {
            SDL_SetNumberProperty(properties, SDL_PROP_WINDOW_CREATE_X_NUMBER, SDL_WINDOWPOS_CENTERED.toLong())
            SDL_SetNumberProperty(properties, SDL_PROP_WINDOW_CREATE_Y_NUMBER, SDL_WINDOWPOS_CENTERED.toLong())
            SDL_SetNumberProperty(properties, SDL_PROP_WINDOW_CREATE_WIDTH_NUMBER, width.toLong())
            SDL_SetNumberProperty(properties, SDL_PROP_WINDOW_CREATE_HEIGHT_NUMBER, height.toLong())
            SDL_SetStringProperty(properties, SDL_PROP_WINDOW_CREATE_TITLE_STRING, title)
            SDL_SetBooleanProperty(properties, SDL_PROP_WINDOW_CREATE_VULKAN_BOOLEAN, true)
            SDL_SetBooleanProperty(properties, SDL_PROP_WINDOW_CREATE_HIDDEN_BOOLEAN, true)
            SDL_SetBooleanProperty(properties, SDL_PROP_WINDOW_CREATE_RESIZABLE_BOOLEAN, resizable)
            SDL_SetBooleanProperty(
                properties,
                SDL_PROP_WINDOW_CREATE_HIGH_PIXEL_DENSITY_BOOLEAN,
                DisplaySdl.instance().isHighPixelDensity
            )
            val handle = SDL_CreateWindowWithProperties(properties)
            check(handle != 0L) { "Unable to create the window: ${SDL_GetError()}" }
            return handle
        } finally {
            SDL_DestroyProperties(properties)
        }
    }
}
