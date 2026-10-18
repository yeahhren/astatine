package yt.ren.astatine.mixin.geometry;

import net.minecraft.client.render.vertex.BufferBuilder;
import net.minecraft.client.render.vertex.BufferUploader;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import yt.ren.astatine.vulkan.WorldDraws;
import yt.ren.astatine.memory.Memory;
import yt.ren.astatine.render.geometry.VertexLayouts;

@Mixin(BufferUploader.class)
public abstract class BufferUploaderMixin {
	/**
	 * @author Ren
	 * @reason Astatine renderer
	 */
	@Overwrite
	public void end(BufferBuilder builder) {
		int count = builder.getVertexCount();
		if (count > 0) {
			WorldDraws.draw(VertexLayouts.of(builder.getFormat()), builder.getDrawMode(), Memory.INSTANCE.address(builder.getBuffer()), count);
		}
		builder.clear();
	}
}
