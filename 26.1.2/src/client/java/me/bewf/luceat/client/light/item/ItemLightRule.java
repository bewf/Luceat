package me.bewf.luceat.client.light.item;

import java.util.List;

import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.BlockItemStateProperties;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

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
			return stack.getItem() instanceof BlockItem blockItem ? emission(stack, blockItem.getBlock()) : 0;
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

	public static int emission(ItemStack stack, Block block) {
		if (block == null) {
			return 0;
		}
		BlockState state = block.defaultBlockState();
		BlockItemStateProperties properties = stack.get(DataComponents.BLOCK_STATE);
		if (properties != null) {
			state = properties.apply(state);
		}
		return state.getLightEmission();
	}

	static Block blockById(String id) {
		Identifier identifier = Identifier.tryParse(id);
		return identifier == null ? null : BuiltInRegistries.BLOCK.getValue(identifier);
	}
}
