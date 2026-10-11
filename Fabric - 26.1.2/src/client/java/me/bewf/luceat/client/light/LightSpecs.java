package me.bewf.luceat.client.light;

import java.util.HashMap;
import java.util.Map;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.CampfireBlock;

public final class LightSpecs {
	private static final LightSpec NONE = new LightSpec(0, 0.0F, 0.0F, 0.0F);
	private static final Map<Item, LightSpec> CACHE = new HashMap<>();

	private LightSpecs() {
	}

	public static LightSpec of(ItemStack stack) {
		if (stack.isEmpty()) {
			return NONE;
		}
		return CACHE.computeIfAbsent(stack.getItem(), LightSpecs::resolve);
	}

	private static LightSpec resolve(Item item) {
		if (item == Items.LAVA_BUCKET) {
			return new LightSpec(15, 1.0F, 0.45F, 0.15F);
		}
		if (item == Items.GLOW_BERRIES) {
			return new LightSpec(14, 1.0F, 0.8F, 0.4F);
		}
		if (!(item instanceof BlockItem blockItem)) {
			return NONE;
		}
		Block block = blockItem.getBlock();
		if (block instanceof CampfireBlock) {
			return NONE;
		}
		int level = block.defaultBlockState().getLightEmission();
		if (level <= 0) {
			return NONE;
		}
		String path = BuiltInRegistries.BLOCK.getKey(block).getPath();
		return colour(level, path);
	}

	private static LightSpec colour(int level, String path) {
		if (path.contains("soul")) {
			return new LightSpec(level, 0.3F, 0.75F, 1.0F);
		}
		if (path.contains("copper")) {
			return new LightSpec(level, 0.35F, 1.0F, 0.7F);
		}
		if (path.contains("redstone")) {
			return new LightSpec(level, 1.0F, 0.2F, 0.1F);
		}
		if (path.contains("amethyst") || path.contains("crying_obsidian") || path.contains("respawn_anchor")) {
			return new LightSpec(level, 0.7F, 0.4F, 1.0F);
		}
		if (path.contains("sea_lantern") || path.contains("conduit") || path.contains("beacon")) {
			return new LightSpec(level, 0.7F, 0.95F, 1.0F);
		}
		if (path.contains("end_rod")) {
			return new LightSpec(level, 1.0F, 0.95F, 0.9F);
		}
		if (path.contains("verdant")) {
			return new LightSpec(level, 0.5F, 1.0F, 0.5F);
		}
		if (path.contains("pearlescent")) {
			return new LightSpec(level, 1.0F, 0.6F, 0.9F);
		}
		if (path.contains("sculk")) {
			return new LightSpec(level, 0.3F, 0.8F, 0.9F);
		}
		if (path.contains("glow_lichen")) {
			return new LightSpec(level, 0.7F, 1.0F, 0.8F);
		}
		if (path.contains("magma")) {
			return new LightSpec(level, 1.0F, 0.4F, 0.15F);
		}
		return new LightSpec(level, 1.0F, 0.7F, 0.35F);
	}
}
