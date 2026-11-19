package yt.ren.astatine.iris;

import net.irisshaders.iris.backend.SyncCommand;
import net.irisshaders.iris.pipeline.transform.PatchShaderType;
import yt.ren.astatine.backend.Scratch;

import java.util.Collection;
import java.util.List;

public record BackendAccess(long handle, int kind, SyncCommand.Access access, boolean write, int stages) {
	public static final int TEXTURE = 0;
	public static final int BUFFER = 1;
	public static final int ANY_TEXTURE = 2;
	public static final int ANY_BUFFER = 3;

	public static int stages(Collection<PatchShaderType> stages) {
		int mask = 0;

		for (var stage : stages) {
			mask |= 1 << stage.ordinal();
		}

		return mask;
	}

	public void write(Scratch scratch) {
		scratch.putLong(this.handle).putInt(this.kind).putInt(this.access.ordinal()).putInt(this.write ? 1 : 0).putInt(this.stages);
	}

	public static void writeAll(Scratch scratch, List<BackendAccess> accesses) {
		for (var access : accesses) {
			access.write(scratch);
		}
	}
}
