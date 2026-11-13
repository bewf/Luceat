package me.bewf.luceat.client.light;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.ShortBuffer;
import java.util.ArrayList;
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
import org.lwjgl.opengl.GL30;

import me.bewf.luceat.Luceat;
import me.bewf.luceat.client.config.LuceatConfig;
import me.bewf.luceat.client.shader.TerrainShaderPatch;
import me.bewf.luceat.client.shadow.PatternAtlas;
import me.bewf.luceat.client.shadow.ShadowVolume;
import me.bewf.luceat.client.shadow.ShapeMasks;
import me.bewf.luceat.client.shadow.Transmit;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.phys.Vec3;

public final class LightVolume {
	public static final int SIDE_X = 112;
	public static final int SIDE_Y = 80;
	public static final int SIDE_Z = 112;
	public static final String LIGHT_SAMPLER = "u_LuceatBakedLight";
	public static final String DIRECTION_SAMPLER = "u_LuceatBakedDir";
	public static final int LIGHT_UNIT = 16;
	public static final int DIRECTION_UNIT = 17;

	private static final int VOXELS = SIDE_X * SIDE_Y * SIDE_Z;
	private static final int SLICE_SHORTS = SIDE_X * SIDE_Y * 4;
	private static final int SLICES_PER_FRAME = 16;
	private static final int BIAS_Y = 40;
	private static final int DRIFT_XZ = 16;
	private static final int DRIFT_Y = 10;
	private static final int MARGIN = 3;
	private static final int CHECK_INTERVAL = 8;
	private static final long MIN_GAP_NANOS = 350_000_000L;
	private static final int HALF_RES = ShapeMasks.RES / 2;
	private static final double[][] TAPS = {{0.28, 0.28, 0.28}, {0.28, -0.28, -0.28}, {-0.28, 0.28, -0.28}, {-0.28, -0.28, 0.28}};
	private static final int[] UNPACK = {
		GL11.GL_UNPACK_ALIGNMENT, GL11.GL_UNPACK_ROW_LENGTH, GL11.GL_UNPACK_SKIP_ROWS,
		GL11.GL_UNPACK_SKIP_PIXELS, GL12.GL_UNPACK_IMAGE_HEIGHT, GL12.GL_UNPACK_SKIP_IMAGES
	};

	private record Bake(long id, double x, double y, double z, float red, float green, float blue, float radius, boolean fire, boolean noShadow) {
	}

	private record Job(int generation, int blockSignature, int originX, int originY, int originZ, List<Bake> lights, Map<Long, Integer> hashes, int minX, int minY, int minZ, int maxX, int maxY, int maxZ) {
	}

	private record Result(Job job, short[] light, short[] direction) {
	}

	private static final LinkedBlockingQueue<Job> JOBS = new LinkedBlockingQueue<>();
	private static final ConcurrentLinkedQueue<Result> RESULTS = new ConcurrentLinkedQueue<>();
	private static final ShortBuffer UPLOAD = BufferUtils.createByteBuffer(SLICE_SHORTS * 2 * SLICES_PER_FRAME * 2).order(ByteOrder.nativeOrder()).asShortBuffer();
	private static final int[] lightTextures = new int[2];
	private static final int[] directionTextures = new int[2];

	private static Thread worker;
	private static volatile boolean failed;
	private static volatile int generation;
	private static Boolean capable;
	private static ClientLevel level;
	private static boolean inFlight;
	private static boolean active;
	private static int front = -1;
	private static int originX;
	private static int originY;
	private static int originZ;
	private static int builtBlockSignature;
	private static Map<Long, Integer> builtHashes = Map.of();
	private static Map<Long, Integer> candidateHashes = Map.of();
	private static Result pending;
	private static int uploaded;
	private static int frame;
	private static long lastQueued;
	private static int bakedCount;

	private LightVolume() {
	}

	public static boolean capable() {
		if (capable == null) {
			try {
				capable = GL11.glGetInteger(GL20.GL_MAX_TEXTURE_IMAGE_UNITS) > DIRECTION_UNIT;
			} catch (Throwable error) {
				return false;
			}
		}
		return capable;
	}

	public static boolean ready() {
		return active && front >= 0 && !failed;
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

	public static int baked() {
		return ready() ? bakedCount : 0;
	}

	public static boolean busy() {
		return inFlight || pending != null;
	}

	public static long bytes() {
		return lightTextures[0] == 0 ? 0L : (long) VOXELS * 32L;
	}

	public static void reset() {
		generation++;
		JOBS.clear();
		RESULTS.clear();
		pending = null;
		inFlight = false;
		front = -1;
		uploaded = 0;
		builtHashes = Map.of();
		candidateHashes = Map.of();
		bakedCount = 0;
	}

	public static void update(ClientLevel clientLevel, List<LightSource> lights, Vec3 camera) {
		LuceatConfig config = LuceatConfig.get();
		active = false;
		if (failed || clientLevel == null || !config.bakedLights || !config.placedLights || !capable() || TerrainShaderPatch.built() == null || !TerrainShaderPatch.bakeCompiled()) {
			if (front >= 0) {
				reset();
			}
			return;
		}
		if (clientLevel != level) {
			level = clientLevel;
			reset();
		}
		active = true;
		ensureTextures();
		ensureWorker();
		frame++;
		List<Bake> candidates = collect(lights);
		Map<Long, Integer> hashes = new HashMap<>(candidates.size() * 2);
		for (Bake bake : candidates) {
			hashes.put(bake.id(), hashOf(bake));
		}
		candidateHashes = hashes;
		Result done = RESULTS.poll();
		if (done != null) {
			inFlight = false;
			if (done.job().generation() == generation && pending == null) {
				pending = done;
				uploaded = 0;
			}
		}
		if (pending != null) {
			continueUpload();
		}
		if (!inFlight && pending == null) {
			maybeQueue(camera, candidates, hashes);
		}
		int count = 0;
		for (Map.Entry<Long, Integer> entry : hashes.entrySet()) {
			if (entry.getValue().equals(builtHashes.get(entry.getKey()))) {
				count++;
			}
		}
		bakedCount = count;
	}

	public static List<LightSource> remaining(List<LightSource> lights) {
		if (!ready() || builtHashes.isEmpty()) {
			return lights;
		}
		List<LightSource> out = new ArrayList<>(lights.size());
		for (LightSource light : lights) {
			if (light.placed()) {
				Integer wanted = candidateHashes.get(light.id());
				if (wanted != null && wanted.equals(builtHashes.get(light.id()))) {
					continue;
				}
			}
			out.add(light);
		}
		return out;
	}

	public static void bind(int program) {
		if (!capable()) {
			return;
		}
		int lightLocation = GL20.glGetUniformLocation(program, LIGHT_SAMPLER);
		int directionLocation = GL20.glGetUniformLocation(program, DIRECTION_SAMPLER);
		if (lightLocation < 0 && directionLocation < 0) {
			return;
		}
		int previousUnit = GL11.glGetInteger(GL13.GL_ACTIVE_TEXTURE);
		if (lightLocation >= 0) {
			GL20.glUniform1i(lightLocation, LIGHT_UNIT);
			GL13.glActiveTexture(GL13.GL_TEXTURE0 + LIGHT_UNIT);
			GL11.glBindTexture(GL12.GL_TEXTURE_3D, lightTextures[0] == 0 ? 0 : lightTextures[front >= 0 ? front : 0]);
		}
		if (directionLocation >= 0) {
			GL20.glUniform1i(directionLocation, DIRECTION_UNIT);
			GL13.glActiveTexture(GL13.GL_TEXTURE0 + DIRECTION_UNIT);
			GL11.glBindTexture(GL12.GL_TEXTURE_3D, directionTextures[0] == 0 ? 0 : directionTextures[front >= 0 ? front : 0]);
		}
		GL13.glActiveTexture(previousUnit);
	}

	private static List<Bake> collect(List<LightSource> lights) {
		List<Bake> out = new ArrayList<>();
		LuceatConfig config = LuceatConfig.get();
		boolean skipShadows = !config.shadows || config.placedShadows == me.bewf.luceat.client.config.ShadowQuality.OFF;
		for (LightSource light : lights) {
			if (!light.placed()) {
				continue;
			}
			float base = LightSmoother.settled(light.id());
			if (base < 1.0F) {
				continue;
			}
			out.add(new Bake(light.id(), light.x(), light.y(), light.z(), light.red(), light.green(), light.blue(), base, light.fire(), light.noShadow() || skipShadows));
		}
		return out;
	}

	private static int hashOf(Bake bake) {
		long h = bake.id() * 0x9E3779B97F4A7C15L;
		h ^= Math.round(bake.x() * 8.0) * 0x632BE59BD9B4E019L;
		h ^= Math.round(bake.y() * 8.0) * 0x85157AF5L;
		h ^= Math.round(bake.z() * 8.0) * 0xC2B2AE3D27D4EB4FL;
		h ^= Math.round(bake.red() * 64.0F) * 0x165667B19E3779F9L;
		h ^= Math.round(bake.green() * 64.0F) * 0x27D4EB2F165667C5L;
		h ^= Math.round(bake.blue() * 64.0F) * 0x9E3779B185EBCA87L;
		h ^= Math.round(bake.radius() * 4.0F) * 0xFF51AFD7ED558CCDL;
		h ^= bake.noShadow() ? 0x1234567L : 0L;
		h ^= bake.fire() ? 0x7654321L : 0L;
		return (int) (h ^ (h >>> 32));
	}

	private static boolean fits(Bake bake, int ox, int oy, int oz) {
		float r = bake.radius();
		return bake.x() - r >= ox + MARGIN && bake.x() + r <= ox + SIDE_X - MARGIN
			&& bake.y() - r >= oy + MARGIN && bake.y() + r <= oy + SIDE_Y - MARGIN
			&& bake.z() - r >= oz + MARGIN && bake.z() + r <= oz + SIDE_Z - MARGIN;
	}

	private static List<Bake> fitting(List<Bake> candidates, int ox, int oy, int oz) {
		List<Bake> out = new ArrayList<>();
		for (Bake bake : candidates) {
			if (fits(bake, ox, oy, oz)) {
				out.add(bake);
			}
		}
		return out;
	}

	private static Map<Long, Integer> hashesOf(List<Bake> bakes, Map<Long, Integer> all) {
		Map<Long, Integer> out = new HashMap<>(bakes.size() * 2);
		for (Bake bake : bakes) {
			out.put(bake.id(), all.get(bake.id()));
		}
		return out;
	}

	private static void maybeQueue(Vec3 camera, List<Bake> candidates, Map<Long, Integer> hashes) {
		long now = System.nanoTime();
		if (now - lastQueued < MIN_GAP_NANOS) {
			return;
		}
		int camX = (int) Math.floor(camera.x);
		int camY = (int) Math.floor(camera.y);
		int camZ = (int) Math.floor(camera.z);
		int wantX = Math.floorDiv(camX - SIDE_X / 2, 8) * 8;
		int wantY = Math.floorDiv(camY - BIAS_Y, 8) * 8;
		int wantZ = Math.floorDiv(camZ - SIDE_Z / 2, 8) * 8;
		boolean moved = front < 0
			|| Math.abs(camX - (originX + SIDE_X / 2)) > DRIFT_XZ
			|| Math.abs(camZ - (originZ + SIDE_Z / 2)) > DRIFT_XZ
			|| Math.abs(camY - (originY + BIAS_Y)) > DRIFT_Y;
		int ox = moved ? wantX : originX;
		int oy = moved ? wantY : originY;
		int oz = moved ? wantZ : originZ;
		List<Bake> inside = fitting(candidates, ox, oy, oz);
		Map<Long, Integer> insideHashes = hashesOf(inside, hashes);
		boolean need = front < 0 ? !inside.isEmpty() : !insideHashes.equals(builtHashes);
		int signature = builtBlockSignature;
		if (!need && front >= 0 && frame % CHECK_INTERVAL == 0 && !builtHashes.isEmpty()) {
			signature = ShadowVolume.looseSignature(ox, oy, oz, ox + SIDE_X - 1, oy + SIDE_Y - 1, oz + SIDE_Z - 1);
			need = signature != builtBlockSignature;
		} else if (need) {
			signature = ShadowVolume.looseSignature(ox, oy, oz, ox + SIDE_X - 1, oy + SIDE_Y - 1, oz + SIDE_Z - 1);
		}
		if (!need) {
			return;
		}
		int cellX = ShadowVolume.centreX();
		int cellY = ShadowVolume.centreY();
		int cellZ = ShadowVolume.centreZ();
		int half = ShadowVolume.HALF;
		int size = ShadowVolume.CELL_SIZE;
		Job job = new Job(
			generation, signature, ox, oy, oz, inside, insideHashes,
			(cellX - half) * size, (cellY - half) * size, (cellZ - half) * size,
			(cellX + half + 1) * size - 1, (cellY + half + 1) * size - 1, (cellZ + half + 1) * size - 1
		);
		inFlight = true;
		lastQueued = now;
		JOBS.add(job);
	}

	private static void continueUpload() {
		Job job = pending.job();
		int target = front == 0 ? 1 : 0;
		int slices = Math.min(Stats.frameMillis() > 40.0F ? SLICES_PER_FRAME * 2 : SLICES_PER_FRAME, SIDE_Z - uploaded);
		int previousUnit = GL11.glGetInteger(GL13.GL_ACTIVE_TEXTURE);
		int[] previous = new int[UNPACK.length];
		for (int i = 0; i < UNPACK.length; i++) {
			previous[i] = GL11.glGetInteger(UNPACK[i]);
			GL11.glPixelStorei(UNPACK[i], i == 0 ? 1 : 0);
		}
		upload(LIGHT_UNIT, lightTextures[target], pending.light(), slices);
		upload(DIRECTION_UNIT, directionTextures[target], pending.direction(), slices);
		GL13.glActiveTexture(previousUnit);
		for (int i = 0; i < UNPACK.length; i++) {
			GL11.glPixelStorei(UNPACK[i], previous[i]);
		}
		uploaded += slices;
		if (uploaded >= SIDE_Z) {
			front = target;
			originX = job.originX();
			originY = job.originY();
			originZ = job.originZ();
			builtBlockSignature = job.blockSignature();
			builtHashes = job.hashes();
			pending = null;
			uploaded = 0;
		}
	}

	private static void upload(int unit, int texture, short[] data, int slices) {
		GL13.glActiveTexture(GL13.GL_TEXTURE0 + unit);
		GL11.glBindTexture(GL12.GL_TEXTURE_3D, texture);
		UPLOAD.clear();
		UPLOAD.put(data, uploaded * SLICE_SHORTS, slices * SLICE_SHORTS);
		UPLOAD.flip();
		GL12.glTexSubImage3D(GL12.GL_TEXTURE_3D, 0, 0, 0, uploaded, SIDE_X, SIDE_Y, slices, GL11.GL_RGBA, GL30.GL_HALF_FLOAT, UPLOAD);
	}

	private static void ensureTextures() {
		if (lightTextures[0] != 0) {
			return;
		}
		int previousUnit = GL11.glGetInteger(GL13.GL_ACTIVE_TEXTURE);
		for (int i = 0; i < 2; i++) {
			lightTextures[i] = createTexture(LIGHT_UNIT);
			directionTextures[i] = createTexture(DIRECTION_UNIT);
		}
		GL13.glActiveTexture(previousUnit);
	}

	private static int createTexture(int unit) {
		GL13.glActiveTexture(GL13.GL_TEXTURE0 + unit);
		int texture = GL11.glGenTextures();
		GL11.glBindTexture(GL12.GL_TEXTURE_3D, texture);
		GL11.glTexParameteri(GL12.GL_TEXTURE_3D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_LINEAR);
		GL11.glTexParameteri(GL12.GL_TEXTURE_3D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_LINEAR);
		GL11.glTexParameteri(GL12.GL_TEXTURE_3D, GL11.GL_TEXTURE_WRAP_S, GL12.GL_CLAMP_TO_EDGE);
		GL11.glTexParameteri(GL12.GL_TEXTURE_3D, GL11.GL_TEXTURE_WRAP_T, GL12.GL_CLAMP_TO_EDGE);
		GL11.glTexParameteri(GL12.GL_TEXTURE_3D, GL12.GL_TEXTURE_WRAP_R, GL12.GL_CLAMP_TO_EDGE);
		GL12.glTexImage3D(GL12.GL_TEXTURE_3D, 0, GL30.GL_RGBA16F, SIDE_X, SIDE_Y, SIDE_Z, 0, GL11.GL_RGBA, GL30.GL_HALF_FLOAT, (ByteBuffer) null);
		return texture;
	}

	private static void ensureWorker() {
		if (worker != null) {
			return;
		}
		worker = new Thread(LightVolume::work, "Luceat light volume");
		worker.setDaemon(true);
		worker.setPriority(Thread.NORM_PRIORITY - 1);
		worker.start();
	}

	private static void work() {
		Baker baker = new Baker();
		while (true) {
			Job job;
			try {
				job = JOBS.take();
			} catch (InterruptedException interrupted) {
				return;
			}
			try {
				RESULTS.add(baker.build(job));
			} catch (RuntimeException error) {
				failed = true;
				Luceat.LOGGER.warn("Luceat light volume stopped", error);
				return;
			}
		}
	}

	private static final class Baker {
		private final float[] red = new float[VOXELS];
		private final float[] green = new float[VOXELS];
		private final float[] blue = new float[VOXELS];
		private final float[] cover = new float[VOXELS];
		private final float[] weight = new float[VOXELS];
		private final float[] fire = new float[VOXELS];
		private final float[] pullX = new float[VOXELS];
		private final float[] pullY = new float[VOXELS];
		private final float[] pullZ = new float[VOXELS];
		private final boolean[] solid = new boolean[VOXELS];
		private double passRed;
		private double passGreen;
		private double passBlue;

		Result build(Job job) {
			java.util.Arrays.fill(red, 0.0F);
			java.util.Arrays.fill(green, 0.0F);
			java.util.Arrays.fill(blue, 0.0F);
			java.util.Arrays.fill(cover, 0.0F);
			java.util.Arrays.fill(weight, 0.0F);
			java.util.Arrays.fill(fire, 0.0F);
			java.util.Arrays.fill(pullX, 0.0F);
			java.util.Arrays.fill(pullY, 0.0F);
			java.util.Arrays.fill(pullZ, 0.0F);
			int index = 0;
			for (int k = 0; k < SIDE_Z; k++) {
				for (int j = 0; j < SIDE_Y; j++) {
					for (int i = 0; i < SIDE_X; i++, index++) {
						solid[index] = (ShadowVolume.mirrorId(job.originX() + i, job.originY() + j, job.originZ() + k) & 0xFFFF) == PatternAtlas.OPAQUE;
					}
				}
			}
			for (Bake bake : job.lights()) {
				add(bake, job);
			}
			fillSolid();
			short[] light = new short[VOXELS * 4];
			short[] direction = new short[VOXELS * 4];
			int at = 0;
			for (int v = 0; v < VOXELS; v++, at += 4) {
				light[at] = Float.floatToFloat16(red[v]);
				light[at + 1] = Float.floatToFloat16(green[v]);
				light[at + 2] = Float.floatToFloat16(blue[v]);
				light[at + 3] = Float.floatToFloat16(cover[v]);
				float total = weight[v];
				if (total > 1.0E-6F) {
					float inverse = 1.0F / total;
					direction[at] = Float.floatToFloat16(pullX[v] * inverse);
					direction[at + 1] = Float.floatToFloat16(pullY[v] * inverse);
					direction[at + 2] = Float.floatToFloat16(pullZ[v] * inverse);
					direction[at + 3] = Float.floatToFloat16(Math.min(1.0F, fire[v] * inverse));
				}
			}
			return new Result(job, light, direction);
		}

		private void add(Bake bake, Job job) {
			double radius = bake.radius();
			int x0 = Math.max(job.originX(), (int) Math.floor(bake.x() - radius));
			int y0 = Math.max(job.originY(), (int) Math.floor(bake.y() - radius));
			int z0 = Math.max(job.originZ(), (int) Math.floor(bake.z() - radius));
			int x1 = Math.min(job.originX() + SIDE_X - 1, (int) Math.ceil(bake.x() + radius));
			int y1 = Math.min(job.originY() + SIDE_Y - 1, (int) Math.ceil(bake.y() + radius));
			int z1 = Math.min(job.originZ() + SIDE_Z - 1, (int) Math.ceil(bake.z() + radius));
			int lightX = (int) Math.floor(bake.x());
			int lightY = (int) Math.floor(bake.y());
			int lightZ = (int) Math.floor(bake.z());
			double reachSquared = radius * radius;
			for (int z = z0; z <= z1; z++) {
				for (int y = y0; y <= y1; y++) {
					int row = ((z - job.originZ()) * SIDE_Y + (y - job.originY())) * SIDE_X - job.originX();
					for (int x = x0; x <= x1; x++) {
						int index = row + x;
						if (solid[index]) {
							continue;
						}
						double dx = bake.x() - (x + 0.5);
						double dy = bake.y() - (y + 0.5);
						double dz = bake.z() - (z + 0.5);
						double distanceSquared = dx * dx + dy * dy + dz * dz;
						if (distanceSquared >= reachSquared) {
							continue;
						}
						double distance = Math.sqrt(distanceSquared);
						float level = (float) Math.min(1.0, (radius - distance) / 15.0);
						float curved = level / (4.0F - 3.0F * level);
						if (curved <= 0.0F) {
							continue;
						}
						cover[index] += curved;
						double seenRed = 1.0;
						double seenGreen = 1.0;
						double seenBlue = 1.0;
						if (!bake.noShadow() && distance > 0.9) {
							soften(bake, x, y, z, lightX, lightY, lightZ, job);
							seenRed = passRed;
							seenGreen = passGreen;
							seenBlue = passBlue;
						}
						float addRed = (float) (bake.red() * curved * seenRed);
						float addGreen = (float) (bake.green() * curved * seenGreen);
						float addBlue = (float) (bake.blue() * curved * seenBlue);
						red[index] += addRed;
						green[index] += addGreen;
						blue[index] += addBlue;
						float share = 0.299F * addRed + 0.587F * addGreen + 0.114F * addBlue;
						if (share > 0.0F && distance > 0.01) {
							weight[index] += share;
							pullX[index] += (float) (dx / distance) * share;
							pullY[index] += (float) (dy / distance) * share;
							pullZ[index] += (float) (dz / distance) * share;
							if (bake.fire()) {
								fire[index] += share;
							}
						}
					}
				}
			}
		}

		private void soften(Bake bake, int x, int y, int z, int lightX, int lightY, int lightZ, Job job) {
			double sumRed = 0.0;
			double sumGreen = 0.0;
			double sumBlue = 0.0;
			double firstRed = 0.0;
			double firstGreen = 0.0;
			double firstBlue = 0.0;
			int taken = 0;
			for (int s = 0; s < TAPS.length; s++) {
				double[] tap = TAPS[s];
				double ox = x + 0.5 + jitter(x, y, z, s * 3);
				double oy = y + 0.5 + jitter(x, y, z, s * 3 + 1);
				double oz = z + 0.5 + jitter(x, y, z, s * 3 + 2);
				double tx = Math.min(lightX + 0.95, Math.max(lightX + 0.05, bake.x() + tap[0]));
				double ty = Math.min(lightY + 0.95, Math.max(lightY + 0.05, bake.y() + tap[1]));
				double tz = Math.min(lightZ + 0.95, Math.max(lightZ + 0.05, bake.z() + tap[2]));
				double length = Math.sqrt((tx - ox) * (tx - ox) + (ty - oy) * (ty - oy) + (tz - oz) * (tz - oz));
				if (length < 0.5) {
					passRed = 1.0;
					passGreen = 1.0;
					passBlue = 1.0;
				} else {
					trace(ox, oy, oz, tx, ty, tz, length, lightX, lightY, lightZ, job);
				}
				sumRed += passRed;
				sumGreen += passGreen;
				sumBlue += passBlue;
				taken++;
				if (s == 0) {
					firstRed = passRed;
					firstGreen = passGreen;
					firstBlue = passBlue;
				} else if (s == 1) {
					double top = Math.max(passRed, Math.max(passGreen, passBlue));
					boolean agree = Math.abs(passRed - firstRed) < 0.02 && Math.abs(passGreen - firstGreen) < 0.02 && Math.abs(passBlue - firstBlue) < 0.02;
					if (agree && (top > 0.98 || top < 0.02)) {
						break;
					}
				}
			}
			passRed = sumRed / taken;
			passGreen = sumGreen / taken;
			passBlue = sumBlue / taken;
		}

		private static double jitter(int x, int y, int z, int salt) {
			int h = x * 73856093 ^ y * 19349663 ^ z * 83492791 ^ salt * 0x9E3779B1;
			h ^= h >>> 13;
			h *= 0x5BD1E995;
			h ^= h >>> 15;
			return ((h & 0xFFFF) / 65535.0 - 0.5) * 0.7;
		}

		private void fillSolid() {
			int index = 0;
			for (int k = 0; k < SIDE_Z; k++) {
				for (int j = 0; j < SIDE_Y; j++) {
					for (int i = 0; i < SIDE_X; i++, index++) {
						if (!solid[index]) {
							continue;
						}
						int n = 0;
						float r = 0.0F;
						float g = 0.0F;
						float b = 0.0F;
						float c = 0.0F;
						float w = 0.0F;
						float f = 0.0F;
						float px = 0.0F;
						float py = 0.0F;
						float pz = 0.0F;
						for (int side = 0; side < 6; side++) {
							int axis = side >> 1;
							int step = (side & 1) == 0 ? -1 : 1;
							int ni = i + (axis == 0 ? step : 0);
							int nj = j + (axis == 1 ? step : 0);
							int nk = k + (axis == 2 ? step : 0);
							if (ni < 0 || nj < 0 || nk < 0 || ni >= SIDE_X || nj >= SIDE_Y || nk >= SIDE_Z) {
								continue;
							}
							int near = (nk * SIDE_Y + nj) * SIDE_X + ni;
							if (solid[near]) {
								continue;
							}
							r += red[near];
							g += green[near];
							b += blue[near];
							c += cover[near];
							w += weight[near];
							f += fire[near];
							px += pullX[near];
							py += pullY[near];
							pz += pullZ[near];
							n++;
						}
						if (n > 0) {
							float inverse = 1.0F / n;
							red[index] = r * inverse;
							green[index] = g * inverse;
							blue[index] = b * inverse;
							cover[index] = c * inverse;
							weight[index] = w * inverse;
							fire[index] = f * inverse;
							pullX[index] = px * inverse;
							pullY[index] = py * inverse;
							pullZ[index] = pz * inverse;
						}
					}
				}
			}
		}

		private void trace(double ox, double oy, double oz, double lx, double ly, double lz, double length, int lightX, int lightY, int lightZ, Job job) {
			passRed = 1.0;
			passGreen = 1.0;
			passBlue = 1.0;
			double dx = (lx - ox) / length;
			double dy = (ly - oy) / length;
			double dz = (lz - oz) / length;
			int x = (int) Math.floor(ox);
			int y = (int) Math.floor(oy);
			int z = (int) Math.floor(oz);
			int stepX = dx > 0.0 ? 1 : -1;
			int stepY = dy > 0.0 ? 1 : -1;
			int stepZ = dz > 0.0 ? 1 : -1;
			double deltaX = Math.abs(dx) < 1.0E-9 ? Double.MAX_VALUE : 1.0 / Math.abs(dx);
			double deltaY = Math.abs(dy) < 1.0E-9 ? Double.MAX_VALUE : 1.0 / Math.abs(dy);
			double deltaZ = Math.abs(dz) < 1.0E-9 ? Double.MAX_VALUE : 1.0 / Math.abs(dz);
			double nextX = deltaX == Double.MAX_VALUE ? Double.MAX_VALUE : (dx > 0.0 ? x + 1 - ox : ox - x) * deltaX;
			double nextY = deltaY == Double.MAX_VALUE ? Double.MAX_VALUE : (dy > 0.0 ? y + 1 - oy : oy - y) * deltaY;
			double nextZ = deltaZ == Double.MAX_VALUE ? Double.MAX_VALUE : (dz > 0.0 ? z + 1 - oz : oz - z) * deltaZ;
			int mx = Math.floorMod(x, ShadowVolume.DIMENSION);
			int my = Math.floorMod(y, ShadowVolume.DIMENSION);
			int mz = Math.floorMod(z, ShadowVolume.DIMENSION);
			for (int guard = 0; guard < 64; guard++) {
				if (nextX <= nextY && nextX <= nextZ) {
					if (nextX >= length) {
						return;
					}
					x += stepX;
					mx = wrap(mx + stepX);
					nextX += deltaX;
				} else if (nextY <= nextZ) {
					if (nextY >= length) {
						return;
					}
					y += stepY;
					my = wrap(my + stepY);
					nextY += deltaY;
				} else {
					if (nextZ >= length) {
						return;
					}
					z += stepZ;
					mz = wrap(mz + stepZ);
					nextZ += deltaZ;
				}
				if (x == lightX && y == lightY && z == lightZ) {
					return;
				}
				if (x < job.minX() || x > job.maxX() || y < job.minY() || y > job.maxY() || z < job.minZ() || z > job.maxZ()) {
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
