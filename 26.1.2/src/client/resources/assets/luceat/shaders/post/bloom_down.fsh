#version 330

uniform sampler2D InSampler;

layout(std140) uniform SamplerInfo {
    vec2 OutSize;
    vec2 InSize;
};

in vec2 texCoord;

out vec4 fragColor;

void main() {
    vec2 texel = 1.0 / InSize;
    vec3 sum = texture(InSampler, texCoord + texel * vec2(-1.0, -1.0)).rgb;
    sum += texture(InSampler, texCoord + texel * vec2(1.0, -1.0)).rgb;
    sum += texture(InSampler, texCoord + texel * vec2(-1.0, 1.0)).rgb;
    sum += texture(InSampler, texCoord + texel * vec2(1.0, 1.0)).rgb;
    fragColor = vec4(sum * 0.25, 1.0);
}
