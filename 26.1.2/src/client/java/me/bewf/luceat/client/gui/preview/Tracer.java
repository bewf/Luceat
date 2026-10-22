package me.bewf.luceat.client.gui.preview;

import java.util.stream.IntStream;

import me.bewf.luceat.client.shadow.ShapeMasks;

public final class Tracer {
	public static final int VOX = 16;
	private static final int SHAPE_RES = ShapeMasks.RES;

	private final int width;
	private final int height;
	private final int depth;
	private final int[][] voxels;
	private final Occ[] occluders;
	private final Light[] lights;
	private final Params params;
	private final double[] eye;
	private final double[] forward = new double[3];
	private final double[] right = new double[3];
	private final double[] up = new double[3];
	private final double tanHalf;

	private static final class Ray {
		float red;
		float green;
		float blue;
		float transmit = 1.0F;
		double depth = Double.MAX_VALUE;
	}

	public Tracer(int width, int height, int depth, int[][] voxels, Occ[] occluders, Light[] lights, Params params, double[] eye, double[] target, double fovDegrees) {
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
	}

	public void render(int[] out, int pixelsWide, int pixelsHigh) {
		render(out, pixelsWide, pixelsHigh, 0, pixelsWide);
	}

	public void render(int[] out, int pixelsWide, int pixelsHigh, int fromColumn, int toColumn) {
		double aspect = (double) pixelsWide / pixelsHigh;
		IntStream.range(0, pixelsHigh).parallel().forEach(row -> {
			double[] dir = new double[3];
			for (int column = fromColumn; column < toColumn; column++) {
				double u = ((column + 0.5) / pixelsWide * 2.0 - 1.0) * tanHalf * aspect;
				double v = (1.0 - (row + 0.5) / pixelsHigh * 2.0) * tanHalf;
				for (int i = 0; i < 3; i++) {
					dir[i] = forward[i] + right[i] * u + up[i] * v;
				}
				normalize(dir);
				out[column + row * pixelsWide] = pixel(dir);
			}
		});
	}

	public double[] rayDirection(double fractionX, double fractionY, double aspect) {
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

	private int pixel(double[] dir) {
		Ray ray = new Ray();
		trace(ray, eye[0], eye[1], eye[2], dir[0], dir[1], dir[2]);
		float[] sky = sky();
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
			float glow = (float) (1.1 * Math.exp(-off / 0.012) + 0.22 * Math.exp(-off / 0.14));
			red += light.red() * glow;
			green += light.green() * glow;
			blue += light.blue() * glow;
		}
		return 0xFF000000 | channel(red) << 16 | channel(green) << 8 | channel(blue);
	}

	private static int channel(float value) {
		return Math.min(255, Math.max(0, Math.round(value * 255.0F)));
	}

	private float[] sky() {
		float t = params.time();
		return new float[]{0.02F + 0.5F * t, 0.03F + 0.68F * t, 0.08F + 0.9F * t};
	}

	private float[] ambient() {
		float t = params.time();
		return new float[]{0.06F + 0.89F * t, 0.07F + 0.89F * t, 0.12F + 0.88F * t};
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
		for (int i = 0; i < limit; i++) {
			double tExit = Math.min(tMax[0], Math.min(tMax[1], tMax[2]));
			int exitAxis = tMax[0] <= tMax[1] && tMax[0] <= tMax[2] ? 0 : (tMax[1] <= tMax[2] ? 1 : 2);
			int[] block = voxels[cell[0] + width * (cell[1] + height * cell[2])];
			if (block != null && cellRay(ray, block, o, d, cell, tEntry, Math.min(tExit, far), entryAxis, sgn, safe)) {
				return;
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

	private boolean cellRay(Ray ray, int[] block, double[] o, double[] d, int[] cell, double t0, double t1, int entryAxis, double[] sgn, double[] safe) {
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
		int axis = entryAxis;
		double t = t0;
		float[] out = new float[3];
		for (int k = 0; k < 3 * VOX + 2; k++) {
			double tNext = Math.min(tMax[0], Math.min(tMax[1], tMax[2]));
			int argb = block[v[0] + VOX * (v[1] + VOX * v[2])];
			int alpha = argb >>> 24;
			if (alpha > 0) {
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
				shade(px, py, pz, normal, albedoRed, albedoGreen, albedoBlue, out);
				float a = alpha / 255.0F;
				ray.red += ray.transmit * a * out[0];
				ray.green += ray.transmit * a * out[1];
				ray.blue += ray.transmit * a * out[2];
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

	private void shade(double px, double py, double pz, double[] n, float red, float green, float blue, float[] out) {
		float faceShade = n[1] > 0.5 ? 1.0F : n[1] < -0.5 ? 0.5F : Math.abs(n[2]) > 0.5 ? 0.8F : 0.6F;
		float[] ambient = ambient();
		float[] base = {ambient[0] * faceShade, ambient[1] * faceShade, ambient[2] * faceShade};
		float[] vertex = base.clone();
		if (params.vanilla()) {
			for (Light light : lights) {
				if (!light.placed()) {
					continue;
				}
				double dx = light.x() - px;
				double dy = light.y() - py;
				double dz = light.z() - pz;
				float level = (float) Math.max(light.radius() - Math.sqrt(dx * dx + dy * dy + dz * dz), 0.0) / 15.0F;
				float value = level / (4.0F - 3.0F * level) * 0.9F;
				vertex[0] = Math.max(vertex[0], base[0] + value);
				vertex[1] = Math.max(vertex[1], base[1] + value * 0.88F);
				vertex[2] = Math.max(vertex[2], base[2] + value * 0.66F);
			}
		}
		float[] extra = lighting(px, py, pz, n, vertex, base);
		out[0] = red * (vertex[0] + extra[0]);
		out[1] = green * (vertex[1] + extra[1]);
		out[2] = blue * (vertex[2] + extra[2]);
	}

	private float[] lighting(double px, double py, double pz, double[] n, float[] vertex, float[] base) {
		float exposure = Math.max(base[0], Math.max(base[1], base[2]));
		float fade = 1.0F - params.daylightFade() * smoothstep(0.25F, 0.95F, exposure);
		float[] total = new float[3];
		if (fade <= 0.01F) {
			return total;
		}
		float placedCover = 0.0F;
		for (Light light : lights) {
			double tx = light.x() - px;
			double ty = light.y() - py;
			double tz = light.z() - pz;
			double dist = Math.sqrt(tx * tx + ty * ty + tz * tz);
			float level = (float) Math.max(light.radius() - dist, 0.0) / 15.0F;
			if (level <= 0.0F) {
				continue;
			}
			float ndotl = (float) ((n[0] * tx + n[1] * ty + n[2] * tz) / Math.max(dist, 1.0E-4));
			float facing = mix(1.0F, smoothstep(-0.35F, 0.15F, ndotl), params.directional());
			if (facing <= 0.0F) {
				continue;
			}
			float lambert = mix(1.0F, 0.4F + 0.6F * Math.min(1.0F, Math.max(0.0F, ndotl)), params.directional());
			float curved = level / (4.0F - 3.0F * level);
			float power = curved * lambert * facing * (light.placed() ? params.placedStrength() : 1.0F);
			if (light.placed()) {
				placedCover += curved * facing;
			}
			float[] visible = {1.0F, 1.0F, 1.0F};
			float strongest = Math.max(light.red(), Math.max(light.green(), light.blue()));
			double ex = px - eye[0];
			double ey = py - eye[1];
			double ez = pz - eye[2];
			boolean near = ex * ex + ey * ey + ez * ez < (double) params.shadowDistance() * params.shadowDistance();
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
		}
		float blockRed = Math.max(vertex[0] - base[0], 0.0F);
		float blockGreen = Math.max(vertex[1] - base[1], 0.0F);
		float blockBlue = Math.max(vertex[2] - base[2], 0.0F);
		float luma = 0.299F * blockRed + 0.587F * blockGreen + 0.114F * blockBlue;
		float coverage = Math.min(1.0F, Math.max(0.0F, placedCover * fade / Math.max(luma, 0.02F)));
		float cap = params.maxBrightness();
		float[] result = new float[3];
		float[] block = {blockRed, blockGreen, blockBlue};
		for (int c = 0; c < 3; c++) {
			result[c] = soft(total[c], cap) * fade - block[c] * (params.vanillaReplace() * coverage);
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
