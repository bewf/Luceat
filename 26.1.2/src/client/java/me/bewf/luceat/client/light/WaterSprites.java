package me.bewf.luceat.client.light;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.block.FluidModel;
import net.minecraft.client.renderer.block.FluidStateModelSet;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.world.level.material.Fluids;

public final class WaterSprites {
	private static final float[] RECTS = new float[8];
	private static FluidStateModelSet source;

	private WaterSprites() {
	}

	public static float[] rects() {
		FluidStateModelSet set = Minecraft.getInstance().getModelManager().getFluidStateModelSet();
		if (set == null) {
			return RECTS;
		}
		if (set != source) {
			source = set;
			FluidModel model = set.get(Fluids.WATER.defaultFluidState());
			fill(0, model.stillMaterial().sprite());
			fill(4, model.flowingMaterial().sprite());
		}
		return RECTS;
	}

	private static void fill(int at, TextureAtlasSprite sprite) {
		float margin = 1.0E-4F;
		RECTS[at] = sprite.getU0() - margin;
		RECTS[at + 1] = sprite.getV0() - margin;
		RECTS[at + 2] = sprite.getU1() + margin;
		RECTS[at + 3] = sprite.getV1() + margin;
	}
}
