package me.bewf.luceat.client.light;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Arrays;
import java.util.List;

import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL12;
import org.lwjgl.opengl.GL13;
import org.lwjgl.opengl.GL20;
import org.lwjgl.opengl.GL30;

import net.minecraft.world.phys.Vec3;

// 3d map of which lights can reach which 8 block cell
public final class LightGrid {
	public static final int CELL = 8;
	public static final int GX = 24;
	public static final int GY = 16;
	public static final int GZ = 24;
	public static final int SLOTS = 12;
	public static final int REACH_XZ = 88;
	public static final int REACH_Y = 56;
	public static final String SAMPLER = "u_LuceatGrid";

	private static final int UNIT = 12;
	private static final int TEXELS = SLOTS / 4;
	private static final int WIDTH = GX * TEXELS;
	private static final int CELLS = GX * GY * GZ;
	private static final float PAD = 0.9F;
	private static final int[] UNPACK = {
		GL11.GL_UNPACK_ALIGNMENT, GL11.GL_UNPACK_ROW_LENGTH, GL11.GL_UNPACK_SKIP_ROWS,
		GL11.GL_UNPACK_SKIP_PIXELS, GL12.GL_UNPACK_IMAGE_HEIGHT, GL12.GL_UNPACK_SKIP_IMAGES
	};

	private static final int[] ids = new int[CELLS * SLOTS];
	private static final float[] scores = new float[CELLS * SLOTS];
	private static final byte[] counts = new byte[CELLS];
	private static final ByteBuffer data = BufferUtils.createByteBuffer(CELLS * SLOTS * 2).order(ByteOrder.nativeOrder());

	private static int texture;
	private static int originX;
	private static int originY;
	private static int originZ;
	private static long signature = Long.MIN_VALUE;
	private static int cellsUsed;
	private static List<LightSource> current = List.of();

	private LightGrid() {
	}

	public static long bytes() {
		return texture == 0 ? 0L : (long) CELLS * SLOTS * 2L;
	}

	public static int originX() {
		return originX;
	}

	public static int originY() {
		return originY;
	}

	public static int originZ() {
		return originZ;
	}

	public static int cellsUsed() {
		return cellsUsed;
	}

	public static void reset() {
		signature = Long.MIN_VALUE;
	}

	public static void update(List<LightSource> lights, Vec3 camera) {
		ensureTexture();
		current = lights;
		originX = (Math.floorDiv((int) Math.floor(camera.x), CELL) - GX / 2) * CELL;
		originY = (Math.floorDiv((int) Math.floor(camera.y), CELL) - GY / 2) * CELL;
		originZ = (Math.floorDiv((int) Math.floor(camera.z), CELL) - GZ / 2) * CELL;
		int count = Math.min(lights.size(), LightBuffer.MAX_LIGHTS);
		long hash = 1469598103934665603L;
		hash = (hash ^ originX) * 1099511628211L;
		hash = (hash ^ originY) * 1099511628211L;
		hash = (hash ^ originZ) * 1099511628211L;
		hash = (hash ^ count) * 1099511628211L;
		for (int i = 0; i < count; i++) {
			LightSource light = lights.get(i);
			hash = (hash ^ (long) Math.floor(light.x())) * 1099511628211L;
			hash = (hash ^ (long) Math.floor(light.y())) * 1099511628211L;
			hash = (hash ^ (long) Math.floor(light.z())) * 1099511628211L;
			hash = (hash ^ (long) Math.ceil(light.radius() * 0.5F)) * 1099511628211L;
		}
		if (hash == signature) {
			return;
		}
		signature = hash;
		Arrays.fill(counts, (byte) 0);
		for (int i = 0; i < count; i++) {
			insert(i, lights.get(i));
		}
		data.clear();
		int used = 0;
		for (int cell = 0; cell < CELLS; cell++) {
			int n = counts[cell];
			if (n > 0) {
				used++;
			}
			int base = cell * SLOTS;
			for (int k = 0; k < SLOTS; k++) {
				data.putShort((short) (k < n ? ids[base + k] : 0));
			}
		}
		cellsUsed = used;
		data.flip();
		upload();
	}

	private static void insert(int index, LightSource light) {
		float qx = (float) Math.floor(light.x()) + 0.5F;
		float qy = (float) Math.floor(light.y()) + 0.5F;
		float qz = (float) Math.floor(light.z()) + 0.5F;
		float reach = (float) Math.ceil(light.radius() * 0.5F) * 2.0F + PAD;
		if (reach <= PAD) {
			return;
		}
		int x0 = Math.max(0, (int) Math.floor((qx - reach - originX) / CELL));
		int y0 = Math.max(0, (int) Math.floor((qy - reach - originY) / CELL));
		int z0 = Math.max(0, (int) Math.floor((qz - reach - originZ) / CELL));
		int x1 = Math.min(GX - 1, (int) Math.floor((qx + reach - originX) / CELL));
		int y1 = Math.min(GY - 1, (int) Math.floor((qy + reach - originY) / CELL));
		int z1 = Math.min(GZ - 1, (int) Math.floor((qz + reach - originZ) / CELL));
		float weight = Math.max(light.red(), Math.max(light.green(), light.blue()));
		for (int z = z0; z <= z1; z++) {
			float nearZ = nearest(qz, originZ + z * CELL);
			for (int y = y0; y <= y1; y++) {
				float nearY = nearest(qy, originY + y * CELL);
				for (int x = x0; x <= x1; x++) {
					float nearX = nearest(qx, originX + x * CELL);
					float distance = (float) Math.sqrt(nearX * nearX + nearY * nearY + nearZ * nearZ);
					if (distance > reach) {
						continue;
					}
					float score = (light.radius() - distance) * weight;
					int cell = (z * GY + y) * GX + x;
					int base = cell * SLOTS;
					int n = counts[cell];
					if (n < SLOTS) {
						ids[base + n] = index + 1;
						scores[base + n] = score;
						counts[cell] = (byte) (n + 1);
						continue;
					}
					int weakest = 0;
					for (int k = 1; k < SLOTS; k++) {
						if (scores[base + k] < scores[base + weakest]) {
							weakest = k;
						}
					}
					if (score > scores[base + weakest]) {
						ids[base + weakest] = index + 1;
						scores[base + weakest] = score;
					}
				}
			}
		}
	}

	private static float nearest(float point, float low) {
		if (point < low) {
			return low - point;
		}
		return point > low + CELL ? point - low - CELL : 0.0F;
	}

	// strongest light level reaching a point, same lights the shader sees
	public static float levelAt(double x, double y, double z) {
		int cx = (int) Math.floor((x - originX) / CELL);
		int cy = (int) Math.floor((y - originY) / CELL);
		int cz = (int) Math.floor((z - originZ) / CELL);
		if (cx < 0 || cy < 0 || cz < 0 || cx >= GX || cy >= GY || cz >= GZ) {
			return 0.0F;
		}
		int cell = (cz * GY + cy) * GX + cx;
		int base = cell * SLOTS;
		int n = counts[cell];
		float best = 0.0F;
		for (int k = 0; k < n; k++) {
			int index = ids[base + k] - 1;
			if (index < 0 || index >= current.size()) {
				continue;
			}
			LightSource light = current.get(index);
			double dx = light.x() - x;
			double dy = light.y() - y;
			double dz = light.z() - z;
			best = Math.max(best, light.radius() - (float) Math.sqrt(dx * dx + dy * dy + dz * dz));
		}
		return best;
	}

	public static void bind(int program) {
		if (texture == 0) {
			return;
		}
		int location = GL20.glGetUniformLocation(program, SAMPLER);
		if (location < 0) {
			return;
		}
		GL20.glUniform1i(location, UNIT);
		int previousUnit = GL11.glGetInteger(GL13.GL_ACTIVE_TEXTURE);
		GL13.glActiveTexture(GL13.GL_TEXTURE0 + UNIT);
		GL11.glBindTexture(GL12.GL_TEXTURE_3D, texture);
		GL13.glActiveTexture(previousUnit);
	}

	private static void ensureTexture() {
		if (texture != 0) {
			return;
		}
		int previousUnit = GL11.glGetInteger(GL13.GL_ACTIVE_TEXTURE);
		GL13.glActiveTexture(GL13.GL_TEXTURE0 + UNIT);
		texture = GL11.glGenTextures();
		GL11.glBindTexture(GL12.GL_TEXTURE_3D, texture);
		GL11.glTexParameteri(GL12.GL_TEXTURE_3D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_NEAREST);
		GL11.glTexParameteri(GL12.GL_TEXTURE_3D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_NEAREST);
		GL11.glTexParameteri(GL12.GL_TEXTURE_3D, GL11.GL_TEXTURE_WRAP_S, GL12.GL_CLAMP_TO_EDGE);
		GL11.glTexParameteri(GL12.GL_TEXTURE_3D, GL11.GL_TEXTURE_WRAP_T, GL12.GL_CLAMP_TO_EDGE);
		GL11.glTexParameteri(GL12.GL_TEXTURE_3D, GL12.GL_TEXTURE_WRAP_R, GL12.GL_CLAMP_TO_EDGE);
		GL12.glTexImage3D(GL12.GL_TEXTURE_3D, 0, GL30.GL_RGBA16UI, WIDTH, GY, GZ, 0, GL30.GL_RGBA_INTEGER, GL11.GL_UNSIGNED_SHORT, (ByteBuffer) null);
		GL13.glActiveTexture(previousUnit);
	}

	private static void upload() {
		int previousUnit = GL11.glGetInteger(GL13.GL_ACTIVE_TEXTURE);
		int[] previous = new int[UNPACK.length];
		for (int i = 0; i < UNPACK.length; i++) {
			previous[i] = GL11.glGetInteger(UNPACK[i]);
			GL11.glPixelStorei(UNPACK[i], i == 0 ? 1 : 0);
		}
		GL13.glActiveTexture(GL13.GL_TEXTURE0 + UNIT);
		GL11.glBindTexture(GL12.GL_TEXTURE_3D, texture);
		GL12.glTexSubImage3D(GL12.GL_TEXTURE_3D, 0, 0, 0, 0, WIDTH, GY, GZ, GL30.GL_RGBA_INTEGER, GL11.GL_UNSIGNED_SHORT, data);
		GL13.glActiveTexture(previousUnit);
		for (int i = 0; i < UNPACK.length; i++) {
			GL11.glPixelStorei(UNPACK[i], previous[i]);
		}
	}
}
