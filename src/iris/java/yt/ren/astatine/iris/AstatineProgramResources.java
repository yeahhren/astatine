package yt.ren.astatine.iris;

import com.mojang.renderpearl.api.buffers.GpuBufferSlice;
import net.irisshaders.iris.backend.SyncCommand;
import net.irisshaders.iris.samplers.ProgramResources;
import yt.ren.astatine.backend.AstatineBuffer;
import yt.ren.astatine.backend.Scratch;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class AstatineProgramResources {
	private final ProgramResources resources;
	private final AstatinePackResources pack;

	public AstatineProgramResources(ProgramResources resources) {
		this.resources = resources;
		this.pack = (AstatinePackResources) resources.getPackSamplers().getResources();
	}

	public AstatinePackResources pack() {
		return this.pack;
	}

	public long write(Scratch scratch, ProgramResources.Bindings bindings, Map<String, List<GpuBufferSlice>> buffers, AstatineResourceSets sets,
		AstatineFrameResources frame, List<BackendAccess> accesses) {
		scratch.clear();

		for (int i = 0; i < 6; i++) {
			scratch.putInt(0);
		}

		int[] counts = this.pack.write(scratch, bindings.textures(), bindings.texels());

		if (sets != null) {
			sets.writeBuffers(scratch, buffers);
			sets.writeViews(scratch, bindings.images());
		}

		if (frame != null) {
			AstatineResourceSets.writeSlice(scratch, frame.slice());
		}

		BackendAccess.writeAll(scratch, accesses);
		scratch.putIntAt(0, counts[0]).putIntAt(4, counts[1]).putIntAt(8, sets == null ? 0 : sets.bufferCount())
			.putIntAt(12, sets == null ? 0 : sets.viewCount()).putIntAt(16, frame == null ? 0 : 1).putIntAt(20, accesses.size());
		return scratch.address();
	}

	public List<BackendAccess> accesses(ProgramResources.Bindings bindings) {
		var accesses = new ArrayList<BackendAccess>();

		for (var entry : this.resources.getSamplerReferences().entrySet()) {
			var descriptor = this.resources.getSamplerDeclarations().get(entry.getKey());
			var binding = bindings.textures().get(entry.getValue());
			int stages = BackendAccess.stages(descriptor.stages());

			if (binding == null) {
				accesses.add(new BackendAccess(0L, BackendAccess.ANY_BUFFER, SyncCommand.Access.SHADER, false, stages));
			} else {
				accesses.add(new BackendAccess(AstatineImage.texture(binding.texture(), binding.image()), BackendAccess.TEXTURE,
					SyncCommand.Access.SHADER, false, stages));
			}
		}

		for (var descriptor : this.resources.getImageDeclarations().values()) {
			for (var binding : bindings.images().get(descriptor.name())) {
				accesses.add(new BackendAccess(AstatineImage.texture(binding.texture(), binding.image()), BackendAccess.TEXTURE,
					SyncCommand.Access.SHADER, true, BackendAccess.stages(descriptor.stages())));
			}
		}

		var buffers = bindings.buffers();

		for (var descriptor : this.resources.getStorageDeclarations()) {
			for (var slice : buffers.get(descriptor.name())) {
				accesses.add(new BackendAccess(((AstatineBuffer) slice.buffer()).handle(), BackendAccess.BUFFER, SyncCommand.Access.SHADER, true,
					BackendAccess.stages(descriptor.stages())));
			}
		}

		return accesses;
	}
}
