package yt.ren.astatine.iris;

import com.mojang.blaze3d.systems.RenderSystem;
import net.irisshaders.iris.backend.ComputePipeline;
import net.irisshaders.iris.pipeline.transform.PatchShaderType;
import net.irisshaders.iris.pipeline.transform.PatchedShader;
import net.irisshaders.iris.pipeline.transform.ShaderResourceLayout;
import net.irisshaders.iris.samplers.ProgramResources;
import net.irisshaders.iris.shaderpack.FilledIndirectPointer;
import net.irisshaders.iris.uniforms.buffer.BufferUniformHolder;
import org.joml.Vector3i;
import yt.ren.astatine.backend.AstatineBuffer;
import yt.ren.astatine.backend.BackendNatives;
import yt.ren.astatine.backend.Scratch;

import java.util.List;
import java.util.concurrent.CompletableFuture;

public class AstatineComputePipeline implements ComputePipeline {
	private final AstatineProgramResources bindings;
	private final String name;
	private final BufferUniformHolder uniforms;
	private final ProgramResources resources;
	private final AstatineResourceSets sets;
	private final AstatineFrameResources frame;
	private final CompletableFuture<Long> compilation;
	private final Scratch scratch = new Scratch(4096);
	private final Vector3i localSize = new Vector3i(1);
	private long pipeline;
	private boolean closed;

	public AstatineComputePipeline(AstatineShaderCache cache, String name, PatchedShader shader, ProgramResources resources,
		BufferUniformHolder uniforms) {
		this.bindings = new AstatineProgramResources(resources);
		this.name = name;
		this.uniforms = uniforms;
		this.resources = resources;
		this.uniforms.resolveLayout(shader.layout());
		this.sets = AstatineResourceSets.create(shader.layout(), 32, 0);
		this.frame = AstatineFrameResources.getOrCreate(uniforms.frameUniforms());
		long heap = this.bindings.pack().heap();
		long setsHandle = AstatineResourceSets.handle(this.sets);
		long frameHandle = AstatineFrameResources.handle(this.frame);
		this.compilation = cache.submit(() -> {
			long code = cache.compile(name + ".csh", PatchShaderType.COMPUTE, shader.sources().get(PatchShaderType.COMPUTE));

			try {
				return BackendNatives.computePipelineCreate(heap, setsHandle, frameHandle, code);
			} finally {
				BackendNatives.shaderRelease(code);
			}
		});
	}

	@Override
	public Vector3i localSize() {
		this.prepare();
		return new Vector3i(this.localSize);
	}

	public void prepare() {
		if (this.closed) {
			throw new IllegalStateException("compute program is closed: " + this.name);
		}

		if (this.pipeline == 0L) {
			this.pipeline = AstatineShaderCache.await(this.compilation);
			this.localSize.set(BackendNatives.computeLocalSize(this.pipeline, 0), BackendNatives.computeLocalSize(this.pipeline, 1),
				BackendNatives.computeLocalSize(this.pipeline, 2));
		}
	}

	public void dispatch(Vector3i groups, FilledIndirectPointer indirect) {
		this.prepare();

		if (groups.x < 0 || groups.y < 0 || groups.z < 0) {
			throw new IllegalArgumentException("invalid dispatch for " + this.name + ": " + groups);
		}

		var resolved = this.resources.resolve();
		var buffers = resolved.buffers();

		if (this.uniforms.data().hasRemaining()) {
			var device = RenderSystem.getDevice();
			buffers.put(ShaderResourceLayout.UNIFORM_BLOCK, List.of(this.uniforms.upload(device.createCommandEncoder(),
				device.getDeviceInfo().limits().minUniformOffsetAlignment())));
		}

		long heap = this.bindings.pack().heap();
		long address = this.bindings.write(this.scratch, resolved, buffers, this.sets, this.frame, this.bindings.accesses(resolved));
		long indirectBuffer = indirect == null ? 0L : ((AstatineBuffer) indirect.buffer()).handle();
		BackendNatives.computeDispatch(this.pipeline, heap, address, groups.x, groups.y, groups.z, indirectBuffer,
			indirect == null ? 0L : indirect.offset());
	}

	@Override
	public void close() {
		if (this.closed) {
			return;
		}

		this.closed = true;
		this.compilation.handle((pipeline, failure) -> {
			if (pipeline != null) {
				BackendNatives.computePipelineRelease(pipeline);
			}

			return null;
		}).join();

		if (this.sets != null) {
			this.sets.close();
		}
	}
}
