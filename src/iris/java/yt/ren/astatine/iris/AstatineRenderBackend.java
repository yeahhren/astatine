package yt.ren.astatine.iris;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.renderpearl.api.GpuFormat;
import com.mojang.renderpearl.api.buffers.GpuBuffer;
import com.mojang.renderpearl.api.commands.RenderPass.RenderArea;
import com.mojang.renderpearl.api.commands.RenderPassDescriptor;
import com.mojang.renderpearl.api.textures.GpuTexture;
import com.mojang.renderpearl.api.textures.GpuTextureView;
import com.mojang.renderpearl.backend.api.CommandEncoderBackend;
import com.mojang.renderpearl.backend.api.RenderPassBackend;
import com.mojang.renderpearl.frontend.FrontendCommandEncoder;
import net.irisshaders.iris.backend.ComputePipeline;
import net.irisshaders.iris.backend.GraphicsShader;
import net.irisshaders.iris.backend.IrisImage;
import net.irisshaders.iris.backend.NonRenderCommands;
import net.irisshaders.iris.backend.PackResources;
import net.irisshaders.iris.backend.RenderBackend;
import net.irisshaders.iris.backend.SyncCommand;
import net.irisshaders.iris.backend.WorldPassCommands;
import net.irisshaders.iris.gl.texture.TextureType;
import net.irisshaders.iris.pipeline.transform.PatchedShader;
import net.irisshaders.iris.samplers.PackSamplers;
import net.irisshaders.iris.samplers.ProgramResources;
import net.irisshaders.iris.shaderpack.FilledIndirectPointer;
import net.irisshaders.iris.targets.RenderTarget;
import net.irisshaders.iris.uniforms.buffer.BufferUniformHolder;
import org.joml.Vector3i;
import org.joml.Vector4f;
import yt.ren.astatine.backend.AstatineBuffer;
import yt.ren.astatine.backend.AstatineCommandEncoder;
import yt.ren.astatine.backend.AstatineTexture;
import yt.ren.astatine.backend.AstatineTextureView;
import yt.ren.astatine.backend.BackendNatives;
import yt.ren.astatine.backend.Scratch;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.List;

public class AstatineRenderBackend implements RenderBackend, NonRenderCommands {
	private final Scratch scratch = new Scratch(256);
	private AstatineShaderCache shaderCache;
	private volatile boolean closed;

	public AstatineRenderBackend() {
		BackendNatives.mipmapSources(read("ffx_core.h"), read("ffx_spd.h"), read("spd.glsl"), read("area.glsl"), read("tail.glsl"));
	}

	private static String read(String name) {
		try (var stream = AstatineRenderBackend.class.getResourceAsStream("/assets/iris/shaders/mipmaps/" + name)) {
			if (stream == null) {
				throw new IllegalStateException("missing mipmap shader: " + name);
			}

			return new String(stream.readAllBytes(), StandardCharsets.UTF_8);
		} catch (IOException e) {
			throw new UncheckedIOException(e);
		}
	}

	private static AstatineCommandEncoder encoder() {
		return (AstatineCommandEncoder) ((FrontendCommandEncoder) RenderSystem.getDevice().createCommandEncoder()).backend();
	}

	@Override
	public WorldPassCommands createRenderPass(CommandEncoderBackend encoder, RenderPassDescriptor descriptor) {
		return new AstatineWorldCommands((AstatineCommandEncoder) encoder, descriptor);
	}

	@Override
	public WorldPassCommands adaptRenderPass(RenderPassBackend pass, RenderArea area) {
		return new AstatineWorldCommands(encoder(), pass, false);
	}

	@Override
	public NonRenderCommands commands() {
		return this;
	}

	@Override
	public SyncCommand createSync() {
		return new AstatineSyncCommand();
	}

	@Override
	public void dispatch(ComputePipeline pipeline, Vector3i groups, FilledIndirectPointer indirect) {
		((AstatineComputePipeline) pipeline).dispatch(groups, indirect);
	}

	@Override
	public PackResources createPackResources(PackSamplers pack) {
		return new AstatinePackResources(pack);
	}

	@Override
	public GraphicsShader createGraphicsShader(String name, PatchedShader source, ProgramResources resources, BufferUniformHolder uniforms) {
		return new AstatineGraphicsShader(this.getShaderCache(), name, source, resources, uniforms);
	}

	@Override
	public ComputePipeline createComputePipeline(String name, PatchedShader source, ProgramResources resources, BufferUniformHolder uniforms) {
		return new AstatineComputePipeline(this.getShaderCache(), name, source, resources, uniforms);
	}

	@Override
	public void finishCompilation() {
		if (this.shaderCache != null) {
			this.shaderCache.finishCompilation();
		}
	}

	private AstatineShaderCache getShaderCache() {
		if (this.closed) {
			throw new IllegalStateException("Astatine render backend is closed");
		}

		if (this.shaderCache == null) {
			this.shaderCache = new AstatineShaderCache();
		}

		return this.shaderCache;
	}

	@Override
	public void close() {
		if (this.closed) {
			return;
		}

		this.closed = true;

		if (this.shaderCache != null) {
			this.shaderCache.close();
		}
	}

	@Override
	public IrisImage createImage(String name, TextureType type, GpuFormat format, int width, int height, int depth, boolean storage) {
		return new AstatineImage(name, type, format, width, height, depth, storage);
	}

	@Override
	public GpuBuffer createStorageBuffer(String name, long size, byte[] content) {
		long contentSize = content == null ? 0L : content.length;
		var data = new Scratch((int) Math.max(64, contentSize));

		if (content != null) {
			data.putBytes(ByteBuffer.wrap(content));
		}

		long handle = BackendNatives.storageBufferCreate(name, size, data.address(), contentSize);
		return AstatineBuffer.adopt(handle, size,
			AstatineBuffer.USAGE_STORAGE | GpuBuffer.USAGE_COPY_SRC | GpuBuffer.USAGE_COPY_DST | GpuBuffer.USAGE_INDIRECT_PARAMETERS);
	}

	@Override
	public void retire(Runnable destructor) {
		encoder().queueForDestroy(destructor);
	}

	@Override
	public void generateMipmaps(GpuTexture texture, boolean linear) {
		BackendNatives.generateMipmaps(((AstatineTexture) texture).handle(), linear);
	}

	@Override
	public void copyTexture(GpuTexture source, GpuTexture destination, int width, int height) {
		BackendNatives.copyTextureSynced(((AstatineTexture) source).handle(), ((AstatineTexture) destination).handle(), width, height);
	}

	@Override
	public int getMipmapUsage(GpuFormat format) {
		return BackendNatives.mipmapsSupported(format.ordinal()) ? RenderTarget.USAGE_STORAGE : 0;
	}

	@Override
	public void clearColors(List<GpuTextureView> textures, Vector4f color) {
		if (textures.isEmpty()) {
			return;
		}

		var views = this.scratch.clear();

		for (var view : textures) {
			views.putLong(((AstatineTextureView) view).handle());
		}

		BackendNatives.clearColors(views.address(), textures.size(), color.x, color.y, color.z, color.w);
	}

	@Override
	public void generateDepthMips(GpuTexture texture) {
		if (texture.getFormat() != GpuFormat.D32_FLOAT || texture.isClosed()) {
			throw new IllegalArgumentException("expected live D32 shadow depth");
		}

		BackendNatives.generateDepthMipmaps(((AstatineTexture) texture).handle());
	}
}
