package yt.ren.astatine.mixin.geometry;

import net.minecraft.client.render.world.VboRenderChunkList;
import net.minecraft.client.render.world.WorldRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin({VboRenderChunkList.class, WorldRenderer.class})
public abstract class ClientArrayMixin {
	@Redirect(method = "*", at = @At(value = "INVOKE", target = "Lorg/lwjgl/opengl/GL11;glEnableClientState(I)V"), require = 0)
	private void astatine$enableClientState(int array) {
	}

	@Redirect(method = "*", at = @At(value = "INVOKE", target = "Lorg/lwjgl/opengl/GL11;glDisableClientState(I)V"), require = 0)
	private void astatine$disableClientState(int array) {
	}

	@Redirect(method = "*", at = @At(value = "INVOKE", target = "Lorg/lwjgl/opengl/GL11;glVertexPointer(IIIJ)V"), require = 0)
	private void astatine$vertexPointer(int size, int type, int stride, long offset) {
	}

	@Redirect(method = "*", at = @At(value = "INVOKE", target = "Lorg/lwjgl/opengl/GL11;glColorPointer(IIIJ)V"), require = 0)
	private void astatine$colorPointer(int size, int type, int stride, long offset) {
	}

	@Redirect(method = "*", at = @At(value = "INVOKE", target = "Lorg/lwjgl/opengl/GL11;glTexCoordPointer(IIIJ)V"), require = 0)
	private void astatine$texCoordPointer(int size, int type, int stride, long offset) {
	}
}
