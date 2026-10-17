package me.bewf.luceat.client.shadow;

import java.util.IdentityHashMap;
import java.util.Map;

import me.bewf.luceat.client.config.LuceatConfig;
import me.bewf.luceat.client.config.ShadowQuality;

import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.model.sprite.Material;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.state.BlockState;

public final class Occluders {
	private static final Map<BlockState, Integer> STATES = new IdentityHashMap<>();

	private Occluders() {
	}

	public static void clear() {
		STATES.clear();
		ShapeMasks.clear();
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
		if (state.is(Blocks.BARRIER) || state.is(Blocks.LIGHT) || state.is(Blocks.STRUCTURE_VOID) || state.getBlock() instanceof LiquidBlock) {
			return 0;
		}
		if (state.getRenderShape() == RenderShape.MODEL && state.isCollisionShapeFullBlock(level, pos)) {
			return cube(state);
		}
		LuceatConfig config = LuceatConfig.get();
		if (!config.thinShadows) {
			return 0;
		}
		if (!config.plantShadows && state.getCollisionShape(level, pos).isEmpty()) {
			return 0;
		}
		return ShapeMasks.idFor(state, level, pos);
	}

	private static int cube(BlockState state) {
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
