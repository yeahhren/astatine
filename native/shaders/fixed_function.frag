#version 450
#extension GL_GOOGLE_include_directive : require
#ifdef BINDLESS
#extension GL_EXT_nonuniform_qualifier : require
#endif

#include "fixed_function.glsl"

#ifdef BINDLESS
layout(constant_id = 0) const int TEXTURE_CAPACITY = 1024;
layout(set = 1, binding = 0) uniform sampler2D textures[TEXTURE_CAPACITY];
#define SAMPLER0 textures[nonuniformEXT(vTextures.x)]
#define SAMPLER1 textures[nonuniformEXT(vTextures.y)]
#else
layout(set = 1, binding = 0) uniform sampler2D texture0;
layout(set = 2, binding = 0) uniform sampler2D texture1;
#define SAMPLER0 texture0
#define SAMPLER1 texture1
#endif

#ifdef FLAT
layout(location = 0) flat in vec4 vColor;
#else
layout(location = 0) in vec4 vColor;
#endif
layout(location = 1) in vec4 vTexCoord0;
layout(location = 2) in vec4 vTexCoord1;
layout(location = 3) in vec3 vEye;
layout(location = 4) flat in ivec4 vTextures;

layout(location = 0) out vec4 outColor;

vec2 project(vec4 coordinate, bool clampS, bool clampT) {
    vec2 uv = coordinate.xy / coordinate.w;
    if (clampS) {
        uv.x = clamp(uv.x, 0.0, 1.0);
    }
    if (clampT) {
        uv.y = clamp(uv.y, 0.0, 1.0);
    }
    return uv;
}

bool passes(int function, float alpha, float reference) {
    switch (function) {
        case 0: return false;
        case 1: return alpha < reference;
        case 2: return alpha == reference;
        case 3: return alpha <= reference;
        case 4: return alpha > reference;
        case 5: return alpha != reference;
        case 6: return alpha >= reference;
    }
    return true;
}

void main() {
    int flags = environment.flags.x;
    int textureFlags = vTextures.z;
    vec4 color = vColor;
    vec4 texel0 = vec4(1.0);
    vec4 texel1 = vec4(1.0);
    if ((textureFlags & TEXTURE0) != 0) {
        texel0 = texture(SAMPLER0, project(vTexCoord0, (textureFlags & TEXTURE0_CLAMP_S) != 0, (textureFlags & TEXTURE0_CLAMP_T) != 0));
    }
    if ((textureFlags & TEXTURE1) != 0) {
        texel1 = texture(SAMPLER1, project(vTexCoord1, (textureFlags & TEXTURE1_CLAMP_S) != 0, (textureFlags & TEXTURE1_CLAMP_T) != 0));
    }

    if ((flags & OVERLAY) != 0) {
        vec4 overlay = environment.overlayColor;
        color = vec4(mix(texel0.rgb * color.rgb, overlay.rgb, overlay.a) * texel1.rgb, texel0.a);
    } else {
        color *= texel0;
        color *= texel1;
    }

    if ((flags & FOG) != 0) {
        vec4 fog = environment.fogParams;
        float distance = (flags & FOG_RADIAL) != 0 ? length(vEye) : abs(vEye.z);
        float factor;
        switch (environment.flags.z) {
            case FOG_LINEAR: factor = (fog.y - distance) * fog.w; break;
            case FOG_EXP: factor = exp(-fog.z * distance); break;
            default: factor = exp(-(fog.z * distance) * (fog.z * distance)); break;
        }
        color.rgb = mix(environment.fogColor.rgb, color.rgb, clamp(factor, 0.0, 1.0));
    }

    if ((flags & ALPHA_TEST) != 0 && !passes(environment.flags.y, color.a, environment.alphaReference.x)) {
        discard;
    }
    outColor = color;
}
