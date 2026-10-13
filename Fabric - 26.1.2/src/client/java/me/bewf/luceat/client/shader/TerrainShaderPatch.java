package me.bewf.luceat.client.shader;

import me.bewf.luceat.Luceat;
import me.bewf.luceat.client.light.LightBuffer;
import me.bewf.luceat.client.shadow.ShadowVolume;

import net.minecraft.resources.Identifier;

public final class TerrainShaderPatch {
	private static final String NAMESPACE = "sodium";
	private static final String VERTEX_PATH = "blocks/block_layer_opaque.vsh";
	private static final String FRAGMENT_PATH = "blocks/block_layer_opaque.fsh";

	private static final String FRAGMENT_LIGHTING = """

			in vec3 v_LuceatPos;

			layout(std140) uniform @BLOCK@ {
			    vec4 u_LuceatState;
			    ivec4 u_LuceatCamBlock;
			    vec4 u_LuceatCamFrac;
			    vec4 u_LuceatPosRadius[@LIGHTS@];
			    vec4 u_LuceatColor[@LIGHTS@];
			};

			uniform sampler3D @SAMPLER@;

			ivec3 luceatWrap(ivec3 cell) {
			    return cell - @DIM@ * ivec3(floor(vec3(cell) / float(@DIM@)));
			}

			float luceatVisibility(vec3 pos, vec3 lightPos) {
			    vec3 delta = lightPos - pos;
			    float len = length(delta);
			    if (len < 0.2) {
			        return 1.0;
			    }
			    vec3 dir = delta / len;
			    vec3 origin = pos + dir * 0.12 + u_LuceatCamFrac.xyz;
			    vec3 target = lightPos + u_LuceatCamFrac.xyz;
			    ivec3 cell = ivec3(floor(origin));
			    vec3 sgn = vec3(dir.x >= 0.0 ? 1.0 : -1.0, dir.y >= 0.0 ? 1.0 : -1.0, dir.z >= 0.0 ? 1.0 : -1.0);
			    vec3 safe = max(abs(dir), vec3(1.0e-5));
			    vec3 tDelta = 1.0 / safe;
			    vec3 tMax = (vec3(cell) + max(sgn, 0.0) - origin) * sgn / safe;
			    ivec3 stepCell = ivec3(sgn);
			    float limit = len - 0.12;
			    float trans = 1.0;
			    for (int i = 0; i < 96; i++) {
			        if (distance(vec3(cell) + 0.5, target) > 0.9) {
			            float o = texelFetch(@SAMPLER@, luceatWrap(cell + u_LuceatCamBlock.xyz), 0).r;
			            trans *= 1.0 - o;
			            if (trans < 0.02) {
			                return 0.0;
			            }
			        }
			        float tNext = min(tMax.x, min(tMax.y, tMax.z));
			        if (tNext >= limit) {
			            break;
			        }
			        if (tMax.x <= tMax.y && tMax.x <= tMax.z) {
			            cell.x += stepCell.x;
			            tMax.x += tDelta.x;
			        } else if (tMax.y <= tMax.z) {
			            cell.y += stepCell.y;
			            tMax.y += tDelta.y;
			        } else {
			            cell.z += stepCell.z;
			            tMax.z += tDelta.z;
			        }
			    }
			    return trans;
			}

			vec3 luceatSoft(vec3 x, float cap) {
			    float knee = cap * 0.7;
			    vec3 over = max(x - vec3(knee), vec3(0.0));
			    return min(x, vec3(knee)) + (cap - knee) * (vec3(1.0) - exp(-over / (cap - knee)));
			}

			vec3 luceatLight(vec3 pos) {
			    vec3 total = vec3(0.0);
			    int count = int(u_LuceatState.x);
			    for (int i = 0; i < count; i++) {
			        vec4 light = u_LuceatPosRadius[i];
			        float level = max(light.w - distance(pos, light.xyz), 0.0) / 15.0;
			        if (level <= 0.0) {
			            continue;
			        }
			        float curved = level / (4.0 - 3.0 * level);
			        float visible = u_LuceatState.y > 0.5 ? luceatVisibility(pos, light.xyz) : 1.0;
			        total += u_LuceatColor[i].rgb * (u_LuceatState.z * curved * visible);
			    }
			    return luceatSoft(total, u_LuceatState.w);
			}
			"""
			.replace("@BLOCK@", LightBuffer.BLOCK_NAME)
			.replace("@LIGHTS@", Integer.toString(LightBuffer.MAX_LIGHTS))
			.replace("@SAMPLER@", ShadowVolume.SAMPLER)
			.replace("@DIM@", Integer.toString(ShadowVolume.DIMENSION));

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
