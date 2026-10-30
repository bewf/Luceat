package me.bewf.luceat.client.gui.glass;

public final class Motion {
	private static long last;
	private static float dt = 0.016F;

	private Motion() {
	}

	public static void tick(long nanos) {
		if (last != 0L) {
			dt = Math.max(0.001F, Math.min(0.1F, (nanos - last) / 1.0E9F));
		}
		last = nanos;
	}

	public static float dt() {
		return dt;
	}

	public static float clamp(float v) {
		return v < 0.0F ? 0.0F : Math.min(v, 1.0F);
	}

	public static float outCubic(float t) {
		float k = 1.0F - clamp(t);
		return 1.0F - k * k * k;
	}

	public static float inCubic(float t) {
		float c = clamp(t);
		return c * c * c;
	}

	public static float inOut(float t) {
		float c = clamp(t);
		return c < 0.5F ? 4.0F * c * c * c : 1.0F - (float) Math.pow(-2.0F * c + 2.0F, 3.0) / 2.0F;
	}

	public static float bezier(float x1, float y1, float x2, float y2, float x) {
		float c = clamp(x);
		float lo = 0.0F;
		float hi = 1.0F;
		float t = c;
		for (int i = 0; i < 18; i++) {
			float u = 1.0F - t;
			float bx = 3.0F * u * u * t * x1 + 3.0F * u * t * t * x2 + t * t * t;
			if (bx < c) {
				lo = t;
			} else {
				hi = t;
			}
			t = (lo + hi) * 0.5F;
		}
		float u = 1.0F - t;
		return 3.0F * u * u * t * y1 + 3.0F * u * t * t * y2 + t * t * t;
	}

	public static float enter(float t) {
		return bezier(0.05F, 0.7F, 0.1F, 1.0F, t);
	}

	public static float exit(float t) {
		return bezier(0.3F, 0.0F, 0.8F, 0.15F, t);
	}

	public static float standard(float t) {
		return bezier(0.2F, 0.0F, 0.0F, 1.0F, t);
	}

	public static float lerp(float a, float b, float t) {
		return a + (b - a) * t;
	}

	public static final class Smooth {
		private float value;
		private boolean set;

		public Smooth() {
		}

		public Smooth(float start) {
			value = start;
			set = true;
		}

		public float step(float target, float halfLife) {
			if (!set) {
				value = target;
				set = true;
				return value;
			}
			float k = 1.0F - (float) Math.pow(0.5, dt / Math.max(0.001F, halfLife));
			value += (target - value) * k;
			if (Math.abs(target - value) < 0.0005F) {
				value = target;
			}
			return value;
		}

		public float get() {
			return value;
		}

		public void snap(float v) {
			value = v;
			set = true;
		}
	}
}
