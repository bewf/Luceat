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
		if (source == null || !"minecraft".equals(id.getNamespace())) {
			return source;
		}
		if ("core/item".equals(id.getPath())) {
			return applyItem(source);
		}
		if (!"core/entity".equals(id.getPath())) {
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

	private static String applyItem(String source) {
		if (source.contains("luceatTag")) {
			return source;
		}
		if (source.contains("in vec3 Position;")) {
			String light = "sample_lightmap(Sampler2, UV2)";
			String out = "out vec2 texCoord0;";
			String uv = "texCoord0 = UV0;";
			if (source.indexOf(light) < 0 || source.indexOf(out) < 0 || source.indexOf(uv) < 0) {
				return source;
			}
			return source.replace(light, "sample_lightmap(Sampler2, UV2 & ivec2(-16))")
				.replace(out, out + "\nflat out float luceatTag;")
				.replace(uv, uv + "\n    luceatTag = float(UV2.x & 15);");
		}
		String in = "in vec2 texCoord0;";
		String mul = "    color *= vertexColor * ColorModulator;";
		if (source.indexOf(in) < 0 || source.indexOf(mul) < 0 || source.indexOf(FRAGMENT_ENTRY) < 0) {
			return source;
		}
		String lit = "    float luceatLum = dot(color.rgb, vec3(0.299, 0.587, 0.114));\n"
			+ "    color *= vertexColor * ColorModulator;\n"
			+ "    color.rgb = luceatItemGlow(color.rgb, luceatLum, luceatTag);";
		return source.replace(in, in + "\nflat in float luceatTag;").replace(mul, lit).replace(FRAGMENT_ENTRY, GLOW + FRAGMENT_ENTRY);
	}
}
