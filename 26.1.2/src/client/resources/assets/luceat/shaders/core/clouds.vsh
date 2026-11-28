#version 330

#moj_import <minecraft:dynamictransforms.glsl>
#moj_import <minecraft:projection.glsl>
#moj_import <minecraft:globals.glsl>

layout(std140) uniform CloudInfo {
    vec4 CloudColor;
    vec3 CloudOffset;
    vec3 CellSize;
};

uniform isamplerBuffer CloudFaces;

out vec3 rel;
flat out vec4 cloudTint;
flat out vec2 camera;
flat out float slabBase;
flat out int keepFaces;

const vec2[] corners = vec2[](vec2(-1.0, -1.0), vec2(1.0, -1.0), vec2(1.0, 1.0), vec2(-1.0, 1.0));

void main() {
    keepFaces = texelFetch(CloudFaces, 0).r;
    cloudTint = CloudColor;
    slabBase = CloudOffset.y;

    ivec2 block = ((CameraBlockPos.xz % 720) + 720) % 720;
    float scroll = GameTime * 24000.0 * 0.03;
    camera = vec2(block) + CameraOffset.xz + vec2(scroll, 3.96);

    if (gl_VertexID > 3) {
        rel = vec3(0.0);
        gl_Position = vec4(0.0);
        return;
    }

    vec4 unprojected = inverse(ProjMat) * vec4(corners[gl_VertexID], 0.5, 1.0);
    vec3 viewPoint = unprojected.xyz / unprojected.w;
    viewPoint *= -0.2 / viewPoint.z;
    rel = transpose(mat3(ModelViewMat)) * viewPoint;
    gl_Position = ProjMat * vec4(viewPoint, 1.0);
}
