package yt.ren.astatine.backend;

import com.mojang.renderpearl.api.textures.GpuTexture;
import com.mojang.renderpearl.api.textures.GpuTextureView;
import yt.ren.astatine.Native;

import java.util.HashMap;
import java.util.Map;

public final class MinecraftTextureViews {
	private static final Map<Long, Entry> VIEWS = new HashMap<>();

	private MinecraftTextureViews() {
	}

	public static AstatineTextureView view(long texture) {
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

		long handle = BackendNatives.nativeTextureView(texture);

		if (handle == 0L) {
			return null;
		}

		var view = AstatineTextureView.wrap(handle, GpuTexture.USAGE_TEXTURE_BINDING, "Minecraft texture " + texture);

		if (entry != null) {
			entry.view.close();
		}

		VIEWS.put(texture, new Entry(current, view));
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
		var entry = VIEWS.remove(texture);

		if (entry != null) {
			entry.view.close();
		}
	}

	private record Entry(long imageView, AstatineTextureView view) {
	}
}
