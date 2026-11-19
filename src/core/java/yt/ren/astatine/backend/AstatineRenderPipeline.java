package yt.ren.astatine.backend;

import com.mojang.renderpearl.api.pipeline.BindGroupLayout;
import com.mojang.renderpearl.api.pipeline.RenderPipeline;
import com.mojang.renderpearl.api.pipeline.ShaderSource;
import com.mojang.renderpearl.api.pipeline.ShaderStage;
import com.mojang.renderpearl.api.pipeline.UniformType;
import com.mojang.renderpearl.backend.api.BackendRenderPipeline;

import java.util.ArrayList;
import java.util.List;

public final class AstatineRenderPipeline implements BackendRenderPipeline {
	private static final int VERTEX_STAGE = 0;
	private static final int FRAGMENT_STAGE = 4;
	private final RenderPipeline pipeline;
	private final List<BindGroupLayout.UniformDescription> uniforms = new ArrayList<>();
	private long handle;

	public AstatineRenderPipeline(RenderPipeline pipeline, ShaderSource source) {
		if (source == null) {
			throw new IllegalArgumentException("no shader source for render pipeline " + pipeline.getLocation());
		}

		this.pipeline = pipeline;

		for (var group : pipeline.getBindGroupLayouts()) {
			this.uniforms.addAll(group.uniforms());
		}

		long vertex = BackendNatives.shaderCompile(VERTEX_STAGE, pipeline.getLocation() + ".vsh", this.prepare(source.get(pipeline,
			ShaderStage.VERTEX)));

		try {
			long fragment = BackendNatives.shaderCompile(FRAGMENT_STAGE, pipeline.getLocation() + ".fsh",
				this.prepare(source.get(pipeline, ShaderStage.FRAGMENT)));

			try {
				var description = new Scratch(1024);
				this.describe(description, vertex, fragment);
				this.handle = BackendNatives.programCreate(description.address());
			} finally {
				BackendNatives.shaderRelease(fragment);
			}
		} finally {
			BackendNatives.shaderRelease(vertex);
		}
	}

	private String prepare(String source) {
		if (source == null) {
			throw new IllegalArgumentException("missing shader source for render pipeline " + this.pipeline.getLocation());
		}

		String defines = this.pipeline.getShaderDefines() == null ? "" : this.pipeline.getShaderDefines().asSourceDirectives();

		if (defines.isEmpty()) {
			return source;
		}

		int version = source.indexOf("#version");
		int line = version < 0 ? 0 : source.indexOf('\n', version) + 1;

		if (version >= 0 && line == 0) {
			return source + "\n" + defines + "\n";
		}

		return source.substring(0, line) + defines + "\n" + source.substring(line);
	}

	private void describe(Scratch scratch, long vertex, long fragment) {
		var formats = this.pipeline.getVertexFormatBindings();
		int attributes = 0;
		int bindings = 0;

		for (var format : formats) {
			if (format != null) {
				attributes += format.getElements().size();
				bindings++;
			}
		}

		var depth = this.pipeline.getDepthStencilState();
		var colors = this.pipeline.getColorTargetStates();
		scratch.putLong(vertex).putLong(fragment).putInt(this.uniforms.size()).putInt(attributes).putInt(bindings).putInt(colors.size())
			.putInt(this.pipeline.getPolygonMode().ordinal()).putInt(this.pipeline.isCull() ? 1 : 0)
			.putInt(this.pipeline.getPrimitiveTopology().ordinal()).putInt(depth != null ? 1 : 0)
			.putInt(depth != null && depth.writeDepth() ? 1 : 0).putInt(depth != null ? depth.depthTest().ordinal() : 0)
			.putFloat(depth != null ? depth.depthBiasConstant() : 0.0f).putFloat(depth != null ? depth.depthBiasScaleFactor() : 0.0f);

		for (int i = 0; i < this.uniforms.size(); i++) {
			int type = switch (this.uniforms.get(i).type()) {
				case COMBINED_IMAGE_SAMPLER -> 0;
				case UNIFORM_BUFFER -> 1;
				case TEXEL_BUFFER -> 2;
			};
			scratch.putInt(i).putInt(type);
		}

		int location = 0;

		for (int binding = 0; binding < formats.size(); binding++) {
			var format = formats.get(binding);

			if (format == null) {
				continue;
			}

			for (var element : format.getElements()) {
				scratch.putInt(location++).putInt(binding).putInt(element.format().ordinal()).putInt(element.offset());
			}
		}

		for (int binding = 0; binding < formats.size(); binding++) {
			var format = formats.get(binding);

			if (format != null) {
				scratch.putInt(binding).putInt(format.getVertexSize()).putInt(format.getStepRate() == 0 ? 0 : 1)
					.putInt(Math.max(1, format.getStepRate()));
			}
		}

		for (var color : colors) {
			var blend = color == null ? null : color.blendFunction().orElse(null);
			scratch.putInt(color == null ? 15 : color.writeMask()).putInt(blend != null ? 1 : 0)
				.putInt(blend != null ? blend.color().sourceFactor().ordinal() : 0).putInt(blend != null ? blend.color().destFactor().ordinal() : 0)
				.putInt(blend != null ? blend.color().op().ordinal() : 0).putInt(blend != null ? blend.alpha().sourceFactor().ordinal() : 0)
				.putInt(blend != null ? blend.alpha().destFactor().ordinal() : 0).putInt(blend != null ? blend.alpha().op().ordinal() : 0);
		}
	}

	public RenderPipeline pipeline() {
		return this.pipeline;
	}

	public List<BindGroupLayout.UniformDescription> uniforms() {
		return this.uniforms;
	}

	public long handle() {
		if (this.handle == 0L) {
			throw new IllegalStateException("render pipeline is closed: " + this.pipeline.getLocation());
		}

		return this.handle;
	}

	public UniformType uniformType(int index) {
		return this.uniforms.get(index).type();
	}

	@Override
	public boolean isClosed() {
		return this.handle == 0L;
	}

	@Override
	public void close() {
		if (this.handle != 0L) {
			BackendNatives.programRelease(this.handle);
			this.handle = 0L;
		}
	}
}
