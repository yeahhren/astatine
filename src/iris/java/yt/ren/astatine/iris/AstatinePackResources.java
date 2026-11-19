package yt.ren.astatine.iris;

import net.irisshaders.iris.backend.PackResources;
import net.irisshaders.iris.gl.sampler.GlSampler;
import net.irisshaders.iris.gl.sampler.SamplerHolder.TextureBinding;
import net.irisshaders.iris.samplers.PackSamplers;
import net.irisshaders.iris.samplers.PackSamplers.Reference;
import net.irisshaders.iris.samplers.SamplerHeapLayout;
import yt.ren.astatine.backend.AstatineSampler;
import yt.ren.astatine.backend.BackendNatives;
import yt.ren.astatine.backend.Scratch;

import java.util.Map;

public class AstatinePackResources implements PackResources {
	private final PackSamplers pack;
	private long heap;
	private boolean closed;

	public AstatinePackResources(PackSamplers pack) {
		this.pack = pack;
	}

	private static int flags(SamplerHeapLayout.Type type) {
		String glsl = type.glsl;
		int flags = 0;
		flags |= type.texelBuffer() ? 1 : 0;
		flags |= glsl.endsWith("Shadow") ? 2 : 0;
		flags |= glsl.contains("Cube") ? 4 : 0;
		flags |= glsl.contains("Array") ? 8 : 0;
		flags |= glsl.contains("MS") ? 16 : 0;
		flags |= glsl.contains("1D") ? 32 : 0;
		flags |= glsl.contains("3D") ? 64 : 0;
		flags |= glsl.startsWith("i") ? 128 : 0;
		flags |= glsl.startsWith("u") ? 256 : 0;
		return flags;
	}

	public static int description(GlSampler.Description description) {
		return (description.linear() ? 1 : 0) | (description.linearMips() ? 2 : 0) | (description.mipmapped() ? 4 : 0) | (description.hardwareShadow()
			? 8 : 0) | (description.clamp() ? 16 : 0);
	}

	@Override
	public void beginFrame() {
		if (this.heap != 0L) {
			BackendNatives.samplerHeapBeginFrame(this.heap);
		}
	}

	public SamplerHeapLayout getLayout() {
		return this.pack.getLayout();
	}

	public long heap() {
		if (this.closed) {
			throw new IllegalStateException("pack samplers are closed");
		}

		if (this.heap == 0L) {
			var layout = this.getLayout();
			var scratch = new Scratch();

			for (var entry : layout.heaps().entrySet()) {
				scratch.putInt(entry.getKey().binding()).putInt(entry.getValue().size()).putInt(flags(entry.getKey()));
			}

			for (var entry : layout.drawHeaps().entrySet()) {
				scratch.putInt(entry.getKey().binding()).putInt(entry.getValue().size()).putInt(flags(entry.getKey()));
			}

			this.heap = BackendNatives.samplerHeapCreate(scratch.address(), layout.heaps().size(), layout.drawHeaps().size(), 64);
		}

		return this.heap;
	}

	public int[] write(Scratch scratch, Map<Reference, TextureBinding.Resolved> bindings, Map<Reference, Long> texels) {
		this.heap();
		var layout = this.getLayout();
		int heapCount = 0;
		int drawCount = 0;

		for (int pass = 0; pass < 2; pass++) {
			boolean draws = pass == 1;

			for (var entry : texels.entrySet()) {
				var slot = this.pack.getSlot(entry.getKey());

				if (layout.isDraw(slot) == draws) {
					scratch.putInt(slot.type().binding()).putInt(slot.index()).putInt(1).putInt(0).putLong(entry.getValue()).putLong(0L);
					if (draws) drawCount++;
					else heapCount++;
				}
			}

			for (var entry : bindings.entrySet()) {
				var slot = this.pack.getSlot(entry.getKey());

				if (layout.isDraw(slot) != draws) {
					continue;
				}

				var binding = entry.getValue();
				long view = AstatineImage.view(binding.texture(), binding.image());
				boolean external = binding.nativeSampler() != null;
				long sampler = external ? ((AstatineSampler) binding.nativeSampler()).handle() : description(binding.sampler());
				scratch.putInt(slot.type().binding()).putInt(slot.index()).putInt(0).putInt(external ? 1 : 0).putLong(view).putLong(sampler);
				if (draws) drawCount++;
				else heapCount++;
			}
		}

		return new int[]{heapCount, drawCount};
	}

	@Override
	public void close() {
		if (this.closed) {
			return;
		}

		this.closed = true;

		if (this.heap != 0L) {
			BackendNatives.samplerHeapRelease(this.heap);
			this.heap = 0L;
		}
	}
}
