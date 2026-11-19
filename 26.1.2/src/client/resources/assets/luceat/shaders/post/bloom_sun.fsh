#version 330

uniform sampler2D InSampler;
uniform sampler2D DepthSampler;

layout(std140) uniform SamplerInfo {
    vec2 OutSize;
    vec2 InSize;
    vec2 DepthSize;
};

in vec2 texCoord;

out vec4 fragColor;

void main() {
    vec2 spacing = (InSize / OutSize) / (4.0 * InSize);
    vec3 sum = vec3(0.0);
    for (int y = 0; y < 4; y++) {
        for (int x = 0; x < 4; x++) {
            vec2 uv = texCoord + (vec2(float(x), float(y)) - 1.5) * spacing;
            vec3 colour = texture(InSampler, uv).rgb;
            float sky = step(0.99999, texture(DepthSampler, uv).r);
            float white = min(colour.r, min(colour.g, colour.b));
            float warm = 1.0 - smoothstep(0.0, 0.06, colour.b - colour.r);
            vec3 tint = mix(vec3(1.0), vec3(1.0, 0.88, 0.62), warm);
            sum += colour * tint * sky * smoothstep(0.86, 0.99, white);
        }
    }
    fragColor = vec4(sum / 16.0, 1.0);
}
