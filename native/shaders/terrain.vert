#version 450
#extension GL_GOOGLE_include_directive : require

#include "fixed_function.glsl"

layout(location = 0) in uvec4 inPosition;
layout(location = 1) in vec4 inColor;
layout(location = 2) in vec2 inTexCoord;
layout(location = 3) in uvec2 inLight;

layout(location = 0) out vec4 vColor;
layout(location = 1) out vec4 vTexCoord0;
layout(location = 2) out vec4 vTexCoord1;
layout(location = 3) out vec3 vEye;
layout(location = 4) flat out ivec4 vTextures;

const float POSITION_SCALE = 1.0 / 2048.0;
const float POSITION_OFFSET = 8.0;

void main() {
    uvec4 section = sections[gl_InstanceIndex];
    Draw draw = draws[section.w];
    vec3 local = vec3(inPosition.xyz) * POSITION_SCALE - POSITION_OFFSET;
    local = (local - 8.0) * 1.000001 + 8.0;
    vec4 eye = draw.modelView * vec4(local + uintBitsToFloat(section.xyz), 1.0);
    gl_Position = environment.projection * eye;
    vEye = eye.xyz;
    vColor = inColor;
    vTextures = draw.textures;
    vTexCoord0 = environment.textureMatrix0 * vec4(inTexCoord, 0.0, 1.0);
    vTexCoord1 = environment.textureMatrix1 * vec4(vec2(inLight), 0.0, 1.0);
}
