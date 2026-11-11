package me.bewf.luceat.client.shadow;

import java.nio.ByteBuffer;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.LinkedBlockingQueue;

import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL12;
import org.lwjgl.opengl.GL13;
import org.lwjgl.opengl.GL20;

import me.bewf.luceat.Luceat;
import me.bewf.luceat.client.config.LuceatConfig;
import me.bewf.luceat.client.light.Stats;
import me.bewf.luceat.client.light.SunLight;
import me.bewf.luceat.client.shader.ShaderVariant;
import me.bewf.luceat.client.shader.TerrainShaderPatch;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.phys.Vec3;

public final class SunCache {
	public static final int SIDE_X = 96;
	public static final int SIDE_Y = 64;
	public static final int SIDE_Z = 96;
	public static final String SAMPLER = "u_LuceatSunVolume";
	public static final int UNIT = 7;
	public static final float REACH_LIMIT = 38.0F;
	public static final int BIAS_Y = 28;

	private static final int VOXELS = SIDE_X * SIDE_Y * SIDE_Z;
	private static final int SLICE_BYTES = SIDE_X * SIDE_Y * 4;
	private static final int SLICES_PER_FRAME = 12;
	private static final double RAY_LENGTH = 40.0;
	private static final double SUN_TURN = 0.99998;
	private static final int DRIFT = 10;
	private static final int CHECK_INTERVAL = 8;
	private static final int HALF_RES = ShapeMasks.RES / 2;
	private static final int[] UNPACK = {
		GL11.GL_UNPACK_ALIGNMENT, GL11.GL_UNPACK_ROW_LENGTH, GL11.GL_UNPACK_SKIP_ROWS,
		GL11.GL_UNPACK_SKIP_PIXELS, GL12.GL_UNPACK_IMAGE_HEIGHT, GL12.GL_UNPACK_SKIP_IMAGES
	};

	private record Job(int generation, int signature, int originX, int originY, int originZ, double dirX, double dirY, double dirZ, int minX, int minY, int minZ, int maxX, int maxY, int maxZ) {
	}

	private record Result(Job job, byte[] data) {
	}

	private static final LinkedBlockingQueue<Job> JOBS = new LinkedBlockingQueue<>();
	private static final ConcurrentLinkedQueue<Result> RESULTS = new ConcurrentLinkedQueue<>();
	private static final ByteBuffer UPLOAD = BufferUtils.createByteBuffer(SLICE_BYTES * SLICES_PER_FRAME * 2);
	private static final int[] textures = new int[2];

	private static Thread worker;
	private static volatile boolean failed;
	private static volatile int generation;
	private static boolean inFlight;
	private static boolean active;
	private static int front = -1;
	private static int originX;
	private static int originY;
	private static int originZ;
	private static double builtX;
	private static double builtY;
	private static double builtZ;
	private static int builtSignature;
	private static Result pending;
	private static int uploaded;
	private static int frame;
	private static ClientLevel level;

	private SunCache() {
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

	public static boolean busy() {
		return inFlight || pending != null;
	}

	public static long bytes() {
		return textures[0] == 0 ? 0L : (long) VOXELS * 8L;
	}

	public static void reset() {
		generation++;
		JOBS.clear();
		RESULTS.clear();
		pending = null;
		inFlight = false;
		front = -1;
		uploaded = 0;
	}

	public static void update(ClientLevel clientLevel, Vec3 camera) {
		long started = System.nanoTime();
		LuceatConfig config = LuceatConfig.get();
		ShaderVariant variant = TerrainShaderPatch.built();
		active = false;
		if (failed || clientLevel == null || !config.sunShadows || variant == null || !variant.rays()) {
			return;
		}
		if (clientLevel != level) {
			level = clientLevel;
			reset();
		}
		float[] sun = SunLight.values(clientLevel, Minecraft.getInstance().getDeltaTracker().getGameTimeDeltaPartialTick(false), config);
		active = true;
		ensureTextures();
		if (sun[3] <= 0.001F) {
			return;
		}
		ensureWorker();
		frame++;
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
			maybeQueue(camera, sun);
		}
		Stats.record(Stats.CACHE, System.nanoTime() - started);
	}

	public static void bind(int program) {
		int location = GL20.glGetUniformLocation(program, SAMPLER);
		if (location < 0) {
			return;
		}
		GL20.glUniform1i(location, UNIT);
		int previousUnit = GL11.glGetInteger(GL13.GL_ACTIVE_TEXTURE);
		GL13.glActiveTexture(GL13.GL_TEXTURE0 + UNIT);
		GL11.glBindTexture(GL12.GL_TEXTURE_3D, textures[0] == 0 ? 0 : textures[front >= 0 ? front : 0]);
		GL13.glActiveTexture(previousUnit);
	}

	private static void maybeQueue(Vec3 camera, float[] sun) {
		int camX = (int) Math.floor(camera.x);
		int camY = (int) Math.floor(camera.y);
		int camZ = (int) Math.floor(camera.z);
		int wantX = Math.floorDiv(camX - SIDE_X / 2, 8) * 8;
		int wantY = Math.floorDiv(camY - BIAS_Y, 8) * 8;
		int wantZ = Math.floorDiv(camZ - SIDE_Z / 2, 8) * 8;
		boolean need = front < 0;
		if (!need) {
			double turn = sun[0] * builtX + sun[1] * builtY + sun[2] * builtZ;
			need = turn < SUN_TURN
				|| Math.abs(camX - (originX + SIDE_X / 2)) > DRIFT
				|| Math.abs(camZ - (originZ + SIDE_Z / 2)) > DRIFT
				|| Math.abs(camY - (originY + BIAS_Y)) > DRIFT - 2;
		}
		int signature = 0;
		if (need || frame % CHECK_INTERVAL == 0) {
			int x0 = need ? wantX : originX;
			int y0 = need ? wantY : originY;
			int z0 = need ? wantZ : originZ;
			signature = ShadowVolume.looseSignature(x0, y0, z0, x0 + SIDE_X - 1, y0 + SIDE_Y - 1, z0 + SIDE_Z - 1);
			if (!need) {
				need = signature != builtSignature;
			}
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
			generation, signature, wantX, wantY, wantZ, sun[0], sun[1], sun[2],
			(cellX - half) * size, (cellY - half) * size, (cellZ - half) * size,
			(cellX + half + 1) * size - 1, (cellY + half + 1) * size - 1, (cellZ + half + 1) * size - 1
		);
		inFlight = true;
		JOBS.add(job);
	}

	private static void continueUpload() {
		Job job = pending.job();
		int target = front == 0 ? 1 : 0;
		boolean slow = Stats.frameMillis() > 40.0F;
		int slices = Math.min(slow ? SLICES_PER_FRAME * 2 : SLICES_PER_FRAME, SIDE_Z - uploaded);
		int previousUnit = GL11.glGetInteger(GL13.GL_ACTIVE_TEXTURE);
		int[] previous = new int[UNPACK.length];
		for (int i = 0; i < UNPACK.length; i++) {
			previous[i] = GL11.glGetInteger(UNPACK[i]);
			GL11.glPixelStorei(UNPACK[i], i == 0 ? 1 : 0);
		}
		GL13.glActiveTexture(GL13.GL_TEXTURE0 + UNIT);
		GL11.glBindTexture(GL12.GL_TEXTURE_3D, textures[target]);
		UPLOAD.clear();
		UPLOAD.put(pending.data(), uploaded * SLICE_BYTES, slices * SLICE_BYTES);
		UPLOAD.flip();
		GL12.glTexSubImage3D(GL12.GL_TEXTURE_3D, 0, 0, 0, uploaded, SIDE_X, SIDE_Y, slices, GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, UPLOAD);
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
			builtX = job.dirX();
			builtY = job.dirY();
			builtZ = job.dirZ();
			builtSignature = job.signature();
			pending = null;
			uploaded = 0;
		}
	}

	private static void ensureTextures() {
		if (textures[0] != 0) {
			return;
		}
		int previousUnit = GL11.glGetInteger(GL13.GL_ACTIVE_TEXTURE);
		GL13.glActiveTexture(GL13.GL_TEXTURE0 + UNIT);
		for (int i = 0; i < textures.length; i++) {
			textures[i] = GL11.glGenTextures();
			GL11.glBindTexture(GL12.GL_TEXTURE_3D, textures[i]);
			GL11.glTexParameteri(GL12.GL_TEXTURE_3D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_LINEAR);
			GL11.glTexParameteri(GL12.GL_TEXTURE_3D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_LINEAR);
			GL11.glTexParameteri(GL12.GL_TEXTURE_3D, GL11.GL_TEXTURE_WRAP_S, GL12.GL_CLAMP_TO_EDGE);
			GL11.glTexParameteri(GL12.GL_TEXTURE_3D, GL11.GL_TEXTURE_WRAP_T, GL12.GL_CLAMP_TO_EDGE);
			GL11.glTexParameteri(GL12.GL_TEXTURE_3D, GL12.GL_TEXTURE_WRAP_R, GL12.GL_CLAMP_TO_EDGE);
			GL12.glTexImage3D(GL12.GL_TEXTURE_3D, 0, GL11.GL_RGBA8, SIDE_X, SIDE_Y, SIDE_Z, 0, GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, (ByteBuffer) null);
		}
		GL13.glActiveTexture(previousUnit);
	}

	private static void ensureWorker() {
		if (worker != null) {
			return;
		}
		worker = new Thread(SunCache::work, "Luceat sun cache");
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
				Luceat.LOGGER.warn("Luceat sun cache stopped", error);
				return;
			}
		}
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
			int index = 0;
			for (int k = 0; k < SIDE_Z; k++) {
				for (int j = 0; j < SIDE_Y; j++) {
					for (int i = 0; i < SIDE_X; i++, index++) {
						int wx = job.originX() + i;
						int wy = job.originY() + j;
						int wz = job.originZ() + k;
						if ((ShadowVolume.mirrorId(wx, wy, wz) & 0xFFFF) == PatternAtlas.OPAQUE) {
							solid[index] = true;
							continue;
						}
						solid[index] = false;
						trace(wx, wy, wz, job);
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
			for (int k = 0; k < SIDE_Z; k++) {
				for (int j = 0; j < SIDE_Y; j++) {
					for (int i = 0; i < SIDE_X; i++, index++) {
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

		private void trace(int cx, int cy, int cz, Job job) {
			passRed = 1.0;
			passGreen = 1.0;
			passBlue = 1.0;
			double dx = job.dirX();
			double dy = job.dirY();
			double dz = job.dirZ();
			int stepX = dx > 0.0 ? 1 : -1;
			int stepY = dy > 0.0 ? 1 : -1;
			int stepZ = dz > 0.0 ? 1 : -1;
			double deltaX = Math.abs(dx) < 1.0E-9 ? Double.MAX_VALUE : 1.0 / Math.abs(dx);
			double deltaY = Math.abs(dy) < 1.0E-9 ? Double.MAX_VALUE : 1.0 / Math.abs(dy);
			double deltaZ = Math.abs(dz) < 1.0E-9 ? Double.MAX_VALUE : 1.0 / Math.abs(dz);
			double nextX = deltaX == Double.MAX_VALUE ? Double.MAX_VALUE : 0.5 * deltaX;
			double nextY = deltaY == Double.MAX_VALUE ? Double.MAX_VALUE : 0.5 * deltaY;
			double nextZ = deltaZ == Double.MAX_VALUE ? Double.MAX_VALUE : 0.5 * deltaZ;
			int x = cx;
			int y = cy;
			int z = cz;
			int mx = Math.floorMod(cx, ShadowVolume.DIMENSION);
			int my = Math.floorMod(cy, ShadowVolume.DIMENSION);
			int mz = Math.floorMod(cz, ShadowVolume.DIMENSION);
			for (int guard = 0; guard < 192; guard++) {
				if (nextX <= nextY && nextX <= nextZ) {
					if (nextX >= RAY_LENGTH) {
						return;
					}
					x += stepX;
					mx = wrap(mx + stepX);
					nextX += deltaX;
				} else if (nextY <= nextZ) {
					if (nextY >= RAY_LENGTH) {
						return;
					}
					y += stepY;
					my = wrap(my + stepY);
					nextY += deltaY;
				} else {
					if (nextZ >= RAY_LENGTH) {
						return;
					}
					z += stepZ;
					mz = wrap(mz + stepZ);
					nextZ += deltaZ;
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
