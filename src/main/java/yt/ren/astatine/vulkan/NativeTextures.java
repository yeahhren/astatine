package yt.ren.astatine.vulkan;

import com.mojang.renderpearl.api.textures.GpuTexture;
import com.mojang.renderpearl.api.textures.GpuTextureView;
import com.mojang.renderpearl.backend.vulkan.VulkanConst;
import com.mojang.renderpearl.backend.vulkan.VulkanDevice;
import com.mojang.renderpearl.backend.vulkan.VulkanGpuTexture;
import com.mojang.renderpearl.backend.vulkan.VulkanGpuTextureView;
import yt.ren.astatine.Native;

import java.util.HashMap;
import java.util.Map;

public final class NativeTextures {
	private static final Map<Long, Entry> VIEWS = new HashMap<>();

	private NativeTextures() {
	}

	public static VulkanGpuTextureView view(long texture) {
		if (texture == 0L) {
			return null;
		}

		long current = Native.textureImageHandle(texture);

		if (current == 0L) {
			return null;
		}

		var entry = VIEWS.get(texture);

		if (entry != null && entry.imageView == current) {
			return entry.view;
		}

		var images = Native.textureImage(texture);

		if (images == null) {
			return null;
		}

		var image = new VulkanGpuTexture(VulkanDevice.get(), images[0], GpuTexture.USAGE_TEXTURE_BINDING, "Minecraft texture " + texture,
			VulkanConst.fromVk((int) images[2]), (int) images[3], (int) images[4], 1, (int) images[5]);
		var view = new VulkanGpuTextureView(image, images[1], 0, (int) images[5], (int) images[6]);
		VIEWS.put(texture, new Entry(images[1], view));
		return view;
	}

	public static long handle(GpuTextureView view) {
		for (var entry : VIEWS.entrySet()) {
			if (entry.getValue().view == view) {
				return entry.getKey();
			}
		}

		return 0L;
	}

	public static void forget(long texture) {
		VIEWS.remove(texture);
	}

	private record Entry(long imageView, VulkanGpuTextureView view) {
	}
}
