package me.bewf.luceat.client.shadow;

import java.util.IdentityHashMap;
import java.util.Map;

import me.bewf.luceat.client.config.LuceatConfig;
import me.bewf.luceat.client.config.ShadowQuality;

import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.model.sprite.Material;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.state.BlockState;

public final class Occluders {
	private static final Map<BlockState, Integer> STATES = new IdentityHashMap<>();

	private Occluders() {
	}

	public static void clear() {
		STATES.clear();
	}

	public static int id(BlockState state, BlockGetter level, BlockPos pos) {
		if (state.isAir()) {
			return 0;
		}
		Integer known = STATES.get(state);
		if (known != null) {
			return known;
		}
		int id = compute(state, level, pos);
		STATES.put(state, id);
		return id;
	}

	private static int compute(BlockState state, BlockGetter level, BlockPos pos) {
		if (state.isSolidRender()) {
			return PatternAtlas.OPAQUE;
		}
		if (state.getRenderShape() == RenderShape.INVISIBLE || !state.isCollisionShapeFullBlock(level, pos)) {
			return 0;
		}
		if (LuceatConfig.get().foliageShadows == ShadowQuality.OFF) {
			return PatternAtlas.OPAQUE;
		}
		Material.Baked material = Minecraft.getInstance().getModelManager().getBlockStateModelSet().getParticleMaterial(state);
		if (material == null) {
			return 0;
		}
		return PatternAtlas.idFor(material.sprite().contents().name());
	}
}
