package me.bewf.luceat.client.gui.preview;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

import me.bewf.luceat.client.shadow.SpriteData;

import net.minecraft.client.Minecraft;
import net.minecraft.client.color.block.BlockTintSource;
import net.minecraft.client.model.geom.builders.UVPair;
import net.minecraft.client.renderer.block.dispatch.BlockStateModel;
import net.minecraft.client.renderer.block.dispatch.BlockStateModelPart;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.model.geometry.BakedQuad;
import net.minecraft.core.Direction;
import net.minecraft.resources.Identifier;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.state.BlockState;

import org.joml.Vector3fc;

public final class BlockVoxels {
	private static final int RES = Tracer.VOX;
	private static final int MAX_STEPS = 128;
	private static final Direction[] SIDES = Direction.values();
	private static final Map<BlockState, int[]> CACHE = new IdentityHashMap<>();
	private static final int[] NOTHING = new int[0];

	private BlockVoxels() {
	}

	public static void clear() {
		CACHE.clear();
	}

	public static int[] of(BlockState state) {
		int[] known = CACHE.get(state);
		if (known == null) {
			int[] built = build(state);
			known = built == null ? NOTHING : built;
			CACHE.put(state, known);
		}
		return known == NOTHING ? null : known;
	}

	private static int[] build(BlockState state) {
		if (state.is(Blocks.WATER)) {
			return water(state);
		}
		if (state.isAir() || state.getRenderShape() != RenderShape.MODEL) {
			return null;
		}
		BlockStateModel model = Minecraft.getInstance().getModelManager().getBlockStateModelSet().get(state);
		List<BlockStateModelPart> parts = new ArrayList<>();
		model.collectParts(RandomSource.create(42L), parts);
		int[] voxels = new int[RES * RES * RES];
		boolean drawn = false;
		for (BlockStateModelPart part : parts) {
			drawn |= quads(part.getQuads(null), voxels, state);
			for (Direction side : SIDES) {
				drawn |= quads(part.getQuads(side), voxels, state);
			}
		}
		return drawn ? voxels : null;
	}

	private static int[] water(BlockState state) {
		if (state.getValue(LiquidBlock.LEVEL) != 0) {
			return null;
		}
		SpriteData sprite = SpriteData.of(Identifier.fromNamespaceAndPath("minecraft", "block/water_still"));
		int[] voxels = new int[RES * RES * RES];
		for (int z = 0; z < RES; z++) {
			for (int x = 0; x < RES; x++) {
				int pixel = sprite.sample((x + 0.5F) / RES, (z + 0.5F) / RES);
				int red = (pixel >> 16 & 255) * 0x3F / 255;
				int green = (pixel >> 8 & 255) * 0x76 / 255;
				int blue = (pixel & 255) * 0xE4 / 255;
				int tone = Math.max(red, Math.max(green, blue)) < 40 ? 1 : 0;
				voxels[x + RES * (13 + RES * z)] = 120 << 24 | Math.min(255, red * 2 + 20 + tone) << 16 | Math.min(255, green * 2 + 40) << 8 | Math.min(255, blue * 2 + 60);
			}
		}
		return voxels;
	}

	private static boolean quads(List<BakedQuad> quads, int[] voxels, BlockState state) {
		boolean drawn = false;
		for (BakedQuad quad : quads) {
			drawn |= quad(quad, voxels, state);
		}
		return drawn;
	}

	private static boolean quad(BakedQuad quad, int[] voxels, BlockState state) {
		TextureAtlasSprite atlas = quad.materialInfo().sprite();
		SpriteData sprite = SpriteData.of(atlas.contents().name());
		float u0 = atlas.getU0();
		float u1 = atlas.getU1();
		float v0 = atlas.getV0();
		float v1 = atlas.getV1();
		int tint = tintOf(state, quad.materialInfo().tintIndex());
		float[] p = new float[12];
		float[] t = new float[8];
		for (int k = 0; k < 4; k++) {
			Vector3fc position = quad.position(k);
			p[k * 3] = position.x();
			p[k * 3 + 1] = position.y();
			p[k * 3 + 2] = position.z();
			long packed = quad.packedUV(k);
			t[k * 2] = (UVPair.unpackU(packed) - u0) / (u1 - u0);
			t[k * 2 + 1] = (UVPair.unpackV(packed) - v0) / (v1 - v0);
		}
		float lengthU = Math.max(distance(p, 0, 1), distance(p, 3, 2)) * RES;
		float lengthV = Math.max(distance(p, 0, 3), distance(p, 1, 2)) * RES;
		int stepsU = Math.min(MAX_STEPS, Math.max(2, (int) Math.ceil(lengthU * 2.0F) + 1));
		int stepsV = Math.min(MAX_STEPS, Math.max(2, (int) Math.ceil(lengthV * 2.0F) + 1));
		boolean touched = false;
		for (int j = 0; j < stepsV; j++) {
			float v = (j + 0.5F) / stepsV;
			for (int i = 0; i < stepsU; i++) {
				float u = (i + 0.5F) / stepsU;
				float x = bilinear(p, 3, 0, u, v);
				float y = bilinear(p, 3, 1, u, v);
				float z = bilinear(p, 3, 2, u, v);
				if (x < -0.001F || x > 1.001F || y < -0.001F || y > 1.001F || z < -0.001F || z > 1.001F) {
					continue;
				}
				float tu = Math.min(Math.max(bilinear(t, 2, 0, u, v), 0.0F), 0.9999F);
				float tv = Math.min(Math.max(bilinear(t, 2, 1, u, v), 0.0F), 0.9999F);
				int pixel = sprite.sample(tu, tv);
				int alpha = pixel >>> 24;
				if (alpha == 0) {
					continue;
				}
				int red = (pixel >> 16 & 255) * (tint >> 16 & 255) / 255;
				int green = (pixel >> 8 & 255) * (tint >> 8 & 255) / 255;
				int blue = (pixel & 255) * (tint & 255) / 255;
				int vx = Math.min(RES - 1, Math.max(0, (int) Math.floor(x * RES)));
				int vy = Math.min(RES - 1, Math.max(0, (int) Math.floor(y * RES)));
				int vz = Math.min(RES - 1, Math.max(0, (int) Math.floor(z * RES)));
				int at = vx + RES * (vy + RES * vz);
				if ((voxels[at] >>> 24) <= alpha) {
					voxels[at] = alpha << 24 | red << 16 | green << 8 | blue;
				}
				touched = true;
			}
		}
		return touched;
	}

	private static int tintOf(BlockState state, int tintIndex) {
		if (tintIndex < 0) {
			return 0xFFFFFF;
		}
		BlockTintSource source = Minecraft.getInstance().getBlockColors().getTintSource(state, tintIndex);
		if (source == null) {
			return 0xFFFFFF;
		}
		return source.color(state) & 0xFFFFFF;
	}

	private static float bilinear(float[] p, int stride, int axis, float u, float v) {
		float bottom = p[axis] + (p[stride + axis] - p[axis]) * u;
		float top = p[3 * stride + axis] + (p[2 * stride + axis] - p[3 * stride + axis]) * u;
		return bottom + (top - bottom) * v;
	}

	private static float distance(float[] p, int a, int b) {
		float dx = p[a * 3] - p[b * 3];
		float dy = p[a * 3 + 1] - p[b * 3 + 1];
		float dz = p[a * 3 + 2] - p[b * 3 + 2];
		return (float) Math.sqrt(dx * dx + dy * dy + dz * dz);
	}
}
