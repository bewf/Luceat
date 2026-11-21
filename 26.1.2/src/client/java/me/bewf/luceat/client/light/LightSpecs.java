package me.bewf.luceat.client.light;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import me.bewf.luceat.Luceat;
import me.bewf.luceat.client.config.LuceatConfig;
import me.bewf.luceat.client.light.block.BlockRule;
import me.bewf.luceat.client.light.block.BlockRules;
import me.bewf.luceat.client.light.item.ItemLightRule;
import me.bewf.luceat.client.light.item.ItemLightRules;

import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

public final class LightSpecs {
	private static final LightSpec NONE = new LightSpec(0, 0.0F, 0.0F, 0.0F);
	private static final LightSpec ENCHANT_GLOW = new LightSpec(2, 0.62F, 0.38F, 1.0F);
	private static final float[] DEFAULT_COLOUR = {1.0F, 0.7F, 0.35F};
	private static final float[] GLOW_FRAME = {0.3F, 0.95F, 0.85F};
	private static final Map<Item, float[]> COLOURS = new HashMap<>();

	private LightSpecs() {
	}

	public static void clearCache() {
		COLOURS.clear();
	}

	public enum Where {
		HELD, DROPPED, FRAMED
	}

	public static LightSpec of(ItemStack stack, boolean wet) {
		return of(stack, wet, Where.HELD);
	}

	public static LightSpec of(ItemStack stack, boolean wet, Where where) {
		if (stack.isEmpty()) {
			return NONE;
		}
		LuceatConfig config = LuceatConfig.get();
		BlockRule custom = BlockRules.forItem(stack.getItem());
		if (custom != null && !custom.enabled) {
			return NONE;
		}
		if (custom != null && !(where == Where.HELD ? custom.whenHeld : where == Where.DROPPED ? custom.whenDropped : custom.whenFramed)) {
			return NONE;
		}
		boolean submerged = wet && (custom != null && custom.water >= 0 ? custom.water == 1 : config.waterPutsOutLights);
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
			level = ItemLightRule.emission(stack, blockItem.getBlock());
		}
		if (custom != null && custom.level >= 0) {
			level = custom.level;
		}
		if (level <= 0) {
			if (config.enchantGlow && rules.isEmpty() && stack.hasFoil()) {
				return ENCHANT_GLOW;
			}
			return NONE;
		}
		if (custom != null && Math.abs(custom.radius - 1.0F) > 0.001F) {
			level = Math.max(1, Math.min(24, Math.round(level * custom.radius)));
		}
		float[] colour = custom != null && custom.colour >= 0 ? custom.rgb() : best != null && best.colour() != null ? best.colour() : colourOf(stack, config.textureColours);
		boolean hidden = BlockRules.noShadow(stack.getItem() instanceof BlockItem blockItem ? blockItem.getBlock() : null, custom);
		return new LightSpec(level, colour[0], colour[1], colour[2], hidden);
	}

	public static LightSpec glowFrame(ItemStack stack) {
		if (stack.isEmpty()) {
			return new LightSpec(4, GLOW_FRAME[0], GLOW_FRAME[1], GLOW_FRAME[2]);
		}
		LightSpec own = of(stack, false, Where.FRAMED);
		if (own.level() >= 7) {
			return own;
		}
		float[] item = colourOf(stack, true);
		return new LightSpec(Math.max(own.level(), 7),
			item[0] * 0.75F + GLOW_FRAME[0] * 0.25F,
			item[1] * 0.75F + GLOW_FRAME[1] * 0.25F,
			item[2] * 0.75F + GLOW_FRAME[2] * 0.25F);
	}

	private static float[] colourOf(ItemStack stack, boolean fromTexture) {
		if (!fromTexture) {
			return DEFAULT_COLOUR;
		}
		return COLOURS.computeIfAbsent(stack.getItem(), item -> {
			float[] estimate = ColourEstimator.estimate(stack);
			float[] colour = estimate == null ? DEFAULT_COLOUR : estimate;
			Luceat.LOGGER.info("Light colour for {} is {} {} {} ({})", item, colour[0], colour[1], colour[2], estimate == null ? "default" : "texture");
			return colour;
		});
	}
}
