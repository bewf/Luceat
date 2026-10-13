package me.bewf.luceat.client.shadow;

import me.bewf.luceat.client.config.LuceatConfig;

import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.block.state.BlockState;

public final class Occluders {
	private Occluders() {
	}

	public static int opacity(BlockState state) {
		if (state.isSolidRender()) {
			return 255;
		}
		if (state.getBlock() instanceof LeavesBlock) {
			return Math.round(255.0F * Math.clamp(LuceatConfig.get().leafShadow, 0.0F, 1.0F));
		}
		return 0;
	}
}
