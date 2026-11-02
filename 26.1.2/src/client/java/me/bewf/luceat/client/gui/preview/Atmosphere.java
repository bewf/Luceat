package me.bewf.luceat.client.gui.preview;

final class Atmosphere {
	private static final float[] ZENITH_DAY = {0.22F, 0.44F, 0.92F};
	private static final float[] HORIZON_DAY = {0.64F, 0.80F, 0.99F};
	private static final float[] ZENITH_DUSK = {0.16F, 0.20F, 0.46F};
	private static final float[] HORIZON_DUSK = {1.00F, 0.56F, 0.30F};
	private static final float[] ZENITH_NIGHT = {0.006F, 0.012F, 0.045F};
	private static final float[] HORIZON_NIGHT = {0.035F, 0.05F, 0.11F};
	private static final float[] SUN_LOW = {1.0F, 0.58F, 0.28F};
	private static final float[] SUN_HIGH = {1.0F, 0.96F, 0.88F};

	final float time;
	final double elevation;
	final double[] sunDir = new double[3];
	final double[] moonDir = new double[3];
	final float day;
	final float night;
	final float dusk;
	final float sunPower;
	final float[] zenith;
	final float[] horizon;
	final float[] sunColour;
	final float[] ambient = new float[3];
	final double sunYaw;

	Atmosphere(float time, double sunYaw) {
		this.time = time;
		this.sunYaw = sunYaw;
		double degrees = -30.0 + 88.0 * Math.pow(Math.max(0.0, Math.min(1.0, time)), 0.95);
		this.elevation = Math.toRadians(degrees);
		sunDir[0] = Math.sin(sunYaw) * Math.cos(elevation);
		sunDir[1] = Math.sin(elevation);
		sunDir[2] = Math.cos(sunYaw) * Math.cos(elevation);
		double moonElevation = Math.toRadians(36.0);
		moonDir[0] = Math.sin(sunYaw + 0.35) * Math.cos(moonElevation);
		moonDir[1] = Math.sin(moonElevation);
		moonDir[2] = Math.cos(sunYaw + 0.35) * Math.cos(moonElevation);
		this.day = smooth(4.0, 28.0, degrees);
		this.night = 1.0F - smooth(-10.0, 2.0, degrees);
		this.dusk = (float) Math.max(0.0, 1.0 - Math.abs(degrees - 3.0) / 17.0);
		this.sunPower = smooth(-1.0, 10.0, degrees);
		float[] z = mix(ZENITH_NIGHT, ZENITH_DAY, day);
		float[] h = mix(HORIZON_NIGHT, HORIZON_DAY, day);
		this.zenith = mix(z, ZENITH_DUSK, dusk * 0.85F);
		this.horizon = mix(h, HORIZON_DUSK, dusk * 0.9F);
		this.sunColour = mix(SUN_LOW, SUN_HIGH, smooth(6.0, 38.0, degrees));
		float t = time;
		float warm = dusk * 0.18F;
		ambient[0] = (0.06F + 0.89F * t) * (1.0F + warm);
		ambient[1] = (0.07F + 0.89F * t);
		ambient[2] = (0.12F + 0.88F * t) * (1.0F - warm * 1.4F);
	}

	void sky(double dx, double dy, double dz, double clock, float[] out) {
		double up = Math.max(dy, 0.0);
		float h = (float) Math.pow(1.0 - up, 2.6);
		float r = zenith[0] + (horizon[0] - zenith[0]) * h;
		float g = zenith[1] + (horizon[1] - zenith[1]) * h;
		float b = zenith[2] + (horizon[2] - zenith[2]) * h;
		if (dy < 0.0) {
			float haze = (float) Math.min(1.0, -dy * 2.4);
			r = horizon[0] * (1.0F - 0.35F * haze);
			g = horizon[1] * (1.0F - 0.35F * haze);
			b = horizon[2] * (1.0F - 0.30F * haze);
		}
		double along = dx * sunDir[0] + dy * sunDir[1] + dz * sunDir[2];
		if (along > 0.0 && sunPower > 0.0F) {
			double glow = Math.pow(along, 5.0) * 0.28 + Math.pow(along, 28.0) * 0.45 + Math.pow(along, 180.0) * 1.1;
			float amount = (float) glow * sunPower;
			r += sunColour[0] * amount;
			g += sunColour[1] * amount;
			b += sunColour[2] * amount;
			if (along > 0.99945) {
				float edge = (float) Math.min(1.0, (along - 0.99945) / 0.00012);
				r = r + (sunColour[0] * 5.0F - r) * edge * sunPower;
				g = g + (sunColour[1] * 5.0F - g) * edge * sunPower;
				b = b + (sunColour[2] * 5.0F - b) * edge * sunPower;
			}
		}
		if (night > 0.02F && dy > -0.02) {
			double moon = dx * moonDir[0] + dy * moonDir[1] + dz * moonDir[2];
			if (moon > 0.0) {
				float halo = (float) (Math.pow(moon, 60.0) * 0.18 + Math.pow(moon, 600.0) * 0.5) * night;
				r += 0.55F * halo;
				g += 0.65F * halo;
				b += 0.95F * halo;
				if (moon > 0.99905) {
					float edge = (float) Math.min(1.0, (moon - 0.99905) / 0.0001);
					float shade = 0.82F + 0.18F * noise(dx * 90.0, dy * 90.0 + dz * 40.0);
					r += (0.95F * shade - r) * edge * night;
					g += (0.97F * shade - g) * edge * night;
					b += (1.05F * shade - b) * edge * night;
				}
			}
			if (dy > 0.03) {
				int qx = (int) Math.floor(dx * 210.0);
				int qy = (int) Math.floor(dy * 210.0);
				int qz = (int) Math.floor(dz * 210.0);
				float star = hash(qx * 31 + qz * 17, qy * 13 + qz * 7);
				if (star > 0.9935F) {
					float twinkle = 0.55F + 0.45F * hash(qx, qz + 5);
					float amount = (star - 0.9935F) / 0.0065F * twinkle * night * (float) Math.min(1.0, dy * 4.0);
					r += amount;
					g += amount;
					b += amount * 1.05F;
				}
			}
		}
		if (dy > 0.035) {
			double scale = 1.0 / dy;
			double cx = dx * scale * 0.55 + clock * 0.012;
			double cz = dz * scale * 0.55;
			float cloud = fbm(cx * 2.1, cz * 2.1);
			float density = smooth(0.5F, 0.74F, cloud);
			if (density > 0.001F) {
				float fadeHorizon = (float) Math.min(1.0, (dy - 0.035) / 0.2);
				float lit = 0.1F + 0.9F * Math.max(day, dusk * 0.8F);
				float under = 0.72F + 0.28F * (1.0F - density);
				float cr = (day + (1.0F - day) * sunColour[0]) * lit * under;
				float cg = (day + (1.0F - day) * sunColour[1] * 0.9F) * lit * under;
				float cb = (day + (1.0F - day) * sunColour[2] * 0.85F) * lit * under;
				cr = cr * (1.0F - night) + 0.05F * night;
				cg = cg * (1.0F - night) + 0.065F * night;
				cb = cb * (1.0F - night) + 0.12F * night;
				float amount = density * fadeHorizon * 0.88F;
				r += (cr - r) * amount;
				g += (cg - g) * amount;
				b += (cb - b) * amount;
			}
		}
		out[0] = r;
		out[1] = g;
		out[2] = b;
	}

	void fog(float[] out) {
		out[0] = horizon[0] * 0.96F;
		out[1] = horizon[1] * 0.96F;
		out[2] = horizon[2] * 0.96F;
	}

	static float hash(int x, int y) {
		int h = x * 374761393 + y * 668265263;
		h = (h ^ (h >>> 13)) * 1274126177;
		h ^= h >>> 16;
		return (h & 0xFFFFFF) / 16777215.0F;
	}

	static float noise(double x, double y) {
		int xi = (int) Math.floor(x);
		int yi = (int) Math.floor(y);
		float fx = (float) (x - xi);
		float fy = (float) (y - yi);
		fx = fx * fx * (3.0F - 2.0F * fx);
		fy = fy * fy * (3.0F - 2.0F * fy);
		float a = hash(xi, yi);
		float b = hash(xi + 1, yi);
		float c = hash(xi, yi + 1);
		float d = hash(xi + 1, yi + 1);
		return (a + (b - a) * fx) + ((c + (d - c) * fx) - (a + (b - a) * fx)) * fy;
	}

	static float fbm(double x, double y) {
		return 0.5F * noise(x, y) + 0.25F * noise(x * 2.0 + 17.0, y * 2.0 + 3.0) + 0.125F * noise(x * 4.0 + 5.0, y * 4.0 + 11.0) + 0.0625F * noise(x * 8.0 + 2.0, y * 8.0 + 7.0) + 0.0625F * 0.5F;
	}

	static float smooth(double edge0, double edge1, double x) {
		double t = Math.min(1.0, Math.max(0.0, (x - edge0) / (edge1 - edge0)));
		return (float) (t * t * (3.0 - 2.0 * t));
	}

	static float[] mix(float[] a, float[] b, float amount) {
		return new float[]{a[0] + (b[0] - a[0]) * amount, a[1] + (b[1] - a[1]) * amount, a[2] + (b[2] - a[2]) * amount};
	}
}
