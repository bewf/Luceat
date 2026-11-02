package me.bewf.luceat.client.gui.preview;

import java.util.Optional;

import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Property;

final class Bs {
	private Bs() {
	}

	static BlockState b(Block block) {
		return block.defaultBlockState();
	}

	@SuppressWarnings({"unchecked", "rawtypes"})
	static BlockState with(BlockState state, String name, String value) {
		Property property = state.getBlock().getStateDefinition().getProperty(name);
		if (property == null) {
			return state;
		}
		Optional parsed = property.getValue(value);
		return parsed.isPresent() ? state.setValue(property, (Comparable) parsed.get()) : state;
	}
}
