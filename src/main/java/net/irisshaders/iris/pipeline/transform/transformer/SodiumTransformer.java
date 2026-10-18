package net.irisshaders.iris.pipeline.transform.transformer;

import io.github.douira.glsl_transformer.ast.node.Identifier;
import io.github.douira.glsl_transformer.ast.node.TranslationUnit;
import io.github.douira.glsl_transformer.ast.node.declaration.TypeAndInitDeclaration;
import io.github.douira.glsl_transformer.ast.node.external_declaration.DeclarationExternalDeclaration;
import io.github.douira.glsl_transformer.ast.node.type.qualifier.StorageQualifier;
import io.github.douira.glsl_transformer.ast.node.type.specifier.BuiltinNumericTypeSpecifier;
import io.github.douira.glsl_transformer.ast.query.Root;
import io.github.douira.glsl_transformer.ast.transform.ASTInjectionPoint;
import io.github.douira.glsl_transformer.ast.transform.ASTParser;
import io.github.douira.glsl_transformer.util.Type;
import net.irisshaders.iris.gl.shader.ShaderType;
import net.irisshaders.iris.pipeline.transform.parameter.SodiumParameters;

import static net.irisshaders.iris.pipeline.transform.transformer.CommonTransformer.addIfNotExists;

public class SodiumTransformer {
	public static void transform(ASTParser t, TranslationUnit tree, Root root, SodiumParameters parameters) {
		CommonTransformer.transform(t, tree, root, parameters, false);

		replaceMidTexCoord(t, tree, root, 1.0f);
		replaceMCEntity(t, tree, root);
		CommonTransformer.replaceMidBlock(t, tree, root, parameters);

		boolean needsNormal = root.identifierIndex.has("gl_Normal") || root.identifierIndex.has("at_tangent");

		root.replaceExpressionMatches(t, CommonTransformer.glTextureMatrix0, "mat4(1.0)");
		root.replaceExpressionMatches(t, CommonTransformer.glTextureMatrix1, DrawDataTransformer.ENVIRONMENT + ".textureMatrix1");
		root.replaceReferenceExpressions(t, "gl_ProjectionMatrix", "iris_undoRevZ(u_ProjectionMatrix)");

		if (parameters.type.glShaderType == ShaderType.VERTEX) {
			// Alias of gl_MultiTexCoord1 on 1.15+ for OptiFine
			// See https://github.com/IrisShaders/Iris/issues/1149
			root.rename("gl_MultiTexCoord2", "gl_MultiTexCoord1");

			root.replaceReferenceExpressions(t, "gl_MultiTexCoord0", "vec4(_vert_tex_diffuse_coord, 0.0, 1.0)");

			root.replaceReferenceExpressions(t, "gl_MultiTexCoord1", "vec4(_vert_tex_light_coord, 0.0, 1.0)");

			CommonTransformer.patchMultiTexCoord3(t, tree, root, parameters);

			// gl_MultiTexCoord0 and gl_MultiTexCoord1 are the only valid inputs (with
			// gl_MultiTexCoord2 and gl_MultiTexCoord3 as aliases), other texture
			// coordinates are not valid inputs.
			CommonTransformer.replaceGlMultiTexCoordBounded(t, root, 4, 7);
		}

		root.rename("gl_Color", "_vert_color");

		if (parameters.type.glShaderType == ShaderType.VERTEX && needsNormal) {
			root.rename("gl_Normal", "irs_Normal");
			root.replaceReferenceExpressions(t, "at_tangent", "irs_Tangent");
		}

		// TODO: Should probably add the normal matrix as a proper uniform that's
		// computed on the CPU-side of things
		root.replaceReferenceExpressions(t, "gl_NormalMatrix", "iris_NormalMat");

		// TODO: All of the transformed variants of the input matrices, preferably
		// computed on the CPU side...
		root.rename("gl_ModelViewMatrix", "u_ModelViewMatrix");
		root.rename("gl_ModelViewMatrixInverse", "iris_ModelViewMatInverse");
		root.rename("gl_ProjectionMatrixInverse", "iris_ProjMatInverse");

		if (parameters.type.glShaderType == ShaderType.VERTEX) {
			// TODO: Vaporwave-Shaderpack expects that vertex positions will be aligned to
			// chunks.
			if (root.identifierIndex.has("ftransform")) {
				tree.parseAndInjectNodes(t, ASTInjectionPoint.BEFORE_FUNCTIONS,
					"vec4 ftransform() { return gl_ModelViewProjectionMatrix * gl_Vertex; }");
			}
			tree.parseAndInjectNodes(t, ASTInjectionPoint.BEFORE_DECLARATIONS,
				"vec4 getVertexPosition() { return vec4(_vert_position + _get_draw_translation(_draw_id), 1.0); }");
			root.replaceReferenceExpressions(t, "gl_Vertex", "getVertexPosition()");

			// inject here so that _vert_position is available to the above. (injections
			// inject in reverse order if performed piece-wise but in correct order if
			// performed as an array of injections)
			injectVertInit(t, tree, root, parameters, needsNormal);
		} else {
		}

		DrawDataTransformer.inject(t, tree, root, parameters);

		root.replaceReferenceExpressions(t, "gl_ModelViewProjectionMatrix", "(iris_undoRevZ(u_ProjectionMatrix) * u_ModelViewMatrix)");

		CommonTransformer.injectForwardZProjection(t, tree);

		CommonTransformer.applyIntelHd4000Workaround(root);
		replaceTerrainNames(t, root);
	}

	public static void replaceTerrainNames(ASTParser t, Root root) {
		root.replaceReferenceExpressions(t, "u_ModelViewMatrix", DrawDataTransformer.MODEL_VIEW);
		root.replaceReferenceExpressions(t, "u_ProjectionMatrix", DrawDataTransformer.PROJECTION);
		root.replaceReferenceExpressions(t, "iris_NormalMat", DrawDataTransformer.NORMAL_MATRIX);
		root.replaceReferenceExpressions(t, "iris_ModelViewMatInverse", "inverse(" + DrawDataTransformer.MODEL_VIEW + ")");
		root.replaceReferenceExpressions(t, "iris_ProjMatInverse", "inverse(iris_undoRevZ(" + DrawDataTransformer.PROJECTION + "))");
		root.replaceReferenceExpressions(t, "iris_RegionOffset", "vec3(0.0)");
	}

	public static void injectVertInit(ASTParser t, TranslationUnit tree, Root root, SodiumParameters parameters, boolean needsNormal) {
		String chunkFadeDeclaration = parameters.shadow ? "const float mc_chunkFade = -1.0;" : "const float mc_chunkFade = 1.0;";

		tree.parseAndInjectNodes(t, ASTInjectionPoint.BEFORE_DECLARATIONS,
			"vec3 _vert_position;", "vec2 _vert_tex_diffuse_coord;", "vec2 _vert_tex_light_coord;", "vec4 _vert_color;",
			"vec3 iris_sectionOffset;", "uint _draw_id;", "vec3 irs_Normal;", "vec4 irs_Tangent;", chunkFadeDeclaration, """
				vec3 _get_draw_translation(uint id) {
				    return iris_sectionOffset;
				}
				""", """
				void _vert_init() {
				    uvec4 v = iris_TerrainVertex;
				    uvec3 position = uvec3(v.x, v.x >> 15, v.y) & 0x7FFFu;
				    _vert_position = vec3(position) * (1.0 / 1024.0) - 8.0;
				    uvec2 uv = uvec2(v.y >> 15, v.z) & 0x7FFFu;
				    vec2 bias = mix(vec2(-0.75), vec2(0.75), equal(uv >> 14u, uvec2(1u)));
				    _vert_tex_diffuse_coord = (vec2(uv & 0x3FFFu) + bias) * (1.0 / 16384.0);
				    _vert_tex_light_coord = vec2(uvec2(v.z >> 15, v.z >> 21) & 63u) * 4.0;
				    _vert_color = vec4(unpackUnorm4x8(v.w).rgb, 1.0 - iris_TerrainOcclusion);
				    uint slot = (v.x >> 30) | ((v.y >> 30) << 2) | (((v.z >> 27) & 31u) << 4) | ((v.w >> 24) << 9);
				    uvec4 section = iris_sections[uint(gl_InstanceIndex) + slot];
				    iris_sectionOffset = uintBitsToFloat(section.xyz);
				    _draw_id = section.w;
				    iris_DrawId = int(section.w);
				    irs_Normal = unpackSnorm4x8(iris_TerrainExtra.x).xyz;
				    irs_Tangent = unpackSnorm4x8(iris_TerrainExtra.y);
				}
				""");
		DrawDataTransformer.injectSections(t, tree);
		addIfNotExists(root, t, tree, "iris_TerrainVertex", Type.U32VEC4, StorageQualifier.StorageType.IN);
		addIfNotExists(root, t, tree, "iris_TerrainExtra", Type.U32VEC4, StorageQualifier.StorageType.IN);
		addIfNotExists(root, t, tree, "iris_TerrainOcclusion", Type.FLOAT32, StorageQualifier.StorageType.IN);
		tree.prependMainFunctionBody(t, "_vert_init();");
	}


	public static void replaceMCEntity(ASTParser t, TranslationUnit tree, Root root) {
		Type dimension = Type.BOOL;
		for (Identifier id : root.identifierIndex.get("mc_Entity")) {
			TypeAndInitDeclaration initDeclaration = (TypeAndInitDeclaration) id.getAncestor(2, 0,
				TypeAndInitDeclaration.class::isInstance);
			if (initDeclaration == null) {
				continue;
			}
			DeclarationExternalDeclaration declaration = (DeclarationExternalDeclaration) initDeclaration.getAncestor(1, 0,
				DeclarationExternalDeclaration.class::isInstance);
			if (declaration == null) {
				continue;
			}
			if (initDeclaration.getType().getTypeSpecifier() instanceof BuiltinNumericTypeSpecifier numeric) {
				dimension = numeric.type;

				declaration.detachAndDelete();
				initDeclaration.detachAndDelete();
				id.detachAndDelete();
				break;
			}
		}


		root.replaceReferenceExpressions(t, "mc_Entity", "iris_Entity");

		switch (dimension) {
			case BOOL:
				return;
			case FLOAT32:
				tree.parseAndInjectNode(t, ASTInjectionPoint.BEFORE_DECLARATIONS, "float iris_Entity = int(mc_Entity >> 1u) - 1;");
				break;
			case F32VEC2:
				tree.parseAndInjectNode(t, ASTInjectionPoint.BEFORE_DECLARATIONS,
					"vec2 iris_Entity = vec2(int(mc_Entity >> 1u) - 1, mc_Entity & 1u);");
				break;
			case F32VEC3:
				tree.parseAndInjectNode(t, ASTInjectionPoint.BEFORE_DECLARATIONS,
					"vec3 iris_Entity = vec3(int(mc_Entity >> 1u) - 1, mc_Entity & 1u, 0.0);");
				break;
			case F32VEC4:
				tree.parseAndInjectNode(t, ASTInjectionPoint.BEFORE_DECLARATIONS,
					"vec4 iris_Entity = vec4(int(mc_Entity >> 1u) - 1, mc_Entity & 1u, 0.0, 1.0);");
				break;
			case INT32:
				tree.parseAndInjectNode(t, ASTInjectionPoint.BEFORE_DECLARATIONS, "uint iris_Entity = int(mc_Entity >> 1u) - 1;");
				break;
			case I32VEC2:
				tree.parseAndInjectNode(t, ASTInjectionPoint.BEFORE_DECLARATIONS,
					"ivec2 iris_Entity = ivec2(int(mc_Entity >> 1u) - 1, mc_Entity & 1u);");
				break;
			case I32VEC3:
				tree.parseAndInjectNode(t, ASTInjectionPoint.BEFORE_DECLARATIONS,
					"ivec3 iris_Entity = ivec3(int(mc_Entity >> 1u) - 1, mc_Entity & 1u, 0);");
				break;
			case I32VEC4:
				tree.parseAndInjectNode(t, ASTInjectionPoint.BEFORE_DECLARATIONS,
					"ivec4 iris_Entity = ivec4(int(mc_Entity >> 1u) - 1, mc_Entity & 1u, 0, 1);");
				break;
			default:
				throw new IllegalStateException("Got an invalid format mc_Entity (" + dimension.getCompactName() + ").");
		}

		tree.parseAndInjectNode(t, ASTInjectionPoint.BEFORE_DECLARATIONS, "uint mc_Entity = iris_TerrainExtra.w;");
	}


	public static void replaceMidTexCoord(ASTParser t, TranslationUnit tree, Root root, float textureScale) {
		Type dimension = Type.BOOL;
		for (Identifier id : root.identifierIndex.get("mc_midTexCoord")) {
			TypeAndInitDeclaration initDeclaration = (TypeAndInitDeclaration) id.getAncestor(2, 0,
				TypeAndInitDeclaration.class::isInstance);
			if (initDeclaration == null) {
				continue;
			}
			DeclarationExternalDeclaration declaration = (DeclarationExternalDeclaration) initDeclaration.getAncestor(1, 0,
				DeclarationExternalDeclaration.class::isInstance);
			if (declaration == null) {
				continue;
			}
			if (initDeclaration.getType().getTypeSpecifier() instanceof BuiltinNumericTypeSpecifier numeric) {
				dimension = numeric.type;

				declaration.detachAndDelete();
				initDeclaration.detachAndDelete();
				id.detachAndDelete();
				break;
			}
		}


		root.replaceReferenceExpressions(t, "mc_midTexCoord", "iris_MidTex");

		switch (dimension) {
			case BOOL:
				return;
			case FLOAT32:
				tree.parseAndInjectNode(t, ASTInjectionPoint.BEFORE_DECLARATIONS,
					"float iris_MidTex = (float(mc_midTexCoord.x) * " + textureScale + ");");
				break;
			case F32VEC2:
				tree.parseAndInjectNode(t, ASTInjectionPoint.BEFORE_DECLARATIONS,
					"vec2 iris_MidTex = (vec2(mc_midTexCoord.xy) * " + textureScale + ").xy;");
				break;
			case F32VEC3:
				tree.parseAndInjectNode(t, ASTInjectionPoint.BEFORE_DECLARATIONS,
					"vec3 iris_MidTex = vec3(vec2(mc_midTexCoord.xy) * " + textureScale + ", 0.0);");
				break;
			case F32VEC4:
				tree.parseAndInjectNode(t, ASTInjectionPoint.BEFORE_DECLARATIONS,
					"vec4 iris_MidTex = vec4(vec2(mc_midTexCoord.xy) * " + textureScale + ", 0.0, 1.0);");
				break;
			default:
				throw new IllegalStateException("Somehow got a midTexCoord that is *above* 4 dimensions???");
		}

		tree.parseAndInjectNode(t, ASTInjectionPoint.BEFORE_DECLARATIONS, "vec2 mc_midTexCoord = unpackUnorm2x16(iris_TerrainExtra.z);");
	}
}
