package me.bewf.luceat.client.shadow;

import java.nio.ByteBuffer;
import java.util.HashMap;
import java.util.Map;

import com.mojang.blaze3d.platform.NativeImage;

import me.bewf.luceat.client.config.LuceatConfig;
import me.bewf.luceat.client.config.ShadowQuality;
import me.bewf.luceat.client.light.TextureFiles;

import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL12;
import org.lwjgl.opengl.GL13;
import org.lwjgl.opengl.GL20;

import net.minecraft.resources.Identifier;
import net.minecraft.util.ARGB;

public final class PatternAtlas {
	public static final int OPAQUE = 1;
	public static final int FIRST = 2;
	public static final int TILE = 16;
	public static final int TILES_PER_ROW = 64;
	public static final int SIZE = TILE * TILES_PER_ROW;
	public static final String SAMPLER = "u_LuceatPatterns";

	private static final int UNIT = 15;
	private static final int CAPACITY = TILES_PER_ROW * TILES_PER_ROW;
	private static final Map<Identifier, Integer> IDS = new HashMap<>();
	private static final ByteBuffer TILE_DATA = BufferUtils.createByteBuffer(TILE * TILE * 4);

	private static int texture;
	private static int next = FIRST;

	private PatternAtlas() {
	}

	public static long bytes() {
		return texture == 0 ? 0L : (long) SIZE * SIZE * 4L;
	}

	public static void clear() {
		IDS.clear();
		next = FIRST;
	}

	public static int idFor(Identifier sprite) {
		Integer known = IDS.get(sprite);
		if (known != null) {
			return known;
		}
		int id = build(sprite);
		IDS.put(sprite, id);
		return id;
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

	private static int build(Identifier sprite) {
		if (next >= CAPACITY) {
			return OPAQUE;
		}
		try (NativeImage image = TextureFiles.read(sprite)) {
			if (image == null) {
				return OPAQUE;
			}
			ensureTexture();
			float[] mean = fill(image);
			int index = next - FIRST;
			upload(index % TILES_PER_ROW, index / TILES_PER_ROW);
			Transmit.set(next, mean[0], mean[1], mean[2]);
			return next++;
		}
	}

	private static float[] fill(NativeImage image) {
		float[] data = compute(image, LuceatConfig.get());
		float[] mean = new float[3];
		TILE_DATA.clear();
		for (int i = 0; i < TILE * TILE; i++) {
			for (int c = 0; c < 3; c++) {
				float value = Math.clamp(data[i * 3 + c], 0.0F, 1.0F);
				mean[c] += value / (TILE * TILE);
				TILE_DATA.put((byte) Math.round(value * 255.0F));
			}
			TILE_DATA.put((byte) -1);
		}
		TILE_DATA.flip();
		return mean;
	}

	public static float[] cpuTile(Identifier sprite, LuceatConfig config) {
		try (NativeImage image = TextureFiles.read(sprite)) {
			return image == null ? null : compute(image, config);
		}
	}

	private static float[] compute(NativeImage image, LuceatConfig config) {
		int width = image.getWidth();
		int height = Math.min(image.getHeight(), width);
		float opaqueBlock = 1.0F - Math.clamp(config.cutoutOpacity, 0.0F, 1.0F);
		boolean tint = config.glassTint;
		float[][] tile = new float[TILE * TILE][3];
		float[] sum = new float[3];
		for (int ty = 0; ty < TILE; ty++) {
			for (int tx = 0; tx < TILE; tx++) {
				int x0 = tx * width / TILE;
				int x1 = Math.max(x0 + 1, (tx + 1) * width / TILE);
				int y0 = ty * height / TILE;
				int y1 = Math.max(y0 + 1, (ty + 1) * height / TILE);
				float[] average = tile[tx + ty * TILE];
				int count = 0;
				for (int y = y0; y < y1; y++) {
					for (int x = x0; x < x1; x++) {
						pixel(image.getPixel(Math.min(x, width - 1), Math.min(y, height - 1)), opaqueBlock, tint, average);
						count++;
					}
				}
				for (int c = 0; c < 3; c++) {
					average[c] /= count;
					sum[c] += average[c];
				}
			}
		}
		boolean uniform = config.foliageShadows == ShadowQuality.FAST;
		if (!uniform) {
			soften(tile);
		}
		float[] data = new float[TILE * TILE * 3];
		for (int i = 0; i < TILE * TILE; i++) {
			for (int c = 0; c < 3; c++) {
				data[i * 3 + c] = uniform ? sum[c] / (TILE * TILE) : tile[i][c];
			}
		}
		return data;
	}

	private static void soften(float[][] tile) {
		float[][] source = new float[tile.length][];
		for (int i = 0; i < tile.length; i++) {
			source[i] = tile[i].clone();
		}
		for (int y = 0; y < TILE; y++) {
			for (int x = 0; x < TILE; x++) {
				float[] out = tile[x + y * TILE];
				for (int c = 0; c < 3; c++) {
					float total = 0.0F;
					for (int dy = -1; dy <= 1; dy++) {
						for (int dx = -1; dx <= 1; dx++) {
							int sx = Math.min(TILE - 1, Math.max(0, x + dx));
							int sy = Math.min(TILE - 1, Math.max(0, y + dy));
							float weight = (dx == 0 ? 2.0F : 1.0F) * (dy == 0 ? 2.0F : 1.0F);
							total += source[sx + sy * TILE][c] * weight;
						}
					}
					out[c] = total / 16.0F;
				}
			}
		}
	}

	static void pixel(int argb, float opaqueBlock, boolean tint, float[] out) {
		float alpha = ARGB.alpha(argb) / 255.0F;
		if (alpha <= 0.0F) {
			out[0] += 1.0F;
			out[1] += 1.0F;
			out[2] += 1.0F;
		} else if (alpha >= 0.999F) {
			out[0] += opaqueBlock;
			out[1] += opaqueBlock;
			out[2] += opaqueBlock;
		} else if (tint) {
			out[0] += 1.0F - alpha + alpha * ARGB.red(argb) / 255.0F;
			out[1] += 1.0F - alpha + alpha * ARGB.green(argb) / 255.0F;
			out[2] += 1.0F - alpha + alpha * ARGB.blue(argb) / 255.0F;
		} else {
			float clear = 1.0F - alpha;
			out[0] += clear;
			out[1] += clear;
			out[2] += clear;
		}
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
		GL11.glTexImage2D(GL11.GL_TEXTURE_2D, 0, GL11.GL_RGBA8, SIZE, SIZE, 0, GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, (ByteBuffer) null);
		GL13.glActiveTexture(previousUnit);
	}

	private static void upload(int tileX, int tileY) {
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
		GL11.glTexSubImage2D(GL11.GL_TEXTURE_2D, 0, tileX * TILE, tileY * TILE, TILE, TILE, GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, TILE_DATA);
		GL13.glActiveTexture(previousUnit);
		GL11.glPixelStorei(GL11.GL_UNPACK_ALIGNMENT, previousAlignment);
		GL11.glPixelStorei(GL11.GL_UNPACK_ROW_LENGTH, previousRow);
		GL11.glPixelStorei(GL11.GL_UNPACK_SKIP_ROWS, previousSkipRows);
		GL11.glPixelStorei(GL11.GL_UNPACK_SKIP_PIXELS, previousSkipPixels);
	}
}
