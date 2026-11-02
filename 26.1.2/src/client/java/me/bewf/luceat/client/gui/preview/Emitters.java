package me.bewf.luceat.client.gui.preview;

import me.bewf.luceat.client.gui.preview.Light.Source;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.level.block.state.BlockState;

final class Emitters {
	private Emitters() {
	}

	static Light light(BlockState state, int x, int y, int z) {
		int level = state.getLightEmission();
		if (level <= 0) {
			return null;
		}
		String name = BuiltInRegistries.BLOCK.getKey(state.getBlock()).getPath();
		float[] c = colour(name);
		double height = name.contains("torch") ? 0.75 : name.contains("campfire") ? 0.5 : name.contains("lantern") && !name.contains("sea") && !name.contains("jack") ? 0.5 : 0.5;
		Light light = Light.of(Source.PLACED, x + 0.5, y + height, z + 0.5, level, c[0], c[1], c[2]);
		return fire(name) ? light.asFire() : light;
	}

	static float[] colour(String name) {
		if (name.contains("soul")) {
			return new float[]{0.3F, 0.72F, 1.0F};
		}
		if (name.contains("sea_lantern")) {
			return new float[]{0.62F, 0.92F, 1.0F};
		}
		if (name.contains("redstone")) {
			return new float[]{1.0F, 0.22F, 0.12F};
		}
		if (name.contains("end_rod") || name.contains("froglight") && name.contains("pearl")) {
			return new float[]{0.92F, 0.9F, 1.0F};
		}
		if (name.contains("glowstone")) {
			return new float[]{1.0F, 0.84F, 0.5F};
		}
		if (name.contains("shroomlight") || name.contains("magma")) {
			return new float[]{1.0F, 0.52F, 0.2F};
		}
		if (name.contains("amethyst")) {
			return new float[]{0.7F, 0.4F, 1.0F};
		}
		if (name.contains("candle")) {
			return new float[]{1.0F, 0.72F, 0.36F};
		}
		if (name.contains("copper")) {
			return new float[]{0.5F, 1.0F, 0.75F};
		}
		if (name.contains("enchanting")) {
			return new float[]{0.7F, 0.4F, 1.0F};
		}
		return new float[]{1.0F, 0.72F, 0.38F};
	}

	static boolean fire(String name) {
		if (name.contains("soul") || name.contains("redstone") || name.contains("sea") || name.contains("glowstone") || name.contains("rod")) {
			return false;
		}
		return name.contains("torch") || name.contains("campfire") || name.contains("lantern") || name.contains("candle") || name.contains("fire");
	}
}
