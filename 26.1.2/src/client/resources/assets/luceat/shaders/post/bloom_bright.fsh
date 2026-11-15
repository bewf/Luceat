#version 330

uniform sampler2D InSampler;

layout(std140) uniform SamplerInfo {
    vec2 OutSize;
    vec2 InSize;
};

layout(std140) uniform BloomConfig {
    vec4 Threshold;
};

in vec2 texCoord;

out vec4 fragColor;

vec3 bright(vec2 uv) {
    vec3 colour = texture(InSampler, uv).rgb;
    float peak = max(colour.r, max(colour.g, colour.b));
    float gate = smoothstep(Threshold.x - Threshold.y, Threshold.x + Threshold.y, peak);
    float fade = 1.0 / (1.0 + peak);
    return colour * gate * fade * 2.0;
}

void main() {
    vec2 reach = (InSize / OutSize) / (3.0 * InSize);
    vec3 sum = vec3(0.0);
    for (int y = -1; y <= 1; y++) {
        for (int x = -1; x <= 1; x++) {
            sum += bright(texCoord + vec2(float(x), float(y)) * reach);
        }
    }
    fragColor = vec4(sum / 9.0, 1.0);
}
