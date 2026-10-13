package me.bewf.luceat.client.light;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import me.bewf.luceat.Luceat;
import me.bewf.luceat.client.light.item.ItemLightRule;
import me.bewf.luceat.client.light.item.ItemLightRules;

import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

public final class LightSpecs {
	private static final LightSpec NONE = new LightSpec(0, 0.0F, 0.0F, 0.0F);
	private static final float[] DEFAULT_COLOUR = {1.0F, 0.7F, 0.35F};
	private static final Map<Item, float[]> COLOURS = new HashMap<>();

	private LightSpecs() {
	}

	public static void clearCache() {
		COLOURS.clear();
	}

	public static LightSpec of(ItemStack stack, boolean submerged) {
		if (stack.isEmpty()) {
			return NONE;
		}
		List<ItemLightRule> rules = ItemLightRules.matching(stack);
		int level = 0;
		ItemLightRule best = null;
		for (ItemLightRule rule : rules) {
			if (submerged && rule.waterSensitive()) {
				continue;
			}
			int ruleLevel = rule.level(stack);
			if (ruleLevel > 0 && ruleLevel >= level) {
				level = ruleLevel;
				best = rule;
			}
		}
		if (rules.isEmpty() && stack.getItem() instanceof BlockItem blockItem) {
			level = ItemLightRule.emission(blockItem.getBlock());
		}
		if (level <= 0) {
			return NONE;
		}
		float[] colour = best != null && best.colour() != null ? best.colour() : colourOf(stack);
		return new LightSpec(level, colour[0], colour[1], colour[2]);
	}

	private static float[] colourOf(ItemStack stack) {
		return COLOURS.computeIfAbsent(stack.getItem(), item -> {
			float[] estimate = ColourEstimator.estimate(stack);
			float[] colour = estimate == null ? DEFAULT_COLOUR : estimate;
			Luceat.LOGGER.info("Light colour for {} is {} {} {} ({})", item, colour[0], colour[1], colour[2], estimate == null ? "default" : "texture");
			return colour;
		});
	}
}
