package me.bewf.luceat.client.shader;

import net.minecraft.resources.Identifier;

public final class EntityShaderPatch {
	private static final String VERTEX_LIGHT = "lightMapColor = sample_lightmap(Sampler2, UV2);";
	private static final String FRAGMENT_LIGHT = "    color *= lightMapColor;";
	private static final String FRAGMENT_ENTRY = "void main() {";

	private static final String GLOW = """
		vec3 luceatItemGlow(vec3 lit, float lum, float tag) {
		    if (tag < 0.5) {
		        return lit;
		    }
		    float glow = tag < 1.5 ? 0.5 : (tag < 2.5 ? 1.0 : 2.0);
		    float k = smoothstep(0.42, 0.8, lum) * glow;
		    return mix(lit * (1.0 + 0.7 * k), vec3(1.0, 0.96, 0.88), clamp(0.4 * k, 0.0, 0.8));
		}

		""";

	private EntityShaderPatch() {
	}

	public static String apply(Identifier id, String source) {
		if (source == null || !"minecraft".equals(id.getNamespace()) || !"core/entity".equals(id.getPath())) {
			return source;
		}
		if (source.contains("in Position;") || source.contains("in vec3 Position;")) {
			if (source.indexOf(VERTEX_LIGHT) < 0 || source.contains("luceatGlow")) {
				return source;
			}
			return source.replace(VERTEX_LIGHT, "lightMapColor = sample_lightmap(Sampler2, UV2 & ivec2(-16));\n    lightMapColor.a = 1.0 - 0.1 * float(UV2.x & 15);");
		}
		if (source.indexOf(FRAGMENT_LIGHT) < 0 || source.indexOf(FRAGMENT_ENTRY) < 0 || source.contains("luceatItemGlow")) {
			return source;
		}
		String lit = "    float luceatLum = dot(color.rgb, vec3(0.299, 0.587, 0.114));\n"
			+ "    float luceatTag = floor((1.0 - lightMapColor.a) * 10.0 + 0.5);\n"
			+ "    color *= vec4(lightMapColor.rgb, 1.0);\n"
			+ "    color.rgb = luceatItemGlow(color.rgb, luceatLum, luceatTag);";
		return source.replace(FRAGMENT_LIGHT, lit).replace(FRAGMENT_ENTRY, GLOW + FRAGMENT_ENTRY);
	}
}
