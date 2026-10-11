package me.bewf.luceat.client.shader;

import me.bewf.luceat.Luceat;
import me.bewf.luceat.client.light.LightBuffer;

import net.minecraft.resources.Identifier;

public final class TerrainShaderPatch {
	private static final String NAMESPACE = "sodium";
	private static final String VERTEX_PATH = "blocks/block_layer_opaque.vsh";
	private static final String FRAGMENT_PATH = "blocks/block_layer_opaque.fsh";

	private static final String FRAGMENT_LIGHTING = """

			in vec3 v_LuceatPos;

			layout(std140) uniform %s {
			    vec4 u_LuceatCount;
			    vec4 u_LuceatPosRadius[%d];
			    vec4 u_LuceatColor[%d];
			};

			vec3 luceatLight(vec3 pos) {
			    vec3 total = vec3(0.0);
			    int count = int(u_LuceatCount.x);
			    for (int i = 0; i < count; i++) {
			        vec4 light = u_LuceatPosRadius[i];
			        float level = max(light.w - distance(pos, light.xyz), 0.0) / 15.0;
			        total += u_LuceatColor[i].rgb * (1.2 * level / (4.0 - 3.0 * level));
			    }
			    return total;
			}
			""".formatted(LightBuffer.BLOCK_NAME, LightBuffer.MAX_LIGHTS, LightBuffer.MAX_LIGHTS);

	private static boolean vertexPatched;

	private TerrainShaderPatch() {
	}

	public static String apply(Identifier id, String source) {
		if (!NAMESPACE.equals(id.getNamespace())) {
			return source;
		}
		if (VERTEX_PATH.equals(id.getPath())) {
			String patched = patchVertex(source);
			vertexPatched = patched != null;
			return orOriginal(id, source, patched);
		}
		if (FRAGMENT_PATH.equals(id.getPath()) && vertexPatched) {
			return orOriginal(id, source, patchFragment(source));
		}
		return source;
	}

	private static String patchVertex(String source) {
		String patched = replaceOnce(source, "flat out uint v_Material;", "flat out uint v_Material;\nout vec3 v_LuceatPos;");
		return replaceOnce(patched, "vec3 position = _vert_position + translation;", "vec3 position = _vert_position + translation;\n    v_LuceatPos = position;");
	}

	private static String patchFragment(String source) {
		String patched = replaceOnce(source, "flat in uint v_Material;", "flat in uint v_Material;\n" + FRAGMENT_LIGHTING);
		patched = replaceOnce(patched, "color *= v_Color;", "vec3 luceatAlbedo = color.rgb;\n    color *= v_Color;");
		return replaceOnce(patched, "fragColor = _linearFog(", "color.rgb += luceatAlbedo * luceatLight(v_LuceatPos);\n\n    fragColor = _linearFog(");
	}

	private static String replaceOnce(String source, String anchor, String replacement) {
		if (source == null) {
			return null;
		}
		int first = source.indexOf(anchor);
		if (first < 0 || source.indexOf(anchor, first + 1) >= 0) {
			return null;
		}
		return source.replace(anchor, replacement);
	}

	private static String orOriginal(Identifier id, String source, String patched) {
		if (patched == null) {
			Luceat.LOGGER.warn("Could not patch shader {}", id);
			return source;
		}
		return patched;
	}
}
