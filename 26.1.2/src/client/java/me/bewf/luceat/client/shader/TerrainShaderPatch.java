package me.bewf.luceat.client.shader;

import me.bewf.luceat.Luceat;
import me.bewf.luceat.client.light.LightBuffer;
import me.bewf.luceat.client.shadow.PatternAtlas;
import me.bewf.luceat.client.shadow.ShapeMasks;
import me.bewf.luceat.client.shadow.ShadowVolume;

import net.minecraft.resources.Identifier;

public final class TerrainShaderPatch {
	private static final String NAMESPACE = "sodium";
	private static final String VERTEX_PATH = "blocks/block_layer_opaque.vsh";
	private static final String FRAGMENT_PATH = "blocks/block_layer_opaque.fsh";

	private static final String FRAGMENT_LIGHTING = """

			in vec3 v_LuceatPos;
			in vec4 v_LuceatBase;

			layout(std140) uniform @BLOCK@ {
			    vec4 u_LuceatState;
			    vec4 u_LuceatOptions;
			    vec4 u_LuceatExtra;
			    ivec4 u_LuceatCamBlock;
			    vec4 u_LuceatCamFrac;
			    ivec4 u_LuceatVol;
			    vec4 u_LuceatPosRadius[@LIGHTS@];
			    vec4 u_LuceatColor[@LIGHTS@];
			};

			uniform usampler3D @SAMPLER@;
			uniform sampler2D @PATTERNS@;
			uniform sampler2D @SHAPES@;

			ivec3 luceatWrap(ivec3 cell) {
			    ivec3 m = cell % @DIM@;
			    return m + @DIM@ * ivec3(lessThan(m, ivec3(0)));
			}

			vec3 luceatPattern(uint id, vec3 hit, ivec3 cell, int axis, bool positiveFace) {
			    vec3 local = clamp(hit - vec3(cell), 0.0, 0.999);
			    vec2 uv;
			    if (axis == 0) {
			        uv = vec2(positiveFace ? 1.0 - local.z : local.z, 1.0 - local.y);
			    } else if (axis == 1) {
			        uv = vec2(local.x, positiveFace ? local.z : 1.0 - local.z);
			    } else {
			        uv = vec2(positiveFace ? local.x : 1.0 - local.x, 1.0 - local.y);
			    }
			    int index = int(id) - 2;
			    ivec2 tile = ivec2(index & 63, index >> 6) * 16;
			    return texelFetch(@PATTERNS@, tile + min(ivec2(uv * 16.0), ivec2(15)), 0).rgb;
			}

			vec3 luceatNormal(vec3 pos) {
			    vec3 n = cross(dFdx(pos), dFdy(pos));
			    float size = length(n);
			    if (size < 1.0e-12) {
			        return vec3(0.0, 1.0, 0.0);
			    }
			    n /= size;
			    n = dot(n, pos) > 0.0 ? -n : n;
			    vec3 a = abs(n);
			    float m = max(a.x, max(a.y, a.z));
			    if (m > 0.93) {
			        n = vec3(a.x == m ? sign(n.x) : 0.0, a.y == m ? sign(n.y) : 0.0, a.z == m ? sign(n.z) : 0.0);
			    }
			    return n;
			}

			vec3 luceatShape(uint tile, vec3 origin, vec3 dir, ivec3 cell, float t0, float t1, vec3 sgn, vec3 safe) {
			    vec3 local = (origin + dir * t0 - vec3(cell)) * @RES@.0 + dir * 0.01;
			    local = clamp(local, vec3(0.0), vec3(@RES@.0 - 0.001));
			    ivec3 v = ivec3(floor(local));
			    vec3 tDelta = 1.0 / (safe * @RES@.0);
			    vec3 tMax = vec3(t0) + (vec3(v) + max(sgn, 0.0) - local) * sgn / (safe * @RES@.0);
			    ivec3 stepCell = ivec3(sgn);
			    ivec2 base = ivec2(int(tile % @PER_ROW@u) * @TILE_W@, int(tile / @PER_ROW@u) * @RES@);
			    vec3 trans = vec3(1.0);
			    float t = t0;
			    for (int k = 0; k < @STEPS@; k++) {
			        float tNext = min(tMax.x, min(tMax.y, tMax.z));
			        float tEnd = min(tNext, t1);
			        if (tEnd > 0.02) {
			            vec3 vt = texelFetch(@SHAPES@, base + ivec2(v.z * @RES@ + v.x, v.y), 0).rgb;
			            if (vt.r < 0.999 || vt.g < 0.999 || vt.b < 0.999) {
			                if (max(vt.r, max(vt.g, vt.b)) < 0.03) {
			                    return vec3(0.0);
			                }
			                trans *= pow(max(vt, vec3(0.03)), vec3(max(tEnd - max(t, 0.02), 0.0) * @RES@.0));
			            }
			        }
			        if (tNext >= t1) {
			            break;
			        }
			        t = tNext;
			        if (tMax.x <= tMax.y && tMax.x <= tMax.z) {
			            v.x += stepCell.x;
			            tMax.x += tDelta.x;
			        } else if (tMax.y <= tMax.z) {
			            v.y += stepCell.y;
			            tMax.y += tDelta.y;
			        } else {
			            v.z += stepCell.z;
			            tMax.z += tDelta.z;
			        }
			        if (any(lessThan(v, ivec3(0))) || any(greaterThan(v, ivec3(@RES@ - 1)))) {
			            break;
			        }
			    }
			    return trans;
			}

			vec3 luceatVisibility(vec3 pos, vec3 normal, vec3 lightPos) {
			    vec3 delta = lightPos - pos;
			    float len = length(delta);
			    if (len < 0.15) {
			        return vec3(1.0);
			    }
			    vec3 dir = delta / len;
			    vec3 origin = pos + normal * 0.02 + dir * 0.01 + u_LuceatCamFrac.xyz;
			    vec3 target = lightPos + u_LuceatCamFrac.xyz;
			    ivec3 cell = ivec3(floor(origin));
			    ivec3 lightCell = ivec3(floor(target));
			    vec3 sgn = vec3(dir.x >= 0.0 ? 1.0 : -1.0, dir.y >= 0.0 ? 1.0 : -1.0, dir.z >= 0.0 ? 1.0 : -1.0);
			    vec3 safe = max(abs(dir), vec3(1.0e-5));
			    vec3 tDelta = 1.0 / safe;
			    vec3 tMax = (vec3(cell) + max(sgn, 0.0) - origin) * sgn / safe;
			    ivec3 stepCell = ivec3(sgn);
			    float limit = len;
			    float tEntry = 0.0;
			    int entryAxis = 0;
			    vec3 trans = vec3(1.0);
			    for (int i = 0; i < 96; i++) {
			        float tExit = min(tMax.x, min(tMax.y, tMax.z));
			        int exitAxis = tMax.x <= tMax.y && tMax.x <= tMax.z ? 0 : (tMax.y <= tMax.z ? 1 : 2);
			        if (any(notEqual(cell, lightCell))) {
			            ivec3 world = cell + u_LuceatCamBlock.xyz;
			            ivec3 offset = (world >> 4) - u_LuceatVol.xyz;
			            bool inside = all(lessThanEqual(abs(offset), ivec3(u_LuceatVol.w)));
			            uint id = inside ? texelFetch(@SAMPLER@, luceatWrap(world), 0).r : 0u;
			            if (id == 1u) {
			                return vec3(0.0);
			            }
			            if (id > 1u) {
			                if ((id & 32768u) != 0u) {
			                    ivec3 gap = abs(cell - lightCell);
			                    if (max(gap.x, max(gap.y, gap.z)) > 1) {
			                        trans *= luceatShape(id & 32767u, origin, dir, cell, tEntry, min(tExit, limit), sgn, safe);
			                    }
			                } else if (i > 0) {
			                    bool enterPositive = sgn[entryAxis] < 0.0;
			                    bool exitPositive = sgn[exitAxis] > 0.0;
			                    vec3 through = luceatPattern(id, origin + dir * tEntry, cell, entryAxis, enterPositive);
			                    if (tExit < limit) {
			                        through *= luceatPattern(id, origin + dir * tExit, cell, exitAxis, exitPositive);
			                    }
			                    trans *= through;
			                }
			                if (max(trans.r, max(trans.g, trans.b)) < 0.02) {
			                    return vec3(0.0);
			                }
			            }
			        }
			        if (tExit >= limit) {
			            break;
			        }
			        tEntry = tExit;
			        entryAxis = exitAxis;
			        if (exitAxis == 0) {
			            cell.x += stepCell.x;
			            tMax.x += tDelta.x;
			        } else if (exitAxis == 1) {
			            cell.y += stepCell.y;
			            tMax.y += tDelta.y;
			        } else {
			            cell.z += stepCell.z;
			            tMax.z += tDelta.z;
			        }
			    }
			    return trans;
			}

			vec3 luceatShadow(vec3 pos, vec3 normal, vec3 lightPos) {
			    vec3 centre = luceatVisibility(pos, normal, lightPos);
			    float radius = u_LuceatExtra.z;
			    if (radius <= 0.0 || max(centre.r, max(centre.g, centre.b)) > 0.98) {
			        return centre;
			    }
			    vec3 dir = normalize(lightPos - pos);
			    vec3 side = normalize(cross(dir, abs(dir.y) > 0.9 ? vec3(1.0, 0.0, 0.0) : vec3(0.0, 1.0, 0.0)));
			    vec3 up = cross(dir, side);
			    ivec3 home = ivec3(floor(lightPos + u_LuceatCamFrac.xyz));
			    vec3 total = centre;
			    for (int k = 0; k < 3; k++) {
			        float angle = 2.0943951 * float(k) + 0.5;
			        vec3 spot = lightPos + (side * cos(angle) + up * sin(angle)) * radius;
			        if (ivec3(floor(spot + u_LuceatCamFrac.xyz)) != home) {
			            spot = lightPos;
			        }
			        total += luceatVisibility(pos, normal, spot);
			    }
			    return total * 0.25;
			}

			vec3 luceatSoft(vec3 x, float cap) {
			    float knee = cap * 0.7;
			    vec3 over = max(x - vec3(knee), vec3(0.0));
			    return min(x, vec3(knee)) + (cap - knee) * (vec3(1.0) - exp(-over / (cap - knee)));
			}

			vec3 luceatLight(vec3 pos, vec3 normal, vec4 vertexColor, vec4 baseColor) {
			    float exposure = max(baseColor.r, max(baseColor.g, baseColor.b));
			    float fade = 1.0 - u_LuceatOptions.x * smoothstep(0.25, 0.95, exposure);
			    if (fade <= 0.01) {
			        return vec3(0.0);
			    }
			    float shadowFade = u_LuceatState.y > 0.5 ? 1.0 - smoothstep(0.6 * u_LuceatExtra.y, u_LuceatExtra.y, length(pos)) : 0.0;
			    bool shadowsOn = shadowFade > 0.01;
			    vec3 total = vec3(0.0);
			    float placedCover = 0.0;
			    int count = int(u_LuceatState.x);
			    for (int i = 0; i < count; i++) {
			        vec4 light = u_LuceatPosRadius[i];
			        vec3 toLight = light.xyz - pos;
			        float dist = length(toLight);
			        float level = max(light.w - dist, 0.0) / 15.0;
			        if (level <= 0.0) {
			            continue;
			        }
			        float ndotl = dot(normal, toLight / max(dist, 1.0e-4));
			        float facing = mix(1.0, smoothstep(-0.35, 0.15, ndotl), u_LuceatOptions.y);
			        if (facing <= 0.0) {
			            continue;
			        }
			        bool placed = u_LuceatColor[i].w > 0.5;
			        float lambert = mix(1.0, 0.4 + 0.6 * clamp(ndotl, 0.0, 1.0), u_LuceatOptions.y);
			        float curved = level / (4.0 - 3.0 * level);
			        float power = curved * lambert * facing * (placed ? u_LuceatExtra.x : 1.0);
			        vec3 tint = u_LuceatColor[i].rgb;
			        if (placed) {
			            placedCover += curved * facing;
			        }
			        vec3 visible = vec3(1.0);
			        if (shadowsOn && power * u_LuceatState.z * fade * max(tint.r, max(tint.g, tint.b)) > 0.01) {
			            visible = mix(vec3(1.0), luceatShadow(pos, normal, light.xyz), u_LuceatOptions.z * shadowFade);
			        }
			        total += tint * (u_LuceatState.z * power) * visible;
			    }
			    vec3 blockPart = max(vertexColor.rgb - baseColor.rgb, vec3(0.0));
			    float coverage = clamp(placedCover * fade / max(dot(blockPart, vec3(0.299, 0.587, 0.114)), 0.02), 0.0, 1.0);
			    return luceatSoft(total, u_LuceatState.w) * fade - blockPart * (u_LuceatOptions.w * coverage);
			}
			"""
			.replace("@BLOCK@", LightBuffer.BLOCK_NAME)
			.replace("@LIGHTS@", Integer.toString(LightBuffer.MAX_LIGHTS))
			.replace("@SAMPLER@", ShadowVolume.SAMPLER)
			.replace("@PATTERNS@", PatternAtlas.SAMPLER)
			.replace("@SHAPES@", ShapeMasks.SAMPLER)
			.replace("@DIM@", Integer.toString(ShadowVolume.DIMENSION))
			.replace("@RES@", Integer.toString(ShapeMasks.RES))
			.replace("@PER_ROW@", Integer.toString(ShapeMasks.PER_ROW))
			.replace("@TILE_W@", Integer.toString(ShapeMasks.TILE_WIDTH))
			.replace("@STEPS@", Integer.toString(ShapeMasks.RES * 3));

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
		String patched = replaceOnce(source, "flat out uint v_Material;", "flat out uint v_Material;\nout vec3 v_LuceatPos;\nout vec4 v_LuceatBase;");
		patched = replaceOnce(patched, "vec3 position = _vert_position + translation;", "vec3 position = _vert_position + translation;\n    v_LuceatPos = position;");
		return replaceOnce(patched, "v_Color = _vert_color * texture(u_LightTex, _vert_tex_light_coord);", "v_Color = _vert_color * texture(u_LightTex, _vert_tex_light_coord);\n    v_LuceatBase = _vert_color * texture(u_LightTex, vec2(0.0, _vert_tex_light_coord.y));");
	}

	private static String patchFragment(String source) {
		String patched = replaceOnce(source, "flat in uint v_Material;", "flat in uint v_Material;\n" + FRAGMENT_LIGHTING);
		patched = replaceOnce(patched, "color *= v_Color;", "vec3 luceatAlbedo = color.rgb;\n    vec3 luceatFaceNormal = luceatNormal(v_LuceatPos);\n    color *= v_Color;");
		return replaceOnce(patched, "fragColor = _linearFog(", "color.rgb += luceatAlbedo * luceatLight(v_LuceatPos, luceatFaceNormal, v_Color, v_LuceatBase);\n\n    fragColor = _linearFog(");
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
