package me.bewf.luceat.client.light;

import java.nio.ByteBuffer;
import java.util.List;

import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL12;
import org.lwjgl.opengl.GL13;
import org.lwjgl.opengl.GL15;
import org.lwjgl.opengl.GL20;
import org.lwjgl.opengl.GL42;
import org.lwjgl.opengl.GL43;

import me.bewf.luceat.Luceat;
import me.bewf.luceat.client.config.LuceatConfig;
import me.bewf.luceat.client.shadow.ShadowVolume;

import net.minecraft.world.phys.Vec3;

public final class LightSpread {
	public static final int SIZE = 64;
	public static final String SAMPLER = "u_LuceatSpread";
	public static final int UNIT = 9;

	private static final int VOXEL_UNIT = ShadowVolume.UNIT;
	private static final int REACH_STEPS = 24;

	private static final String BLOCK = """
			layout(std140) uniform LuceatLights {
			    vec4 u_LuceatState;
			    vec4 u_LuceatOptions;
			    vec4 u_LuceatExtra;
			    ivec4 u_LuceatCamBlock;
			    vec4 u_LuceatCamFrac;
			    ivec4 u_LuceatVol;
			    vec4 u_LuceatMore;
			    ivec4 u_LuceatGridOrigin;
			    vec4 u_LuceatSun;
			    vec4 u_LuceatSun2;
			    ivec4 u_LuceatSunOrigin;
			    ivec4 u_LuceatBakeOrigin;
			    vec4 u_LuceatBakeParams;
			    vec4 u_LuceatPosRadius[@LIGHTS@];
			    vec4 u_LuceatColor[@LIGHTS@];
			};
			""".replace("@LIGHTS@", Integer.toString(LightBuffer.MAX_LIGHTS));

	private static final String PROPAGATE = "#version 430 core\n" + BLOCK + """
			layout(local_size_x = 4, local_size_y = 4, local_size_z = 4) in;
			layout(rgba8, binding = 0) uniform readonly image3D uSource;
			layout(rgba8, binding = 1) uniform writeonly image3D uTarget;
			uniform usampler3D uVoxels;

			const ivec3 DIRS[6] = ivec3[6](ivec3(1, 0, 0), ivec3(-1, 0, 0), ivec3(0, 1, 0), ivec3(0, -1, 0), ivec3(0, 0, 1), ivec3(0, 0, -1));

			uint voxel(ivec3 world) {
			    ivec3 offset = (world >> 4) - u_LuceatVol.xyz;
			    if (any(greaterThan(abs(offset), ivec3(u_LuceatVol.w)))) {
			        return 0u;
			    }
			    ivec3 m = world % @DIM@;
			    m += @DIM@ * ivec3(lessThan(m, ivec3(0)));
			    return texelFetch(uVoxels, m, 0).r;
			}

			void main() {
			    ivec3 index = ivec3(gl_GlobalInvocationID);
			    ivec3 base = u_LuceatCamBlock.xyz - @HALF@;
			    ivec3 world = base + ((index - base) & @MASK@);
			    uint id = voxel(world);
			    vec3 best = vec3(0.0);
			    if (id != 1u) {
			        for (int d = 0; d < 6; d++) {
			            ivec3 next = world + DIRS[d];
			            if (any(lessThan(next, base)) || any(greaterThanEqual(next, base + @SIZE@))) {
			                continue;
			            }
			            best = max(best, imageLoad(uSource, next & @MASK@).rgb);
			        }
			        float m = max(best.r, max(best.g, best.b));
			        if (m > 0.0) {
			            float keep = id > 1u ? 0.85 : 1.0;
			            best *= max(m - 0.0455, 0.0) / m * keep;
			        }
			    }
			    imageStore(uTarget, index, vec4(best, 1.0));
			}
			""";

	private static final String INJECT = "#version 430 core\n" + BLOCK + """
			layout(local_size_x = 64) in;
			layout(rgba8, binding = 1) uniform image3D uTarget;

			void main() {
			    int i = int(gl_GlobalInvocationID.x);
			    if (i >= int(u_LuceatState.x)) {
			        return;
			    }
			    vec4 light = u_LuceatPosRadius[i];
			    ivec3 world = ivec3(floor(light.xyz + u_LuceatCamFrac.xyz)) + u_LuceatCamBlock.xyz;
			    ivec3 away = world - u_LuceatCamBlock.xyz;
			    if (any(greaterThanEqual(abs(away), ivec3(@HALF@ - 1)))) {
			        return;
			    }
			    vec3 value = u_LuceatColor[i].rgb * min(light.w / 22.0, 1.0);
			    ivec3 at = world & @MASK@;
			    vec3 old = imageLoad(uTarget, at).rgb;
			    imageStore(uTarget, at, vec4(max(old, value), 1.0));
			}
			""";

	private static final int[] textures = new int[2];
	private static int propagate;
	private static int inject;
	private static int current;
	private static boolean failed;
	private static long lastSignature = Long.MIN_VALUE;
	private static int restFrames;
	private static Boolean capable;
	private static String blocker = "";

	private LightSpread() {
	}

	private static String renderer = "";

	public static boolean supported() {
		if (failed) {
			return false;
		}
		if (capable == null) {
			try {
				org.lwjgl.opengl.GLCapabilities caps = GL.getCapabilities();
				boolean hardware = caps.OpenGL43 || (caps.GL_ARB_compute_shader && caps.GL_ARB_shader_image_load_store);
				renderer = String.valueOf(GL11.glGetString(GL11.GL_RENDERER)).trim();
				String text = (GL11.glGetString(GL11.GL_VENDOR) + " " + renderer).toLowerCase(java.util.Locale.ROOT);
				boolean forced = Boolean.getBoolean("luceat.forceSpread");
				boolean limits = hardware && GL11.glGetInteger(GL43.GL_MAX_COMPUTE_WORK_GROUP_INVOCATIONS) >= 64 && GL11.glGetInteger(GL12.GL_MAX_3D_TEXTURE_SIZE) >= SIZE && GL11.glGetInteger(GL42.GL_MAX_IMAGE_UNITS) >= 2;
				boolean software = text.contains("llvmpipe") || text.contains("softpipe") || text.contains("swrast") || text.contains("software") || text.contains("basic render") || text.contains("gdi generic");
				boolean intel = text.contains("intel");
				String reason = "";
				if (!hardware) {
					reason = "needs OpenGL 4.3 or the compute extension";
				} else if (!limits) {
					reason = "limits too low";
				} else if (software) {
					reason = "software renderer";
				} else if (intel && !forced) {
					reason = "driver crashes";
				}
				blocker = reason;
				capable = reason.isEmpty();
				Luceat.LOGGER.info("Luceat light spreading on {}: OpenGL 4.3 {}, compute {}, image load/store {}, limits {}, so it is {}", renderer, caps.OpenGL43, caps.GL_ARB_compute_shader, caps.GL_ARB_shader_image_load_store, limits, capable ? "available" : "off (" + reason + ")");
			} catch (RuntimeException error) {
				capable = false;
				blocker = "limits too low";
			}
		}
		return capable;
	}

	public static String blocker() {
		supported();
		return failed ? "turned off after a graphics error" : blocker;
	}

	public static String shortBlocker() {
		String reason = blocker();
		if (reason.isEmpty()) {
			return "";
		}
		if (failed) {
			return "Turned off after an error";
		}
		return switch (reason) {
			case "driver crashes" -> "Not supported on this GPU";
			case "software renderer" -> "Needs a real GPU";
			case "limits too low" -> "GPU limits too low";
			default -> "Needs OpenGL 4.3";
		};
	}

	public static String blockerDetail() {
		String reason = blocker();
		if (reason.isEmpty()) {
			return "";
		}
		if (failed) {
			return "A graphics error happened while spreading light, so Luceat turned it off for this session. Restart the game to try again.";
		}
		String card = renderer.isEmpty() ? "This graphics card" : renderer;
		return switch (reason) {
			case "driver crashes" -> "Light around corners runs as a compute shader. The graphics driver for " + card + " crashes on it, so Luceat keeps it off. Cards that lack OpenGL 4.3 compute support are turned off the same way.";
			case "software renderer" -> "Light around corners runs as a compute shader and needs a real graphics card. " + card + " is a software renderer.";
			case "limits too low" -> "Light around corners needs compute shader and 3D image limits that " + card + " does not offer.";
			default -> "Light around corners runs as a compute shader, which needs OpenGL 4.3 or the compute shader extension. " + card + " does not offer either.";
		};
	}

	public static long bytes() {
		return textures[0] == 0 ? 0L : 2L * SIZE * SIZE * SIZE * 4L;
	}

	private static long signature(List<LightSource> lights, Vec3 camera) {
		long h = 1469598103934665603L;
		h = (h ^ (long) Math.floor(camera.x)) * 1099511628211L;
		h = (h ^ (long) Math.floor(camera.y)) * 1099511628211L;
		h = (h ^ (long) Math.floor(camera.z)) * 1099511628211L;
		h = (h ^ ShadowVolume.revision()) * 1099511628211L;
		int count = Math.min(lights.size(), LightBuffer.MAX_LIGHTS);
		h = (h ^ count) * 1099511628211L;
		for (int i = 0; i < count; i++) {
			LightSource light = lights.get(i);
			h = (h ^ (long) Math.floor(light.x() * 2.0)) * 1099511628211L;
			h = (h ^ (long) Math.floor(light.y() * 2.0)) * 1099511628211L;
			h = (h ^ (long) Math.floor(light.z() * 2.0)) * 1099511628211L;
			h = (h ^ (long) (light.radius() * 0.2F)) * 1099511628211L;
			h = (h ^ (long) (light.red() * 7.0F) ^ (long) (light.green() * 7.0F) << 3 ^ (long) (light.blue() * 7.0F) << 6) * 1099511628211L;
		}
		return h;
	}

	public static void update(LightBuffer buffer, List<LightSource> lights, Vec3 camera) {
		if (!supported()) {
			return;
		}
		LuceatConfig config = LuceatConfig.get();
		int steps = config.spreadSteps;
		if (config.spreadRests) {
			long now = signature(lights, camera);
			if (now != lastSignature) {
				lastSignature = now;
				restFrames = (REACH_STEPS + steps - 1) / steps + 2;
			}
			if (restFrames <= 0) {
				return;
			}
			restFrames--;
		}
		int previousProgram = GL11.glGetInteger(GL20.GL_CURRENT_PROGRAM);
		int previousUnit = GL11.glGetInteger(GL13.GL_ACTIVE_TEXTURE);
		try {
			ensure();
			programs();
			GL13.glActiveTexture(GL13.GL_TEXTURE0 + VOXEL_UNIT);
			GL11.glBindTexture(GL12.GL_TEXTURE_3D, ShadowVolume.texture());
			for (int pass = 0; pass < steps; pass++) {
				int source = current;
				int target = 1 - current;
				GL20.glUseProgram(propagate);
				buffer.bind(propagate);
				GL20.glUniform1i(GL20.glGetUniformLocation(propagate, "uVoxels"), VOXEL_UNIT);
				GL42.glBindImageTexture(0, textures[source], 0, true, 0, GL15.GL_READ_ONLY, GL11.GL_RGBA8);
				GL42.glBindImageTexture(1, textures[target], 0, true, 0, GL15.GL_WRITE_ONLY, GL11.GL_RGBA8);
				GL43.glDispatchCompute(SIZE / 4, SIZE / 4, SIZE / 4);
				GL42.glMemoryBarrier(GL42.GL_SHADER_IMAGE_ACCESS_BARRIER_BIT);
				GL20.glUseProgram(inject);
				buffer.bind(inject);
				GL42.glBindImageTexture(1, textures[target], 0, true, 0, GL15.GL_READ_WRITE, GL11.GL_RGBA8);
				GL43.glDispatchCompute((LightBuffer.MAX_LIGHTS + 63) / 64, 1, 1);
				GL42.glMemoryBarrier(GL42.GL_SHADER_IMAGE_ACCESS_BARRIER_BIT | GL42.GL_TEXTURE_FETCH_BARRIER_BIT);
				current = target;
			}
		} catch (Throwable error) {
			failed = true;
			Luceat.LOGGER.warn("Luceat light spreading is not available on this graphics card", error);
		} finally {
			GL20.glUseProgram(previousProgram);
			GL13.glActiveTexture(previousUnit);
		}
	}

	public static void bind(int program) {
		ensure();
		int location = GL20.glGetUniformLocation(program, SAMPLER);
		if (location < 0) {
			return;
		}
		GL20.glUniform1i(location, UNIT);
		int previousUnit = GL11.glGetInteger(GL13.GL_ACTIVE_TEXTURE);
		GL13.glActiveTexture(GL13.GL_TEXTURE0 + UNIT);
		GL11.glBindTexture(GL12.GL_TEXTURE_3D, textures[current]);
		GL13.glActiveTexture(previousUnit);
	}

	private static void ensure() {
		if (textures[0] != 0) {
			return;
		}
		ByteBuffer zero = BufferUtils.createByteBuffer(SIZE * SIZE * SIZE * 4);
		int previousUnit = GL11.glGetInteger(GL13.GL_ACTIVE_TEXTURE);
		GL13.glActiveTexture(GL13.GL_TEXTURE0 + UNIT);
		for (int i = 0; i < 2; i++) {
			textures[i] = GL11.glGenTextures();
			GL11.glBindTexture(GL12.GL_TEXTURE_3D, textures[i]);
			GL11.glTexParameteri(GL12.GL_TEXTURE_3D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_LINEAR);
			GL11.glTexParameteri(GL12.GL_TEXTURE_3D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_LINEAR);
			GL11.glTexParameteri(GL12.GL_TEXTURE_3D, GL11.GL_TEXTURE_WRAP_S, GL11.GL_REPEAT);
			GL11.glTexParameteri(GL12.GL_TEXTURE_3D, GL11.GL_TEXTURE_WRAP_T, GL11.GL_REPEAT);
			GL11.glTexParameteri(GL12.GL_TEXTURE_3D, GL12.GL_TEXTURE_WRAP_R, GL11.GL_REPEAT);
			GL12.glTexImage3D(GL12.GL_TEXTURE_3D, 0, GL11.GL_RGBA8, SIZE, SIZE, SIZE, 0, GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, zero);
		}
		GL13.glActiveTexture(previousUnit);
	}

	private static void programs() {
		if (propagate == 0) {
			propagate = program(PROPAGATE);
			inject = program(INJECT);
		}
	}

	private static int program(String source) {
		try {
			return build(source);
		} catch (IllegalStateException first) {
			String alternative = source.replaceFirst("#version 430 core\n", "#version 330 core\n#extension GL_ARB_compute_shader : require\n#extension GL_ARB_shader_image_load_store : require\n#extension GL_ARB_shading_language_420pack : require\n");
			if (alternative.equals(source)) {
				throw first;
			}
			try {
				return build(alternative);
			} catch (IllegalStateException second) {
				throw new IllegalStateException(first.getMessage() + " | extension fallback: " + second.getMessage());
			}
		}
	}

	private static int build(String source) {
		String text = source
			.replace("@DIM@", Integer.toString(ShadowVolume.DIMENSION))
			.replace("@HALF@", Integer.toString(SIZE / 2))
			.replace("@SIZE@", Integer.toString(SIZE))
			.replace("@MASK@", Integer.toString(SIZE - 1));
		int shader = GL20.glCreateShader(GL43.GL_COMPUTE_SHADER);
		GL20.glShaderSource(shader, text);
		GL20.glCompileShader(shader);
		if (GL20.glGetShaderi(shader, GL20.GL_COMPILE_STATUS) == GL11.GL_FALSE) {
			String log = GL20.glGetShaderInfoLog(shader);
			GL20.glDeleteShader(shader);
			throw new IllegalStateException("compute shader failed: " + log);
		}
		int handle = GL20.glCreateProgram();
		GL20.glAttachShader(handle, shader);
		GL20.glLinkProgram(handle);
		GL20.glDeleteShader(shader);
		if (GL20.glGetProgrami(handle, GL20.GL_LINK_STATUS) == GL11.GL_FALSE) {
			String log = GL20.glGetProgramInfoLog(handle);
			GL20.glDeleteProgram(handle);
			throw new IllegalStateException("compute program failed: " + log);
		}
		return handle;
	}
}
