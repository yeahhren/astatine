package yt.ren.astatine

import com.mojang.blaze3d.systems.RenderSystem
import com.mojang.renderpearl.frontend.FrontendGpuDevice
import net.fabricmc.loader.api.entrypoint.PreLaunchEntrypoint
import pl.tomgirl.pylon.window.DisplaySdl
import yt.ren.astatine.backend.AstatineDevice
import yt.ren.astatine.window.VulkanSurface

class Astatine : PreLaunchEntrypoint {
    override fun onPreLaunch() {
        RenderSystem.setDeviceFactory { FrontendGpuDevice(AstatineDevice.get()) }
        DisplaySdl.instance().setSurface(VulkanSurface())
    }
}
