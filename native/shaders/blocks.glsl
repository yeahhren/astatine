layout(set = 0, binding = 0, std140) uniform Environment {
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
} environment;

struct Draw {
    mat4 modelView;
    vec4 normalMatrix[3];
    vec4 color;
    vec4 normal;
    vec4 texCoord0;
    vec4 texCoord1;
    ivec4 textures;
};

layout(set = 0, binding = 1, std430) readonly buffer Draws {
    Draw draws[];
};

layout(set = 0, binding = 2, std430) readonly buffer Sections {
    uvec4 sections[];
};
