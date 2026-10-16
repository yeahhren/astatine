#version 450
#extension GL_GOOGLE_include_directive : require

#include "fixed_function.glsl"

layout(location = 0) in uvec4 inVertex;

layout(location = 0) out vec4 vColor;
layout(location = 1) out vec4 vTexCoord0;
layout(location = 2) out vec4 vTexCoord1;
layout(location = 3) out vec3 vEye;
layout(location = 4) flat out ivec4 vTextures;

const float POSITION_SCALE = 1.0 / 1024.0;
const float POSITION_OFFSET = 8.0;
const float UV_SCALE = 1.0 / 16384.0;
const float UV_INSET = 0.75;

void main() {
    uvec3 position = uvec3(inVertex.x, inVertex.x >> 15, inVertex.y) & 0x7FFFu;
    uvec2 uv = uvec2(inVertex.y >> 15, inVertex.z) & 0x7FFFu;
    vec2 bias = mix(vec2(-UV_INSET), vec2(UV_INSET), equal(uv >> 14u, uvec2(1u)));
    vec2 texCoord = (vec2(uv & 0x3FFFu) + bias) * UV_SCALE;
    vec2 light = vec2(uvec2(inVertex.z >> 15, inVertex.z >> 21) & 63u) * 4.0;
    uint slot = (inVertex.x >> 30) | ((inVertex.y >> 30) << 2) | (((inVertex.z >> 27) & 31u) << 4) | ((inVertex.w >> 24) << 9);
    uvec4 section = sections[uint(gl_InstanceIndex) + slot];
    Draw draw = draws[section.w];
    environmentIndex = draw.textures.w;
    vec3 local = vec3(position) * POSITION_SCALE - POSITION_OFFSET;
    local = (local - 8.0) * 1.000001 + 8.0;
    vec4 eye = draw.modelView * vec4(local + uintBitsToFloat(section.xyz), 1.0);
    gl_Position = environment.projection * eye;
    vEye = eye.xyz;
    vColor = vec4(unpackUnorm4x8(inVertex.w).rgb, 1.0);
    vTextures = draw.textures;
    vTexCoord0 = environment.textureMatrix0 * vec4(texCoord, 0.0, 1.0);
    vTexCoord1 = environment.textureMatrix1 * vec4(light, 0.0, 1.0);
}
