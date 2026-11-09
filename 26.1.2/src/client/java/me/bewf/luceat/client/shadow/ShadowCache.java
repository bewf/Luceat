package me.bewf.luceat.client.shadow;

import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.LinkedBlockingQueue;

import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL12;
import org.lwjgl.opengl.GL13;
import org.lwjgl.opengl.GL20;

import me.bewf.luceat.Luceat;
import me.bewf.luceat.client.config.LuceatConfig;
import me.bewf.luceat.client.config.ShadowQuality;
import me.bewf.luceat.client.light.LightBuffer;
import me.bewf.luceat.client.light.LightSource;
import me.bewf.luceat.client.light.Stats;
import me.bewf.luceat.client.shader.ShaderVariant;
import me.bewf.luceat.client.shader.TerrainShaderPatch;

import net.minecraft.world.phys.Vec3;

public final class ShadowCache {
	public static final int SIDE = 40;
	public static final int HALF = SIDE / 2;
	public static final int AXIS = 4;
	public static final int SLOTS = AXIS * AXIS * AXIS;
	public static final int ATLAS = SIDE * AXIS;
	public static final String SAMPLER = "u_LuceatCache";

	private static final int UNIT = 8;
	private static final int VOXELS = SIDE * SIDE * SIDE;
	public static final double RADIUS = HALF - 0.5;
	private static final int UPLOADS_PER_FRAME = 2;
	private static final int JOBS_PER_FRAME = 3;
	private static final int QUEUE_LIMIT = 10;
	private static final double SWAP_MARGIN = 6.0;
	private static final int HALF_RES = ShapeMasks.RES / 2;
	private static final int[] UNPACK = {
		GL11.GL_UNPACK_ALIGNMENT, GL11.GL_UNPACK_ROW_LENGTH, GL11.GL_UNPACK_SKIP_ROWS,
		GL11.GL_UNPACK_SKIP_PIXELS, GL12.GL_UNPACK_IMAGE_HEIGHT, GL12.GL_UNPACK_SKIP_IMAGES
	};

	private record Job(int slot, int generation, int signature, double x, double y, double z) {
	}

	private record Result(Job job, byte[] data) {
	}

	private static final long[] keys = new long[SLOTS];
	private static final double[] lightX = new double[SLOTS];
	private static final double[] lightY = new double[SLOTS];
	private static final double[] lightZ = new double[SLOTS];
	private static final boolean[] used = new boolean[SLOTS];
	private static final boolean[] ready = new boolean[SLOTS];
	private static final boolean[] building = new boolean[SLOTS];
	private static final int[] builtSignature = new int[SLOTS];
	private static final int[] generation = new int[SLOTS];
	private static final double[] distance = new double[SLOTS];
	private static final Map<Long, Integer> bySlot = new HashMap<>();
	private static final int[] codes = new int[LightBuffer.MAX_LIGHTS];
	private static final long[] order = new long[LightBuffer.MAX_LIGHTS];
	private static final LinkedBlockingQueue<Job> JOBS = new LinkedBlockingQueue<>();
	private static final ConcurrentLinkedQueue<Result> RESULTS = new ConcurrentLinkedQueue<>();
	private static final ByteBuffer UPLOAD = BufferUtils.createByteBuffer(VOXELS * 4);

	private static Thread worker;
	private static volatile boolean failed;
	private static int texture;

	private ShadowCache() {
	}

	public static boolean active() {
		LuceatConfig config = LuceatConfig.get();
		return texture != 0 && !failed && config.shadows && config.placedShadows != ShadowQuality.OFF;
	}

	public static int slotCode(int lightIndex) {
		return lightIndex >= 0 && lightIndex < codes.length ? codes[lightIndex] : 0;
	}

	public static int readySlots() {
		int n = 0;
		for (int slot = 0; slot < SLOTS; slot++) {
			if (used[slot] && ready[slot]) {
				n++;
			}
		}
		return n;
	}

	public static boolean busy() {
		if (!JOBS.isEmpty() || !RESULTS.isEmpty()) {
			return true;
		}
		for (int slot = 0; slot < SLOTS; slot++) {
			if (building[slot]) {
				return true;
			}
		}
		return false;
	}

	public static long bytes() {
		return texture == 0 ? 0L : (long) ATLAS * ATLAS * ATLAS * 4L;
	}

	public static void reset() {
		for (int slot = 0; slot < SLOTS; slot++) {
			generation[slot]++;
			release(slot);
		}
		bySlot.clear();
		JOBS.clear();
		RESULTS.clear();
		Arrays.fill(codes, 0);
	}

	public static void update(List<LightSource> lights, Vec3 camera) {
		long started = System.nanoTime();
		Arrays.fill(codes, 0);
		LuceatConfig config = LuceatConfig.get();
		ShaderVariant variant = TerrainShaderPatch.built();
		if (failed || !config.shadows || config.placedShadows == ShadowQuality.OFF || variant == null || !variant.rays()) {
			return;
		}
		ensureTexture();
		ensureWorker();
		int count = Math.min(lights.size(), LightBuffer.MAX_LIGHTS);
		double reach = Math.min(config.shadowDistance, ShadowVolume.RELIABLE);
		int candidates = 0;
		for (int i = 0; i < count; i++) {
			LightSource light = lights.get(i);
			if (!light.placed() || light.noShadow() || light.radius() < 1.0F) {
				continue;
			}
			double dx = light.x() - camera.x;
			double dy = light.y() - camera.y;
			double dz = light.z() - camera.z;
			double away = Math.sqrt(dx * dx + dy * dy + dz * dz);
			if (away > reach + light.radius()) {
				continue;
			}
			order[candidates++] = ((long) Float.floatToIntBits((float) away) << 32) | i;
		}
		Arrays.sort(order, 0, candidates);
		Arrays.fill(distance, Double.MAX_VALUE);
		for (int c = 0; c < candidates; c++) {
			int index = (int) (order[c] & 0xFFFFFFFFL);
			Integer slot = bySlot.get(keyOf(lights.get(index)));
			if (slot != null) {
				distance[slot] = Float.intBitsToFloat((int) (order[c] >>> 32));
			}
		}
		int wanted = Math.min(candidates, SLOTS);
		for (int c = 0; c < wanted; c++) {
			int index = (int) (order[c] & 0xFFFFFFFFL);
			LightSource light = lights.get(index);
			long key = keyOf(light);
			if (bySlot.containsKey(key)) {
				continue;
			}
			double away = Float.intBitsToFloat((int) (order[c] >>> 32));
			int slot = freeSlot();
			if (slot < 0) {
				slot = farthestSlot();
				if (slot < 0 || distance[slot] <= away + SWAP_MARGIN) {
					break;
				}
				generation[slot]++;
				release(slot);
			}
			used[slot] = true;
			ready[slot] = false;
			building[slot] = false;
			keys[slot] = key;
			lightX[slot] = light.x();
			lightY[slot] = light.y();
			lightZ[slot] = light.z();
			distance[slot] = away;
			bySlot.put(key, slot);
		}
		boolean slow = Stats.frameMillis() > 40.0F;
		upload(slow ? UPLOADS_PER_FRAME * 3 : UPLOADS_PER_FRAME);
		int jobCap = slow ? JOBS_PER_FRAME * 2 : JOBS_PER_FRAME;
		int jobs = 0;
		for (int c = 0; c < candidates; c++) {
			int index = (int) (order[c] & 0xFFFFFFFFL);
			Integer found = bySlot.get(keyOf(lights.get(index)));
			if (found == null) {
				continue;
			}
			int slot = found;
			int signature = signature(slot);
			if (signature != -1 && !building[slot] && (!ready[slot] || signature != builtSignature[slot]) && jobs < jobCap && JOBS.size() < QUEUE_LIMIT) {
				building[slot] = true;
				JOBS.add(new Job(slot, generation[slot], signature, lightX[slot], lightY[slot], lightZ[slot]));
				jobs++;
			}
			if (ready[slot]) {
				codes[index] = slot + 1;
			}
		}
		Stats.record(Stats.CACHE, System.nanoTime() - started);
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

	private static long keyOf(LightSource light) {
		long x = (long) Math.floor(light.x() * 4.0);
		long y = (long) Math.floor(light.y() * 4.0);
		long z = (long) Math.floor(light.z() * 4.0);
		return ((x & 0x1FFFFFL) << 42) | ((y & 0x1FFFFFL) << 21) | (z & 0x1FFFFFL) | Long.MIN_VALUE;
	}

	private static int freeSlot() {
		for (int slot = 0; slot < SLOTS; slot++) {
			if (!used[slot]) {
				return slot;
			}
		}
		return -1;
	}

	private static int farthestSlot() {
		int best = -1;
		for (int slot = 0; slot < SLOTS; slot++) {
			if (used[slot] && (best < 0 || distance[slot] > distance[best])) {
				best = slot;
			}
		}
		return best;
	}

	private static void release(int slot) {
		if (used[slot]) {
			bySlot.remove(keys[slot]);
		}
		used[slot] = false;
		ready[slot] = false;
		building[slot] = false;
		keys[slot] = 0L;
	}

	private static int signature(int slot) {
		int x0 = (int) Math.floor(lightX[slot]) - HALF;
		int y0 = (int) Math.floor(lightY[slot]) - HALF;
		int z0 = (int) Math.floor(lightZ[slot]) - HALF;
		return ShadowVolume.signature(x0, y0, z0, x0 + SIDE - 1, y0 + SIDE - 1, z0 + SIDE - 1);
	}

	private static void ensureWorker() {
		if (worker != null) {
			return;
		}
		worker = new Thread(ShadowCache::work, "Luceat shadow cache");
		worker.setDaemon(true);
		worker.setPriority(Thread.NORM_PRIORITY - 1);
		worker.start();
	}

	private static void work() {
		Tracer tracer = new Tracer();
		while (true) {
			Job job;
			try {
				job = JOBS.take();
			} catch (InterruptedException interrupted) {
				return;
			}
			try {
				RESULTS.add(new Result(job, tracer.build(job)));
			} catch (RuntimeException error) {
				failed = true;
				Luceat.LOGGER.warn("Luceat shadow cache stopped", error);
				return;
			}
		}
	}

	private static void upload(int cap) {
		Result result = RESULTS.poll();
		if (result == null) {
			return;
		}
		int previousUnit = GL11.glGetInteger(GL13.GL_ACTIVE_TEXTURE);
		int[] previous = new int[UNPACK.length];
		for (int i = 0; i < UNPACK.length; i++) {
			previous[i] = GL11.glGetInteger(UNPACK[i]);
			GL11.glPixelStorei(UNPACK[i], i == 0 ? 1 : 0);
		}
		GL13.glActiveTexture(GL13.GL_TEXTURE0 + UNIT);
		GL11.glBindTexture(GL12.GL_TEXTURE_3D, texture);
		int done = 0;
		while (result != null && done < cap) {
			Job job = result.job();
			int slot = job.slot();
			if (generation[slot] == job.generation() && used[slot]) {
				UPLOAD.clear();
				UPLOAD.put(result.data());
				UPLOAD.flip();
				GL12.glTexSubImage3D(
					GL12.GL_TEXTURE_3D, 0,
					(slot % AXIS) * SIDE, (slot / AXIS % AXIS) * SIDE, (slot / (AXIS * AXIS)) * SIDE,
					SIDE, SIDE, SIDE, GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, UPLOAD
				);
				building[slot] = false;
				ready[slot] = true;
				builtSignature[slot] = job.signature();
				done++;
			}
			result = done < cap ? RESULTS.poll() : null;
		}
		GL13.glActiveTexture(previousUnit);
		for (int i = 0; i < UNPACK.length; i++) {
			GL11.glPixelStorei(UNPACK[i], previous[i]);
		}
	}

	private static void ensureTexture() {
		if (texture != 0) {
			return;
		}
		int previousUnit = GL11.glGetInteger(GL13.GL_ACTIVE_TEXTURE);
		GL13.glActiveTexture(GL13.GL_TEXTURE0 + UNIT);
		texture = GL11.glGenTextures();
		GL11.glBindTexture(GL12.GL_TEXTURE_3D, texture);
		GL11.glTexParameteri(GL12.GL_TEXTURE_3D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_LINEAR);
		GL11.glTexParameteri(GL12.GL_TEXTURE_3D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_LINEAR);
		GL11.glTexParameteri(GL12.GL_TEXTURE_3D, GL11.GL_TEXTURE_WRAP_S, GL12.GL_CLAMP_TO_EDGE);
		GL11.glTexParameteri(GL12.GL_TEXTURE_3D, GL11.GL_TEXTURE_WRAP_T, GL12.GL_CLAMP_TO_EDGE);
		GL11.glTexParameteri(GL12.GL_TEXTURE_3D, GL12.GL_TEXTURE_WRAP_R, GL12.GL_CLAMP_TO_EDGE);
		GL12.glTexImage3D(GL12.GL_TEXTURE_3D, 0, GL11.GL_RGBA8, ATLAS, ATLAS, ATLAS, 0, GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, (ByteBuffer) null);
		GL13.glActiveTexture(previousUnit);
	}

	private static final class Tracer {
		private final float[] red = new float[VOXELS];
		private final float[] green = new float[VOXELS];
		private final float[] blue = new float[VOXELS];
		private final boolean[] solid = new boolean[VOXELS];
		private double passRed;
		private double passGreen;
		private double passBlue;

		byte[] build(Job job) {
			int lightCellX = (int) Math.floor(job.x());
			int lightCellY = (int) Math.floor(job.y());
			int lightCellZ = (int) Math.floor(job.z());
			int originX = lightCellX - HALF;
			int originY = lightCellY - HALF;
			int originZ = lightCellZ - HALF;
			int index = 0;
			for (int k = 0; k < SIDE; k++) {
				for (int j = 0; j < SIDE; j++) {
					for (int i = 0; i < SIDE; i++, index++) {
						int wx = originX + i;
						int wy = originY + j;
						int wz = originZ + k;
						if ((ShadowVolume.mirrorId(wx, wy, wz) & 0xFFFF) == PatternAtlas.OPAQUE) {
							solid[index] = true;
							continue;
						}
						solid[index] = false;
						double dx = wx + 0.5 - job.x();
						double dy = wy + 0.5 - job.y();
						double dz = wz + 0.5 - job.z();
						if (dx * dx + dy * dy + dz * dz > RADIUS * RADIUS) {
							red[index] = 1.0F;
							green[index] = 1.0F;
							blue[index] = 1.0F;
							continue;
						}
						trace(wx, wy, wz, job.x(), job.y(), job.z(), lightCellX, lightCellY, lightCellZ);
						red[index] = (float) passRed;
						green[index] = (float) passGreen;
						blue[index] = (float) passBlue;
					}
				}
			}
			fillSolid();
			byte[] out = new byte[VOXELS * 4];
			int at = 0;
			for (int v = 0; v < VOXELS; v++) {
				out[at++] = pack(red[v]);
				out[at++] = pack(green[v]);
				out[at++] = pack(blue[v]);
				out[at++] = (byte) 255;
			}
			return out;
		}

		private void fillSolid() {
			int index = 0;
			for (int k = 0; k < SIDE; k++) {
				for (int j = 0; j < SIDE; j++) {
					for (int i = 0; i < SIDE; i++, index++) {
						if (!solid[index]) {
							continue;
						}
						float r = 0.0F;
						float g = 0.0F;
						float b = 0.0F;
						int n = 0;
						for (int side = 0; side < 6; side++) {
							int axis = side >> 1;
							int step = (side & 1) == 0 ? -1 : 1;
							int ni = i + (axis == 0 ? step : 0);
							int nj = j + (axis == 1 ? step : 0);
							int nk = k + (axis == 2 ? step : 0);
							if (ni < 0 || nj < 0 || nk < 0 || ni >= SIDE || nj >= SIDE || nk >= SIDE) {
								continue;
							}
							int near = (nk * SIDE + nj) * SIDE + ni;
							if (solid[near]) {
								continue;
							}
							r += red[near];
							g += green[near];
							b += blue[near];
							n++;
						}
						if (n > 0) {
							red[index] = r / n;
							green[index] = g / n;
							blue[index] = b / n;
						} else {
							red[index] = 0.0F;
							green[index] = 0.0F;
							blue[index] = 0.0F;
						}
					}
				}
			}
		}

		private static byte pack(float value) {
			return (byte) Math.round(Math.min(1.0F, Math.max(0.0F, value)) * 255.0F);
		}

		private void trace(int cx, int cy, int cz, double ex, double ey, double ez, int lightCellX, int lightCellY, int lightCellZ) {
			passRed = 1.0;
			passGreen = 1.0;
			passBlue = 1.0;
			double ox = cx + 0.5;
			double oy = cy + 0.5;
			double oz = cz + 0.5;
			double dx = ex - ox;
			double dy = ey - oy;
			double dz = ez - oz;
			if (dx * dx + dy * dy + dz * dz < 0.0225) {
				return;
			}
			int stepX = dx > 0.0 ? 1 : -1;
			int stepY = dy > 0.0 ? 1 : -1;
			int stepZ = dz > 0.0 ? 1 : -1;
			double deltaX = dx == 0.0 ? Double.MAX_VALUE : 1.0 / Math.abs(dx);
			double deltaY = dy == 0.0 ? Double.MAX_VALUE : 1.0 / Math.abs(dy);
			double deltaZ = dz == 0.0 ? Double.MAX_VALUE : 1.0 / Math.abs(dz);
			double nextX = dx == 0.0 ? Double.MAX_VALUE : 0.5 * deltaX;
			double nextY = dy == 0.0 ? Double.MAX_VALUE : 0.5 * deltaY;
			double nextZ = dz == 0.0 ? Double.MAX_VALUE : 0.5 * deltaZ;
			int x = cx;
			int y = cy;
			int z = cz;
			int mx = Math.floorMod(cx, ShadowVolume.DIMENSION);
			int my = Math.floorMod(cy, ShadowVolume.DIMENSION);
			int mz = Math.floorMod(cz, ShadowVolume.DIMENSION);
			for (int guard = 0; guard < 3 * SIDE; guard++) {
				if (nextX <= nextY && nextX <= nextZ) {
					if (nextX >= 1.0) {
						return;
					}
					x += stepX;
					mx = wrap(mx + stepX);
					nextX += deltaX;
				} else if (nextY <= nextZ) {
					if (nextY >= 1.0) {
						return;
					}
					y += stepY;
					my = wrap(my + stepY);
					nextY += deltaY;
				} else {
					if (nextZ >= 1.0) {
						return;
					}
					z += stepZ;
					mz = wrap(mz + stepZ);
					nextZ += deltaZ;
				}
				if (x == lightCellX && y == lightCellY && z == lightCellZ) {
					return;
				}
				int id = ShadowVolume.mirrorAt(mx, my, mz) & 0xFFFF;
				if (id == 0) {
					continue;
				}
				if (id == PatternAtlas.OPAQUE) {
					passRed = 0.0;
					passGreen = 0.0;
					passBlue = 0.0;
					return;
				}
				if ((id & ShapeMasks.FLAG) != 0) {
					int gap = Math.max(Math.abs(x - lightCellX), Math.max(Math.abs(y - lightCellY), Math.abs(z - lightCellZ)));
					if (gap <= 1) {
						continue;
					}
					passRed *= Math.pow(Math.max(Transmit.red(id), 0.03F), HALF_RES);
					passGreen *= Math.pow(Math.max(Transmit.green(id), 0.03F), HALF_RES);
					passBlue *= Math.pow(Math.max(Transmit.blue(id), 0.03F), HALF_RES);
				} else {
					passRed *= Transmit.red(id) * Transmit.red(id);
					passGreen *= Transmit.green(id) * Transmit.green(id);
					passBlue *= Transmit.blue(id) * Transmit.blue(id);
				}
				if (Math.max(passRed, Math.max(passGreen, passBlue)) < 0.02) {
					passRed = 0.0;
					passGreen = 0.0;
					passBlue = 0.0;
					return;
				}
			}
		}

		private static int wrap(int value) {
			if (value >= ShadowVolume.DIMENSION) {
				return value - ShadowVolume.DIMENSION;
			}
			return value < 0 ? value + ShadowVolume.DIMENSION : value;
		}
	}
}
