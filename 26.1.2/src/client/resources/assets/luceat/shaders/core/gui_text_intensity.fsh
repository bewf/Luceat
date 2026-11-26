#version 330

#moj_import <minecraft:fog.glsl>
#moj_import <minecraft:dynamictransforms.glsl>

uniform sampler2D Sampler0;

in float sphericalVertexDistance;
in float cylindricalVertexDistance;
in vec4 vertexColor;
in vec2 texCoord0;

out vec4 fragColor;

void main() {
    vec2 foot = fwidth(texCoord0) * 0.33;
    float seen = 0.0;
    for (int ix = -1; ix <= 1; ix++) {
        for (int iy = -1; iy <= 1; iy++) {
            seen += texture(Sampler0, texCoord0 + vec2(float(ix), float(iy)) * foot).r;
        }
    }
    float coverage = pow(clamp(seen / 9.0, 0.0, 1.0), 0.65);
    vec4 color = vec4(coverage) * vertexColor * ColorModulator;
    if (color.a < 0.004) {
        discard;
    }
    fragColor = apply_fog(color, sphericalVertexDistance, cylindricalVertexDistance, FogEnvironmentalStart, FogEnvironmentalEnd, FogRenderDistanceStart, FogRenderDistanceEnd, FogColor);
}
