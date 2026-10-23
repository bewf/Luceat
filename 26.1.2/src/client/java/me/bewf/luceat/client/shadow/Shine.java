package me.bewf.luceat.client.shadow;

import java.util.IdentityHashMap;
import java.util.Map;

import me.bewf.luceat.client.light.block.BlockRule;
import me.bewf.luceat.client.light.block.BlockRules;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.level.block.state.BlockState;

// how glossy a block looks under a light, 0 to 255
public final class Shine {
	private static final Map<BlockState, Integer> KNOWN = new IdentityHashMap<>();

	private Shine() {
	}

	public static void clear() {
		KNOWN.clear();
	}

	public static int of(BlockState state) {
		if (state.isAir()) {
			return 0;
		}
		Integer known = KNOWN.get(state);
		if (known != null) {
			return known;
		}
		BlockRule rule = BlockRules.rule(state);
		int value;
		if (rule != null && rule.shine >= 0.0F) {
			value = Math.round(Math.min(rule.shine, 1.0F) * 255.0F);
		} else {
			value = Math.round(material(BuiltInRegistries.BLOCK.getKey(state.getBlock()).getPath()) * 255.0F);
		}
		KNOWN.put(state, value);
		return value;
	}

	public static float material(String path) {
		if (path.contains("ore") || path.contains("raw_") || path.contains("powder") || path.contains("stairs") && !path.contains("quartz")) {
			return 0.0F;
		}
		if (path.contains("iron_block") || path.contains("gold_block") || path.contains("netherite_block") || path.contains("diamond_block") || path.contains("emerald_block")) {
			return 1.0F;
		}
		if (path.contains("copper")) {
			return 0.9F;
		}
		if (path.contains("glass") || path.contains("ice")) {
			return 0.9F;
		}
		if (path.contains("obsidian")) {
			return 0.6F;
		}
		if (path.contains("quartz") || path.contains("polished") || path.contains("amethyst")) {
			return 0.55F;
		}
		if (path.contains("glazed_terracotta")) {
			return 0.45F;
		}
		if (path.contains("smooth_")) {
			return 0.35F;
		}
		if (path.contains("concrete")) {
			return 0.2F;
		}
		if (path.contains("lapis_block") || path.contains("redstone_block")) {
			return 0.4F;
		}
		return 0.0F;
	}
}
