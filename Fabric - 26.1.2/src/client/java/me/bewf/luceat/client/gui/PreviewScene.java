package me.bewf.luceat.client.gui;

import me.bewf.luceat.client.config.LuceatConfig;
import me.bewf.luceat.client.config.ShadowQuality;

import net.minecraft.client.gui.GuiGraphicsExtractor;

public final class PreviewScene {
	public static final int WIDTH_BLOCKS = 14;
	public static final int DEPTH_BLOCKS = 8;
	private static final int SAMPLES = 4;
	private static final float LIGHT_HEIGHT = 1.4F;
	private static final float RADIUS = 14.0F;
	private static final float WALL_HEIGHT = 2.0F;
	private static final float[] LIGHT_COLOUR = {1.0F, 0.7F, 0.35F};
	private static final float[] GLASS_COLOUR = {1.0F, 0.22F, 0.22F};
	private static final boolean[][] LEAF_MASK = {
		{true, false, true, true},
		{true, true, false, true},
		{false, true, true, false},
		{true, true, false, true}
	};

	private static final int AIR = 0;
	private static final int STONE = 1;
	private static final int LEAVES = 2;
	private static final int GLASS = 3;

	private final int[][] blocks = new int[WIDTH_BLOCKS][DEPTH_BLOCKS];
	private float lightX = 3.5F;
	private float lightZ = 4.0F;

	public PreviewScene() {
		int[] column = {STONE, STONE, LEAVES, GLASS, AIR, LEAVES, STONE, STONE};
		for (int z = 0; z < DEPTH_BLOCKS; z++) {
			blocks[8][z] = column[z];
		}
	}

	public void moveLight(float blockX, float blockZ) {
		lightX = Math.max(0.5F, Math.min(WIDTH_BLOCKS - 0.5F, blockX));
		lightZ = Math.max(0.5F, Math.min(DEPTH_BLOCKS - 0.5F, blockZ));
	}

	public void render(GuiGraphicsExtractor graphics, int x, int y, int cell, float time) {
		LuceatConfig config = LuceatConfig.get();
		float[] ambient = ambient(time);
		float exposure = Math.max(ambient[0], Math.max(ambient[1], ambient[2]));
		float fade = 1.0F - config.daylightFade * smoothstep(0.25F, 0.95F, exposure);
		int width = WIDTH_BLOCKS * SAMPLES;
		int depth = DEPTH_BLOCKS * SAMPLES;
		for (int sz = 0; sz < depth; sz++) {
			for (int sx = 0; sx < width; sx++) {
				float worldX = (sx + 0.5F) / SAMPLES;
				float worldZ = (sz + 0.5F) / SAMPLES;
				int block = blocks[Math.min((int) worldX, WIDTH_BLOCKS - 1)][Math.min((int) worldZ, DEPTH_BLOCKS - 1)];
				int colour = block == AIR ? floor(worldX, worldZ, config, ambient, fade) : blockColour(block, sx, sz, ambient);
				graphics.fill(x + sx * cell, y + sz * cell, x + (sx + 1) * cell, y + (sz + 1) * cell, colour);
			}
		}
		int lx = x + Math.round(lightX * SAMPLES * cell);
		int lz = y + Math.round(lightZ * SAMPLES * cell);
		int size = Math.max(3, cell);
		graphics.fill(lx - size - 1, lz - size - 1, lx + size + 1, lz + size + 1, 0xFF000000);
		graphics.fill(lx - size, lz - size, lx + size, lz + size, 0xFFFFD27A);
	}

	private int floor(float worldX, float worldZ, LuceatConfig config, float[] ambient, float fade) {
		float dx = lightX - worldX;
		float dz = lightZ - worldZ;
		float dist = (float) Math.sqrt(dx * dx + dz * dz + LIGHT_HEIGHT * LIGHT_HEIGHT);
		float level = Math.max(RADIUS - dist, 0.0F) / 15.0F;
		float[] light = new float[3];
		if (level > 0.0F) {
			float ndotl = LIGHT_HEIGHT / dist;
			float facing = mixFloat(1.0F, smoothstep(-0.35F, 0.15F, ndotl), config.directionalShading);
			float lambert = mixFloat(1.0F, 0.4F + 0.6F * ndotl, config.directionalShading);
			float curved = level / (4.0F - 3.0F * level);
			float[] visible = {1.0F, 1.0F, 1.0F};
			if (config.shadows) {
				float[] through = visibility(worldX, worldZ, config);
				for (int i = 0; i < 3; i++) {
					visible[i] = mixFloat(1.0F, through[i], config.shadowStrength);
				}
			}
			for (int i = 0; i < 3; i++) {
				light[i] = LIGHT_COLOUR[i] * config.brightness * curved * lambert * facing * visible[i];
			}
		}
		float cap = Math.max(config.maxBrightness, 0.1F);
		int packed = 0xFF000000;
		for (int i = 0; i < 3; i++) {
			float lit = ambient[i] * 0.62F + 0.62F * soft(light[i], cap) * fade;
			packed |= Math.min(255, Math.round(lit * 255.0F)) << (16 - 8 * i);
		}
		return packed;
	}

	private float[] visibility(float sx, float sz, LuceatConfig config) {
		float dx = lightX - sx;
		float dz = lightZ - sz;
		float[] transmitted = {1.0F, 1.0F, 1.0F};
		if (dx * dx + dz * dz < 1.0E-6F) {
			return transmitted;
		}
		int cellX = (int) Math.floor(sx);
		int cellZ = (int) Math.floor(sz);
		int lightCellX = (int) Math.floor(lightX);
		int lightCellZ = (int) Math.floor(lightZ);
		int stepX = dx >= 0.0F ? 1 : -1;
		int stepZ = dz >= 0.0F ? 1 : -1;
		float deltaX = dx == 0.0F ? Float.MAX_VALUE : Math.abs(1.0F / dx);
		float deltaZ = dz == 0.0F ? Float.MAX_VALUE : Math.abs(1.0F / dz);
		float maxX = dx == 0.0F ? Float.MAX_VALUE : ((dx > 0.0F ? cellX + 1 : cellX) - sx) / dx;
		float maxZ = dz == 0.0F ? Float.MAX_VALUE : ((dz > 0.0F ? cellZ + 1 : cellZ) - sz) / dz;
		float entry = 0.0F;
		int entryAxis = 0;
		for (int i = 0; i < 64; i++) {
			float exit = Math.min(maxX, maxZ);
			int exitAxis = maxX <= maxZ ? 0 : 1;
			boolean own = cellX == lightCellX && cellZ == lightCellZ;
			int block = cellX >= 0 && cellX < WIDTH_BLOCKS && cellZ >= 0 && cellZ < DEPTH_BLOCKS ? blocks[cellX][cellZ] : AIR;
			if (!own && block != AIR) {
				float yEntry = LIGHT_HEIGHT * entry;
				float yExit = LIGHT_HEIGHT * Math.min(exit, 1.0F);
				if (Math.min(yEntry, yExit) < WALL_HEIGHT) {
					float[] through = passThrough(block, config, sx, sz, dx, dz, entry, entryAxis, yEntry);
					if (exit < 1.0F) {
						float[] other = passThrough(block, config, sx, sz, dx, dz, exit, exitAxis, yExit);
						for (int c = 0; c < 3; c++) {
							through[c] = Math.min(through[c], other[c]);
						}
					}
					for (int c = 0; c < 3; c++) {
						transmitted[c] *= through[c];
					}
					if (Math.max(transmitted[0], Math.max(transmitted[1], transmitted[2])) < 0.02F) {
						return new float[]{0.0F, 0.0F, 0.0F};
					}
				}
			}
			if (exit >= 1.0F) {
				break;
			}
			entry = exit;
			entryAxis = exitAxis;
			if (exitAxis == 0) {
				cellX += stepX;
				maxX += deltaX;
			} else {
				cellZ += stepZ;
				maxZ += deltaZ;
			}
		}
		return transmitted;
	}

	private float[] passThrough(int block, LuceatConfig config, float sx, float sz, float dx, float dz, float t, int axis, float y) {
		if (block == STONE) {
			return new float[]{0.0F, 0.0F, 0.0F};
		}
		if (block == GLASS) {
			if (config.glassTint) {
				return GLASS_COLOUR.clone();
			}
			return new float[]{0.85F, 0.85F, 0.85F};
		}
		float hole = 1.0F;
		float leaf = 1.0F - config.cutoutOpacity;
		if (config.foliageShadows == ShadowQuality.OFF) {
			return new float[]{0.0F, 0.0F, 0.0F};
		}
		float value;
		if (config.foliageShadows == ShadowQuality.FAST) {
			int holes = 0;
			for (boolean[] row : LEAF_MASK) {
				for (boolean solid : row) {
					holes += solid ? 0 : 1;
				}
			}
			float share = holes / 16.0F;
			value = share * hole + (1.0F - share) * leaf;
		} else {
			float along = axis == 0 ? sz + dz * t : sx + dx * t;
			float u = along - (float) Math.floor(along);
			float local = Math.min(y, WALL_HEIGHT - 0.001F);
			float v = local - (float) Math.floor(local);
			boolean solid = LEAF_MASK[Math.min(3, (int) (v * 4.0F))][Math.min(3, (int) (u * 4.0F))];
			value = solid ? leaf : hole;
		}
		return new float[]{value, value, value};
	}

	private int blockColour(int block, int sx, int sz, float[] ambient) {
		float shade = 0.35F + 0.65F * Math.max(ambient[0], Math.max(ambient[1], ambient[2]));
		int red;
		int green;
		int blue;
		if (block == STONE) {
			boolean edge = sx % SAMPLES == 0 || sz % SAMPLES == 0;
			int base = edge ? 92 : 108;
			red = base;
			green = base;
			blue = base + 6;
		} else if (block == GLASS) {
			red = 210;
			green = 70;
			blue = 80;
		} else {
			boolean solid = LEAF_MASK[Math.floorMod(sz, SAMPLES)][Math.floorMod(sx, SAMPLES)];
			red = solid ? 58 : 20;
			green = solid ? 128 : 44;
			blue = solid ? 52 : 24;
		}
		return 0xFF000000 | Math.round(red * shade) << 16 | Math.round(green * shade) << 8 | Math.round(blue * shade);
	}

	private static float[] ambient(float time) {
		float[] night = {0.06F, 0.07F, 0.12F};
		float[] day = {0.95F, 0.96F, 1.0F};
		float[] result = new float[3];
		for (int i = 0; i < 3; i++) {
			result[i] = night[i] + (day[i] - night[i]) * time;
		}
		return result;
	}

	private static float soft(float x, float cap) {
		float knee = cap * 0.7F;
		float over = Math.max(x - knee, 0.0F);
		return Math.min(x, knee) + (cap - knee) * (1.0F - (float) Math.exp(-over / (cap - knee)));
	}

	private static float smoothstep(float edge0, float edge1, float x) {
		float t = Math.max(0.0F, Math.min(1.0F, (x - edge0) / (edge1 - edge0)));
		return t * t * (3.0F - 2.0F * t);
	}

	private static float mixFloat(float a, float b, float amount) {
		return a + (b - a) * amount;
	}
}
