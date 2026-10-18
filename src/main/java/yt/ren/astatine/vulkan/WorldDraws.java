package yt.ren.astatine.vulkan;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.renderpearl.api.GpuFormat;
import com.mojang.renderpearl.api.buffers.GpuBuffer;
import com.mojang.renderpearl.api.buffers.GpuBufferSlice;
import com.mojang.renderpearl.api.pipeline.BindGroupLayout;
import com.mojang.renderpearl.api.pipeline.BlendFactor;
import com.mojang.renderpearl.api.pipeline.BlendFunction;
import com.mojang.renderpearl.api.pipeline.ColorTargetState;
import com.mojang.renderpearl.api.pipeline.CompareOp;
import com.mojang.renderpearl.api.pipeline.DepthStencilState;
import com.mojang.renderpearl.api.pipeline.PolygonMode;
import com.mojang.renderpearl.api.pipeline.PrimitiveTopology;
import com.mojang.renderpearl.api.pipeline.RenderPipeline;
import com.mojang.renderpearl.api.pipeline.ShaderDefines;
import com.mojang.renderpearl.api.pipeline.UniformType;
import com.mojang.renderpearl.api.textures.AddressMode;
import com.mojang.renderpearl.api.textures.FilterMode;
import com.mojang.renderpearl.api.textures.GpuSampler;
import com.mojang.renderpearl.api.textures.GpuTextureView;
import com.mojang.renderpearl.api.vertex.VertexFormat;
import com.mojang.renderpearl.backend.vulkan.VulkanConst;
import com.mojang.renderpearl.backend.vulkan.VulkanDevice;
import com.mojang.renderpearl.backend.vulkan.VulkanGpuBuffer;
import com.mojang.renderpearl.backend.vulkan.VulkanGpuSampler;
import com.mojang.renderpearl.frontend.FrontendRenderPass;
import com.mojang.renderpearl.frontend.FrontendRenderPipeline;
import com.mojang.renderpearl.util.TextureViewAndSampler;
import net.irisshaders.iris.Iris;
import net.irisshaders.iris.gl.state.FogMode;
import net.irisshaders.iris.pipeline.IrisPipelines;
import net.irisshaders.iris.pipeline.IrisRenderPipeline;
import net.irisshaders.iris.pipeline.IrisRenderingPipeline;
import net.irisshaders.iris.pipeline.WorldRenderPass;
import net.irisshaders.iris.pipeline.WorldRenderingPhase;
import net.irisshaders.iris.pipeline.programs.ExtendedShader;
import net.irisshaders.iris.shadows.ShadowRenderingState;
import net.irisshaders.iris.uniforms.CameraUniforms;
import net.minecraft.client.Minecraft;
import net.minecraft.client.render.block.BlockLayer;
import org.joml.Vector3d;
import org.joml.Vector3f;
import org.joml.Vector3i;
import org.lwjgl.opengl.GL11;
import yt.ren.astatine.Native;
import yt.ren.astatine.render.DrawState;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalDouble;

public final class WorldDraws {
	private static final List<BindGroupLayout> BIND_GROUPS = List.of(new BindGroupLayout(List.of(
		new BindGroupLayout.UniformDescription("Sampler0", UniformType.COMBINED_IMAGE_SAMPLER),
		new BindGroupLayout.UniformDescription("Sampler1", UniformType.COMBINED_IMAGE_SAMPLER),
		new BindGroupLayout.UniformDescription("Sampler2", UniformType.COMBINED_IMAGE_SAMPLER))));
	private static final Map<Integer, List<VertexFormat>> FORMATS = new HashMap<>();
	private static final Map<RenderPipeline, RenderPipeline> PIPELINES = new HashMap<>();
	private static final Map<RenderPipeline, FrontendRenderPipeline> FRONTENDS = new HashMap<>();
	private static final Map<Long, GpuSampler> SAMPLERS = new HashMap<>();
	private static GpuSampler fallbackSampler;
	private static final Map<String, Object> RESOURCES = new HashMap<>();
	private static FrontendRenderPass pass;
	private static long[] drawBuffers;
	private static GpuBufferSlice environments;
	private static GpuBufferSlice records;
	private static GpuBufferSlice sections;
	private static GpuBufferSlice globals;
	private static long globalsFrame = -1L;

	private WorldDraws() {
	}

	public static void open(FrontendRenderPass renderPass) {
		pass = renderPass;
	}

	public static void close(FrontendRenderPass renderPass) {
		if (pass == renderPass) {
			pass = null;
		}
	}

	public static boolean active() {
		return pass != null && pass.backend() instanceof WorldRenderPass;
	}

	public static void draw(int layout, int mode, long source, int count) {
		if (!active()) {
			Native.draw(layout, mode, source, count);
		} else if (bind(IrisPipelines.IMMEDIATE, formats(layout), mode)) {
			Native.draw(layout, mode, source, count);
		}
	}

	public static void drawMesh(long mesh, int mode) {
		if (!active()) {
			Native.drawMesh(mesh, mode);
		} else if (bind(IrisPipelines.IMMEDIATE, formats(Native.meshLayout(mesh)), mode)) {
			Native.drawMesh(mesh, mode);
		}
	}

	public static int terrain(BlockLayer layer) {
		if (!active()) {
			return Native.terrainDraw(layer.ordinal());
		}

		String location = switch (layer) {
			case SOLID -> IrisPipelines.TERRAIN_SOLID;
			case CUTOUT_MIPPED -> IrisPipelines.TERRAIN_CUTOUT_MIPPED;
			case CUTOUT -> IrisPipelines.TERRAIN_CUTOUT;
			case TRANSLUCENT -> IrisPipelines.TERRAIN_TRANSLUCENT;
		};

		if (!bind(location, formats(Native.terrainLayout()), GL11.GL_QUADS)) {
			return 0;
		}

		return Native.terrainDraw(layer.ordinal());
	}

	private static List<VertexFormat> formats(int layout) {
		return FORMATS.computeIfAbsent(layout, WorldDraws::createFormats);
	}

	private static List<VertexFormat> createFormats(int layout) {
		int[] description = Native.layoutAttributes(layout);
		boolean terrain = description[0] != 0;
		boolean split = description[1] != 0;
		int stride = description[2];
		int attributeStride = description[3];
		boolean tangents = description[4] != 0;
		int tangentOffset = description[5];
		int count = description[6];

		if (terrain) {
			var builder = VertexFormat.builder(0).addAttributeAt("TerrainVertex", 0, GpuFormat.RGBA32_UINT);

			if (stride >= 32) {
				builder.addAttributeAt("TerrainExtra", 16, GpuFormat.RGBA32_UINT);
			}

			if (stride >= 48) {
				builder.addAttributeAt("at_midBlock", 32, GpuFormat.RGBA8_SNORM).addAttributeAt("TerrainOcclusion", 36, GpuFormat.R8_UNORM);
			}

			return List.of(builder.build(stride));
		}

		var first = VertexFormat.builder(0);
		var second = VertexFormat.builder(0);

		for (int i = 0; i < count; i++) {
			int location = description[7 + i * 4];
			int binding = description[8 + i * 4];
			GpuFormat format = VulkanConst.fromVk(description[9 + i * 4]);
			int offset = description[10 + i * 4];
			String name = switch (location) {
				case 0 -> "Position";
				case 1 -> "Color";
				case 2 -> "UV0";
				case 3 -> "UV2";
				case 4 -> "Normal";
				default -> null;
			};

			if (name != null) {
				(binding == 0 ? first : second).addAttributeAt(name, offset, format);
			}
		}

		if (tangents && split) {
			second.addAttributeAt("at_tangent", tangentOffset, GpuFormat.RGBA8_SNORM).addAttributeAt("mc_midTexCoord", tangentOffset + 4,
				GpuFormat.RG32_FLOAT);
		}

		var formats = new ArrayList<VertexFormat>();
		formats.add(first.build(split ? 12 : stride));

		if (split && attributeStride > 0) {
			formats.add(second.build(attributeStride));
		}

		return List.copyOf(formats);
	}

	private static CompareOp compare(int function) {
		return switch (function) {
			case GL11.GL_NEVER -> CompareOp.NEVER_PASS;
			case GL11.GL_LESS -> CompareOp.LESS_THAN;
			case GL11.GL_EQUAL -> CompareOp.EQUAL;
			case GL11.GL_LEQUAL -> CompareOp.LESS_THAN_OR_EQUAL;
			case GL11.GL_GREATER -> CompareOp.GREATER_THAN;
			case GL11.GL_NOTEQUAL -> CompareOp.NOT_EQUAL;
			case GL11.GL_GEQUAL -> CompareOp.GREATER_THAN_OR_EQUAL;
			default -> CompareOp.ALWAYS_PASS;
		};
	}

	private static BlendFactor factor(int factor) {
		return switch (factor) {
			case GL11.GL_ZERO -> BlendFactor.ZERO;
			case GL11.GL_SRC_COLOR -> BlendFactor.SRC_COLOR;
			case GL11.GL_ONE_MINUS_SRC_COLOR -> BlendFactor.ONE_MINUS_SRC_COLOR;
			case GL11.GL_SRC_ALPHA -> BlendFactor.SRC_ALPHA;
			case GL11.GL_ONE_MINUS_SRC_ALPHA -> BlendFactor.ONE_MINUS_SRC_ALPHA;
			case GL11.GL_DST_ALPHA -> BlendFactor.DST_ALPHA;
			case GL11.GL_ONE_MINUS_DST_ALPHA -> BlendFactor.ONE_MINUS_DST_ALPHA;
			case GL11.GL_DST_COLOR -> BlendFactor.DST_COLOR;
			case GL11.GL_ONE_MINUS_DST_COLOR -> BlendFactor.ONE_MINUS_DST_COLOR;
			case GL11.GL_SRC_ALPHA_SATURATE -> BlendFactor.SRC_ALPHA_SATURATE;
			case 0x8001 -> BlendFactor.CONSTANT_COLOR;
			case 0x8002 -> BlendFactor.ONE_MINUS_CONSTANT_COLOR;
			case 0x8003 -> BlendFactor.CONSTANT_ALPHA;
			case 0x8004 -> BlendFactor.ONE_MINUS_CONSTANT_ALPHA;
			default -> BlendFactor.ONE;
		};
	}

	private static PrimitiveTopology topology(int mode) {
		return switch (mode) {
			case GL11.GL_POINTS -> PrimitiveTopology.POINTS;
			case GL11.GL_LINES -> PrimitiveTopology.DEBUG_LINES;
			case GL11.GL_LINE_STRIP, GL11.GL_LINE_LOOP -> PrimitiveTopology.DEBUG_LINE_STRIP;
			case GL11.GL_TRIANGLE_STRIP -> PrimitiveTopology.TRIANGLE_STRIP;
			case GL11.GL_TRIANGLE_FAN -> PrimitiveTopology.TRIANGLE_FAN;
			case GL11.GL_QUADS -> PrimitiveTopology.QUADS;
			default -> PrimitiveTopology.TRIANGLES;
		};
	}

	private static RenderPipeline pipeline(String location, List<VertexFormat> formats, int mode) {
		var defines = new HashMap<String, String>();
		boolean textured = DrawState.textureEnabled(0) && DrawState.boundTexture(0) != 0L;
		boolean lightmapped = DrawState.textureEnabled(1) && DrawState.boundTexture(1) != 0L;

		if (textured) {
			defines.put("TEXTURED", "1");
		}

		if (lightmapped) {
			defines.put("LIGHTMAP", "1");
		}

		if (formats.stream().anyMatch(format -> format.contains("Color"))) {
			defines.put("COLOR", "1");
		}

		if (formats.stream().anyMatch(format -> format.contains("Normal"))) {
			defines.put("NORMAL", "1");
		}

		if (DrawState.blend()) {
			defines.put("BLEND", "1");
		}

		if (DrawState.alphaTest()) {
			defines.put("ALPHA_CUTOUT", "1");
		}

		var depth = DrawState.depthTest() ? new DepthStencilState(compare(DrawState.depthFunction()), DrawState.depthMask(),
			DrawState.polygonOffset() ? DrawState.offsetFactor() : 0.0f, DrawState.polygonOffset() ? DrawState.offsetUnits() : 0.0f)
			: new DepthStencilState(CompareOp.ALWAYS_PASS, false);
		var blend = DrawState.blend() ? Optional.of(new BlendFunction(factor(DrawState.sourceColor()), factor(DrawState.destinationColor()),
			factor(DrawState.sourceAlpha()), factor(DrawState.destinationAlpha()))) : Optional.<BlendFunction>empty();
		var color = new ColorTargetState(blend, GpuFormat.RGBA8_UNORM, DrawState.colorMask());
		var created = new RenderPipeline(location, formats, depth, DrawState.cull() && DrawState.cullFace() == GL11.GL_BACK, List.of(color),
			PolygonMode.FILL, topology(mode), BIND_GROUPS, new ShaderDefines(defines));
		return PIPELINES.computeIfAbsent(created, key -> key);
	}

	private static GpuSampler sampler(long texture) {
		long handle = texture == 0L ? 0L : Native.textureSampler(texture);

		if (handle == 0L) {
			if (fallbackSampler == null) {
				fallbackSampler = RenderSystem.getDevice()
					.createSampler(AddressMode.REPEAT, AddressMode.REPEAT, FilterMode.NEAREST, FilterMode.NEAREST, 1, OptionalDouble.of(0.0));
			}

			return fallbackSampler;
		}

		return SAMPLERS.computeIfAbsent(handle, key -> {
			int flags = Native.textureSampling(texture);
			FilterMode filter = (flags & 1) != 0 ? FilterMode.LINEAR : FilterMode.NEAREST;
			AddressMode u = (flags & 4) != 0 ? AddressMode.CLAMP_TO_EDGE : AddressMode.REPEAT;
			AddressMode v = (flags & 8) != 0 ? AddressMode.CLAMP_TO_EDGE : AddressMode.REPEAT;
			return new VulkanGpuSampler(VulkanDevice.get(), key, filter, filter, u, v);
		});
	}

	private static void updateBuffers() {
		long[] buffers = Native.drawBuffers();

		if (drawBuffers != null && buffers[0] == drawBuffers[0] && buffers[2] == drawBuffers[2] && buffers[4] == drawBuffers[4]) {
			return;
		}

		drawBuffers = buffers;
		environments = new VulkanGpuBuffer.Foreign(buffers[0], VulkanGpuBuffer.USAGE_STORAGE, buffers[1]).slice();
		records = new VulkanGpuBuffer.Foreign(buffers[2], VulkanGpuBuffer.USAGE_STORAGE, buffers[3]).slice();
		sections = new VulkanGpuBuffer.Foreign(buffers[4], VulkanGpuBuffer.USAGE_STORAGE, buffers[5]).slice();
	}

	private static GpuBufferSlice globals() {
		long frame = Native.presentedFrames();

		if (globals != null && frame == globalsFrame) {
			return globals;
		}

		Minecraft minecraft = Minecraft.getInstance();
		Vector3d camera = CameraUniforms.getUnshiftedCameraPosition();
		Vector3i block = CameraUniforms.getCameraPositionInt(camera);
		Vector3f offset = CameraUniforms.getCameraPositionFract(camera);
		long time = minecraft.world == null ? 0L : minecraft.world.getTime();
		ByteBuffer data = ByteBuffer.allocateDirect(48).order(ByteOrder.nativeOrder());
		data.putInt(0, block.x).putInt(4, block.y).putInt(8, block.z).putFloat(12, 1.0f);
		data.putFloat(16, offset.x).putFloat(20, offset.y).putFloat(24, offset.z).putFloat(28, (time % 24000L) / 24000.0f);
		data.putFloat(32, minecraft.width).putFloat(36, minecraft.height).putInt(40, 0).putInt(44, 0);
		int alignment = RenderSystem.getDevice().getDeviceInfo().limits().minUniformOffsetAlignment();
		globals = RenderSystem.getDevice().createCommandEncoder().transientMemory().uploadGpu(List.of(data), alignment, GpuBuffer.USAGE_UNIFORM);
		globalsFrame = frame;
		return globals;
	}

	private static boolean bind(String location, List<VertexFormat> formats, int mode) {
		if (!(Iris.getPipelineManager().getPipelineNullable() instanceof IrisRenderingPipeline iris)) {
			return false;
		}

		var source = pipeline(location, formats, mode);
		var key = IrisPipelines.getPipeline(iris, source);

		if (key == null) {
			return false;
		}

		ExtendedShader shader = iris.getShaderMap().getShader(key);

		if (shader == null) {
			return false;
		}

		var frontend = FRONTENDS.computeIfAbsent(source,
			unused -> new FrontendRenderPipeline(location, null, source.getVertexFormatBindings(), Map.of(), List.of(), source.getColorTargetStates(),
				false, 0));
		var override = (IrisRenderPipeline) shader.override(source, frontend).backendRenderPipeline();
		var world = (WorldRenderPass) pass.backend();
		updateBuffers();
		long albedo = DrawState.textureEnabled(0) ? DrawState.boundTexture(0) : 0L;
		long lightmap = DrawState.textureEnabled(1) ? DrawState.boundTexture(1) : 0L;
		GpuTextureView albedoView = albedo != 0L ? NativeTextures.view(albedo) : null;

		if (albedoView == null) {
			albedo = 0L;
			albedoView = iris.getWhitePixel().getTextureView();
		}
		GpuTextureView lightmapView = lightmap != 0L ? NativeTextures.view(lightmap) : null;

		if (lightmapView == null) {
			lightmap = 0L;
			lightmapView = MinecraftTextures.lightmap();
		}
		iris.onSetAlbedoTex(albedoView);
		RESOURCES.clear();
		RESOURCES.put("Sampler0", new TextureViewAndSampler(albedoView, sampler(albedo)));
		RESOURCES.put("Sampler1", new TextureViewAndSampler(iris.getWhitePixel().getTextureView(), sampler(0L)));
		RESOURCES.put("Sampler2", new TextureViewAndSampler(lightmapView, sampler(lightmap)));
		RESOURCES.put("Environments", environments);
		RESOURCES.put("Draws", records);
		RESOURCES.put("Sections", sections);
		RESOURCES.put("Globals", globals());
		boolean fogDisabled = ShadowRenderingState.areShadowsCurrentlyBeingRendered() || iris.getPhase().ordinal() >= WorldRenderingPhase.SKY.ordinal()
			&& iris.getPhase().ordinal() <= WorldRenderingPhase.VOID.ordinal();
		Native.irisDrawState(iris.getPhase().ordinal(), (key.getFogMode() == FogMode.OFF ? 1 : 0) | (fogDisabled ? 2 : 0));
		VulkanDevice.get().createCommandEncoder().commandBuffer();
		world.setUniforms(RESOURCES);
		world.setPipeline(override);
		world.prepareDraw();
		return true;
	}
}
