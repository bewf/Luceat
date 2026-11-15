#version 330

uniform sampler2D InSampler;

layout(std140) uniform SamplerInfo {
    vec2 OutSize;
    vec2 InSize;
};

layout(std140) uniform BlurConfig {
    vec4 Direction;
};

in vec2 texCoord;

out vec4 fragColor;

void main() {
    vec2 stride = Direction.xy / InSize;
    vec3 sum = texture(InSampler, texCoord).rgb * 0.2270270270;
    sum += texture(InSampler, texCoord + stride * 1.3846153846).rgb * 0.3162162162;
    sum += texture(InSampler, texCoord - stride * 1.3846153846).rgb * 0.3162162162;
    sum += texture(InSampler, texCoord + stride * 3.2307692308).rgb * 0.0702702703;
    sum += texture(InSampler, texCoord - stride * 3.2307692308).rgb * 0.0702702703;
    fragColor = vec4(sum, 1.0);
}
