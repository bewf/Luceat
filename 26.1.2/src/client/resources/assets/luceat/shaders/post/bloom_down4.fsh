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
    vec3 sum = vec3(0.0);
    for (int y = 0; y < 4; y++) {
        for (int x = 0; x < 4; x++) {
            sum += texture(InSampler, texCoord + (vec2(float(x), float(y)) - 1.5) * texel).rgb;
        }
    }
    fragColor = vec4(sum / 16.0, 1.0);
}
