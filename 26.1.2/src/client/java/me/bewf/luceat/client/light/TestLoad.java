package me.bewf.luceat.client.light;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.world.phys.Vec3;

public final class TestLoad {
	private static final double[][] SPOTS = {
		{3.0, 0.4, 2.0}, {-3.0, 0.8, 2.5}, {0.5, -0.6, -4.0}, {5.0, 1.2, -2.0}, {-5.0, 0.2, -3.0}, {2.0, 2.0, 5.0}
	};
	private static final float[][] TINTS = {
		{1.0F, 0.72F, 0.38F}, {0.35F, 0.7F, 1.0F}, {1.0F, 0.6F, 0.3F}, {0.7F, 0.4F, 1.0F}, {1.0F, 0.8F, 0.5F}, {0.4F, 1.0F, 0.6F}
	};

	private static boolean active;

	private TestLoad() {
	}

	public static void set(boolean value) {
		active = value;
	}

	public static boolean active() {
		return active;
	}

	public static List<LightSource> lights(Vec3 camera) {
		List<LightSource> out = new ArrayList<>();
		if (!active) {
			return out;
		}
		for (int i = 0; i < SPOTS.length; i++) {
			double[] spot = SPOTS[i];
			float[] tint = TINTS[i];
			out.add(new LightSource(camera.x + spot[0], camera.y + spot[1], camera.z + spot[2], tint[0], tint[1], tint[2], 14.0F, true, false, -1000L - i, false));
		}
		return out;
	}
}
