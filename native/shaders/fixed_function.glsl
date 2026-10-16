#define TEXTURE0 (1 << 0)
#define TEXTURE1 (1 << 1)
#define TEXTURE0_CLAMP_S (1 << 2)
#define TEXTURE0_CLAMP_T (1 << 3)
#define TEXTURE1_CLAMP_S (1 << 4)
#define TEXTURE1_CLAMP_T (1 << 5)

#define LIGHTING (1 << 0)
#define LIGHT0 (1 << 1)
#define LIGHT1 (1 << 2)
#define COLOR_MATERIAL (1 << 3)
#define COLOR_MATERIAL_SHIFT 4
#define FOG (1 << 7)
#define FOG_RADIAL (1 << 8)
#define ALPHA_TEST (1 << 9)
#define OVERLAY (1 << 10)
#define NORMALIZE (1 << 11)
#define TEXGEN_S (1 << 12)

#define MATERIAL_AMBIENT_AND_DIFFUSE 0
#define MATERIAL_AMBIENT 1
#define MATERIAL_DIFFUSE 2
#define MATERIAL_EMISSION 3

#define FOG_LINEAR 0
#define FOG_EXP 1

#define TEXGEN_EYE_LINEAR 1

#ifdef FLAT
#define SHADE flat
#else
#define SHADE
#endif

#include "blocks.glsl"
