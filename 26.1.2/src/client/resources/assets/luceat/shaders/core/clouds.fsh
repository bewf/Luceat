#version 330

#moj_import <minecraft:fog.glsl>

in float vertexDistance;
in vec4 vertexColor;
in vec2 cloudPos;
in float heightFrac;
flat in float cellDensity;
flat in int faceKind;

#ifndef LUCEAT_CUT
#define LUCEAT_CUT 0.0
#endif
#ifndef LUCEAT_ALPHA
#define LUCEAT_ALPHA 1.0
#endif
#ifndef LUCEAT_DARK
#define LUCEAT_DARK 1.0
#endif
#ifndef LUCEAT_FLAT
#define LUCEAT_FLAT 0.0
#endif

out vec4 fragColor;

float hash21(vec2 p) {
    p = fract(p * vec2(123.34, 456.21));
    p += dot(p, p + 45.32);
    return fract(p.x * p.y);
}

void main() {
    if (cellDensity < LUCEAT_CUT) {
        discard;
    }
    float dense = LUCEAT_CUT > 0.0 ? smoothstep(LUCEAT_CUT, LUCEAT_CUT + 0.2, cellDensity) : smoothstep(0.36, 0.64, cellDensity);
    dense = mix(dense, 0.8, LUCEAT_FLAT);
    vec4 color = vertexColor;
    float alpha;
    float shade = 1.0;
    if (faceKind == 1) {
        float tile = hash21(mod(floor(cloudPos / 3.0), 240.0));
        shade = 0.96 + 0.06 * tile;
        alpha = mix(0.58, 0.97, dense);
    } else if (faceKind == 0) {
        shade = mix(1.0, 0.88, dense);
        alpha = mix(0.52, 0.96, dense);
    } else {
        shade = mix(1.0, 0.92, dense) * (0.9 + 0.1 * (1.0 - heightFrac));
        alpha = mix(0.4, 0.9, dense) * (0.78 + 0.22 * (1.0 - heightFrac));
    }
    color.rgb *= shade * LUCEAT_DARK;
    color.a *= alpha * 1.25 * LUCEAT_ALPHA;
    color.a = min(color.a, 1.0);
    color.a *= 1.0 - linear_fog_value(vertexDistance, 0, FogCloudsEnd);
    fragColor = color;
}
