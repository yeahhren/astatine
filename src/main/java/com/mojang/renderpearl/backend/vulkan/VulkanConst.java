package com.mojang.renderpearl.backend.vulkan;

import com.mojang.renderpearl.api.GpuFormat;
import com.mojang.renderpearl.api.buffers.GpuBuffer;
import com.mojang.renderpearl.api.pipeline.BlendFactor;
import com.mojang.renderpearl.api.pipeline.BlendOp;
import com.mojang.renderpearl.api.pipeline.ColorTargetState;
import com.mojang.renderpearl.api.pipeline.CompareOp;
import com.mojang.renderpearl.api.pipeline.PolygonMode;
import com.mojang.renderpearl.api.pipeline.PrimitiveTopology;
import com.mojang.renderpearl.api.textures.AddressMode;
import com.mojang.renderpearl.api.textures.FilterMode;
import com.mojang.renderpearl.api.textures.GpuTexture;
import org.lwjgl.vulkan.VK10;

public final class VulkanConst {
	private VulkanConst() {
	}

	public static int textureUsageToVk(int usage, GpuFormat format) {
		int result = 0;

		if ((usage & GpuTexture.USAGE_RENDER_ATTACHMENT) != 0) {
			if (format.hasColorAspect()) {
				result |= VK10.VK_IMAGE_USAGE_COLOR_ATTACHMENT_BIT;
			}

			if (format.hasDepthAspect()) {
				result |= VK10.VK_IMAGE_USAGE_DEPTH_STENCIL_ATTACHMENT_BIT;
			}
		}

		if ((usage & GpuTexture.USAGE_TEXTURE_BINDING) != 0) {
			result |= VK10.VK_IMAGE_USAGE_SAMPLED_BIT;
		}

		if ((usage & GpuTexture.USAGE_COPY_DST) != 0) {
			result |= VK10.VK_IMAGE_USAGE_TRANSFER_DST_BIT;
		}

		if ((usage & GpuTexture.USAGE_COPY_SRC) != 0) {
			result |= VK10.VK_IMAGE_USAGE_TRANSFER_SRC_BIT;
		}

		return result;
	}

	public static int bufferUsageToVk(int usage) {
		int result = 0;

		if ((usage & GpuBuffer.USAGE_COPY_DST) != 0) {
			result |= VK10.VK_BUFFER_USAGE_TRANSFER_DST_BIT;
		}

		if ((usage & GpuBuffer.USAGE_COPY_SRC) != 0) {
			result |= VK10.VK_BUFFER_USAGE_TRANSFER_SRC_BIT;
		}

		if ((usage & GpuBuffer.USAGE_VERTEX) != 0) {
			result |= VK10.VK_BUFFER_USAGE_VERTEX_BUFFER_BIT;
		}

		if ((usage & GpuBuffer.USAGE_INDEX) != 0) {
			result |= VK10.VK_BUFFER_USAGE_INDEX_BUFFER_BIT;
		}

		if ((usage & GpuBuffer.USAGE_UNIFORM) != 0) {
			result |= VK10.VK_BUFFER_USAGE_UNIFORM_BUFFER_BIT;
		}

		if ((usage & GpuBuffer.USAGE_UNIFORM_TEXEL_BUFFER) != 0) {
			result |= VK10.VK_BUFFER_USAGE_UNIFORM_TEXEL_BUFFER_BIT;
		}

		if ((usage & GpuBuffer.USAGE_INDIRECT_PARAMETERS) != 0) {
			result |= VK10.VK_BUFFER_USAGE_INDIRECT_BUFFER_BIT;
		}

		return result;
	}

	public static int formatAspectMask(GpuFormat format) {
		int result = 0;

		if (format.hasColorAspect()) {
			result |= VK10.VK_IMAGE_ASPECT_COLOR_BIT;
		}

		if (format.hasDepthAspect()) {
			result |= VK10.VK_IMAGE_ASPECT_DEPTH_BIT;
		}

		if (format.hasStencilAspect()) {
			result |= VK10.VK_IMAGE_ASPECT_STENCIL_BIT;
		}

		return result;
	}

	public static int toVk(AddressMode mode) {
		return switch (mode) {
			case REPEAT -> VK10.VK_SAMPLER_ADDRESS_MODE_REPEAT;
			case CLAMP_TO_EDGE -> VK10.VK_SAMPLER_ADDRESS_MODE_CLAMP_TO_EDGE;
		};
	}

	public static int toVk(FilterMode filter) {
		return switch (filter) {
			case NEAREST -> VK10.VK_FILTER_NEAREST;
			case LINEAR -> VK10.VK_FILTER_LINEAR;
		};
	}

	public static int toVk(GpuFormat format) {
		return switch (format) {
			case R8_UNORM -> VK10.VK_FORMAT_R8_UNORM;
			case R8_SNORM -> VK10.VK_FORMAT_R8_SNORM;
			case RG8_UNORM -> VK10.VK_FORMAT_R8G8_UNORM;
			case RG8_SNORM -> VK10.VK_FORMAT_R8G8_SNORM;
			case RGB8_UNORM -> VK10.VK_FORMAT_R8G8B8_UNORM;
			case RGB8_SNORM -> VK10.VK_FORMAT_R8G8B8_SNORM;
			case RGBA8_UNORM -> VK10.VK_FORMAT_R8G8B8A8_UNORM;
			case RGBA8_SNORM -> VK10.VK_FORMAT_R8G8B8A8_SNORM;
			case R16_UNORM -> VK10.VK_FORMAT_R16_UNORM;
			case R16_SNORM -> VK10.VK_FORMAT_R16_SNORM;
			case RG16_UNORM -> VK10.VK_FORMAT_R16G16_UNORM;
			case RG16_SNORM -> VK10.VK_FORMAT_R16G16_SNORM;
			case RGB16_UNORM -> VK10.VK_FORMAT_R16G16B16_UNORM;
			case RGB16_SNORM -> VK10.VK_FORMAT_R16G16B16_SNORM;
			case RGBA16_UNORM -> VK10.VK_FORMAT_R16G16B16A16_UNORM;
			case RGBA16_SNORM -> VK10.VK_FORMAT_R16G16B16A16_SNORM;
			case R8_UINT -> VK10.VK_FORMAT_R8_UINT;
			case R8_SINT -> VK10.VK_FORMAT_R8_SINT;
			case RG8_UINT -> VK10.VK_FORMAT_R8G8_UINT;
			case RG8_SINT -> VK10.VK_FORMAT_R8G8_SINT;
			case RGB8_UINT -> VK10.VK_FORMAT_R8G8B8_UINT;
			case RGB8_SINT -> VK10.VK_FORMAT_R8G8B8_SINT;
			case RGBA8_UINT -> VK10.VK_FORMAT_R8G8B8A8_UINT;
			case RGBA8_SINT -> VK10.VK_FORMAT_R8G8B8A8_SINT;
			case R16_UINT -> VK10.VK_FORMAT_R16_UINT;
			case R16_SINT -> VK10.VK_FORMAT_R16_SINT;
			case RG16_UINT -> VK10.VK_FORMAT_R16G16_UINT;
			case RG16_SINT -> VK10.VK_FORMAT_R16G16_SINT;
			case RGB16_UINT -> VK10.VK_FORMAT_R16G16B16_UINT;
			case RGB16_SINT -> VK10.VK_FORMAT_R16G16B16_SINT;
			case RGBA16_UINT -> VK10.VK_FORMAT_R16G16B16A16_UINT;
			case RGBA16_SINT -> VK10.VK_FORMAT_R16G16B16A16_SINT;
			case R32_UINT -> VK10.VK_FORMAT_R32_UINT;
			case R32_SINT -> VK10.VK_FORMAT_R32_SINT;
			case RG32_UINT -> VK10.VK_FORMAT_R32G32_UINT;
			case RG32_SINT -> VK10.VK_FORMAT_R32G32_SINT;
			case RGB32_UINT -> VK10.VK_FORMAT_R32G32B32_UINT;
			case RGB32_SINT -> VK10.VK_FORMAT_R32G32B32_SINT;
			case RGBA32_UINT -> VK10.VK_FORMAT_R32G32B32A32_UINT;
			case RGBA32_SINT -> VK10.VK_FORMAT_R32G32B32A32_SINT;
			case R16_FLOAT -> VK10.VK_FORMAT_R16_SFLOAT;
			case RG16_FLOAT -> VK10.VK_FORMAT_R16G16_SFLOAT;
			case RGB16_FLOAT -> VK10.VK_FORMAT_R16G16B16_SFLOAT;
			case RGBA16_FLOAT -> VK10.VK_FORMAT_R16G16B16A16_SFLOAT;
			case R32_FLOAT -> VK10.VK_FORMAT_R32_SFLOAT;
			case RG32_FLOAT -> VK10.VK_FORMAT_R32G32_SFLOAT;
			case RGB32_FLOAT -> VK10.VK_FORMAT_R32G32B32_SFLOAT;
			case RGBA32_FLOAT -> VK10.VK_FORMAT_R32G32B32A32_SFLOAT;
			case RGB10A2_UNORM -> VK10.VK_FORMAT_A2B10G10R10_UNORM_PACK32;
			case RGB10A2_UINT -> VK10.VK_FORMAT_A2B10G10R10_UINT_PACK32;
			case RG11B10_FLOAT -> VK10.VK_FORMAT_B10G11R11_UFLOAT_PACK32;
			case D32_FLOAT -> VK10.VK_FORMAT_D32_SFLOAT;
			case D32_FLOAT_S8_UINT -> VK10.VK_FORMAT_D32_SFLOAT_S8_UINT;
			case D24_UNORM_S8_UINT -> VK10.VK_FORMAT_D24_UNORM_S8_UINT;
			case D16_UNORM -> VK10.VK_FORMAT_D16_UNORM;
			case S8_UINT -> VK10.VK_FORMAT_S8_UINT;
			case IRIS_RGB565_UNORM -> VK10.VK_FORMAT_R5G6B5_UNORM_PACK16;
			case IRIS_RGB5A1_UNORM -> VK10.VK_FORMAT_A1R5G5B5_UNORM_PACK16;
			case IRIS_RGB9E5_FLOAT -> VK10.VK_FORMAT_E5B9G9R9_UFLOAT_PACK32;
			case BGRA8_UNORM -> VK10.VK_FORMAT_B8G8R8A8_UNORM;
		};
	}

	public static GpuFormat fromVk(int format) {
		for (GpuFormat candidate : GpuFormat.values()) {
			if (toVk(candidate) == format) {
				return candidate;
			}
		}

		throw new IllegalArgumentException("unsupported Vulkan format " + format);
	}

	public static int toVk(BlendFactor factor) {
		return switch (factor) {
			case CONSTANT_ALPHA -> VK10.VK_BLEND_FACTOR_CONSTANT_ALPHA;
			case CONSTANT_COLOR -> VK10.VK_BLEND_FACTOR_CONSTANT_COLOR;
			case ONE_MINUS_CONSTANT_ALPHA -> VK10.VK_BLEND_FACTOR_ONE_MINUS_CONSTANT_ALPHA;
			case ONE_MINUS_CONSTANT_COLOR -> VK10.VK_BLEND_FACTOR_ONE_MINUS_CONSTANT_COLOR;
			case DST_ALPHA -> VK10.VK_BLEND_FACTOR_DST_ALPHA;
			case DST_COLOR -> VK10.VK_BLEND_FACTOR_DST_COLOR;
			case ONE -> VK10.VK_BLEND_FACTOR_ONE;
			case ONE_MINUS_DST_ALPHA -> VK10.VK_BLEND_FACTOR_ONE_MINUS_DST_ALPHA;
			case ONE_MINUS_DST_COLOR -> VK10.VK_BLEND_FACTOR_ONE_MINUS_DST_COLOR;
			case ONE_MINUS_SRC_ALPHA -> VK10.VK_BLEND_FACTOR_ONE_MINUS_SRC_ALPHA;
			case ONE_MINUS_SRC_COLOR -> VK10.VK_BLEND_FACTOR_ONE_MINUS_SRC_COLOR;
			case SRC_ALPHA -> VK10.VK_BLEND_FACTOR_SRC_ALPHA;
			case SRC_ALPHA_SATURATE -> VK10.VK_BLEND_FACTOR_SRC_ALPHA_SATURATE;
			case SRC_COLOR -> VK10.VK_BLEND_FACTOR_SRC_COLOR;
			case ZERO -> VK10.VK_BLEND_FACTOR_ZERO;
		};
	}

	public static int toVk(BlendOp op) {
		return switch (op) {
			case ADD -> VK10.VK_BLEND_OP_ADD;
			case SUBTRACT -> VK10.VK_BLEND_OP_SUBTRACT;
			case REVERSE_SUBTRACT -> VK10.VK_BLEND_OP_REVERSE_SUBTRACT;
			case MIN -> VK10.VK_BLEND_OP_MIN;
			case MAX -> VK10.VK_BLEND_OP_MAX;
		};
	}

	public static int toVk(CompareOp op) {
		return switch (op) {
			case ALWAYS_PASS -> VK10.VK_COMPARE_OP_ALWAYS;
			case LESS_THAN -> VK10.VK_COMPARE_OP_LESS;
			case LESS_THAN_OR_EQUAL -> VK10.VK_COMPARE_OP_LESS_OR_EQUAL;
			case EQUAL -> VK10.VK_COMPARE_OP_EQUAL;
			case NOT_EQUAL -> VK10.VK_COMPARE_OP_NOT_EQUAL;
			case GREATER_THAN_OR_EQUAL -> VK10.VK_COMPARE_OP_GREATER_OR_EQUAL;
			case GREATER_THAN -> VK10.VK_COMPARE_OP_GREATER;
			case NEVER_PASS -> VK10.VK_COMPARE_OP_NEVER;
		};
	}

	public static int toVk(PolygonMode mode) {
		return switch (mode) {
			case FILL -> VK10.VK_POLYGON_MODE_FILL;
			case WIREFRAME -> VK10.VK_POLYGON_MODE_LINE;
		};
	}

	public static int toVk(PrimitiveTopology topology) {
		return switch (topology) {
			case LINES, TRIANGLES, QUADS -> VK10.VK_PRIMITIVE_TOPOLOGY_TRIANGLE_LIST;
			case DEBUG_LINES -> VK10.VK_PRIMITIVE_TOPOLOGY_LINE_LIST;
			case DEBUG_LINE_STRIP -> VK10.VK_PRIMITIVE_TOPOLOGY_LINE_STRIP;
			case POINTS -> VK10.VK_PRIMITIVE_TOPOLOGY_POINT_LIST;
			case TRIANGLE_STRIP -> VK10.VK_PRIMITIVE_TOPOLOGY_TRIANGLE_STRIP;
			case TRIANGLE_FAN -> VK10.VK_PRIMITIVE_TOPOLOGY_TRIANGLE_FAN;
		};
	}

	public static int toVk(ColorTargetState state) {
		int result = 0;

		if (state.writeRed()) {
			result |= VK10.VK_COLOR_COMPONENT_R_BIT;
		}

		if (state.writeGreen()) {
			result |= VK10.VK_COLOR_COMPONENT_G_BIT;
		}

		if (state.writeBlue()) {
			result |= VK10.VK_COLOR_COMPONENT_B_BIT;
		}

		if (state.writeAlpha()) {
			result |= VK10.VK_COLOR_COMPONENT_A_BIT;
		}

		return result;
	}
}
