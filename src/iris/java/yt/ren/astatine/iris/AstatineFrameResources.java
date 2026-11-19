package yt.ren.astatine.iris;

import com.mojang.renderpearl.api.buffers.GpuBufferSlice;
import net.irisshaders.iris.uniforms.buffer.FrameUniforms;

import java.util.IdentityHashMap;
import java.util.Map;

public final class AstatineFrameResources {
	private static final Map<FrameUniforms, AstatineFrameResources> FRAMES = new IdentityHashMap<>();
	public final AstatineResourceSets sets;
	private final FrameUniforms uniforms;

	private AstatineFrameResources(FrameUniforms uniforms) {
		this.uniforms = uniforms;
		this.sets = AstatineResourceSets.create(uniforms.getLayout(), 64, 2);
	}

	public static AstatineFrameResources getOrCreate(FrameUniforms uniforms) {
		if (uniforms == null) {
			return null;
		}

		return FRAMES.computeIfAbsent(uniforms, key -> {
			var result = new AstatineFrameResources(key);
			key.addCloseCallback(() -> {
				FRAMES.remove(key);

				if (result.sets != null) {
					result.sets.close();
				}
			});

			return result;
		});
	}

	public static long handle(AstatineFrameResources frame) {
		return frame == null ? 0L : AstatineResourceSets.handle(frame.sets);
	}

	public GpuBufferSlice slice() {
		return this.uniforms.snapshot();
	}
}
