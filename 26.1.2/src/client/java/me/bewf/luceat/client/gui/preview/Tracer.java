package me.bewf.luceat.client.gui.preview;

import java.util.function.BooleanSupplier;
import java.util.stream.IntStream;

import me.bewf.luceat.client.shadow.ShapeMasks;

public final class Tracer {
	public static final int VOX = 16;
	private static final int SHAPE_RES = ShapeMasks.RES;
	private static final double DESIGN_ASPECT = 1.5;
	private static final float[] FLOOR = {0.035F, 0.04F, 0.065F};
	private static final float[] WATER_ABSORB = {0.42F, 0.15F, 0.09F};
	private static final float[] WATER_DEEP = {0.03F, 0.17F, 0.27F};

	private final int width;
	private final int height;
	private final int depth;
	private final int[][] voxels;
	private final Occ[] occluders;
	private final Light[] lights;
	private final Params params;
	private final float[] shineCells;
	private final double[] eye;
	private final double[] forward = new double[3];
	private final double[] right = new double[3];
	private final double[] up = new double[3];
	private final double tanHalf;
	private Extras extras = new Extras();
	private Atmosphere atmosphere;
	private float[] fogColour = new float[3];

	private static final class Ray {
		float red;
		float green;
		float blue;
		float transmit = 1.0F;
		double depth = Double.MAX_VALUE;
		boolean skipWater;
	}

	public Tracer(int width, int height, int depth, int[][] voxels, Occ[] occluders, Light[] lights, Params params, double[] eye, double[] target, double fovDegrees) {
		this(width, height, depth, voxels, occluders, null, lights, params, eye, target, fovDegrees);
	}

	public Tracer(int width, int height, int depth, int[][] voxels, Occ[] occluders, float[] shineCells, Light[] lights, Params params, double[] eye, double[] target, double fovDegrees) {
		this.shineCells = shineCells;
		this.width = width;
		this.height = height;
		this.depth = depth;
		this.voxels = voxels;
		this.occluders = occluders;
		this.lights = lights;
		this.params = params;
		this.eye = eye;
		this.tanHalf = Math.tan(Math.toRadians(fovDegrees) * 0.5);
		for (int i = 0; i < 3; i++) {
			forward[i] = target[i] - eye[i];
		}
		normalize(forward);
		double[] worldUp = {0.0, 1.0, 0.0};
		cross(forward, worldUp, right);
		normalize(right);
		cross(right, forward, up);
		this.atmosphere = new Atmosphere(params.time(), 0.0);
		atmosphere.fog(fogColour);
	}

	public void extras(Extras value) {
		this.extras = value;
		this.atmosphere = new Atmosphere(params.time(), value.sunYaw);
		atmosphere.fog(fogColour);
	}

	public void render(int[] out, int pixelsWide, int pixelsHigh) {
		render(out, pixelsWide, pixelsHigh, () -> false);
	}

	public void render(int[] out, int pixelsWide, int pixelsHigh, BooleanSupplier cancelled) {
		double aspect = (double) pixelsWide / pixelsHigh;
		double tanHalf = reach(aspect);
		IntStream.range(0, pixelsHigh).parallel().forEach(row -> {
			if (cancelled.getAsBoolean()) {
				return;
			}
			double[] dir = new double[3];
			for (int column = 0; column < pixelsWide; column++) {
				double su = (column + 0.5) / pixelsWide * 2.0 - 1.0;
				double sv = 1.0 - (row + 0.5) / pixelsHigh * 2.0;
				double u = su * tanHalf * aspect;
				double v = sv * tanHalf;
				for (int i = 0; i < 3; i++) {
					dir[i] = forward[i] + right[i] * u + up[i] * v;
				}
				normalize(dir);
				out[column + row * pixelsWide] = pixel(dir, su, sv, column, row);
			}
		});
	}

	public void renderPanorama(int[] out, int pixelsWide, int pixelsHigh, double yawCentre, double yawSpan, double tanVertical) {
		double baseYaw = Math.atan2(forward[0], forward[2]);
		IntStream.range(0, pixelsHigh).parallel().forEach(row -> {
			double[] dir = new double[3];
			for (int column = 0; column < pixelsWide; column++) {
				double yaw = baseYaw + yawCentre + ((column + 0.5) / pixelsWide - 0.5) * yawSpan;
				double sv = 1.0 - (row + 0.5) / pixelsHigh * 2.0;
				dir[0] = Math.sin(yaw);
				dir[1] = sv * tanVertical + forward[1] / Math.max(0.2, Math.sqrt(forward[0] * forward[0] + forward[2] * forward[2]));
				dir[2] = Math.cos(yaw);
				normalize(dir);
				double su = ((column + 0.5) / pixelsWide * 2.0 - 1.0) * 0.35;
				out[column + row * pixelsWide] = pixel(dir, su, sv, column, row);
			}
		});
	}

	private double reach(double aspect) {
		return tanHalf * Math.max(1.0, DESIGN_ASPECT / aspect);
	}

	public double[] rayDirection(double fractionX, double fractionY, double aspect) {
		double tanHalf = reach(aspect);
		double[] dir = new double[3];
		double u = (fractionX * 2.0 - 1.0) * tanHalf * aspect;
		double v = (1.0 - fractionY * 2.0) * tanHalf;
		for (int i = 0; i < 3; i++) {
			dir[i] = forward[i] + right[i] * u + up[i] * v;
		}
		normalize(dir);
		return dir;
	}

	public double[] eye() {
		return eye;
	}

	public double[] project(double x, double y, double z, double aspect) {
		double tanHalf = reach(aspect);
		double rx = x - eye[0];
		double ry = y - eye[1];
		double rz = z - eye[2];
		double depthAlong = rx * forward[0] + ry * forward[1] + rz * forward[2];
		if (depthAlong < 0.05) {
			return null;
		}
		double u = (rx * right[0] + ry * right[1] + rz * right[2]) / depthAlong / (tanHalf * aspect);
		double v = (rx * up[0] + ry * up[1] + rz * up[2]) / depthAlong / tanHalf;
		return new double[]{(u + 1.0) * 0.5, (1.0 - v) * 0.5, depthAlong};
	}

	private int pixel(double[] dir, double su, double sv, int px, int py) {
		Ray ray = new Ray();
		trace(ray, eye[0], eye[1], eye[2], dir[0], dir[1], dir[2]);
		float[] sky = new float[3];
		if (ray.transmit > 0.02F) {
			atmosphere.sky(dir[0], dir[1], dir[2], extras.clock, sky);
		}
		float red = ray.red + ray.transmit * sky[0];
		float green = ray.green + ray.transmit * sky[1];
		float blue = ray.blue + ray.transmit * sky[2];
		double limit = ray.depth;
		for (Light light : lights) {
			double lx = light.x() - eye[0];
			double ly = light.y() - eye[1];
			double lz = light.z() - eye[2];
			double along = lx * dir[0] + ly * dir[1] + lz * dir[2];
			if (along <= 0.0 || along >= limit) {
				continue;
			}
			double off = lx * lx + ly * ly + lz * lz - along * along;
			double scale = Math.max(0.6, 2.4 / Math.max(along, 0.5));
			float glow = (float) ((1.1 * Math.exp(-off / (0.012 * scale)) + 0.22 * Math.exp(-off / (0.14 * scale)) + 0.07 * Math.exp(-off / (1.4 * scale))) * (0.35 + 0.65 * (1.0F - atmosphere.day * 0.8F)));
			red += light.red() * glow;
			green += light.green() * glow;
			blue += light.blue() * glow;
		}
		float vignette = 1.0F - 0.16F * (float) Math.min(1.0, (su * su * 0.7 + sv * sv * 0.5));
		red *= vignette;
		green *= vignette;
		blue *= vignette;
		float noise = Atmosphere.hash(px * 7 + 13, py * 11 + 5) - 0.5F;
		return 0xFF000000 | channel(shoulder(red), noise) << 16 | channel(shoulder(green), noise) << 8 | channel(shoulder(blue), noise);
	}

	private static float shoulder(float value) {
		if (value <= 0.8F) {
			return value;
		}
		return 0.8F + 0.2F * (1.0F - (float) Math.exp(-(value - 0.8F) / 0.2F));
	}

	private static int channel(float value, float noise) {
		return Math.min(255, Math.max(0, Math.round(value * 255.0F + noise)));
	}

	private float[] ambientAt(int index) {
		float level = extras.sky == null || index < 0 ? 1.0F : extras.sky[index];
		float curved = level / (4.0F - 3.0F * level);
		float[] a = atmosphere.ambient;
		return new float[]{a[0] * curved + FLOOR[0], a[1] * curved + FLOOR[1], a[2] * curved + FLOOR[2]};
	}

	private int indexOf(int x, int y, int z) {
		if (x < 0 || y < 0 || z < 0 || x >= width || y >= height || z >= depth) {
			return -1;
		}
		return x + width * (y + height * z);
	}

	private void trace(Ray ray, double ox, double oy, double oz, double dx, double dy, double dz) {
		double[] o = {ox, oy, oz};
		double[] d = {dx, dy, dz};
		int[] size = {width, height, depth};
		double near = 0.0;
		double far = Double.MAX_VALUE;
		int entryAxis = 0;
		for (int a = 0; a < 3; a++) {
			if (Math.abs(d[a]) < 1.0E-9) {
				if (o[a] < 0.0 || o[a] > size[a]) {
					return;
				}
				continue;
			}
			double t1 = -o[a] / d[a];
			double t2 = (size[a] - o[a]) / d[a];
			double lo = Math.min(t1, t2);
			double hi = Math.max(t1, t2);
			if (lo > near) {
				near = lo;
				entryAxis = a;
			}
			far = Math.min(far, hi);
		}
		if (near > far) {
			return;
		}
		double[] sgn = new double[3];
		double[] safe = new double[3];
		double[] tDelta = new double[3];
		double[] tMax = new double[3];
		int[] cell = new int[3];
		for (int a = 0; a < 3; a++) {
			sgn[a] = d[a] >= 0.0 ? 1.0 : -1.0;
			safe[a] = Math.max(Math.abs(d[a]), 1.0E-9);
			tDelta[a] = 1.0 / safe[a];
			double start = o[a] + d[a] * (near + 1.0E-7);
			cell[a] = Math.min(size[a] - 1, Math.max(0, (int) Math.floor(start)));
			tMax[a] = (cell[a] + Math.max(sgn[a], 0.0) - o[a]) * sgn[a] / safe[a];
		}
		double tEntry = near;
		int limit = width + height + depth + 4;
		byte[] water = extras.water;
		for (int i = 0; i < limit; i++) {
			double tExit = Math.min(tMax[0], Math.min(tMax[1], tMax[2]));
			int exitAxis = tMax[0] <= tMax[1] && tMax[0] <= tMax[2] ? 0 : (tMax[1] <= tMax[2] ? 1 : 2);
			int index = cell[0] + width * (cell[1] + height * cell[2]);
			int[] block = voxels[index];
			double segmentEnd = Math.min(tExit, far);
			if (block != null && cellRay(ray, block, o, d, cell, tEntry, segmentEnd, entryAxis, sgn, safe, index)) {
				return;
			}
			if (water != null && water[index] != Extras.DRY && !ray.skipWater) {
				if (ray.depth == Double.MAX_VALUE) {
					ray.depth = tEntry;
				}
				volume(ray, water[index], o, d, tEntry, segmentEnd, index);
				if (ray.transmit < 0.04F) {
					return;
				}
			}
			if (tExit >= far) {
				return;
			}
			tEntry = tExit;
			entryAxis = exitAxis;
			cell[exitAxis] += (int) sgn[exitAxis];
			tMax[exitAxis] += tDelta[exitAxis];
			if (cell[exitAxis] < 0 || cell[exitAxis] >= size[exitAxis]) {
				return;
			}
		}
	}

	private void volume(Ray ray, byte kind, double[] o, double[] d, double t0, double t1, int index) {
		double length = Math.max(0.0, t1 - t0);
		if (length <= 0.0) {
			return;
		}
		double mid = (t0 + t1) * 0.5;
		double px = o[0] + d[0] * mid;
		double py = o[1] + d[1] * mid;
		double pz = o[2] + d[2] * mid;
		float[] ambient = ambientAt(index);
		float[] lit = cheapLight(px, py, pz);
		float light0 = ambient[0] + lit[0];
		float light1 = ambient[1] + lit[1];
		float light2 = ambient[2] + lit[2];
		if (kind == Extras.FALLING) {
			float streak = streak(px, py, pz);
			float keep = (float) Math.exp(-length * 2.3);
			float amount = 1.0F - keep;
			float base = 0.55F + 0.45F * streak;
			float r = (0.62F * base + 0.2F * streak) * light0;
			float g = (0.78F * base + 0.17F * streak) * light1;
			float b = (0.95F * base + 0.1F * streak) * light2;
			float[] fogged = fogged(r, g, b, t0);
			ray.red += ray.transmit * amount * fogged[0];
			ray.green += ray.transmit * amount * fogged[1];
			ray.blue += ray.transmit * amount * fogged[2];
			ray.transmit *= keep;
			return;
		}
		float lengthF = (float) length;
		float keepR = (float) Math.exp(-lengthF * WATER_ABSORB[0]);
		float keepG = (float) Math.exp(-lengthF * WATER_ABSORB[1]);
		float keepB = (float) Math.exp(-lengthF * WATER_ABSORB[2]);
		float keep = (keepR + keepG + keepB) / 3.0F;
		float scatter = 1.0F - keep;
		float r = WATER_DEEP[0] * (0.5F + light0) * scatter;
		float g = WATER_DEEP[1] * (0.5F + light1) * scatter;
		float b = WATER_DEEP[2] * (0.5F + light2) * scatter;
		ray.red += ray.transmit * r;
		ray.green += ray.transmit * g;
		ray.blue += ray.transmit * b;
		ray.transmit *= keep;
	}

	private float streak(double x, double y, double z) {
		double along = x * 9.0 + z * 9.0;
		float a = Atmosphere.noise(along, y * 3.2 + extras.clock * 5.5);
		float b = Atmosphere.noise(along * 2.3 + 11.0, y * 6.0 + extras.clock * 8.5);
		return Math.min(1.0F, 0.15F + 0.6F * a + 0.4F * b);
	}

	private float[] cheapLight(double px, double py, double pz) {
		float[] total = new float[3];
		for (Light light : lights) {
			double dx = light.x() - px;
			double dy = light.y() - py;
			double dz = light.z() - pz;
			double dist = Math.sqrt(dx * dx + dy * dy + dz * dz);
			float level = Math.min(1.0F, (float) Math.max(light.radius() - dist, 0.0) / 15.0F);
			if (level <= 0.0F) {
				continue;
			}
			float curved = level / (4.0F - 3.0F * level);
			float strength = light.placed() ? params.placedStrength() : 1.0F;
			float scale = params.brightness() * curved * strength * 0.8F;
			total[0] += light.red() * scale;
			total[1] += light.green() * scale;
			total[2] += light.blue() * scale;
		}
		return total;
	}

	private float[] fogged(float r, float g, float b, double distance) {
		if (extras.fog <= 0.0F) {
			return new float[]{r, g, b};
		}
		float f = 1.0F - (float) Math.exp(-distance * extras.fog);
		return new float[]{r + (fogColour[0] - r) * f, g + (fogColour[1] - g) * f, b + (fogColour[2] - b) * f};
	}

	private boolean cellRay(Ray ray, int[] block, double[] o, double[] d, int[] cell, double t0, double t1, int entryAxis, double[] sgn, double[] safe, int index) {
		double[] local = new double[3];
		int[] v = new int[3];
		double[] tMax = new double[3];
		double[] tDelta = new double[3];
		for (int a = 0; a < 3; a++) {
			local[a] = Math.min(VOX - 0.001, Math.max(0.0, (o[a] + d[a] * t0 - cell[a]) * VOX));
			v[a] = (int) Math.floor(local[a]);
			tDelta[a] = 1.0 / (safe[a] * VOX);
			tMax[a] = t0 + (v[a] + Math.max(sgn[a], 0.0) - local[a]) * sgn[a] / (safe[a] * VOX);
		}
		byte kind = extras.water == null ? Extras.DRY : extras.water[index];
		int axis = entryAxis;
		double t = t0;
		float[] out = new float[3];
		for (int k = 0; k < 3 * VOX + 2; k++) {
			double tNext = Math.min(tMax[0], Math.min(tMax[1], tMax[2]));
			int argb = block[v[0] + VOX * (v[1] + VOX * v[2])];
			int alpha = argb >>> 24;
			if (alpha > 0 && !(kind != Extras.DRY && ray.skipWater)) {
				if (ray.depth == Double.MAX_VALUE) {
					ray.depth = t;
				}
				double[] normal = new double[3];
				normal[axis] = -sgn[axis];
				double px = o[0] + d[0] * t;
				double py = o[1] + d[1] * t;
				double pz = o[2] + d[2] * t;
				float albedoRed = (argb >> 16 & 255) / 255.0F;
				float albedoGreen = (argb >> 8 & 255) / 255.0F;
				float albedoBlue = (argb & 255) / 255.0F;
				boolean glow = alpha == 254;
				float a = glow ? 1.0F : alpha / 255.0F;
				if (kind != Extras.DRY) {
					if (kind == Extras.FOAM) {
						float foam = Atmosphere.noise(px * 8.0 + extras.clock * 0.7, pz * 8.0 + 3.0);
						float whiten = Math.max(0.0F, foam - 0.38F) * 1.6F;
						albedoRed += (1.0F - albedoRed) * whiten;
						albedoGreen += (1.0F - albedoGreen) * whiten;
						albedoBlue += (1.0F - albedoBlue) * whiten;
						a = Math.min(1.0F, a + whiten * 0.8F);
					}
					shade(px, py, pz, normal, albedoRed, albedoGreen, albedoBlue, out, index, shineAt(cell), t, false);
					waterTop(ray, o, d, px, py, pz, normal, out);
				} else {
					shade(px, py, pz, normal, albedoRed, albedoGreen, albedoBlue, out, index, shineAt(cell), t, glow);
				}
				float[] fogged = fogged(out[0], out[1], out[2], t);
				ray.red += ray.transmit * a * fogged[0];
				ray.green += ray.transmit * a * fogged[1];
				ray.blue += ray.transmit * a * fogged[2];
				ray.transmit *= 1.0F - a;
				if (ray.transmit < 0.04F) {
					return true;
				}
			}
			if (tNext >= t1) {
				return false;
			}
			t = tNext;
			axis = tMax[0] <= tMax[1] && tMax[0] <= tMax[2] ? 0 : (tMax[1] <= tMax[2] ? 1 : 2);
			v[axis] += (int) sgn[axis];
			tMax[axis] += tDelta[axis];
			if (v[axis] < 0 || v[axis] >= VOX) {
				return false;
			}
		}
		return false;
	}

	private void waterTop(Ray ray, double[] o, double[] d, double px, double py, double pz, double[] n, float[] out) {
		if (n[1] < 0.5 || extras.reflectivity <= 0.0F) {
			return;
		}
		double nx = (Atmosphere.noise(px * 3.1 + extras.clock * 0.45, pz * 3.1) - 0.5) * 0.16;
		double nz = (Atmosphere.noise(px * 3.1 + 40.0, pz * 3.1 - extras.clock * 0.4) - 0.5) * 0.16;
		double[] normal = {nx, 1.0, nz};
		normalize(normal);
		double cosine = Math.max(0.0, -(d[0] * normal[0] + d[1] * normal[1] + d[2] * normal[2]));
		double[] refl = {d[0] + 2.0 * cosine * normal[0], d[1] + 2.0 * cosine * normal[1], d[2] + 2.0 * cosine * normal[2]};
		if (refl[1] < 0.03) {
			refl[1] = 0.03;
		}
		normalize(refl);
		float fresnel = (float) Math.min(0.92, (0.1 + 0.85 * Math.pow(1.0 - cosine, 3.0)) * extras.reflectivity);
		Ray mirror = new Ray();
		mirror.skipWater = true;
		trace(mirror, px, py + 0.025, pz, refl[0], refl[1], refl[2]);
		float[] sky = new float[3];
		if (mirror.transmit > 0.02F) {
			atmosphere.sky(refl[0], refl[1], refl[2], extras.clock, sky);
		}
		float mr = mirror.red + mirror.transmit * sky[0];
		float mg = mirror.green + mirror.transmit * sky[1];
		float mb = mirror.blue + mirror.transmit * sky[2];
		out[0] = out[0] * (1.0F - fresnel) + mr * fresnel;
		out[1] = out[1] * (1.0F - fresnel) + mg * fresnel;
		out[2] = out[2] * (1.0F - fresnel) + mb * fresnel;
	}

	private float shineAt(int[] cell) {
		return shineCells == null ? 0.0F : shineCells[cell[0] + width * (cell[1] + height * cell[2])];
	}

	private float occWeight(int x, int y, int z) {
		if (x < 0 || y < 0 || z < 0 || x >= width || y >= height || z >= depth) {
			return y < 0 ? 1.0F : 0.0F;
		}
		Occ occ = occluders[x + width * (y + height * z)];
		return occ.kind == Occ.OPAQUE ? 1.0F : occ.kind == Occ.PATTERN ? 0.55F : 0.0F;
	}

	private float ambientOcclusion(double px, double py, double pz, double[] n) {
		int axis = Math.abs(n[0]) > 0.5 ? 0 : Math.abs(n[1]) > 0.5 ? 1 : 2;
		int u = (axis + 1) % 3;
		int w = (axis + 2) % 3;
		double[] p = {px + n[0] * 0.03, py + n[1] * 0.03, pz + n[2] * 0.03};
		int[] c = {(int) Math.floor(p[0]), (int) Math.floor(p[1]), (int) Math.floor(p[2])};
		if (occWeight(c[0], c[1], c[2]) >= 1.0F) {
			return 1.0F;
		}
		float fu = (float) (p[u] - c[u]);
		float fw = (float) (p[w] - c[w]);
		float[][] corner = new float[2][2];
		for (int cu = 0; cu < 2; cu++) {
			for (int cw = 0; cw < 2; cw++) {
				int su = cu == 1 ? 1 : -1;
				int sw = cw == 1 ? 1 : -1;
				float side1 = occAxis(c, u, su, w, 0);
				float side2 = occAxis(c, u, 0, w, sw);
				float diagonal = occAxis(c, u, su, w, sw);
				float value;
				if (side1 >= 1.0F && side2 >= 1.0F) {
					value = 0.0F;
				} else {
					value = 1.0F - (side1 + side2 + diagonal) / 3.0F;
				}
				corner[cu][cw] = value;
			}
		}
		float low = corner[0][0] + (corner[1][0] - corner[0][0]) * fu;
		float high = corner[0][1] + (corner[1][1] - corner[0][1]) * fu;
		return low + (high - low) * fw;
	}

	private float occAxis(int[] c, int u, int du, int w, int dw) {
		int[] at = {c[0], c[1], c[2]};
		at[u] += du;
		at[w] += dw;
		return occWeight(at[0], at[1], at[2]);
	}

	private void shade(double px, double py, double pz, double[] n, float red, float green, float blue, float[] out, int cellIndex, float gloss, double distance, boolean glow) {
		if (glow) {
			out[0] = Math.min(1.6F, red * 1.25F);
			out[1] = Math.min(1.6F, green * 1.25F);
			out[2] = Math.min(1.6F, blue * 1.25F);
			return;
		}
		float faceShade = n[1] > 0.5 ? 1.0F : n[1] < -0.5 ? 0.5F : Math.abs(n[2]) > 0.5 ? 0.8F : 0.6F;
		int fx = (int) Math.floor(px + n[0] * 0.04);
		int fy = (int) Math.floor(py + n[1] * 0.04);
		int fz = (int) Math.floor(pz + n[2] * 0.04);
		int front = indexOf(fx, fy, fz);
		float[] ambient = ambientAt(front);
		float skyLevel = extras.sky == null || front < 0 ? 1.0F : extras.sky[front];
		float occlusion = ambientOcclusion(px, py, pz, n);
		float aoFactor = 0.46F + 0.54F * occlusion;
		double[] sun = atmosphere.sunDir;
		float shadeR = 1.0F;
		float shadeG = 1.0F;
		float shadeB = 1.0F;
		float sunVisibility = 1.0F;
		if (extras.sunShadows && atmosphere.sunPower > 0.04F && skyLevel > 0.15F) {
			double facing = n[0] * sun[0] + n[1] * sun[1] + n[2] * sun[2];
			float amount;
			if (facing > 0.04) {
				float[] seen = visibility(px, py, pz, n, px + sun[0] * 36.0, py + sun[1] * 36.0, pz + sun[2] * 36.0);
				sunVisibility = (seen[0] + seen[1] + seen[2]) / 3.0F;
				amount = extras.sunStrength * atmosphere.sunPower * (1.0F - sunVisibility) * (0.45F + 0.55F * Atmosphere.smooth(0.04, 0.4, facing));
			} else {
				sunVisibility = 0.0F;
				amount = extras.sunStrength * atmosphere.sunPower * 0.3F;
			}
			amount *= Math.min(1.0F, skyLevel * 1.4F);
			shadeR = 1.0F - amount * 0.52F;
			shadeG = 1.0F - amount * 0.5F;
			shadeB = 1.0F - amount * 0.34F;
		}
		float[] base = {ambient[0] * faceShade * aoFactor * shadeR, ambient[1] * faceShade * aoFactor * shadeG, ambient[2] * faceShade * aoFactor * shadeB};
		float[] vertex = base.clone();
		if (extras.blockLight != null && front >= 0) {
			float level = Math.min(1.0F, extras.blockLight[front] / 15.0F);
			float value = level / (4.0F - 3.0F * level) * 0.9F * (0.6F + 0.4F * occlusion);
			vertex[0] = Math.max(vertex[0], base[0] + value);
			vertex[1] = Math.max(vertex[1], base[1] + value * 0.88F);
			vertex[2] = Math.max(vertex[2], base[2] + value * 0.66F);
		} else if (params.vanilla()) {
			for (Light light : lights) {
				if (!light.placed()) {
					continue;
				}
				double dx = light.x() - px;
				double dy = light.y() - py;
				double dz = light.z() - pz;
				float level = (float) Math.max(light.radius() - Math.sqrt(dx * dx + dy * dy + dz * dz), 0.0) / 15.0F;
				float value = level / (4.0F - 3.0F * level) * 0.9F * aoFactor;
				vertex[0] = Math.max(vertex[0], base[0] + value);
				vertex[1] = Math.max(vertex[1], base[1] + value * 0.88F);
				vertex[2] = Math.max(vertex[2], base[2] + value * 0.66F);
			}
		}
		float[] extra = lighting(px, py, pz, n, vertex, base, gloss * params.shine(), front);
		float outRed = red * (vertex[0] + extra[0]) + extra[3];
		float outGreen = green * (vertex[1] + extra[1]) + extra[4];
		float outBlue = blue * (vertex[2] + extra[2]) + extra[5];
		if (extras.caustics && atmosphere.sunPower > 0.2F && front >= 0) {
			float pattern = caustic(px, py, pz, fx, fy, fz);
			if (pattern > 0.0F) {
				float amount = pattern * atmosphere.sunPower * sunVisibility * (float) Math.max(0.0, n[1]) * 0.95F;
				outRed += red * amount * 1.05F + amount * 0.12F;
				outGreen += green * amount * 1.1F + amount * 0.14F;
				outBlue += blue * amount * 1.0F + amount * 0.12F;
			}
		}
		float emit = extras.emit == null || cellIndex < 0 ? 0.0F : extras.emit[cellIndex];
		if (emit > 0.0F) {
			outRed = Math.max(outRed, red * emit * 1.1F);
			outGreen = Math.max(outGreen, green * emit * 1.1F);
			outBlue = Math.max(outBlue, blue * emit * 1.1F);
		}
		out[0] = outRed;
		out[1] = outGreen;
		out[2] = outBlue;
	}

	private float caustic(double px, double py, double pz, int fx, int fy, int fz) {
		if (extras.water == null) {
			return 0.0F;
		}
		int found = -1;
		for (int k = 0; k < 5; k++) {
			int at = indexOf(fx, fy + k, fz);
			if (at >= 0 && extras.water[at] != Extras.DRY) {
				found = k;
				break;
			}
			if (at >= 0 && occluders[at].kind == Occ.OPAQUE) {
				return 0.0F;
			}
		}
		if (found < 0) {
			return 0.0F;
		}
		double time = extras.clock;
		double a = Math.sin(px * 5.6 + Math.sin(pz * 3.3 + time * 1.1) * 1.8 + time * 0.9);
		double b = Math.sin(pz * 5.2 + Math.sin(px * 3.9 - time * 0.8) * 1.6 - time * 0.7);
		double c = Math.sin((px + pz) * 4.1 + Math.sin((px - pz) * 3.2 + time * 1.3) * 1.2);
		double sum = (a * b + c * 0.7) * 0.5 + 0.5;
		float line = (float) Math.pow(Math.max(0.0, sum), 3.2);
		float depthFade = (float) Math.exp(-found * 0.38);
		return Math.min(1.2F, line * 1.6F) * depthFade;
	}

	private float[] lighting(double px, double py, double pz, double[] n, float[] vertex, float[] base, float gloss, int frontCell) {
		float exposure = Math.max(base[0], Math.max(base[1], base[2]));
		float fade = 1.0F - params.daylightFade() * smoothstep(0.25F, 0.95F, exposure);
		float[] total = new float[3];
		float[] shiny = new float[3];
		float[] result = new float[6];
		if (fade <= 0.01F) {
			return result;
		}
		float placedCover = 0.0F;
		float bounce = params.bounce();
		double vx = eye[0] - px;
		double vy = eye[1] - py;
		double vz = eye[2] - pz;
		double viewLength = Math.max(Math.sqrt(vx * vx + vy * vy + vz * vz), 1.0E-6);
		vx /= viewLength;
		vy /= viewLength;
		vz /= viewLength;
		double ex = px - eye[0];
		double ey = py - eye[1];
		double ez = pz - eye[2];
		boolean near = ex * ex + ey * ey + ez * ez < (double) params.shadowDistance() * params.shadowDistance();
		for (Light light : lights) {
			double tx = light.x() - px;
			double ty = light.y() - py;
			double tz = light.z() - pz;
			double dist = Math.sqrt(tx * tx + ty * ty + tz * tz);
			float level = Math.min(1.0F, (float) Math.max(light.radius() - dist, 0.0) / 15.0F);
			float bounceLevel = bounce > 0.001F ? Math.min(1.0F, Math.max(0.0F, (float) (light.radius() * 1.2 - dist) / 18.0F)) : 0.0F;
			if (level <= 0.0F && bounceLevel <= 0.0F) {
				continue;
			}
			float ndotl = (float) ((n[0] * tx + n[1] * ty + n[2] * tz) / Math.max(dist, 1.0E-4));
			float facing = mix(1.0F, smoothstep(-0.35F, 0.15F, ndotl), params.directional());
			float strength = light.placed() ? params.placedStrength() : 1.0F;
			float strongest = Math.max(light.red(), Math.max(light.green(), light.blue()));
			if (level > 0.0F && facing > 0.0F) {
				float lambert = mix(1.0F, 0.4F + 0.6F * Math.min(1.0F, Math.max(0.0F, ndotl)), params.directional());
				float curved = level / (4.0F - 3.0F * level);
				float power = curved * lambert * facing * strength;
				if (light.placed()) {
					placedCover += curved * facing;
				}
				float[] visible = {1.0F, 1.0F, 1.0F};
				if (params.shadows() && near && power * params.brightness() * fade * strongest > 0.01F) {
					float[] through = shadow(px, py, pz, n, light);
					for (int c = 0; c < 3; c++) {
						visible[c] = mix(1.0F, through[c], params.shadowStrength());
					}
				}
				float scale = params.brightness() * power;
				total[0] += light.red() * scale * visible[0];
				total[1] += light.green() * scale * visible[1];
				total[2] += light.blue() * scale * visible[2];
				if (gloss > 0.01F && ndotl > 0.0F) {
					double lx = tx / Math.max(dist, 1.0E-6) + vx;
					double ly = ty / Math.max(dist, 1.0E-6) + vy;
					double lz = tz / Math.max(dist, 1.0E-6) + vz;
					double hl = Math.max(Math.sqrt(lx * lx + ly * ly + lz * lz), 1.0E-6);
					double nh = Math.max(0.0, (n[0] * lx + n[1] * ly + n[2] * lz) / hl);
					float spec = (float) (Math.pow(nh, 28.0) * 0.8) * gloss * curved * strength * params.brightness();
					shiny[0] += light.red() * spec * visible[0];
					shiny[1] += light.green() * spec * visible[1];
					shiny[2] += light.blue() * spec * visible[2];
				}
			}
			if (bounceLevel > 0.0F && ndotl < 0.6F) {
				float curvedBounce = bounceLevel / (4.0F - 3.0F * bounceLevel);
				float seen = 1.0F;
				if (params.shadows() && near) {
					double[] lifted = {px + n[0] * 0.6, py + n[1] * 0.6, pz + n[2] * 0.6};
					float[] through = visibility(lifted[0], lifted[1], lifted[2], n, light.x(), light.y(), light.z());
					seen = mix(1.0F, (through[0] + through[1] + through[2]) / 3.0F, params.shadowStrength());
				}
				float amount = params.brightness() * bounce * 0.2F * curvedBounce * (1.0F - Math.min(1.0F, Math.max(0.0F, ndotl))) * strength * seen;
				total[0] += light.red() * amount;
				total[1] += light.green() * amount;
				total[2] += light.blue() * amount;
			}
		}
		if (extras.spread != null && extras.spill > 0.0F && frontCell >= 0) {
			float coverage = Math.min(1.0F, (total[0] + total[1] + total[2]) * 0.5F);
			float open = (1.0F - coverage) * extras.spill * params.brightness();
			int at = frontCell * 3;
			total[0] += extras.spread[at] * open;
			total[1] += extras.spread[at + 1] * open;
			total[2] += extras.spread[at + 2] * open;
		}
		float blockRed = Math.max(vertex[0] - base[0], 0.0F);
		float blockGreen = Math.max(vertex[1] - base[1], 0.0F);
		float blockBlue = Math.max(vertex[2] - base[2], 0.0F);
		float luma = 0.299F * blockRed + 0.587F * blockGreen + 0.114F * blockBlue;
		float coverage = Math.min(1.0F, Math.max(0.0F, placedCover * fade / Math.max(luma, 0.02F)));
		float cap = params.maxBrightness();
		float[] block = {blockRed, blockGreen, blockBlue};
		for (int c = 0; c < 3; c++) {
			result[c] = soft(total[c], cap) * fade - block[c] * (params.vanillaReplace() * coverage);
			result[3 + c] = Math.min(1.0F, shiny[c]) * fade;
		}
		return result;
	}

	private float[] shadow(double px, double py, double pz, double[] n, Light light) {
		float[] centre = visibility(px, py, pz, n, light.x(), light.y(), light.z());
		float radius = params.softRadius();
		if (radius <= 0.0F || Math.max(centre[0], Math.max(centre[1], centre[2])) > 0.98F) {
			return centre;
		}
		double[] dir = {light.x() - px, light.y() - py, light.z() - pz};
		normalize(dir);
		double[] helper = Math.abs(dir[1]) > 0.9 ? new double[]{1.0, 0.0, 0.0} : new double[]{0.0, 1.0, 0.0};
		double[] side = new double[3];
		cross(dir, helper, side);
		normalize(side);
		double[] upAxis = new double[3];
		cross(dir, side, upAxis);
		int homeX = (int) Math.floor(light.x());
		int homeY = (int) Math.floor(light.y());
		int homeZ = (int) Math.floor(light.z());
		float[] total = centre.clone();
		for (int k = 0; k < 3; k++) {
			double angle = 2.0943951 * k + 0.5;
			double sx = light.x() + (side[0] * Math.cos(angle) + upAxis[0] * Math.sin(angle)) * radius;
			double sy = light.y() + (side[1] * Math.cos(angle) + upAxis[1] * Math.sin(angle)) * radius;
			double sz = light.z() + (side[2] * Math.cos(angle) + upAxis[2] * Math.sin(angle)) * radius;
			if ((int) Math.floor(sx) != homeX || (int) Math.floor(sy) != homeY || (int) Math.floor(sz) != homeZ) {
				sx = light.x();
				sy = light.y();
				sz = light.z();
			}
			float[] extra = visibility(px, py, pz, n, sx, sy, sz);
			for (int c = 0; c < 3; c++) {
				total[c] += extra[c];
			}
		}
		for (int c = 0; c < 3; c++) {
			total[c] *= 0.25F;
		}
		return total;
	}

	private Occ occAt(int x, int y, int z) {
		if (x < 0 || y < 0 || z < 0 || x >= width || y >= height || z >= depth) {
			return Occ.EMPTY;
		}
		return occluders[x + width * (y + height * z)];
	}

	private float[] visibility(double px, double py, double pz, double[] n, double lx, double ly, double lz) {
		double dx = lx - px;
		double dy = ly - py;
		double dz = lz - pz;
		double len = Math.sqrt(dx * dx + dy * dy + dz * dz);
		float[] trans = {1.0F, 1.0F, 1.0F};
		if (len < 0.15) {
			return trans;
		}
		double[] dir = {dx / len, dy / len, dz / len};
		double[] origin = {
			px + n[0] * 0.02 + dir[0] * 0.01,
			py + n[1] * 0.02 + dir[1] * 0.01,
			pz + n[2] * 0.02 + dir[2] * 0.01
		};
		int[] cell = {(int) Math.floor(origin[0]), (int) Math.floor(origin[1]), (int) Math.floor(origin[2])};
		int[] lightCell = {(int) Math.floor(lx), (int) Math.floor(ly), (int) Math.floor(lz)};
		double[] sgn = new double[3];
		double[] safe = new double[3];
		double[] tDelta = new double[3];
		double[] tMax = new double[3];
		for (int a = 0; a < 3; a++) {
			sgn[a] = dir[a] >= 0.0 ? 1.0 : -1.0;
			safe[a] = Math.max(Math.abs(dir[a]), 1.0E-5);
			tDelta[a] = 1.0 / safe[a];
			tMax[a] = (cell[a] + Math.max(sgn[a], 0.0) - origin[a]) * sgn[a] / safe[a];
		}
		double tEntry = 0.0;
		int entryAxis = 0;
		for (int i = 0; i < 96; i++) {
			double tExit = Math.min(tMax[0], Math.min(tMax[1], tMax[2]));
			int exitAxis = tMax[0] <= tMax[1] && tMax[0] <= tMax[2] ? 0 : (tMax[1] <= tMax[2] ? 1 : 2);
			if (cell[0] != lightCell[0] || cell[1] != lightCell[1] || cell[2] != lightCell[2]) {
				Occ occ = occAt(cell[0], cell[1], cell[2]);
				if (occ.kind == Occ.OPAQUE) {
					return new float[3];
				}
				if (occ.kind == Occ.SHAPE) {
					int gap = Math.max(Math.abs(cell[0] - lightCell[0]), Math.max(Math.abs(cell[1] - lightCell[1]), Math.abs(cell[2] - lightCell[2])));
					if (gap > 1) {
						shape(occ.data, origin, dir, cell, tEntry, Math.min(tExit, len), sgn, safe, trans);
					}
				} else if (occ.kind == Occ.PATTERN && i > 0) {
					boolean enterPositive = sgn[entryAxis] < 0.0;
					boolean exitPositive = sgn[exitAxis] > 0.0;
					float[] through = pattern(occ.data, origin, dir, tEntry, cell, entryAxis, enterPositive);
					if (tExit < len) {
						float[] other = pattern(occ.data, origin, dir, tExit, cell, exitAxis, exitPositive);
						for (int c = 0; c < 3; c++) {
							through[c] *= other[c];
						}
					}
					for (int c = 0; c < 3; c++) {
						trans[c] *= through[c];
					}
				}
				if (Math.max(trans[0], Math.max(trans[1], trans[2])) < 0.02F) {
					return new float[3];
				}
			}
			if (tExit >= len) {
				break;
			}
			tEntry = tExit;
			entryAxis = exitAxis;
			cell[exitAxis] += (int) sgn[exitAxis];
			tMax[exitAxis] += tDelta[exitAxis];
		}
		return trans;
	}

	private static float[] pattern(float[] tile, double[] origin, double[] dir, double t, int[] cell, int axis, boolean positiveFace) {
		double lx = clamp(origin[0] + dir[0] * t - cell[0], 0.0, 0.999);
		double ly = clamp(origin[1] + dir[1] * t - cell[1], 0.0, 0.999);
		double lz = clamp(origin[2] + dir[2] * t - cell[2], 0.0, 0.999);
		double u;
		double v;
		if (axis == 0) {
			u = positiveFace ? 1.0 - lz : lz;
			v = 1.0 - ly;
		} else if (axis == 1) {
			u = lx;
			v = positiveFace ? lz : 1.0 - lz;
		} else {
			u = positiveFace ? lx : 1.0 - lx;
			v = 1.0 - ly;
		}
		int tx = Math.min(15, (int) (u * 16.0));
		int ty = Math.min(15, (int) (v * 16.0));
		int at = (ty * 16 + tx) * 3;
		return new float[]{tile[at], tile[at + 1], tile[at + 2]};
	}

	private static void shape(float[] mask, double[] origin, double[] dir, int[] cell, double t0, double t1, double[] sgn, double[] safe, float[] trans) {
		double[] local = new double[3];
		int[] v = new int[3];
		double[] tMax = new double[3];
		double[] tDelta = new double[3];
		for (int a = 0; a < 3; a++) {
			local[a] = clamp((origin[a] + dir[a] * t0 - cell[a]) * SHAPE_RES + dir[a] * 0.01, 0.0, SHAPE_RES - 0.001);
			v[a] = (int) Math.floor(local[a]);
			tDelta[a] = 1.0 / (safe[a] * SHAPE_RES);
			tMax[a] = t0 + (v[a] + Math.max(sgn[a], 0.0) - local[a]) * sgn[a] / (safe[a] * SHAPE_RES);
		}
		double t = t0;
		for (int k = 0; k < SHAPE_RES * 3; k++) {
			double tNext = Math.min(tMax[0], Math.min(tMax[1], tMax[2]));
			double tEnd = Math.min(tNext, t1);
			if (tEnd > 0.02) {
				int at = (v[0] + SHAPE_RES * (v[1] + SHAPE_RES * v[2])) * 3;
				float r = mask[at];
				float g = mask[at + 1];
				float b = mask[at + 2];
				if (r < 0.999F || g < 0.999F || b < 0.999F) {
					if (Math.max(r, Math.max(g, b)) < 0.03F) {
						trans[0] = 0.0F;
						trans[1] = 0.0F;
						trans[2] = 0.0F;
						return;
					}
					double span = Math.max(tEnd - Math.max(t, 0.02), 0.0) * SHAPE_RES;
					trans[0] *= (float) Math.pow(Math.max(r, 0.03F), span);
					trans[1] *= (float) Math.pow(Math.max(g, 0.03F), span);
					trans[2] *= (float) Math.pow(Math.max(b, 0.03F), span);
				}
			}
			if (tNext >= t1) {
				return;
			}
			t = tNext;
			int axis = tMax[0] <= tMax[1] && tMax[0] <= tMax[2] ? 0 : (tMax[1] <= tMax[2] ? 1 : 2);
			v[axis] += (int) sgn[axis];
			tMax[axis] += tDelta[axis];
			if (v[axis] < 0 || v[axis] >= SHAPE_RES) {
				return;
			}
		}
	}

	private static float soft(float x, float cap) {
		float knee = cap * 0.7F;
		float over = Math.max(x - knee, 0.0F);
		return Math.min(x, knee) + (cap - knee) * (1.0F - (float) Math.exp(-over / (cap - knee)));
	}

	private static float smoothstep(float edge0, float edge1, float x) {
		float t = Math.min(1.0F, Math.max(0.0F, (x - edge0) / (edge1 - edge0)));
		return t * t * (3.0F - 2.0F * t);
	}

	private static float mix(float a, float b, float amount) {
		return a + (b - a) * amount;
	}

	private static double clamp(double value, double min, double max) {
		return Math.max(min, Math.min(max, value));
	}

	private static void normalize(double[] vector) {
		double length = Math.sqrt(vector[0] * vector[0] + vector[1] * vector[1] + vector[2] * vector[2]);
		if (length < 1.0E-12) {
			return;
		}
		vector[0] /= length;
		vector[1] /= length;
		vector[2] /= length;
	}

	private static void cross(double[] a, double[] b, double[] out) {
		out[0] = a[1] * b[2] - a[2] * b[1];
		out[1] = a[2] * b[0] - a[0] * b[2];
		out[2] = a[0] * b[1] - a[1] * b[0];
	}
}
