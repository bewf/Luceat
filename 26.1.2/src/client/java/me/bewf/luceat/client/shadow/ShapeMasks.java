package me.bewf.luceat.client.shadow;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import me.bewf.luceat.client.config.LuceatConfig;

import org.joml.Vector3fc;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL12;
import org.lwjgl.opengl.GL13;
import org.lwjgl.opengl.GL20;

import net.minecraft.client.Minecraft;
import net.minecraft.client.model.geom.builders.UVPair;
import net.minecraft.client.renderer.block.dispatch.BlockStateModel;
import net.minecraft.client.renderer.block.dispatch.BlockStateModelPart;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.model.geometry.BakedQuad;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.VoxelShape;

public final class ShapeMasks {
	public static final int RES = 8;
	public static final int VOXELS = RES * RES * RES;
	public static final int TILE_WIDTH = RES * RES;
	public static final int TILE_HEIGHT = RES;
	public static final int PER_ROW = 16;
	public static final int ROWS = 64;
	public static final int ATLAS_WIDTH = PER_ROW * TILE_WIDTH;
	public static final int ATLAS_HEIGHT = ROWS * TILE_HEIGHT;
	public static final int CAPACITY = PER_ROW * ROWS;
	public static final int FLAG = 0x8000;
	public static final String SAMPLER = "u_LuceatShapes";

	public static final int NONE = 0;
	public static final int SOLID = 1;

	private static final int UNIT = 13;
	private static final int MAX_STEPS = 96;
	private static final Direction[] SIDES = Direction.values();

	private static final Map<Integer, List<Integer>> BUCKETS = new HashMap<>();
	private static final List<byte[]> STORED = new ArrayList<>();
	private static final ByteBuffer TILE_DATA = BufferUtils.createByteBuffer(TILE_WIDTH * TILE_HEIGHT * 4);
	private static final float[] sum = new float[VOXELS * 3];
	private static final int[] count = new int[VOXELS];
	private static final float[] sample = new float[3];

	private static int texture;

	private ShapeMasks() {
	}

	public static long bytes() {
		return texture == 0 ? 0L : (long) ATLAS_WIDTH * ATLAS_HEIGHT * 4L;
	}

	public static void clear() {
		BUCKETS.clear();
		STORED.clear();
	}

	public static void bind(int program) {
		if (texture == 0) {
			return;
		}
		int location = GL20.glGetUniformLocation(program, SAMPLER);
		if (location < 0) {
			return;
		}
		GL20.glUniform1i(location, UNIT);
		int previousUnit = GL11.glGetInteger(GL13.GL_ACTIVE_TEXTURE);
		GL13.glActiveTexture(GL13.GL_TEXTURE0 + UNIT);
		GL11.glBindTexture(GL11.GL_TEXTURE_2D, texture);
		GL13.glActiveTexture(previousUnit);
	}

	public static int idFor(BlockState state, BlockGetter level, BlockPos pos) {
		return classify(build(state, level, pos, LuceatConfig.get()));
	}

	public static float[] build(BlockState state, BlockGetter level, BlockPos pos, LuceatConfig config) {
		float[] mask = new float[VOXELS * 3];
		Arrays.fill(mask, 1.0F);
		boolean drawn = state.getRenderShape() == RenderShape.MODEL && rasterModel(state, mask, config);
		if (!drawn) {
			rasterShape(state.getCollisionShape(level, pos), mask);
		}
		return mask;
	}

	public static int voxelIndex(int x, int y, int z) {
		return x + RES * (y + RES * z);
	}

	private static boolean rasterModel(BlockState state, float[] mask, LuceatConfig config) {
		BlockStateModel model = Minecraft.getInstance().getModelManager().getBlockStateModelSet().get(state);
		List<BlockStateModelPart> parts = new ArrayList<>();
		model.collectParts(RandomSource.create(42L), parts);
		float opaque = 1.0F - Math.clamp(config.cutoutOpacity, 0.0F, 1.0F);
		boolean drawn = false;
		for (BlockStateModelPart part : parts) {
			drawn |= rasterQuads(part.getQuads(null), mask, opaque, config.glassTint);
			for (Direction side : SIDES) {
				drawn |= rasterQuads(part.getQuads(side), mask, opaque, config.glassTint);
			}
		}
		return drawn;
	}

	private static boolean rasterQuads(List<BakedQuad> quads, float[] mask, float opaque, boolean tint) {
		boolean drawn = false;
		for (BakedQuad quad : quads) {
			drawn |= rasterQuad(quad, mask, opaque, tint);
		}
		return drawn;
	}

	private static boolean rasterQuad(BakedQuad quad, float[] mask, float opaque, boolean tint) {
		TextureAtlasSprite atlas = quad.materialInfo().sprite();
		SpriteData sprite = SpriteData.of(atlas.contents().name());
		float u0 = atlas.getU0();
		float u1 = atlas.getU1();
		float v0 = atlas.getV0();
		float v1 = atlas.getV1();
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
		int stepsU = Math.min(MAX_STEPS, Math.max(2, (int) Math.ceil(lengthU * 4.0F) + 1));
		int stepsV = Math.min(MAX_STEPS, Math.max(2, (int) Math.ceil(lengthV * 4.0F) + 1));
		Arrays.fill(sum, 0.0F);
		Arrays.fill(count, 0);
		boolean touched = false;
		for (int j = 0; j < stepsV; j++) {
			float v = (j + 0.5F) / stepsV;
			for (int i = 0; i < stepsU; i++) {
				float u = (i + 0.5F) / stepsU;
				float x = bilinear(p, 0, u, v);
				float y = bilinear(p, 1, u, v);
				float z = bilinear(p, 2, u, v);
				if (x < -0.001F || x > 1.001F || y < -0.001F || y > 1.001F || z < -0.001F || z > 1.001F) {
					continue;
				}
				int vx = Math.min(RES - 1, Math.max(0, (int) Math.floor(x * RES)));
				int vy = Math.min(RES - 1, Math.max(0, (int) Math.floor(y * RES)));
				int vz = Math.min(RES - 1, Math.max(0, (int) Math.floor(z * RES)));
				float tu = bilinear(t, 2, 0, u, v);
				float tv = bilinear(t, 2, 1, u, v);
				int pixel = sprite.sample(Math.min(Math.max(tu, 0.0F), 0.9999F), Math.min(Math.max(tv, 0.0F), 0.9999F));
				sample[0] = 0.0F;
				sample[1] = 0.0F;
				sample[2] = 0.0F;
				PatternAtlas.pixel(pixel, opaque, tint, sample);
				int index = voxelIndex(vx, vy, vz);
				sum[index * 3] += sample[0];
				sum[index * 3 + 1] += sample[1];
				sum[index * 3 + 2] += sample[2];
				count[index]++;
				touched = true;
			}
		}
		if (!touched) {
			return false;
		}
		for (int index = 0; index < VOXELS; index++) {
			int n = count[index];
			if (n > 0) {
				mask[index * 3] *= sum[index * 3] / n;
				mask[index * 3 + 1] *= sum[index * 3 + 1] / n;
				mask[index * 3 + 2] *= sum[index * 3 + 2] / n;
			}
		}
		return true;
	}

	private static float bilinear(float[] p, int axis, float u, float v) {
		return bilinear(p, 3, axis, u, v);
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

	private static boolean rasterShape(VoxelShape shape, float[] mask) {
		if (shape.isEmpty()) {
			return false;
		}
		boolean[] drawn = {false};
		shape.forAllBoxes((x0, y0, z0, x1, y1, z1) -> {
			int ax = Math.max(0, (int) Math.floor(x0 * RES + 1.0E-6));
			int ay = Math.max(0, (int) Math.floor(y0 * RES + 1.0E-6));
			int az = Math.max(0, (int) Math.floor(z0 * RES + 1.0E-6));
			int bx = Math.min(RES - 1, (int) Math.ceil(x1 * RES - 1.0E-6) - 1);
			int by = Math.min(RES - 1, (int) Math.ceil(y1 * RES - 1.0E-6) - 1);
			int bz = Math.min(RES - 1, (int) Math.ceil(z1 * RES - 1.0E-6) - 1);
			for (int z = az; z <= bz; z++) {
				for (int y = ay; y <= by; y++) {
					for (int x = ax; x <= bx; x++) {
						int index = voxelIndex(x, y, z);
						mask[index * 3] = 0.0F;
						mask[index * 3 + 1] = 0.0F;
						mask[index * 3 + 2] = 0.0F;
						drawn[0] = true;
					}
				}
			}
		});
		return drawn[0];
	}

	public static boolean empty(float[] mask) {
		for (int i = 0; i < VOXELS; i++) {
			if (mask[i * 3] < 0.999F || mask[i * 3 + 1] < 0.999F || mask[i * 3 + 2] < 0.999F) {
				return false;
			}
		}
		return true;
	}

	public static boolean closed(float[] mask) {
		return closedBox(mask);
	}

	private static int classify(float[] mask) {
		if (empty(mask)) {
			return NONE;
		}
		if (closedBox(mask)) {
			return SOLID;
		}
		byte[] bytes = new byte[VOXELS * 4];
		for (int z = 0; z < RES; z++) {
			for (int y = 0; y < RES; y++) {
				for (int x = 0; x < RES; x++) {
					int index = voxelIndex(x, y, z);
					int at = (y * TILE_WIDTH + z * RES + x) * 4;
					bytes[at] = (byte) Math.round(Math.clamp(mask[index * 3], 0.0F, 1.0F) * 255.0F);
					bytes[at + 1] = (byte) Math.round(Math.clamp(mask[index * 3 + 1], 0.0F, 1.0F) * 255.0F);
					bytes[at + 2] = (byte) Math.round(Math.clamp(mask[index * 3 + 2], 0.0F, 1.0F) * 255.0F);
					bytes[at + 3] = (byte) -1;
				}
			}
		}
		int hash = Arrays.hashCode(bytes);
		List<Integer> bucket = BUCKETS.computeIfAbsent(hash, key -> new ArrayList<>(1));
		for (int known : bucket) {
			if (Arrays.equals(STORED.get(known), bytes)) {
				return FLAG | known;
			}
		}
		if (STORED.size() >= CAPACITY) {
			return SOLID;
		}
		int index = STORED.size();
		STORED.add(bytes);
		bucket.add(index);
		upload(index, bytes);
		return FLAG | index;
	}

	private static boolean closedBox(float[] mask) {
		for (int a = 0; a < RES; a++) {
			for (int b = 0; b < RES; b++) {
				if (!dark(mask, 0, a, b) || !dark(mask, RES - 1, a, b)
					|| !dark(mask, a, 0, b) || !dark(mask, a, RES - 1, b)
					|| !dark(mask, a, b, 0) || !dark(mask, a, b, RES - 1)) {
					return false;
				}
			}
		}
		return true;
	}

	private static boolean dark(float[] mask, int x, int y, int z) {
		int index = voxelIndex(x, y, z) * 3;
		return Math.max(mask[index], Math.max(mask[index + 1], mask[index + 2])) < 0.03F;
	}

	private static void upload(int index, byte[] bytes) {
		ensureTexture();
		TILE_DATA.clear();
		TILE_DATA.put(bytes);
		TILE_DATA.flip();
		int previousUnit = GL11.glGetInteger(GL13.GL_ACTIVE_TEXTURE);
		int previousAlignment = GL11.glGetInteger(GL11.GL_UNPACK_ALIGNMENT);
		int previousRow = GL11.glGetInteger(GL11.GL_UNPACK_ROW_LENGTH);
		int previousSkipRows = GL11.glGetInteger(GL11.GL_UNPACK_SKIP_ROWS);
		int previousSkipPixels = GL11.glGetInteger(GL11.GL_UNPACK_SKIP_PIXELS);
		GL11.glPixelStorei(GL11.GL_UNPACK_ALIGNMENT, 1);
		GL11.glPixelStorei(GL11.GL_UNPACK_ROW_LENGTH, 0);
		GL11.glPixelStorei(GL11.GL_UNPACK_SKIP_ROWS, 0);
		GL11.glPixelStorei(GL11.GL_UNPACK_SKIP_PIXELS, 0);
		GL13.glActiveTexture(GL13.GL_TEXTURE0 + UNIT);
		GL11.glBindTexture(GL11.GL_TEXTURE_2D, texture);
		GL11.glTexSubImage2D(GL11.GL_TEXTURE_2D, 0, (index % PER_ROW) * TILE_WIDTH, (index / PER_ROW) * TILE_HEIGHT, TILE_WIDTH, TILE_HEIGHT, GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, TILE_DATA);
		GL13.glActiveTexture(previousUnit);
		GL11.glPixelStorei(GL11.GL_UNPACK_ALIGNMENT, previousAlignment);
		GL11.glPixelStorei(GL11.GL_UNPACK_ROW_LENGTH, previousRow);
		GL11.glPixelStorei(GL11.GL_UNPACK_SKIP_ROWS, previousSkipRows);
		GL11.glPixelStorei(GL11.GL_UNPACK_SKIP_PIXELS, previousSkipPixels);
	}

	private static void ensureTexture() {
		if (texture != 0) {
			return;
		}
		int previousUnit = GL11.glGetInteger(GL13.GL_ACTIVE_TEXTURE);
		GL13.glActiveTexture(GL13.GL_TEXTURE0 + UNIT);
		texture = GL11.glGenTextures();
		GL11.glBindTexture(GL11.GL_TEXTURE_2D, texture);
		GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_NEAREST);
		GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_NEAREST);
		GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_S, GL12.GL_CLAMP_TO_EDGE);
		GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_T, GL12.GL_CLAMP_TO_EDGE);
		GL11.glTexImage2D(GL11.GL_TEXTURE_2D, 0, GL11.GL_RGBA8, ATLAS_WIDTH, ATLAS_HEIGHT, 0, GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, (ByteBuffer) null);
		GL13.glActiveTexture(previousUnit);
	}
}
