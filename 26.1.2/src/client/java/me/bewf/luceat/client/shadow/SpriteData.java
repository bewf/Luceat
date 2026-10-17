package me.bewf.luceat.client.shadow;

import java.util.HashMap;
import java.util.Map;

import com.mojang.blaze3d.platform.NativeImage;

import me.bewf.luceat.client.light.TextureFiles;

import net.minecraft.resources.Identifier;

public final class SpriteData {
	private static final Map<Identifier, SpriteData> CACHE = new HashMap<>();
	private static final SpriteData SOLID = new SpriteData(1, 1, new int[]{0xFF000000});

	private final int width;
	private final int height;
	private final int[] argb;

	private SpriteData(int width, int height, int[] argb) {
		this.width = width;
		this.height = height;
		this.argb = argb;
	}

	public static void clear() {
		CACHE.clear();
	}

	public static SpriteData of(Identifier name) {
		SpriteData known = CACHE.get(name);
		if (known != null) {
			return known;
		}
		SpriteData made = load(name);
		CACHE.put(name, made);
		return made;
	}

	private static SpriteData load(Identifier name) {
		try (NativeImage image = TextureFiles.read(name)) {
			if (image == null) {
				return SOLID;
			}
			int width = image.getWidth();
			int height = Math.min(image.getHeight(), width);
			int[] pixels = new int[width * height];
			for (int y = 0; y < height; y++) {
				for (int x = 0; x < width; x++) {
					pixels[x + y * width] = image.getPixel(x, y);
				}
			}
			return new SpriteData(width, height, pixels);
		}
	}

	public int width() {
		return width;
	}

	public int height() {
		return height;
	}

	public int pixel(int x, int y) {
		return argb[Math.min(Math.max(x, 0), width - 1) + Math.min(Math.max(y, 0), height - 1) * width];
	}

	public int sample(float u, float v) {
		return pixel((int) Math.floor(u * width), (int) Math.floor(v * height));
	}
}
