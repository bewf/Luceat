package me.bewf.luceat.client.gui.glass;

import java.util.function.IntConsumer;
import java.util.stream.IntStream;

public final class Optics {
	public record Look(float blur, float body, float refract, float depth, float dispersion, int tint, float rim, float shade, float glow, float lightX, float lightY) {
	}

	public static final Look CLEAR = new Look(6.0F, 1.0F, 4.5F, 11.0F, 0.3F, 0x2A0F0A22, 0.95F, 0.55F, 0.2F, -0.62F, -0.78F);
	public static final Look DENSE = new Look(6.0F, 1.0F, 4.0F, 11.0F, 0.25F, 0x660F0A22, 0.95F, 0.55F, 0.2F, -0.62F, -0.78F);

	private static final int LUT = 256;
	private static final float[] LIT_RIM = new float[LUT];
	private static final float[] AWAY_RIM = new float[LUT];
	private static final float[] LIT_GLOW = new float[LUT];
	private static final float[] LIT_REFLECT = new float[LUT];
	private static final float[] AWAY_SHADE = new float[LUT];
	private static int[] scratch = new int[0];

	static {
		for (int i = 0; i < LUT; i++) {
			float v = i / (LUT - 1.0F);
			LIT_RIM[i] = (float) Math.pow(v, 1.4);
			AWAY_RIM[i] = 0.26F * (float) Math.pow(v, 1.6);
			LIT_GLOW[i] = (float) Math.pow(v, 1.2);
			LIT_REFLECT[i] = (float) Math.pow(v, 2.0);
			AWAY_SHADE[i] = (float) Math.pow(v, 1.2);
		}
	}

	private Optics() {
	}

	private record Entry(int[] params, int hash, int[] out) {
	}

	private static final Entry[] CACHE = new Entry[4];
	private static int cacheNext;

	public static void lens(int[] px, int pw, int ph, int x0, int y0, int x1, int y1, int rTop, int rBottom, int k, Look look, float strength, int clipX0, int clipY0, int clipX1, int clipY1) {
		int ax0 = Math.max(Math.max(0, x0), clipX0);
		int ay0 = Math.max(Math.max(0, y0), clipY0);
		int ax1 = Math.min(Math.min(pw, x1), clipX1);
		int ay1 = Math.min(Math.min(ph, y1), clipY1);
		if (strength <= 0.003F || x1 - x0 < 8 || y1 - y0 < 8 || ax1 <= ax0 || ay1 <= ay0) {
			return;
		}
		int[] params = {pw, ph, x0, y0, x1, y1, rTop, rBottom, k, System.identityHashCode(look), Float.floatToIntBits(strength), ax0, ay0, ax1, ay1};
		int hash = 1;
		for (int y = ay0; y < ay1; y++) {
			int row = y * pw;
			for (int x = ax0; x < ax1; x++) {
				hash = hash * 31 + px[row + x];
			}
		}
		int width = ax1 - ax0;
		for (Entry entry : CACHE) {
			if (entry != null && entry.hash() == hash && java.util.Arrays.equals(entry.params(), params)) {
				for (int y = ay0; y < ay1; y++) {
					System.arraycopy(entry.out(), (y - ay0) * width, px, y * pw + ax0, width);
				}
				return;
			}
		}
		render(px, pw, ph, x0, y0, x1, y1, rTop, rBottom, k, look, strength, clipX0, clipY0, clipX1, clipY1);
		int[] out = new int[width * (ay1 - ay0)];
		for (int y = ay0; y < ay1; y++) {
			System.arraycopy(px, y * pw + ax0, out, (y - ay0) * width, width);
		}
		int slot = -1;
		for (int i = 0; i < CACHE.length; i++) {
			if (CACHE[i] != null && java.util.Arrays.equals(CACHE[i].params(), params)) {
				slot = i;
			}
		}
		if (slot < 0) {
			slot = cacheNext;
			cacheNext = (cacheNext + 1) % CACHE.length;
		}
		CACHE[slot] = new Entry(params, hash, out);
	}

	private static void render(int[] px, int pw, int ph, int x0, int y0, int x1, int y1, int rTop, int rBottom, int k, Look look, float strength, int clipX0, int clipY0, int clipX1, int clipY1) {
		int ax0 = Math.max(Math.max(0, x0), clipX0);
		int ay0 = Math.max(Math.max(0, y0), clipY0);
		int ax1 = Math.min(Math.min(pw, x1), clipX1);
		int ay1 = Math.min(Math.min(ph, y1), clipY1);
		int w = x1 - x0;
		int h = y1 - y0;
		if (strength <= 0.003F || w < 8 || h < 8 || ax1 <= ax0 || ay1 <= ay0) {
			return;
		}
		int sx0 = Math.max(0, x0);
		int sy0 = Math.max(0, y0);
		int sx1 = Math.min(pw, x1);
		int sy1 = Math.min(ph, y1);
		int sw = sx1 - sx0;
		int sh = sy1 - sy0;
		if (scratch.length < sw * sh) {
			scratch = new int[sw * sh];
		}
		for (int y = 0; y < sh; y++) {
			System.arraycopy(px, (sy0 + y) * pw + sx0, scratch, y * sw, sw);
		}
		int f = Math.max(1, k);
		int cw = (w + f - 1) / f;
		int ch = (h + f - 1) / f;
		float[][] sharp = new float[4][cw * ch];
		for (int cy = 0; cy < ch; cy++) {
			for (int cx = 0; cx < cw; cx++) {
				float sa = 0.0F;
				float sr = 0.0F;
				float sg = 0.0F;
				float sb = 0.0F;
				int count = 0;
				for (int dy = 0; dy < f; dy++) {
					int y = y0 + cy * f + dy;
					if (y >= y1) {
						break;
					}
					for (int dx = 0; dx < f; dx++) {
						int x = x0 + cx * f + dx;
						if (x >= x1) {
							break;
						}
						int p = x < sx0 || y < sy0 || x >= sx1 || y >= sy1 ? 0 : scratch[(y - sy0) * sw + (x - sx0)];
						float a = (p >>> 24) / 255.0F;
						sa += a;
						sr += ((p >> 16) & 255) * a;
						sg += ((p >> 8) & 255) * a;
						sb += (p & 255) * a;
						count++;
					}
				}
				float inv = count == 0 ? 0.0F : 1.0F / count;
				int i = cy * cw + cx;
				sharp[0][i] = sa * inv;
				sharp[1][i] = sr * inv;
				sharp[2][i] = sg * inv;
				sharp[3][i] = sb * inv;
			}
		}
		int softR = Math.max(1, Math.round(look.blur()));
		float[][] soft = new float[4][];
		for (int c = 0; c < 4; c++) {
			soft[c] = blur(sharp[c], cw, ch, softR);
		}
		float depthPx = look.depth() * k;
		float refractPx = look.refract() * k;
		float rimBand = Math.max(1.0F, 0.9F * k);
		float shadeBand = 3.5F * k;
		float glowBand = 5.5F * k;
		float reflectAt = depthPx * 0.72F;
		float reflectBand = 1.4F * k;
		int bandMax = (int) Math.ceil(Math.max(depthPx, 2.3F * glowBand)) + 1;
		float[] rimProfile = new float[bandMax + 1];
		float[] glowProfile = new float[bandMax + 1];
		float[] shadeProfile = new float[bandMax + 1];
		float[] reflectProfile = new float[bandMax + 1];
		for (int i = 0; i <= bandMax; i++) {
			float d = i + 0.5F;
			rimProfile[i] = (float) Math.exp(-(d * d) / (rimBand * rimBand));
			glowProfile[i] = (float) Math.exp(-(d * d) / (glowBand * glowBand));
			shadeProfile[i] = (float) Math.exp(-(d * d) / (shadeBand * shadeBand));
			reflectProfile[i] = (float) Math.exp(-((d - reflectAt) * (d - reflectAt)) / (reflectBand * reflectBand));
		}
		float lightLen = (float) Math.sqrt(look.lightX() * look.lightX() + look.lightY() * look.lightY());
		float lx = look.lightX() / lightLen;
		float ly = look.lightY() / lightLen;
		float cxm = w * 0.5F;
		float cym = h * 0.5F;
		float tintA = (look.tint() >>> 24) / 255.0F;
		float tintR = (look.tint() >> 16) & 255;
		float tintG = (look.tint() >> 8) & 255;
		float tintB = look.tint() & 255;
		float body = look.body();
		float fade = Math.min(1.0F, strength);
		int[] src = scratch;
		IntConsumer rowWork = y -> {
			float py = y - y0 + 0.5F;
			float r = py < cym ? rTop : rBottom;
			float qy = Math.abs(py - cym) - (cym - r);
			float sy = py < cym ? -1.0F : 1.0F;
			boolean flat = r == 0.0F;
			float attach = 1.0F;
			if (rTop == 0) {
				attach = Math.min(attach, smooth(py / (3.5F * k)));
			}
			if (rBottom == 0) {
				attach = Math.min(attach, smooth((h - py) / (3.5F * k)));
			}
			for (int x = ax0; x < ax1; x++) {
				float pxx = x - x0 + 0.5F;
				float qx = Math.abs(pxx - cxm) - (cxm - r);
				float mx = Math.max(qx, 0.0F);
				float my = Math.max(qy, 0.0F);
				float outside = mx > 0.0F && my > 0.0F ? (float) Math.sqrt(mx * mx + my * my) : mx + my;
				float sdf = flat ? qx : outside + Math.min(Math.max(qx, qy), 0.0F) - r;
				float cover = Math.max(0.0F, Math.min(1.0F, 0.5F - sdf));
				if (cover <= 0.0F) {
					continue;
				}
				float din = Math.max(0.0F, -sdf);
				int index = y * pw + x;
				int original = src[(y - sy0) * sw + (x - sx0)];
				float a0 = (original >>> 24) / 255.0F;
				float r0 = (original >> 16) & 255;
				float g0 = (original >> 8) & 255;
				float b0 = original & 255;

				float a;
				float red;
				float green;
				float blue;
				float t = Math.max(0.0F, 1.0F - din / depthPx);
				float edge = t * t * attach;
				float nx = 0.0F;
				float ny = 0.0F;
				if (din < bandMax) {
					float sx = pxx < cxm ? -1.0F : 1.0F;
					if (qx > 0.0F && qy > 0.0F) {
						float len = (float) Math.sqrt(qx * qx + qy * qy);
						nx = sx * qx / len;
						ny = sy * qy / len;
					} else if (flat || qx > qy) {
						nx = sx;
					} else {
						ny = sy;
					}
				}
				if (edge < 0.002F) {
					if (body > 0.0F) {
						float u = pxx / f - 0.5F;
						float v = py / f - 0.5F;
						int cx0 = Math.max(0, Math.min(cw - 1, (int) Math.floor(u)));
						int cy0 = Math.max(0, Math.min(ch - 1, (int) Math.floor(v)));
						int cx1 = Math.min(cw - 1, cx0 + 1);
						int cy1 = Math.min(ch - 1, cy0 + 1);
						float fx = Math.max(0.0F, Math.min(1.0F, u - cx0));
						float fy = Math.max(0.0F, Math.min(1.0F, v - cy0));
						int i00 = cy0 * cw + cx0;
						int i01 = cy0 * cw + cx1;
						int i10 = cy1 * cw + cx0;
						int i11 = cy1 * cw + cx1;
						float na = bilinear(soft[0], i00, i01, i10, i11, fx, fy);
						float nr = bilinear(soft[1], i00, i01, i10, i11, fx, fy);
						float ng = bilinear(soft[2], i00, i01, i10, i11, fx, fy);
						float nb = bilinear(soft[3], i00, i01, i10, i11, fx, fy);
						float safe = Math.max(na, 0.003F);
						a = a0 + (na - a0) * body;
						red = r0 + ((na <= 0.003F ? r0 : Math.min(255.0F, nr / safe)) - r0) * body;
						green = g0 + ((na <= 0.003F ? g0 : Math.min(255.0F, ng / safe)) - g0) * body;
						blue = b0 + ((na <= 0.003F ? b0 : Math.min(255.0F, nb / safe)) - b0) * body;
					} else {
						a = a0;
						red = r0;
						green = g0;
						blue = b0;
					}
				} else {
					float disp = refractPx * edge;
					float blurW = Math.max(body, Math.min(1.0F, edge * 3.0F));
					float rd = disp * (1.0F + look.dispersion());
					float bd = disp * (1.0F - look.dispersion());
					float gxp = pxx - nx * disp;
					float gyp = py - ny * disp;
					float ox = x0 - sx0;
					float oy = y0 - sy0;
					int gi = nearest(src, sw, sh, gxp + ox, gyp + oy);
					int ri = nearest(src, sw, sh, pxx - nx * rd + ox, py - ny * rd + oy);
					int bi = nearest(src, sw, sh, pxx - nx * bd + ox, py - ny * bd + oy);
					float sa = (gi >>> 24) / 255.0F;
					float sr = (ri >> 16) & 255;
					float sg = (gi >> 8) & 255;
					float sb = bi & 255;
					float u = gxp / f - 0.5F;
					float v = gyp / f - 0.5F;
					int cx0 = Math.max(0, Math.min(cw - 1, (int) Math.floor(u)));
					int cy0 = Math.max(0, Math.min(ch - 1, (int) Math.floor(v)));
					int cx1 = Math.min(cw - 1, cx0 + 1);
					int cy1 = Math.min(ch - 1, cy0 + 1);
					float fx = Math.max(0.0F, Math.min(1.0F, u - cx0));
					float fy = Math.max(0.0F, Math.min(1.0F, v - cy0));
					int i00 = cy0 * cw + cx0;
					int i01 = cy0 * cw + cx1;
					int i10 = cy1 * cw + cx0;
					int i11 = cy1 * cw + cx1;
					float ba = bilinear(soft[0], i00, i01, i10, i11, fx, fy);
					float safe = Math.max(ba, 0.003F);
					float br = ba <= 0.003F ? sr : Math.min(255.0F, bilinear(soft[1], i00, i01, i10, i11, fx, fy) / safe);
					float bg = ba <= 0.003F ? sg : Math.min(255.0F, bilinear(soft[2], i00, i01, i10, i11, fx, fy) / safe);
					float bb = ba <= 0.003F ? sb : Math.min(255.0F, bilinear(soft[3], i00, i01, i10, i11, fx, fy) / safe);
					a = sa + (ba - sa) * blurW;
					red = sr + (br - sr) * blurW;
					green = sg + (bg - sg) * blurW;
					blue = sb + (bb - sb) * blurW;
				}

				float ta = tintA * (0.75F + 0.25F * (1.0F - t));
				float oa = ta + a * (1.0F - ta);
				if (oa > 0.001F) {
					float keep = a * (1.0F - ta);
					red = (tintR * ta + red * keep) / oa;
					green = (tintG * ta + green * keep) / oa;
					blue = (tintB * ta + blue * keep) / oa;
				}
				a = oa;

				if (din < bandMax) {
					int bi = Math.min(bandMax, (int) din);
					float facing = nx * lx + ny * ly;
					int li = Math.min(LUT - 1, Math.round(Math.abs(facing) * (LUT - 1)));
					float sheen;
					float glow = 0.0F;
					float reflect = 0.0F;
					float shade = 0.0F;
					if (facing > 0.0F) {
						sheen = look.rim() * LIT_RIM[li] * rimProfile[bi] * attach;
						glow = look.glow() * LIT_GLOW[li] * glowProfile[bi] * attach;
						reflect = look.glow() * 0.55F * LIT_REFLECT[li] * reflectProfile[bi] * attach;
					} else {
						sheen = look.rim() * AWAY_RIM[li] * rimProfile[bi] * attach;
						shade = Math.min(0.85F, look.shade() * AWAY_SHADE[li] * shadeProfile[bi] * attach);
					}
					if (shade > 0.002F) {
						float na = shade + a * (1.0F - shade);
						float keep = a * (1.0F - shade);
						red = (4.0F * shade + red * keep) / na;
						green = (2.0F * shade + green * keep) / na;
						blue = (14.0F * shade + blue * keep) / na;
						a = na;
					}
					float lightA = Math.min(0.95F, sheen + glow + reflect);
					if (lightA > 0.002F) {
						float na = lightA + a * (1.0F - lightA);
						float keep = a * (1.0F - lightA);
						red = (236.0F * lightA + red * keep) / na;
						green = (228.0F * lightA + green * keep) / na;
						blue = (255.0F * lightA + blue * keep) / na;
						a = na;
					}
				}

				float c = cover * fade;
				float outA = a0 + (a - a0) * c;
				if (outA <= 0.002F) {
					px[index] = 0;
					continue;
				}
				float wo = a0 * (1.0F - c);
				float wg = a * c;
				float total = wo + wg;
				int outR = Math.round((r0 * wo + red * wg) / total);
				int outG = Math.round((g0 * wo + green * wg) / total);
				int outB = Math.round((b0 * wo + blue * wg) / total);
				px[index] = Math.min(255, Math.round(outA * 255.0F)) << 24 | clamp(outR) << 16 | clamp(outG) << 8 | clamp(outB);
			}
		};
		if ((ay1 - ay0) * (ax1 - ax0) >= 40000) {
			IntStream.range(ay0, ay1).parallel().forEach(rowWork);
		} else {
			for (int y = ay0; y < ay1; y++) {
				rowWork.accept(y);
			}
		}
	}

	private static int nearest(int[] data, int w, int h, float x, float y) {
		int ix = Math.max(0, Math.min(w - 1, (int) x));
		int iy = Math.max(0, Math.min(h - 1, (int) y));
		return data[iy * w + ix];
	}

	private static int clamp(int v) {
		return Math.max(0, Math.min(255, v));
	}

	private static float smooth(float t) {
		float c = Math.max(0.0F, Math.min(1.0F, t));
		return c * c * (3.0F - 2.0F * c);
	}

	private static float bilinear(float[] data, int i00, int i01, int i10, int i11, float fx, float fy) {
		float top = data[i00] + (data[i01] - data[i00]) * fx;
		float bottom = data[i10] + (data[i11] - data[i10]) * fx;
		return top + (bottom - top) * fy;
	}

	private static float[] blur(float[] source, int w, int h, int radius) {
		float[] current = source;
		for (int pass = 0; pass < 2; pass++) {
			float[] across = new float[w * h];
			for (int y = 0; y < h; y++) {
				float sum = 0.0F;
				for (int x = -radius; x <= radius; x++) {
					sum += current[y * w + Math.max(0, Math.min(w - 1, x))];
				}
				for (int x = 0; x < w; x++) {
					across[y * w + x] = sum / (radius * 2 + 1);
					sum += current[y * w + Math.min(w - 1, x + radius + 1)] - current[y * w + Math.max(0, x - radius)];
				}
			}
			float[] down = new float[w * h];
			for (int x = 0; x < w; x++) {
				float sum = 0.0F;
				for (int y = -radius; y <= radius; y++) {
					sum += across[Math.max(0, Math.min(h - 1, y)) * w + x];
				}
				for (int y = 0; y < h; y++) {
					down[y * w + x] = sum / (radius * 2 + 1);
					sum += across[Math.min(h - 1, y + radius + 1) * w + x] - across[Math.max(0, y - radius) * w + x];
				}
			}
			current = down;
		}
		return current;
	}
}
