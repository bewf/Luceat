package me.bewf.luceat.client.light.item;

import java.util.List;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;

public record ItemLightRule(List<Item> items, List<TagKey<Item>> tags, Luminance luminance, boolean waterSensitive, float[] colour) {
	public boolean matches(ItemStack stack) {
		if (items.contains(stack.getItem())) {
			return true;
		}
		for (TagKey<Item> tag : tags) {
			if (stack.is(tag)) {
				return true;
			}
		}
		return false;
	}

	public int level(ItemStack stack) {
		return luminance.of(stack);
	}

	public sealed interface Luminance permits Fixed, BlockSelf, BlockReference {
		int of(ItemStack stack);
	}

	public record Fixed(int level) implements Luminance {
		@Override
		public int of(ItemStack stack) {
			return level;
		}
	}

	public record BlockSelf() implements Luminance {
		@Override
		public int of(ItemStack stack) {
			return stack.getItem() instanceof BlockItem blockItem ? emission(blockItem.getBlock()) : 0;
		}
	}

	public record BlockReference(Block block) implements Luminance {
		@Override
		public int of(ItemStack stack) {
			return emission(block);
		}
	}

	public static int emission(Block block) {
		return block == null ? 0 : block.defaultBlockState().getLightEmission();
	}

	static Block blockById(String id) {
		Identifier identifier = Identifier.tryParse(id);
		return identifier == null ? null : BuiltInRegistries.BLOCK.getValue(identifier);
	}
}
