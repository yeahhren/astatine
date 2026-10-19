package net.irisshaders.iris.pipeline.fallback;

import net.irisshaders.iris.gl.state.ShaderAttributeInputs;
import net.irisshaders.iris.pipeline.transform.transformer.DrawDataTransformer;

public class ShaderSynthesizer {
	public static String sodiumVsh() {
		return """
			#version 120
			varying vec4 vertexColor;
			varying vec2 texCoord;
			varying vec2 lightCoord;
			varying vec2 fogDistance;
			varying float fadeFactor;

			void main() {
			    vec3 position = gl_Vertex.xyz;
			    gl_Position = gl_ModelViewProjectionMatrix * gl_Vertex;
			    vertexColor = gl_Color;
			    texCoord = gl_MultiTexCoord0.xy;
			    lightCoord = clamp((gl_MultiTexCoord1.xy + 8.0) / 256.0, vec2(0.5 / 16.0), vec2(15.5 / 16.0));
			    fogDistance = vec2(max(length(position.xz), abs(position.y)), length(position));
			    fadeFactor = mc_chunkFade;
			}
			""";
	}

	public static String sodiumFsh(boolean shadow) {
		StringBuilder shader = new StringBuilder("""
			#version 120
			uniform sampler2D gtexture;
			uniform sampler2D lightmap;
			varying vec4 vertexColor;
			varying vec2 texCoord;
			varying vec2 lightCoord;
			varying vec2 fogDistance;
			varying float fadeFactor;
			""");

		if (!shadow) {
			shader.append("""
				float linearFog(float distance, vec2 range) {
				    if (distance <= range.x) return 0.0;
				    if (distance >= range.y) return 1.0;
				    return (distance - range.x) / (range.y - range.x);
				}
				""");
		}

		shader.append("""
			void main() {
			    vec4 color = texture2D(gtexture, texCoord) * vertexColor * texture2D(lightmap, lightCoord);
			""");

		if (!shadow) {
			shader.append("""
				    float fog = max(linearFog(fogDistance.y, u_EnvironmentFog), linearFog(fogDistance.x, u_RenderFog));
				    fog = max(fog, 1.0 - fadeFactor);
				    color.rgb = mix(color.rgb, u_FogColor.rgb, fog * u_FogColor.a);
				""");
		}

		shader.append("""
			    gl_FragData[0] = color;
			}
			""");

		return shader.toString();
	}

	public static String vsh() {
		return """
			#version 120
			varying vec4 color;
			varying vec4 texcoord;
			varying vec4 lmcoord;
			varying vec3 eye;

			vec4 astatine_lit(vec3 normal, vec3 eye, vec4 color);

			void main() {
			    vec4 position = gl_ModelViewMatrix * gl_Vertex;
			    gl_Position = gl_ProjectionMatrix * position;
			    eye = position.xyz;
			    color = astatine_lit(gl_NormalMatrix * gl_Normal, position.xyz, gl_Color);
			    texcoord = gl_TextureMatrix[0] * gl_MultiTexCoord0;
			    lmcoord = gl_TextureMatrix[1] * gl_MultiTexCoord1;
			}
			""";
	}

	public static String fsh(boolean intensityTex) {
		return """
			#version 120
			uniform sampler2D gtexture;
			uniform sampler2D lightmap;
			varying vec4 color;
			varying vec4 texcoord;
			varying vec4 lmcoord;
			varying vec3 eye;

			vec4 astatine_shade(vec4 color, vec4 texel0, vec4 texel1, vec3 eye);

			void main() {
			    vec4 texel0 = texture2D(gtexture, texcoord.xy / texcoord.w)%s;
			    vec4 texel1 = texture2D(lightmap, lmcoord.xy / lmcoord.w);
			    gl_FragData[0] = astatine_shade(color, texel0, texel1, eye);
			}
			""".formatted(intensityTex ? ".rrrr" : "");
	}

	public static String vshHelpers(ShaderAttributeInputs inputs) {
		String normal = inputs.hasNormal() ? "" : "normal = " + DrawDataTransformer.NORMAL_MATRIX + " * " + DrawDataTransformer.DRAW + ".normal.xyz;";

		return """
			vec4 astatine_lit(vec3 normal, vec3 eye, vec4 color) {
			    int flags = %1$s.flags.x;
			    if ((flags & 1) == 0) {
			        return clamp(color, 0.0, 1.0);
			    }
			    %2$s
			    if ((flags & 2048) != 0) {
			        normal = normalize(normal);
			    }
			    int mode = (flags >> 4) & 7;
			    bool tracks = (flags & 8) != 0;
			    vec4 ambient = tracks && (mode == 0 || mode == 1) ? color : %1$s.materialAmbient;
			    vec4 diffuse = tracks && (mode == 0 || mode == 2) ? color : %1$s.materialDiffuse;
			    vec4 emission = tracks && mode == 3 ? color : %1$s.materialEmission;
			    vec3 result = emission.rgb + ambient.rgb * %1$s.sceneAmbient.rgb;
			    for (int i = 0; i < 2; i++) {
			        if ((flags & (2 << i)) == 0) {
			            continue;
			        }
			        vec4 position = %1$s.lightPosition[i];
			        vec3 direction = position.w == 0.0 ? normalize(position.xyz) : normalize(position.xyz / position.w - eye);
			        result += max(dot(normal, direction), 0.0) * diffuse.rgb * %1$s.lightDiffuse[i].rgb;
			    }
			    return clamp(vec4(result, diffuse.a), 0.0, 1.0);
			}
			""".formatted(DrawDataTransformer.ENVIRONMENT, normal);
	}

	public static String fshHelpers(boolean shadow) {
		return """
			vec4 astatine_shade(vec4 color, vec4 texel0, vec4 texel1, vec3 eye) {
			    int flags = %1$s.flags.x;
			    int textureFlags = %2$s.textures.z;
			    if ((textureFlags & 1) == 0) {
			        texel0 = vec4(1.0);
			    }
			    if ((textureFlags & 2) == 0) {
			        texel1 = vec4(1.0);
			    }
			    if ((flags & 1024) != 0) {
			        vec4 overlay = %1$s.overlayColor;
			        color = vec4(mix(texel0.rgb * color.rgb, overlay.rgb, overlay.a) * texel1.rgb, texel0.a);
			    } else {
			        color *= texel0 * texel1;
			    }
			    if (%3$s(flags & 128) != 0) {
			        vec4 fog = %1$s.fogParams;
			        float distance = (flags & 256) != 0 ? length(eye) : abs(eye.z);
			        float factor;
			        if (%1$s.flags.z == 0) {
			            factor = (fog.y - distance) * fog.w;
			        } else if (%1$s.flags.z == 1) {
			            factor = exp(-fog.z * distance);
			        } else {
			            factor = exp(-(fog.z * distance) * (fog.z * distance));
			        }
			        color.rgb = mix(%1$s.fogColor.rgb, color.rgb, clamp(factor, 0.0, 1.0));
			    }
			    return color;
			}
			""".formatted(DrawDataTransformer.ENVIRONMENT, DrawDataTransformer.DRAW, shadow ? "false && " : "");
	}
}
