#version 330

uniform sampler2D InSampler;
uniform sampler2D DepthSampler;

layout(std140) uniform SamplerInfo {
    vec2 OutSize;
    vec2 InSize;
    vec2 DepthSize;
};

layout(std140) uniform BloomConfig {
    vec4 Threshold;
};

in vec2 texCoord;

out vec4 fragColor;

float peakOf(vec3 colour) {
    return max(colour.r, max(colour.g, colour.b));
}

vec3 bright(vec2 uv, float surround) {
    vec3 colour = texture(InSampler, uv).rgb;
    float peak = peakOf(colour);
    float sky = step(0.99999, texture(DepthSampler, uv).r);
    float white = min(colour.r, min(colour.g, colour.b));
    float gateSky = smoothstep(0.82, 0.97, white);
    float gateNear = smoothstep(Threshold.x - Threshold.y, Threshold.x + Threshold.y, peak)
        * smoothstep(0.10, 0.32, peak - surround);
    float gate = mix(gateNear, gateSky, sky);
    return colour * gate * (2.0 / (1.0 + peak));
}

void main() {
    vec2 reach = (InSize / OutSize) / (3.0 * InSize);
    vec2 ring = 6.0 / OutSize;
    float surround = 0.0;
    for (int i = 0; i < 8; i++) {
        float angle = float(i) * 0.7853982;
        surround += peakOf(texture(InSampler, texCoord + vec2(cos(angle), sin(angle)) * ring).rgb);
    }
    surround *= 0.125;
    vec3 sum = vec3(0.0);
    for (int y = -1; y <= 1; y++) {
        for (int x = -1; x <= 1; x++) {
            sum += bright(texCoord + vec2(float(x), float(y)) * reach, surround);
        }
    }
    fragColor = vec4(sum / 9.0, 1.0);
}
