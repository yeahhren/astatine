package yt.ren.astatine

import net.fabricmc.loader.api.entrypoint.PreLaunchEntrypoint
import pl.tomgirl.pylon.window.DisplaySdl
import yt.ren.astatine.window.VulkanSurface

class Astatine : PreLaunchEntrypoint {
    override fun onPreLaunch() {
        DisplaySdl.instance().setSurface(VulkanSurface())
    }
}
