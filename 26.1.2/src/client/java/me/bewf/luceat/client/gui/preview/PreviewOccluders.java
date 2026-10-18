package me.bewf.luceat.client.gui.preview;

import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Objects;

import me.bewf.luceat.client.config.LuceatConfig;
import me.bewf.luceat.client.config.ShadowQuality;
import me.bewf.luceat.client.shadow.PatternAtlas;
import me.bewf.luceat.client.shadow.ShapeMasks;

import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.model.sprite.Material;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.EmptyBlockGetter;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.state.BlockState;

public final class PreviewOccluders {
	private final LuceatConfig config;
	private final Map<BlockState, Occ> cache = new IdentityHashMap<>();

	public PreviewOccluders(LuceatConfig config) {
		this.config = config;
	}

	public static int key(LuceatConfig config) {
		return Objects.hash(config.cutoutOpacity, config.glassTint, config.foliageShadows, config.thinShadows, config.plantShadows);
	}

	public Occ of(BlockState state) {
		Occ known = cache.get(state);
		if (known == null) {
			known = compute(state);
			cache.put(state, known);
		}
		return known;
	}

	private Occ compute(BlockState state) {
		if (state.isAir()) {
			return Occ.EMPTY;
		}
		if (state.isSolidRender()) {
			return Occ.SOLID;
		}
		if (state.is(Blocks.BARRIER) || state.is(Blocks.LIGHT) || state.is(Blocks.STRUCTURE_VOID) || state.getBlock() instanceof LiquidBlock) {
			return Occ.EMPTY;
		}
		if (state.getRenderShape() == RenderShape.MODEL && state.isCollisionShapeFullBlock(EmptyBlockGetter.INSTANCE, BlockPos.ZERO)) {
			return cube(state);
		}
		if (!config.thinShadows) {
			return Occ.EMPTY;
		}
		if (!config.plantShadows && state.getCollisionShape(EmptyBlockGetter.INSTANCE, BlockPos.ZERO).isEmpty()) {
			return Occ.EMPTY;
		}
		float[] mask = ShapeMasks.build(state, EmptyBlockGetter.INSTANCE, BlockPos.ZERO, config);
		if (ShapeMasks.empty(mask)) {
			return Occ.EMPTY;
		}
		if (ShapeMasks.closed(mask)) {
			return Occ.SOLID;
		}
		return new Occ(Occ.SHAPE, mask);
	}

	private Occ cube(BlockState state) {
		if (config.foliageShadows == ShadowQuality.OFF) {
			return Occ.SOLID;
		}
		Material.Baked material = Minecraft.getInstance().getModelManager().getBlockStateModelSet().getParticleMaterial(state);
		if (material == null) {
			return Occ.EMPTY;
		}
		float[] tile = PatternAtlas.cpuTile(material.sprite().contents().name(), config);
		return tile == null ? Occ.SOLID : new Occ(Occ.PATTERN, tile);
	}
}
