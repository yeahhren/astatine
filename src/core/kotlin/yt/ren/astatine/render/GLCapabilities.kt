package yt.ren.astatine.render

import org.lwjgl.opengl.ContextCapabilities
import org.lwjgl.opengl.GL11.*
import yt.ren.astatine.Native
import yt.ren.astatine.memory.Memory

object GLCapabilities {
    private val SUPPORTED = listOf(
        "OpenGL11", "OpenGL12", "OpenGL13", "OpenGL14", "OpenGL15", "OpenGL20", "OpenGL30",
        "GL_ARB_multitexture", "GL_ARB_texture_env_combine", "GL_EXT_blend_func_separate",
        "GL_ARB_framebuffer_object", "GL_ARB_vertex_buffer_object", "GL_NV_fog_distance",
    )

    @JvmStatic
    val capabilities: ContextCapabilities by lazy {
        val capabilities = Memory.UNSAFE.allocateInstance(ContextCapabilities::class.java) as ContextCapabilities
        for (name in SUPPORTED) {
            runCatching { ContextCapabilities::class.java.getField(name).setBoolean(capabilities, true) }
        }
        capabilities
    }

    @JvmStatic
    fun string(name: Int): String = when (name) {
        GL_VENDOR -> when (Native.vendorId()) {
            0x10DE -> "NVIDIA Corporation"
            0x1002 -> "ATI Technologies Inc."
            0x8086 -> "Intel"
            else -> "Vulkan"
        }

        GL_RENDERER -> Native.deviceName()
        GL_VERSION -> {
            val version = Native.apiVersion()
            "Vulkan ${version ushr 22}.${(version ushr 12) and 0x3FF}.${version and 0xFFF} (Astatine)"
        }

        else -> ""
    }
}
