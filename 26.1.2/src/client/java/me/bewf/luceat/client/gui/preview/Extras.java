package me.bewf.luceat.client.gui.preview;

import java.util.Arrays;

public final class Extras {
	public static final byte DRY = 0;
	public static final byte STILL = 1;
	public static final byte FALLING = 2;
	public static final byte FOAM = 3;

	public float[] emit;
	public float[] sky;
	public byte[] water;
	public float[] spread;
	public float fog;
	public double sunYaw;
	public boolean sunShadows;
	public float sunStrength = 0.65F;
	public boolean caustics;
	public float spill;
	public double clock;
	public float reflectivity = 1.0F;
	public float[] blockLight;

	public static float[] skyLight(int width, int height, int depth, byte[] kind) {
		int total = width * height * depth;
		float[] level = new float[total];
		int[] queue = new int[total * 3 + 8];
		int[] light = new int[total];
		int head = 0;
		int tail = 0;
		for (int z = 0; z < depth; z++) {
			for (int x = 0; x < width; x++) {
				int current = 15;
				for (int y = height - 1; y >= 0; y--) {
					int at = x + width * (y + height * z);
					byte o = kind[at];
					if (o == 1) {
						break;
					}
					if (o == 2) {
						current = Math.max(0, current - 1);
					}
					light[at] = current;
					queue[tail++] = at;
				}
			}
		}
		int[] dx = {1, -1, 0, 0, 0, 0};
		int[] dy = {0, 0, 1, -1, 0, 0};
		int[] dz = {0, 0, 0, 0, 1, -1};
		while (head < tail) {
			int at = queue[head++];
			int value = light[at];
			if (value <= 1) {
				continue;
			}
			int x = at % width;
			int y = at / width % height;
			int z = at / (width * height);
			for (int k = 0; k < 6; k++) {
				int nx = x + dx[k];
				int ny = y + dy[k];
				int nz = z + dz[k];
				if (nx < 0 || ny < 0 || nz < 0 || nx >= width || ny >= height || nz >= depth) {
					continue;
				}
				int next = nx + width * (ny + height * nz);
				byte o = kind[next];
				if (o == 1) {
					continue;
				}
				int passed = value - (o == 2 ? 2 : 1);
				if (passed > light[next] && tail < queue.length) {
					light[next] = passed;
					queue[tail++] = next;
				}
			}
		}
		for (int i = 0; i < total; i++) {
			level[i] = light[i] / 15.0F;
		}
		return level;
	}

	public static float[] lightField(int width, int height, int depth, byte[] kind, Light[] lights, float strength) {
		int total = width * height * depth;
		float[] field = new float[total * 3];
		int[] reach = new int[total];
		int[] queue = new int[total + 8];
		int[] dx = {1, -1, 0, 0, 0, 0};
		int[] dy = {0, 0, 1, -1, 0, 0};
		int[] dz = {0, 0, 0, 0, 1, -1};
		for (Light light : lights) {
			Arrays.fill(reach, 0);
			int sx = (int) Math.floor(light.x());
			int sy = (int) Math.floor(light.y());
			int sz = (int) Math.floor(light.z());
			if (sx < 0 || sy < 0 || sz < 0 || sx >= width || sy >= height || sz >= depth) {
				continue;
			}
			int start = sx + width * (sy + height * sz);
			int head = 0;
			int tail = 0;
			reach[start] = Math.max(1, Math.round(light.radius()));
			queue[tail++] = start;
			while (head < tail) {
				int at = queue[head++];
				int value = reach[at];
				float curved = value / 15.0F;
				curved = curved / (4.0F - 3.0F * curved);
				int base = at * 3;
				field[base] = Math.max(field[base], light.red() * curved * strength);
				field[base + 1] = Math.max(field[base + 1], light.green() * curved * strength);
				field[base + 2] = Math.max(field[base + 2], light.blue() * curved * strength);
				if (value <= 1) {
					continue;
				}
				int x = at % width;
				int y = at / width % height;
				int z = at / (width * height);
				for (int k = 0; k < 6; k++) {
					int nx = x + dx[k];
					int ny = y + dy[k];
					int nz = z + dz[k];
					if (nx < 0 || ny < 0 || nz < 0 || nx >= width || ny >= height || nz >= depth) {
						continue;
					}
					int next = nx + width * (ny + height * nz);
					if (kind[next] == 1) {
						continue;
					}
					if (value - 1 > reach[next] && tail < queue.length) {
						reach[next] = value - 1;
						queue[tail++] = next;
					}
				}
			}
		}
		return field;
	}

	public static float[] blockLight(int width, int height, int depth, byte[] kind, Light[] lights) {
		int total = width * height * depth;
		float[] field = new float[total];
		int[] reach = new int[total];
		int[] queue = new int[total + 8];
		int[] dx = {1, -1, 0, 0, 0, 0};
		int[] dy = {0, 0, 1, -1, 0, 0};
		int[] dz = {0, 0, 0, 0, 1, -1};
		for (Light light : lights) {
			Arrays.fill(reach, 0);
			int sx = (int) Math.floor(light.x());
			int sy = (int) Math.floor(light.y());
			int sz = (int) Math.floor(light.z());
			if (sx < 0 || sy < 0 || sz < 0 || sx >= width || sy >= height || sz >= depth) {
				continue;
			}
			int start = sx + width * (sy + height * sz);
			int head = 0;
			int tail = 0;
			reach[start] = Math.max(1, Math.round(light.radius()));
			queue[tail++] = start;
			while (head < tail) {
				int at = queue[head++];
				int value = reach[at];
				field[at] = Math.max(field[at], value);
				if (value <= 1) {
					continue;
				}
				int x = at % width;
				int y = at / width % height;
				int z = at / (width * height);
				for (int k = 0; k < 6; k++) {
					int nx = x + dx[k];
					int ny = y + dy[k];
					int nz = z + dz[k];
					if (nx < 0 || ny < 0 || nz < 0 || nx >= width || ny >= height || nz >= depth) {
						continue;
					}
					int next = nx + width * (ny + height * nz);
					if (kind[next] == 1) {
						field[next] = Math.max(field[next], value - 1);
						continue;
					}
					if (value - 1 > reach[next] && tail < queue.length) {
						reach[next] = value - 1;
						queue[tail++] = next;
					}
				}
			}
		}
		return field;
	}
}
