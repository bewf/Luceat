package me.bewf.luceat.client.light;

import net.minecraft.client.Minecraft;
import net.minecraft.world.phys.Vec3;

// one rule for every light
public final class LightCull {
	private static final double HALF_VIEW = Math.toRadians(85.0);

	private static double cx;
	private static double cy;
	private static double cz;
	private static double fx;
	private static double fy;
	private static double fz = 1.0;

	private LightCull() {
	}

	public static void setup(Minecraft minecraft, Vec3 camera, float partialTick) {
		cx = camera.x;
		cy = camera.y;
		cz = camera.z;
		if (minecraft.player != null) {
			Vec3 look = minecraft.player.getViewVector(partialTick);
			fx = look.x;
			fy = look.y;
			fz = look.z;
		}
	}

	public static double forwardX() {
		return fx;
	}

	public static double forwardY() {
		return fy;
	}

	public static double forwardZ() {
		return fz;
	}

	public static boolean sees(double x, double y, double z, double radius) {
		double dx = x - cx;
		double dy = y - cy;
		double dz = z - cz;
		double distanceSquared = dx * dx + dy * dy + dz * dz;
		if (distanceSquared <= radius * radius) {
			return true;
		}
		double distance = Math.sqrt(distanceSquared);
		double cosine = (dx * fx + dy * fy + dz * fz) / distance;
		double limit = HALF_VIEW + Math.asin(Math.min(1.0, radius / distance));
		return limit >= Math.PI || cosine >= Math.cos(limit);
	}
}
