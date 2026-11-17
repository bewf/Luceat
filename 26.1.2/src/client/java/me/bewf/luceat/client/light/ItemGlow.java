package me.bewf.luceat.client.light;

import me.bewf.luceat.client.compat.IrisCompat;
import me.bewf.luceat.client.config.LuceatConfig;

import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;

public final class ItemGlow {
	private static final int MIN_LEVEL = 6;

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
		if (config.emissiveGlow < 0.75F) {
			return 1;
		}
		return config.emissiveGlow < 1.5F ? 2 : 3;
	}
}
