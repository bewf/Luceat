package me.bewf.luceat.client.light;

import me.bewf.luceat.client.config.LuceatConfig;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

public final class LightPlacement {
	private static final double BACK_OFF = 0.3;

	private LightPlacement() {
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
}
