#version 450
#extension GL_GOOGLE_include_directive : require

#include "fixed_function.glsl"

layout(location = 0) in vec3 inPosition;
#ifdef HAS_COLOR
layout(location = 1) in vec4 inColor;
#endif
#ifdef HAS_UV0
layout(location = 2) in vec2 inTexCoord0;
#endif
#ifdef HAS_UV1
layout(location = 3) in ivec2 inTexCoord1;
#endif
#ifdef HAS_NORMAL
layout(location = 4) in vec3 inNormal;
#endif

#ifdef FLAT
layout(location = 0) flat out vec4 vColor;
#else
layout(location = 0) out vec4 vColor;
#endif
layout(location = 1) out vec4 vTexCoord0;
layout(location = 2) out vec4 vTexCoord1;
layout(location = 3) out vec3 vEye;
layout(location = 4) flat out ivec4 vTextures;

vec4 lit(vec3 normal, vec3 eye, vec4 color, int flags) {
    int mode = (flags >> COLOR_MATERIAL_SHIFT) & 7;
    bool tracks = (flags & COLOR_MATERIAL) != 0;
    vec4 ambient = tracks && (mode == MATERIAL_AMBIENT_AND_DIFFUSE || mode == MATERIAL_AMBIENT) ? color : environment.materialAmbient;
    vec4 diffuse = tracks && (mode == MATERIAL_AMBIENT_AND_DIFFUSE || mode == MATERIAL_DIFFUSE) ? color : environment.materialDiffuse;
    vec4 emission = tracks && mode == MATERIAL_EMISSION ? color : environment.materialEmission;
    vec3 result = emission.rgb + ambient.rgb * environment.sceneAmbient.rgb;
    for (int i = 0; i < 2; i++) {
        if ((flags & (LIGHT0 << i)) == 0) {
            continue;
        }
        vec4 position = environment.lightPosition[i];
        vec3 direction = position.w == 0.0 ? normalize(position.xyz) : normalize(position.xyz / position.w - eye);
        result += max(dot(normal, direction), 0.0) * diffuse.rgb * environment.lightDiffuse[i].rgb;
    }
    return vec4(result, diffuse.a);
}

float generate(int coordinate, vec4 object, vec4 eye, float current, int flags, int modes) {
    if ((flags & (TEXGEN_S << coordinate)) == 0) {
        return current;
    }
    vec4 plane = environment.texGenPlanes[coordinate];
    return ((modes >> (coordinate * 2)) & 3) == TEXGEN_EYE_LINEAR ? dot(plane, eye) : dot(plane, object);
}

void main() {
    Draw draw = draws[gl_InstanceIndex];
    environmentIndex = draw.textures.w;
    vec4 object = vec4(inPosition, 1.0);
    vec4 eye = draw.modelView * object;
    gl_Position = environment.projection * eye;
    gl_PointSize = 1.0;
    vEye = eye.xyz;
    vTextures = draw.textures;

    int flags = environment.flags.x;
#ifdef HAS_COLOR
    vec4 color = inColor;
#else
    vec4 color = draw.color;
#endif
    if ((flags & LIGHTING) != 0) {
#ifdef HAS_NORMAL
        vec3 normal = inNormal;
#else
        vec3 normal = draw.normal.xyz;
#endif
        mat3 normalMatrix = mat3(draw.normalMatrix[0].xyz, draw.normalMatrix[1].xyz, draw.normalMatrix[2].xyz);
        normal = normalMatrix * normal * draw.normalMatrix[0].w;
        if ((flags & NORMALIZE) != 0) {
            normal = normalize(normal);
        }
        color = lit(normal, eye.xyz, color, flags);
    }
    vColor = clamp(color, 0.0, 1.0);

#ifdef HAS_UV0
    vec4 texCoord0 = vec4(inTexCoord0, 0.0, 1.0);
#else
    vec4 texCoord0 = draw.texCoord0;
#endif
    int modes = environment.flags.w;
    texCoord0 = vec4(
        generate(0, object, eye, texCoord0.s, flags, modes),
        generate(1, object, eye, texCoord0.t, flags, modes),
        generate(2, object, eye, texCoord0.p, flags, modes),
        generate(3, object, eye, texCoord0.q, flags, modes)
    );
    vTexCoord0 = environment.textureMatrix0 * texCoord0;

#ifdef HAS_UV1
    vec4 texCoord1 = vec4(vec2(inTexCoord1), 0.0, 1.0);
#else
    vec4 texCoord1 = draw.texCoord1;
#endif
    vTexCoord1 = environment.textureMatrix1 * texCoord1;
}
