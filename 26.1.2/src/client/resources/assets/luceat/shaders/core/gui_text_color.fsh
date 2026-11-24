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
    vec2 reach = fwidth(texCoord0) * 0.25;
    vec4 texel = texture(Sampler0, texCoord0);
    float across = max(texture(Sampler0, texCoord0 + vec2(reach.x, 0.0)).a, texture(Sampler0, texCoord0 - vec2(reach.x, 0.0)).a);
    float along = max(texture(Sampler0, texCoord0 + vec2(0.0, reach.y)).a, texture(Sampler0, texCoord0 - vec2(0.0, reach.y)).a);
    texel.a = 1.0 - pow(1.0 - max(texel.a, max(across * 0.8, along * 0.4)), 1.7);
    vec4 color = texel * vertexColor * ColorModulator;
    if (color.a < 0.004) {
        discard;
    }
    fragColor = apply_fog(color, sphericalVertexDistance, cylindricalVertexDistance, FogEnvironmentalStart, FogEnvironmentalEnd, FogRenderDistanceStart, FogRenderDistanceEnd, FogColor);
}
