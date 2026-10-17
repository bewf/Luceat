package me.bewf.luceat.client.light;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

import me.bewf.luceat.client.config.LuceatConfig;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

public final class LightPlacement {
	private static final double BACK_OFF = 0.3;
	private static final double CLEARANCE = 0.3;
	private static final float EASE_SECONDS = 0.05F;

	private static final class Fix {
		double x;
		double y;
		double z;
		long nanos;
	}

	private static final Map<Long, Fix> FIXES = new HashMap<>();
	private static final BlockPos.MutableBlockPos PROBE = new BlockPos.MutableBlockPos();

	private LightPlacement() {
	}

	public static void clear() {
		FIXES.clear();
	}

	public static Vec3 outOfWalls(ClientLevel level, Entity owner, Vec3 from, Vec3 wanted) {
		if (level == null || !LuceatConfig.get().keepLightOutOfWalls) {
			return wanted;
		}
		Vec3 line = wanted.subtract(from);
		double length = line.length();
		if (length < 1.0E-4) {
			return wanted;
		}
		BlockHitResult hit = level.clip(new ClipContext(from, wanted, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, owner));
		if (hit.getType() == HitResult.Type.MISS) {
			return wanted;
		}
		Vec3 direction = line.scale(1.0 / length);
		double travelled = hit.getLocation().subtract(from).length();
		return from.add(direction.scale(Math.max(travelled - BACK_OFF, 0.0)));
	}

	public static Vec3 settle(ClientLevel level, Entity owner, long key, Vec3 from, Vec3 wanted) {
		if (level == null || !LuceatConfig.get().keepLightOutOfWalls) {
			return wanted;
		}
		Vec3 cleared = keepAway(level, outOfWalls(level, owner, from, wanted), from);
		long now = System.nanoTime();
		Fix fix = FIXES.get(key);
		if (fix == null) {
			fix = new Fix();
			fix.x = cleared.x - wanted.x;
			fix.y = cleared.y - wanted.y;
			fix.z = cleared.z - wanted.z;
			fix.nanos = now;
			FIXES.put(key, fix);
			if (FIXES.size() > 128) {
				prune(now);
			}
		}
		float dt = Math.min(0.1F, Math.max(0.0F, (now - fix.nanos) / 1.0E9F));
		fix.nanos = now;
		double ease = 1.0 - Math.exp(-dt / EASE_SECONDS);
		fix.x += (cleared.x - wanted.x - fix.x) * ease;
		fix.y += (cleared.y - wanted.y - fix.y) * ease;
		fix.z += (cleared.z - wanted.z - fix.z) * ease;
		Vec3 result = new Vec3(wanted.x + fix.x, wanted.y + fix.y, wanted.z + fix.z);
		if (solid(level, result.x, result.y, result.z)) {
			fix.x = cleared.x - wanted.x;
			fix.y = cleared.y - wanted.y;
			fix.z = cleared.z - wanted.z;
			return cleared;
		}
		return result;
	}

	public static Vec3 keepAway(ClientLevel level, Vec3 point, Vec3 fallback) {
		int cx = (int) Math.floor(point.x);
		int cy = (int) Math.floor(point.y);
		int cz = (int) Math.floor(point.z);
		if (solid(level, cx, cy, cz)) {
			return fallback;
		}
		double x = squeeze(point.x, cx, solid(level, cx - 1, cy, cz), solid(level, cx + 1, cy, cz));
		double y = squeeze(point.y, cy, solid(level, cx, cy - 1, cz), solid(level, cx, cy + 1, cz));
		double z = squeeze(point.z, cz, solid(level, cx, cy, cz - 1), solid(level, cx, cy, cz + 1));
		return new Vec3(x, y, z);
	}

	private static double squeeze(double value, int cell, boolean low, boolean high) {
		double min = cell + (low ? CLEARANCE : 0.0);
		double max = cell + 1.0 - (high ? CLEARANCE : 0.0);
		if (min > max) {
			return cell + 0.5;
		}
		return Math.max(min, Math.min(max, value));
	}

	private static boolean solid(ClientLevel level, double x, double y, double z) {
		return solid(level, (int) Math.floor(x), (int) Math.floor(y), (int) Math.floor(z));
	}

	private static boolean solid(ClientLevel level, int x, int y, int z) {
		PROBE.set(x, y, z);
		return level.getBlockState(PROBE).isSolidRender();
	}

	private static void prune(long now) {
		Iterator<Fix> iterator = FIXES.values().iterator();
		while (iterator.hasNext()) {
			if (now - iterator.next().nanos > 5_000_000_000L) {
				iterator.remove();
			}
		}
	}
}
