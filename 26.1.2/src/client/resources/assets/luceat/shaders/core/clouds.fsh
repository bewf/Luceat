#version 330

#moj_import <minecraft:fog.glsl>

in float vertexDistance;
in vec4 vertexColor;
in vec2 cloudPos;
in float heightFrac;
flat in float cellDensity;
flat in int faceKind;

out vec4 fragColor;

float hash21(vec2 p) {
    p = fract(p * vec2(123.34, 456.21));
    p += dot(p, p + 45.32);
    return fract(p.x * p.y);
}

void main() {
    float dense = smoothstep(0.36, 0.64, cellDensity);
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
    color.rgb *= shade;
    color.a *= alpha * 1.25;
    color.a = min(color.a, 1.0);
    color.a *= 1.0 - linear_fog_value(vertexDistance, 0, FogCloudsEnd);
    fragColor = color;
}
