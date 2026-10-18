package net.irisshaders.iris.pipeline.transform.transformer;

import io.github.douira.glsl_transformer.ast.node.TranslationUnit;
import io.github.douira.glsl_transformer.ast.node.declaration.DeclarationMember;
import io.github.douira.glsl_transformer.ast.node.declaration.TypeAndInitDeclaration;
import io.github.douira.glsl_transformer.ast.node.external_declaration.DeclarationExternalDeclaration;
import io.github.douira.glsl_transformer.ast.node.type.qualifier.StorageQualifier;
import io.github.douira.glsl_transformer.ast.print.ASTPrinter;
import io.github.douira.glsl_transformer.ast.print.PrintType;
import io.github.douira.glsl_transformer.ast.query.Root;
import io.github.douira.glsl_transformer.ast.transform.ASTInjectionPoint;
import io.github.douira.glsl_transformer.ast.transform.ASTParser;
import net.irisshaders.iris.gl.shader.ShaderType;
import net.irisshaders.iris.pipeline.transform.PatchShaderType;
import net.irisshaders.iris.pipeline.transform.parameter.Parameters;
import net.irisshaders.iris.pipeline.transform.vulkan.VulkanGlsl;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

public final class DrawDataTransformer {
	public static final String DRAW = "iris_draws[iris_DrawId]";
	public static final String ENVIRONMENT = "iris_environments[iris_draws[iris_DrawId].textures.w]";
	public static final String MODEL_VIEW = DRAW + ".modelView";
	public static final String PROJECTION = ENVIRONMENT + ".projection";
	public static final String NORMAL_MATRIX =
		"(mat3(" + DRAW + ".normalMatrix[0].xyz, " + DRAW + ".normalMatrix[1].xyz, " + DRAW + ".normalMatrix[2].xyz) * " + DRAW +
			".normalMatrix[0].w)";

	public static final Map<String, String> PER_DRAW_UNIFORMS = Map.ofEntries(
		Map.entry("gtextureSize", "ivec2(" + DRAW + ".pbr.zw)"),
		Map.entry("blendFunc", DRAW + ".blend"),
		Map.entry("entityId", DRAW + ".ids.x"),
		Map.entry("blockEntityId", DRAW + ".ids.y"),
		Map.entry("currentRenderedItemId", DRAW + ".ids.z"),
		Map.entry("renderStage", DRAW + ".ids.w"),
		Map.entry("entityColor", "((" + ENVIRONMENT + ".flags.x & 1024) != 0 ? " + ENVIRONMENT + ".overlayColor : vec4(0.0))"),
		Map.entry("fogDensity", "(" + ENVIRONMENT + ".flags.z == 0 ? 0.0 : max(" + ENVIRONMENT + ".fogParams.z, 0.0))"),
		Map.entry("fogStart", ENVIRONMENT + ".fogParams.x"),
		Map.entry("fogEnd", ENVIRONMENT + ".fogParams.y"),
		Map.entry("fogMode", "((" + ENVIRONMENT + ".flags.x & 128) == 0 || (" + DRAW + ".textures.z & 1073741824) != 0 ? 0 : " + ENVIRONMENT + ".flags.z == 0 ? 9729 : 2049)"),
		Map.entry("fogShape", "((" + ENVIRONMENT + ".flags.x & 128) == 0 || (" + DRAW + ".textures.z & 1073741824) != 0 ? -1 : 1)"));

	private DrawDataTransformer() {
	}

	public static void replaceDrawUniforms(ASTParser t, TranslationUnit tree, Root root) {
		Map<String, String> declared = new LinkedHashMap<>();

		for (var external : List.copyOf(tree.getChildren())) {
			if (!(external instanceof DeclarationExternalDeclaration declaration) || !(declaration.getDeclaration() instanceof TypeAndInitDeclaration variables) || !VulkanGlsl.hasStorage(
				variables.getType().getTypeQualifier(), StorageQualifier.StorageType.UNIFORM)) {
				continue;
			}

			String type = ASTPrinter.print(PrintType.COMPACT, variables.getType().getTypeSpecifier()).trim();

			for (DeclarationMember member : List.copyOf(variables.getMembers())) {
				String name = member.getName().getName();

				if (PER_DRAW_UNIFORMS.containsKey(name) && member.getArraySpecifier() == null && variables.getType().getTypeSpecifier()
					.getArraySpecifier() == null) {
					declared.put(name, type);

					if (variables.getMembers().size() == 1) {
						declaration.detachAndDelete();
					} else {
						member.detachAndDelete();
					}
				}
			}
		}

		declared.forEach((name, type) -> root.replaceReferenceExpressions(t, name, type + "(" + PER_DRAW_UNIFORMS.get(name) + ")"));
	}

	public static void inject(ASTParser t, TranslationUnit tree, Root root, Parameters parameters) {
		tree.parseAndInjectNodes(t, ASTInjectionPoint.BEFORE_DECLARATIONS, """
			struct iris_Environment {
			    mat4 projection;
			    mat4 textureMatrix0;
			    mat4 textureMatrix1;
			    vec4 lightPosition[2];
			    vec4 lightDiffuse[2];
			    vec4 sceneAmbient;
			    vec4 materialAmbient;
			    vec4 materialDiffuse;
			    vec4 materialEmission;
			    vec4 fogColor;
			    vec4 fogParams;
			    vec4 overlayColor;
			    vec4 texGenPlanes[4];
			    ivec4 flags;
			    vec4 alphaReference;
			};
			""", """
			layout(std430) readonly buffer iris_Environments {
			    iris_Environment iris_environments[];
			};
			""", """
			struct iris_Draw {
			    mat4 modelView;
			    vec4 normalMatrix[3];
			    vec4 color;
			    vec4 normal;
			    vec4 texCoord0;
			    vec4 texCoord1;
			    ivec4 textures;
			    ivec4 ids;
			    ivec4 pbr;
			    ivec4 blend;
			};
			""", """
			layout(std430) readonly buffer iris_Draws {
			    iris_Draw iris_draws[];
			};
			""");

		if (parameters.type.glShaderType == ShaderType.VERTEX) {
			tree.parseAndInjectNode(t, ASTInjectionPoint.BEFORE_DECLARATIONS, "flat out int iris_DrawId;");
		} else if (parameters.type.glShaderType == ShaderType.FRAGMENT) {
			tree.parseAndInjectNode(t, ASTInjectionPoint.BEFORE_DECLARATIONS, "flat in int iris_DrawId;");
		} else {
			tree.parseAndInjectNode(t, ASTInjectionPoint.BEFORE_DECLARATIONS, "int iris_DrawId;");
		}

		replaceDrawUniforms(t, tree, root);
	}

	public static void linkStages(ASTParser t, Map<PatchShaderType, TranslationUnit> trees) {
		TranslationUnit vertex = trees.get(PatchShaderType.VERTEX);
		List<PatchShaderType> middle = Stream.of(PatchShaderType.TESS_CONTROL, PatchShaderType.TESS_EVAL, PatchShaderType.GEOMETRY)
			.filter(stage -> trees.get(stage) != null).toList();

		if (vertex == null || middle.isEmpty() || !vertex.getRoot().identifierIndex.has("iris_DrawId")) {
			return;
		}

		vertex.getRoot().rename("iris_DrawId", "iris_DrawId" + middle.size());

		for (int i = 0; i < middle.size(); i++) {
			PatchShaderType stage = middle.get(i);
			TranslationUnit tree = trees.get(stage);
			Root root = tree.getRoot();
			String input = "iris_DrawId" + (middle.size() - i);
			String output = i == middle.size() - 1 ? "iris_DrawId" : "iris_DrawId" + (middle.size() - i - 1);

			if (!root.identifierIndex.has("iris_DrawId")) {
				tree.parseAndInjectNode(t, ASTInjectionPoint.BEFORE_DECLARATIONS, "int iris_DrawIdValue;");
			} else {
				root.rename("iris_DrawId", "iris_DrawIdValue");
			}

			tree.parseAndInjectNode(t, ASTInjectionPoint.BEFORE_DECLARATIONS, "flat in int " + input + "[];");

			switch (stage) {
				case TESS_CONTROL -> {
					tree.parseAndInjectNode(t, ASTInjectionPoint.BEFORE_DECLARATIONS, "flat out int " + output + "[];");
					tree.prependMainFunctionBody(t, output + "[gl_InvocationID] = iris_DrawIdValue;");
				}
				case TESS_EVAL -> {
					tree.parseAndInjectNode(t, ASTInjectionPoint.BEFORE_DECLARATIONS, "flat out int " + output + ";");
					tree.prependMainFunctionBody(t, output + " = iris_DrawIdValue;");
				}
				default -> {
					tree.parseAndInjectNode(t, ASTInjectionPoint.BEFORE_DECLARATIONS, "flat out int " + output + ";");
					root.rename("EmitVertex", "iris_EmitVertex");
					root.rename("EmitStreamVertex", "iris_EmitStreamVertex");
					tree.parseAndInjectNodes(t, ASTInjectionPoint.BEFORE_FUNCTIONS,
						"void iris_EmitVertex() { " + output + " = iris_DrawIdValue; EmitVertex(); }",
						"void iris_EmitStreamVertex(int stream) { " + output + " = iris_DrawIdValue; EmitStreamVertex(stream); }");
				}
			}

			tree.prependMainFunctionBody(t, "iris_DrawIdValue = " + input + "[0];");
		}
	}

	public static void injectSections(ASTParser t, TranslationUnit tree) {
		tree.parseAndInjectNode(t, ASTInjectionPoint.BEFORE_DECLARATIONS, """
			layout(std430) readonly buffer iris_Sections {
			    uvec4 iris_sections[];
			};
			""");
	}

	public static void injectFog(ASTParser t, TranslationUnit tree) {
		tree.parseAndInjectNodes(t, ASTInjectionPoint.BEFORE_DECLARATIONS,
			"struct iris_FogParameters {" + "vec4 color;" + "float density;" + "float start;" + "float end;" + "float scale;" + "};",
			"iris_FogParameters irisInt_Fog = iris_FogParameters(" + ENVIRONMENT + ".fogColor, " + ENVIRONMENT + ".fogParams.z, " +
				ENVIRONMENT + ".fogParams.x, " + ENVIRONMENT + ".fogParams.y, " + ENVIRONMENT + ".fogParams.w);");
	}
}
