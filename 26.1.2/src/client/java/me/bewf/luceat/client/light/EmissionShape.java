package me.bewf.luceat.client.light;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

public final class EmissionShape {
	public static final int ROUND = 0;
	public static final int LANTERN = 1;
	public static final int FLAME_ON_TOP = 2;

	private static final float[][] PROFILE = {
		{1.0F, 1.0F, 1.0F},
		{1.0F, 0.55F, 0.75F},
		{0.85F, 1.0F, 0.35F}
	};
	private static final Map<Block, Integer> KNOWN = new ConcurrentHashMap<>();

	private EmissionShape() {
	}

	public static void clear() {
		KNOWN.clear();
	}

	public static int of(BlockState state) {
		return KNOWN.computeIfAbsent(state.getBlock(), block -> classify(BuiltInRegistries.BLOCK.getKey(block).getPath()));
	}

	private static int classify(String path) {
		if (path.contains("lantern") && !path.contains("sea_") && !path.contains("jack_o")) {
			return LANTERN;
		}
		if (path.contains("campfire") || path.contains("candle")) {
			return FLAME_ON_TOP;
		}
		return ROUND;
	}

	public static float gain(int shape, double upward) {
		if (shape <= 0 || shape >= PROFILE.length) {
			return 1.0F;
		}
		float[] profile = PROFILE[shape];
		double t = Math.min(1.0, Math.max(0.0, (Math.abs(upward) - 0.25) / 0.65));
		double blend = t * t * (3.0 - 2.0 * t);
		float end = upward > 0.0 ? profile[1] : profile[2];
		return (float) (profile[0] + (end - profile[0]) * blend);
	}
}
