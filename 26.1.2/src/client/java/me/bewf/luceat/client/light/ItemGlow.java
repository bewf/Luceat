package me.bewf.luceat.client.light;

import me.bewf.luceat.client.compat.IrisCompat;
import me.bewf.luceat.client.config.LuceatConfig;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.decoration.GlowItemFrame;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.Set;

public final class ItemGlow {
	private static final int MIN_LEVEL = 6;
	private static final int WHOLE_ITEM = 4;
	private static final Set<Item> FULLY_LIT = Set.of(Items.BLAZE_ROD, Items.BLAZE_POWDER, Items.FIRE_CHARGE, Items.GLOWSTONE_DUST,
		Items.MAGMA_CREAM, Items.GLOW_BERRIES, Items.GLOW_INK_SAC, Items.NETHER_STAR);

	private ItemGlow() {
	}

	public static int tag(ItemStack stack, ItemDisplayContext context) {
		LuceatConfig config = LuceatConfig.get();
		if (!config.enabled || config.emissiveGlow <= 0.01F || context == ItemDisplayContext.GUI || stack.isEmpty() || IrisCompat.packOn()) {
			return 0;
		}
		if (LightSpecs.of(stack, false).level() < MIN_LEVEL) {
			return 0;
		}
		return scale(config) | (FULLY_LIT.contains(stack.getItem()) ? WHOLE_ITEM : 0);
	}

	public static int tag(ItemStack stack, ItemDisplayContext context, Entity holder) {
		int own = tag(stack, context);
		if (own > 0 || !(holder instanceof GlowItemFrame) || stack.isEmpty()) {
			return own;
		}
		LuceatConfig config = LuceatConfig.get();
		if (!config.enabled || config.emissiveGlow <= 0.01F || !config.itemFrames || IrisCompat.packOn()) {
			return 0;
		}
		return scale(config);
	}

	private static int scale(LuceatConfig config) {
		if (config.emissiveGlow < 0.75F) {
			return 1;
		}
		return config.emissiveGlow < 1.5F ? 2 : 3;
	}
}
