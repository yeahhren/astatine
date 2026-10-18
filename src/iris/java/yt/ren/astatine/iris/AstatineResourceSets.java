package yt.ren.astatine.iris;

import com.mojang.renderpearl.api.buffers.GpuBufferSlice;
import net.irisshaders.iris.pipeline.transform.ShaderResourceLayout;
import net.irisshaders.iris.samplers.ProgramResources;
import yt.ren.astatine.backend.AstatineBuffer;
import yt.ren.astatine.backend.BackendNatives;
import yt.ren.astatine.backend.Scratch;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public final class AstatineResourceSets implements AutoCloseable {
	private final long handle;
	private final List<ShaderResourceLayout.Descriptor> blocks = new ArrayList<>();
	private final List<ShaderResourceLayout.Descriptor> images = new ArrayList<>();
	private boolean closed;

	private AstatineResourceSets(ShaderResourceLayout layout, int capacity, int setIndex) {
		var scratch = new Scratch();
		int count = 0;

		for (var descriptor : layout.descriptors()) {
			if (descriptor.set() != setIndex) {
				continue;
			}

			int kind = switch (descriptor.kind()) {
				case UNIFORM_BUFFER -> 0;
				case STORAGE_BUFFER -> 1;
				case IMAGE -> 2;
				default -> throw new IllegalArgumentException("invalid program descriptor: " + descriptor);
			};

			(kind == 2 ? this.images : this.blocks).add(descriptor);
			scratch.putInt(descriptor.binding()).putInt(kind).putInt(descriptor.count()).putInt(BackendAccess.stages(descriptor.stages()))
				.putInt(descriptor.name().equals(ShaderResourceLayout.UNIFORM_BLOCK) ? 1 : 0);
			count++;
		}

		this.handle = BackendNatives.resourceSetsCreate(scratch.address(), count, capacity, setIndex, layout.uniformBufferSize());
	}

	public static AstatineResourceSets create(ShaderResourceLayout layout, int capacity, int setIndex) {
		return layout.descriptors().stream().anyMatch(descriptor -> descriptor.set() == setIndex) ? new AstatineResourceSets(layout,
			capacity, setIndex) : null;
	}

	public static long handle(AstatineResourceSets sets) {
		return sets == null ? 0L : sets.handle;
	}

	public int bufferCount() {
		return this.blocks.stream().mapToInt(ShaderResourceLayout.Descriptor::count).sum();
	}

	public int viewCount() {
		return this.images.stream().mapToInt(ShaderResourceLayout.Descriptor::count).sum();
	}

	public void writeBuffers(Scratch scratch, Map<String, List<GpuBufferSlice>> buffers) {
		for (var descriptor : this.blocks) {
			var slices = buffers.get(descriptor.name());

			if (slices == null || slices.size() != descriptor.count()) {
				throw new IllegalArgumentException("buffer bindings must match shader blocks: " + descriptor.name());
			}

			for (var slice : slices) {
				writeSlice(scratch, slice);
			}
		}
	}

	public void writeViews(Scratch scratch, Map<String, List<ProgramResources.ImageBinding>> bindings) {
		for (var descriptor : this.images) {
			var list = bindings.get(descriptor.name());

			if (list == null || list.size() != descriptor.count()) {
				throw new IllegalArgumentException("image bindings must match shader images: " + descriptor.name());
			}

			for (var binding : list) {
				scratch.putLong(AstatineImage.view(binding.texture(), binding.image()));
			}
		}
	}

	public static void writeSlice(Scratch scratch, GpuBufferSlice slice) {
		if (!(slice.buffer() instanceof AstatineBuffer buffer) || buffer.isClosed() || slice.offset() < 0 || slice.length() <= 0 || slice.offset() > buffer.size() - slice.length()) {
			throw new IllegalArgumentException("invalid Astatine buffer slice");
		}

		scratch.putLong(buffer.handle()).putLong(slice.offset()).putLong(slice.length());
	}

	@Override
	public void close() {
		if (!this.closed) {
			this.closed = true;
			BackendNatives.resourceSetsRelease(this.handle);
		}
	}
}
