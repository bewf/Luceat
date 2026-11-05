package me.bewf.luceat.client.gui.glass;

public final class Frost {
	private static final int TILE = 256;
	private static final float PER_UNIT = 4.0F;
	private static final float[] FIELD = build();

	private Frost() {
	}

	public static float at(float ux, float uy) {
		float x = ux * PER_UNIT;
		float y = uy * PER_UNIT;
		int x0 = (int) Math.floor(x);
		int y0 = (int) Math.floor(y);
		float tx = x - x0;
		float ty = y - y0;
		int ax = Math.floorMod(x0, TILE);
		int ay = Math.floorMod(y0, TILE);
		int bx = (ax + 1) % TILE;
		int by = (ay + 1) % TILE;
		float top = FIELD[ay * TILE + ax] + (FIELD[ay * TILE + bx] - FIELD[ay * TILE + ax]) * tx;
		float bottom = FIELD[by * TILE + ax] + (FIELD[by * TILE + bx] - FIELD[by * TILE + ax]) * tx;
		return top + (bottom - top) * ty;
	}

	private static float[] build() {
		float[] out = new float[TILE * TILE];
		int[] cells = {4, 8, 16, 32, 64};
		float[] weight = {0.34F, 0.3F, 0.16F, 0.1F, 0.1F};
		for (int o = 0; o < cells.length; o++) {
			int n = cells[o];
			float[] lattice = new float[n * n];
			java.util.Random random = new java.util.Random(7919L * (o + 3));
			for (int i = 0; i < lattice.length; i++) {
				lattice[i] = random.nextFloat() * 2.0F - 1.0F;
			}
			float cell = TILE / (float) n;
			for (int y = 0; y < TILE; y++) {
				for (int x = 0; x < TILE; x++) {
					float fx = x / cell;
					float fy = y / cell;
					int ix = (int) fx;
					int iy = (int) fy;
					float tx = smooth(fx - ix);
					float ty = smooth(fy - iy);
					int jx = (ix + 1) % n;
					int jy = (iy + 1) % n;
					float a = lattice[iy * n + ix] + (lattice[iy * n + jx] - lattice[iy * n + ix]) * tx;
					float b = lattice[jy * n + ix] + (lattice[jy * n + jx] - lattice[jy * n + ix]) * tx;
					float v = a + (b - a) * ty;
					if (o == 99) {
						v = 1.0F - 2.0F * Math.abs(v);
					}
					out[y * TILE + x] += v * weight[o];
				}
			}
		}
		float max = 0.0001F;
		for (float v : out) {
			max = Math.max(max, Math.abs(v));
		}
		for (int i = 0; i < out.length; i++) {
			float v = out[i] / max;
			out[i] = Math.signum(v) * (float) Math.pow(Math.abs(v), 1.0);
		}
		return out;
	}

	private static float smooth(float t) {
		return t * t * (3.0F - 2.0F * t);
	}
}
