package net.irisshaders.iris.pipeline.transform.transformer;

import io.github.douira.glsl_transformer.ast.node.TranslationUnit;
import io.github.douira.glsl_transformer.ast.node.type.qualifier.StorageQualifier.StorageType;
import io.github.douira.glsl_transformer.ast.query.Root;
import io.github.douira.glsl_transformer.ast.transform.ASTInjectionPoint;
import io.github.douira.glsl_transformer.ast.transform.ASTParser;
import io.github.douira.glsl_transformer.util.Type;
import net.irisshaders.iris.pipeline.transform.PatchShaderType;
import net.irisshaders.iris.pipeline.transform.parameter.VanillaParameters;

import static net.irisshaders.iris.pipeline.transform.transformer.CommonTransformer.addIfNotExists;

public class VanillaCoreTransformer {
	public static void transform(ASTParser t, TranslationUnit tree, Root root, VanillaParameters parameters) {

		if (parameters.inputs.hasOverlay()) {
			if (!parameters.inputs.isText()) {
				EntityPatcher.patchOverlayColor(t, tree, root, parameters);
			}
			EntityPatcher.patchEntityId(t, tree, root, parameters);
		}

		tree.parseAndInjectNode(t, ASTInjectionPoint.BEFORE_DECLARATIONS, "const float mc_chunkFade = -1.0;");
		DrawDataTransformer.injectFog(t, tree);
		DrawDataTransformer.inject(t, tree, root, parameters);

		if (parameters.type == PatchShaderType.VERTEX) {
			tree.parseAndInjectNode(t, ASTInjectionPoint.BEFORE_DECLARATIONS, "layout(constant_id = 0) const int iris_InputMask = 15;");
			tree.prependMainFunctionBody(t, "iris_DrawId = gl_InstanceIndex;");
		}

		tree.parseAndInjectNodes(t, ASTInjectionPoint.BEFORE_DECLARATIONS, """
			layout(std140) uniform iris_Globals {
			ivec3 CameraBlockPos;
			float GlintAlpha;
			vec3 CameraOffset;
			float GameTime;
			vec2 ScreenSize;
			int MenuBlurRadius;
			int UseRgss;
			} iris_globalInfo;
			""");

		CommonTransformer.transform(t, tree, root, parameters, true);
		CommonTransformer.replaceMidBlock(t, tree, root, parameters);
		root.rename("alphaTestRef", "iris_currentAlphaTest");
		root.replaceReferenceExpressions(t, "modelViewMatrix", DrawDataTransformer.MODEL_VIEW);
		root.replaceReferenceExpressions(t, "gl_ModelViewMatrix", DrawDataTransformer.MODEL_VIEW);
		root.replaceReferenceExpressions(t, "modelViewMatrixInverse", "inverse(" + DrawDataTransformer.MODEL_VIEW + ")");
		root.replaceReferenceExpressions(t, "gl_ModelViewMatrixInverse", "inverse(" + DrawDataTransformer.MODEL_VIEW + ")");
		CommonTransformer.injectForwardZProjection(t, tree);
		root.replaceReferenceExpressions(t, "projectionMatrix", "iris_undoRevZ(" + DrawDataTransformer.PROJECTION + ")");
		root.replaceReferenceExpressions(t, "gl_ProjectionMatrix", "iris_undoRevZ(" + DrawDataTransformer.PROJECTION + ")");
		root.replaceReferenceExpressions(t, "projectionMatrixInverse", "inverse(iris_undoRevZ(" + DrawDataTransformer.PROJECTION + "))");
		root.replaceReferenceExpressions(t, "gl_ProjectionMatrixInverse", "inverse(iris_undoRevZ(" + DrawDataTransformer.PROJECTION + "))");
		root.replaceReferenceExpressions(t, "textureMatrix", DrawDataTransformer.ENVIRONMENT + ".textureMatrix0");

		root.replaceExpressionMatches(t, CommonTransformer.glTextureMatrix0, DrawDataTransformer.ENVIRONMENT + ".textureMatrix0");
		root.replaceExpressionMatches(t, CommonTransformer.glTextureMatrix1,
			"mat4(vec4(0.00390625, 0.0, 0.0, 0.0), vec4(0.0, 0.00390625, 0.0, 0.0), vec4(0.0, 0.0, 0.00390625, 0.0), vec4(0.03125, 0.03125, 0.03125, 1.0))");
		root.replaceExpressionMatches(t, CommonTransformer.glTextureMatrix2,
			"mat4(vec4(0.00390625, 0.0, 0.0, 0.0), vec4(0.0, 0.00390625, 0.0, 0.0), vec4(0.0, 0.0, 0.00390625, 0.0), vec4(0.03125, 0.03125, 0.03125, 1.0))");
		root.replaceReferenceExpressions(t, "normalMatrix", DrawDataTransformer.NORMAL_MATRIX);
		root.replaceReferenceExpressions(t, "gl_NormalMatrix", DrawDataTransformer.NORMAL_MATRIX);
		root.replaceReferenceExpressions(t, "chunkOffset", "vec3(0.0)");

		CommonTransformer.upgradeStorageQualifiers(t, tree, root, parameters);

		if (parameters.type == PatchShaderType.VERTEX) {
			CommonTransformer.patchIntegerAttribute(t, tree, root, "mc_Entity", "iris_Entity", parameters.inputs.getEntityComponents());

			String draw = DrawDataTransformer.DRAW;
			String color = "((iris_InputMask & 1) != 0 ? iris_Color : " + draw + ".color)";
			String uv0 = "((iris_InputMask & 2) != 0 ? iris_UV0 : " + draw + ".texCoord0.xy)";
			String uv2 = "((iris_InputMask & 4) != 0 ? iris_UV2 : " + draw + ".texCoord1.xy)";
			String normal = "((iris_InputMask & 8) != 0 ? iris_Normal : " + draw + ".normal.xyz)";
			root.replaceReferenceExpressions(t, "gl_Vertex", "vec4(iris_Position, 1.0)");
			root.rename("vaPosition", "iris_Position");
			root.replaceReferenceExpressions(t, "vaColor", color);
			root.replaceReferenceExpressions(t, "gl_Color", color);
			root.replaceReferenceExpressions(t, "vaNormal", normal);
			root.replaceReferenceExpressions(t, "gl_Normal", normal);
			root.replaceReferenceExpressions(t, "vaUV0", uv0);
			root.replaceReferenceExpressions(t, "gl_MultiTexCoord0", "vec4(" + uv0 + ", 0.0, 1.0)");
			if (parameters.inputs.hasLight()) {
				root.replaceReferenceExpressions(t, "gl_MultiTexCoord1", "vec4(" + uv2 + ", 0.0, 1.0)");
				root.replaceReferenceExpressions(t, "gl_MultiTexCoord2", "vec4(" + uv2 + ", 0.0, 1.0)");
				root.replaceReferenceExpressions(t, "vaUV2", uv2);
			} else {
				root.replaceReferenceExpressions(t, "gl_MultiTexCoord1", "vec4(240.0, 240.0, 0.0, 1.0)");
				root.replaceReferenceExpressions(t, "gl_MultiTexCoord2", "vec4(240.0, 240.0, 0.0, 1.0)");
				root.rename("vaUV2", "iris_UV2");
			}
			root.rename("vaUV1", "iris_UV1");

			addIfNotExists(root, t, tree, "iris_Color", Type.F32VEC4, StorageType.IN);
			addIfNotExists(root, t, tree, "iris_Position", Type.F32VEC3, StorageType.IN);
			addIfNotExists(root, t, tree, "iris_Normal", Type.F32VEC3, StorageType.IN);
			addIfNotExists(root, t, tree, "iris_UV0", Type.F32VEC2, StorageType.IN);
			addIfNotExists(root, t, tree, "iris_UV1", Type.F32VEC2, StorageType.IN);
			addIfNotExists(root, t, tree, "iris_UV2", Type.F32VEC2, StorageType.IN);
		}

	}
}
