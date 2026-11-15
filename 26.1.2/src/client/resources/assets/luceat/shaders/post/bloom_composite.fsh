#version 330

uniform sampler2D MainSampler;
uniform sampler2D NearSampler;
uniform sampler2D WideSampler;

layout(std140) uniform SamplerInfo {
    vec2 OutSize;
    vec2 MainSize;
    vec2 NearSize;
    vec2 WideSize;
};

layout(std140) uniform BloomConfig {
    vec4 Mix;
};

in vec2 texCoord;

out vec4 fragColor;

void main() {
    vec4 scene = texture(MainSampler, texCoord);
    vec3 glow = texture(NearSampler, texCoord).rgb * Mix.x + texture(WideSampler, texCoord).rgb * Mix.y;
    glow = min(glow, vec3(1.0));
    fragColor = vec4(1.0 - (1.0 - scene.rgb) * (1.0 - glow), scene.a);
}
