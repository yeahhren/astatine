package yt.ren.astatine.iris;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.renderpearl.api.buffers.GpuBuffer;
import net.irisshaders.iris.backend.FramebufferDescription;
import net.irisshaders.iris.backend.GraphicsPipeline;
import net.irisshaders.iris.backend.GraphicsShader;
import net.irisshaders.iris.backend.PipelineState;
import net.irisshaders.iris.pipeline.transform.PatchShaderType;
import net.irisshaders.iris.pipeline.transform.PatchedShader;
import net.irisshaders.iris.pipeline.transform.ShaderResourceLayout;
import net.irisshaders.iris.pipeline.transform.WorldShaderTransformer;
import net.irisshaders.iris.samplers.ProgramResources;
import net.irisshaders.iris.uniforms.buffer.BufferUniformHolder;
import yt.ren.astatine.backend.AstatineBuffer;
import yt.ren.astatine.backend.AstatineDevice;
import yt.ren.astatine.backend.BackendNatives;
import yt.ren.astatine.backend.Scratch;
import yt.ren.astatine.memory.Memory;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.BitSet;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

public class AstatineGraphicsShader implements GraphicsShader {
	private final PatchedShader shader;
	private final ProgramResources resources;
	private final BufferUniformHolder uniforms;
	private final AstatineProgramResources bindings;
	private final AstatineResourceSets sets;
	private final AstatineFrameResources frame;
	private final AstatineShaderCache cache;
	private final List<CompletableFuture<Long>> pipelines = new ArrayList<>();
	private final Scratch scratch = new Scratch(4096);
	private final CompletableFuture<Map<PatchShaderType, Long>> code;
	private boolean closed;

	public AstatineGraphicsShader(AstatineShaderCache cache, String name, PatchedShader shader, ProgramResources resources,
		BufferUniformHolder uniforms) {
		var device = AstatineDevice.get();

		if (shader.sources().containsKey(PatchShaderType.GEOMETRY) && !device.supportsGeometryShaders()) {
			throw new IllegalArgumentException("geometry shaders are not supported: " + name);
		}

		if ((shader.sources().containsKey(PatchShaderType.TESS_CONTROL) || shader.sources()
			.containsKey(PatchShaderType.TESS_EVAL)) && !device.supportsTessellationShaders()) {
			throw new IllegalArgumentException("tessellation shaders are not supported: " + name);
		}

		this.cache = cache;
		this.resources = resources;
		this.uniforms = uniforms;
		this.bindings = new AstatineProgramResources(resources);
		this.shader = shader;
		this.sets = AstatineResourceSets.create(shader.layout(), 4096, 0);
		this.frame = AstatineFrameResources.getOrCreate(uniforms.frameUniforms());
		this.code = cache.submit(() -> {
			var compiled = new EnumMap<PatchShaderType, Long>(PatchShaderType.class);

			try {
				for (var entry : shader.sources().entrySet()) {
					compiled.put(entry.getKey(), cache.compile(name + entry.getKey().extension, entry.getKey(), entry.getValue()));
				}

				return compiled;
			} catch (RuntimeException | Error failure) {
				compiled.values().forEach(BackendNatives::shaderRelease);
				throw failure;
			}
		});
	}

	private static void describe(Scratch scratch, long heap, long uniformSets, long frameSets, Map<PatchShaderType, Long> code,
		PipelineState state, BitSet written) {
		var vanilla = state.vanillaPipeline();
		boolean world = vanilla != null;
		var depthState = world && state.depthFormat() != null ? state.depthState() : null;
		long zeroBuffer = state.zeroVertexBuffer() == null ? 0L : ((AstatineBuffer) state.zeroVertexBuffer()).handle();
		int zeroBinding = world ? WorldShaderTransformer.fallbackVertexBinding(state.vertexFormats()) : 0;
		var bindings = new ArrayList<int[]>();

		if (world) {
			for (int binding = 0; binding < state.vertexFormats().size(); binding++) {
				var format = state.vertexFormats().get(binding);

				if (format != null) {
					bindings.add(new int[]{binding, format.getVertexSize(), format.getStepRate() == 0 ? 0 : 1, Math.max(1, format.getStepRate())});
				}
			}
		} else if (!state.vertexAttributes().isEmpty()) {
			if (state.vertexStride() <= 0) {
				throw new IllegalArgumentException("invalid fullscreen vertex stride");
			}

			bindings.add(new int[]{0, state.vertexStride(), 0, 1});
		}

		if (zeroBuffer != 0L) {
			bindings.add(new int[]{zeroBinding, 0, 0, 1});
		}

		scratch.clear().putLong(heap).putLong(uniformSets).putLong(frameSets).putLong(zeroBuffer);

		for (var stage : PatchShaderType.values()) {
			scratch.putLong(code.getOrDefault(stage, 0L));
		}

		scratch.putInt(zeroBinding).putInt(state.colorFormats().size())
			.putInt(world && state.depthFormat() != null ? state.depthFormat().ordinal() : -1).putInt(world ? 1 : 0)
			.putInt(world ? vanilla.getPolygonMode().ordinal() : 0).putInt(PipelineState.cull(vanilla, state.shadowPass()) ? 1 : 0)
			.putInt(world ? 1 : 0).putInt(world ? vanilla.getPrimitiveTopology().ordinal() : 0).putInt(depthState != null ? 1 : 0)
			.putInt(depthState != null && depthState.writeDepth() ? 1 : 0).putInt(depthState != null ? depthState.depthTest().ordinal() : 0)
			.putFloat(depthState != null ? depthState.depthBiasConstant() : 0.0f)
			.putFloat(depthState != null ? depthState.depthBiasScaleFactor() : 0.0f).putInt(state.vertexAttributes().size())
			.putInt(bindings.size()).putInt(0);

		for (int i = 0; i < state.colorFormats().size(); i++) {
			boolean writes = written == null || written.get(i);
			int writeMask = PipelineState.colorMask(vanilla, writes);
			var blend = state.blendModes().get(i);
			scratch.putInt(state.colorFormats().get(i).ordinal()).putInt(writeMask).putInt(blend != null ? 1 : 0)
				.putInt(blend == null ? 1 : blend.srcRgb()).putInt(blend == null ? 0 : blend.dstRgb())
				.putInt(blend == null ? 1 : blend.srcAlpha()).putInt(blend == null ? 0 : blend.dstAlpha());
		}

		for (var attribute : state.vertexAttributes()) {
			scratch.putInt(attribute.location()).putInt(attribute.binding()).putInt(attribute.format().ordinal()).putInt(attribute.offset());
		}

		for (int[] binding : bindings) {
			scratch.putInt(binding[0]).putInt(binding[1]).putInt(binding[2]).putInt(binding[3]);
		}
	}

	@Override
	public PatchedShader getSource() {
		return this.shader;
	}

	@Override
	public GraphicsPipeline createPipeline(PipelineState state) {
		if (this.closed) {
			throw new IllegalStateException("graphics shader is closed");
		}

		long heap = this.bindings.pack().heap();
		long uniformSets = AstatineResourceSets.handle(this.sets);
		long frameSets = AstatineFrameResources.handle(this.frame);
		var written = this.shader.writtenAttachments();

		if (written != null && written.length() > state.colorFormats().size()) {
			throw new IllegalArgumentException("output write mask exceeds attachment count");
		}

		var pipeline = this.cache.thenApply(this.code, code -> {
			var description = new Scratch(1024);
			describe(description, heap, uniformSets, frameSets, code, state, written);
			return BackendNatives.graphicsPipelineCreate(description.address());
		});
		this.pipelines.add(pipeline);
		return new Pipeline(pipeline, state);
	}

	@Override
	public void close() {
		if (this.closed) {
			return;
		}

		this.closed = true;
		this.pipelines.forEach(future -> future.handle((pipeline, failure) -> null).join());
		this.pipelines.clear();
		this.code.handle((code, failure) -> {
			if (code != null) {
				code.values().forEach(BackendNatives::shaderRelease);
			}

			return null;
		}).join();

		if (this.sets != null) {
			this.sets.close();
		}
	}

	public class Pipeline implements GraphicsPipeline {
		private final CompletableFuture<Long> compilation;
		private final FramebufferDescription framebuffer;
		private final Scratch constants = new Scratch(128);
		private long pipeline;
		private boolean closed;

		private Pipeline(CompletableFuture<Long> compilation, PipelineState state) {
			this.compilation = compilation;
			this.framebuffer = state.framebuffer();
		}

		private long getPipeline() {
			if (this.closed) {
				throw new IllegalStateException("graphics pipeline is closed");
			}

			if (this.pipeline == 0L) {
				this.pipeline = AstatineShaderCache.await(this.compilation);
			}

			return this.pipeline;
		}

		public void bind() {
			long handle = this.getPipeline();
			var shader = AstatineGraphicsShader.this;
			var resolved = shader.resources.resolve();
			var complete = resolved.buffers();

			if (!complete.containsKey(ShaderResourceLayout.UNIFORM_BLOCK) && shader.uniforms.data().hasRemaining()) {
				complete.put(ShaderResourceLayout.UNIFORM_BLOCK, List.of(RenderSystem.getDevice().createCommandEncoder().transientMemory()
					.uploadGpu(shader.uniforms.data(), RenderSystem.getDevice().getDeviceInfo().limits().minUniformOffsetAlignment(),
						GpuBuffer.USAGE_UNIFORM)));
			}

			long address = shader.bindings.write(shader.scratch, resolved, complete, shader.sets, shader.frame, List.of());
			BackendNatives.graphicsPipelineBind(handle, shader.bindings.pack().heap(), address);
		}

		@Override
		public FramebufferDescription framebuffer() {
			return this.framebuffer;
		}

		public List<BackendAccess> accesses() {
			return AstatineGraphicsShader.this.bindings.accesses(AstatineGraphicsShader.this.resources.resolve());
		}

		public void pushConstants(ByteBuffer data) {
			int size = data.remaining();
			long address = data.isDirect() ? Memory.INSTANCE.address(data) + data.position() : this.constants.clear().putBytes(data).address();
			BackendNatives.graphicsPipelinePushConstants(this.getPipeline(), address, size);
		}

		@Override
		public void close() {
			if (!this.closed) {
				this.closed = true;
				this.compilation.handle((pipeline, failure) -> {
					if (pipeline != null) {
						BackendNatives.graphicsPipelineRelease(pipeline);
					}

					return null;
				}).join();
			}
		}
	}
}
