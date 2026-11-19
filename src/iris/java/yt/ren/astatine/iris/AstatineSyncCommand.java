package yt.ren.astatine.iris;

import com.mojang.renderpearl.api.buffers.GpuBufferSlice;
import com.mojang.renderpearl.api.textures.GpuTextureView;
import net.irisshaders.iris.backend.IrisImage;
import net.irisshaders.iris.backend.SyncCommand;
import yt.ren.astatine.backend.AstatineBuffer;
import yt.ren.astatine.backend.AstatineTexture;
import yt.ren.astatine.backend.BackendNatives;
import yt.ren.astatine.backend.Scratch;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public class AstatineSyncCommand implements SyncCommand {
	private static final Scratch SCRATCH = new Scratch(1024);
	private final List<Pending> pending = new ArrayList<>();
	private Stage stage;

	@Override
	public SyncCommand read(Object object, Access access) {
		this.pending.add(new Pending(object, access, false));
		return this;
	}

	@Override
	public SyncCommand write(Object object, Access access) {
		if (access == Access.UNIFORM || access == Access.VERTEX || access == Access.INDEX || access == Access.INDIRECT) {
			throw new IllegalArgumentException("read-only synchronization access: " + access);
		}

		this.pending.add(new Pending(object, access, true));
		return this;
	}

	@Override
	public SyncCommand nextIs(Stage stage) {
		this.stage = Objects.requireNonNull(stage);
		return this;
	}

	@Override
	public void run() {
		if (this.stage == null) {
			throw new IllegalStateException("missing synchronization stage");
		}

		var accesses = new ArrayList<BackendAccess>();

		for (var pending : this.pending) {
			Object object = pending.object();

			if (object instanceof AstatineGraphicsShader.Pipeline pipeline) {
				accesses.addAll(pipeline.accesses());
				continue;
			}

			if (object instanceof GpuTextureView view) {
				object = view.texture();
			} else if (object instanceof GpuBufferSlice slice) {
				object = slice.buffer();
			}

			var access = switch (object) {
				case AstatineTexture texture -> new BackendAccess(texture.handle(), BackendAccess.TEXTURE, pending.access(), pending.write(), 0);
				case IrisImage image -> new BackendAccess(image.getImage(), BackendAccess.TEXTURE, pending.access(), pending.write(), 0);
				case AstatineBuffer buffer -> new BackendAccess(buffer.handle(), BackendAccess.BUFFER, pending.access(), pending.write(), 0);
				default -> throw new IllegalArgumentException("unsupported synchronization resource: " + object);
			};
			accesses.add(access);
		}

		var scratch = SCRATCH.clear();
		BackendAccess.writeAll(scratch, accesses);
		BackendNatives.synchronize(scratch.address(), accesses.size(), this.stage.ordinal());
		this.pending.clear();
		this.stage = null;
	}

	public record Pending(Object object, Access access, boolean write) {
	}
}
