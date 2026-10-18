package yt.ren.astatine.backend;

import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.renderpearl.api.GpuFormat;
import com.mojang.renderpearl.api.textures.GpuTexture;
import com.mojang.renderpearl.api.textures.GpuTextureView;
import net.minecraft.client.Minecraft;
import yt.ren.astatine.extension.FramebufferHolder;

public final class MainTarget {
	private static final RenderTarget TARGET = new RenderTarget();
	private static long framebuffer;
	private static AstatineTextureView colorView;
	private static GpuTexture depth;
	private static GpuTextureView depthView;
	private static boolean general;

	private MainTarget() {
	}

	public static RenderTarget get() {
		var holder = (FramebufferHolder) Minecraft.getInstance().getRenderTarget();
		long current = holder.astatine$framebuffer();

		if (current != framebuffer || colorView == null) {
			framebuffer = current;

			if (colorView != null) {
				colorView.close();
			}

			colorView = AstatineTextureView.wrap(BackendNatives.framebufferColorView(current),
				GpuTexture.USAGE_RENDER_ATTACHMENT | GpuTexture.USAGE_TEXTURE_BINDING | GpuTexture.USAGE_COPY_SRC | GpuTexture.USAGE_COPY_DST,
				"Minecraft main color");
			int width = colorView.getWidth(0);
			int height = colorView.getHeight(0);

			if (depthView != null) {
				depthView.close();
				depth.close();
			}

			var device = RenderSystem.getDevice();
			depth = device.createTexture("Iris main depth",
				GpuTexture.USAGE_RENDER_ATTACHMENT | GpuTexture.USAGE_TEXTURE_BINDING | GpuTexture.USAGE_COPY_SRC | GpuTexture.USAGE_COPY_DST,
				GpuFormat.D32_FLOAT, width, height, 1, 1);
			depthView = device.createTextureView(depth);
			general = false;
			TARGET.update(width, height, colorView.texture(), colorView, depth, depthView);
		}

		return TARGET;
	}

	public static GpuTexture colorTexture() {
		return get().getColorTexture();
	}

	public static void beginAccess() {
		get();

		if (!general) {
			BackendNatives.mainTargetAccess(colorView.texture().handle(), true);
			general = true;
		}
	}

	public static void endAccess() {
		if (general) {
			BackendNatives.mainTargetAccess(colorView.texture().handle(), false);
			general = false;
		}
	}
}
