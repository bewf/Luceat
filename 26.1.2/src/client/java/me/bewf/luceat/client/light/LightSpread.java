package me.bewf.luceat.client.light;

import java.nio.ByteBuffer;

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
import me.bewf.luceat.client.shadow.ShadowVolume;

public final class LightSpread {
	public static final int SIZE = 64;
	public static final String SAMPLER = "u_LuceatSpread";
	public static final int UNIT = 9;

	private static final int VOXEL_UNIT = ShadowVolume.UNIT;
	private static final int PASSES = 2;

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
	private static Boolean capable;

	private LightSpread() {
	}

	public static boolean supported() {
		if (failed) {
			return false;
		}
		if (capable == null) {
			try {
				capable = GL.getCapabilities().OpenGL43;
			} catch (RuntimeException error) {
				capable = false;
			}
		}
		return capable;
	}

	public static long bytes() {
		return textures[0] == 0 ? 0L : 2L * SIZE * SIZE * SIZE * 4L;
	}

	public static void update(LightBuffer buffer) {
		if (!supported()) {
			return;
		}
		int previousProgram = GL11.glGetInteger(GL20.GL_CURRENT_PROGRAM);
		int previousUnit = GL11.glGetInteger(GL13.GL_ACTIVE_TEXTURE);
		try {
			ensure();
			programs();
			GL13.glActiveTexture(GL13.GL_TEXTURE0 + VOXEL_UNIT);
			GL11.glBindTexture(GL12.GL_TEXTURE_3D, ShadowVolume.texture());
			for (int pass = 0; pass < PASSES; pass++) {
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
