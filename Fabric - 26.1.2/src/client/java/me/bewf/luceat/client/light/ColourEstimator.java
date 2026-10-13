package me.bewf.luceat.client.light;

import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;

import com.mojang.blaze3d.platform.NativeImage;

import me.bewf.luceat.Luceat;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.client.resources.model.sprite.Material;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.util.ARGB;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;

public final class ColourEstimator {
	private static final int MIN_ALPHA = 128;
	private static final float TOP_FRACTION = 0.12F;

	private ColourEstimator() {
	}

	public static float[] estimate(ItemStack stack) {
		Identifier texture = textureOf(stack);
		if (texture == null) {
			return null;
		}
		Identifier file = Identifier.fromNamespaceAndPath(texture.getNamespace(), "textures/" + texture.getPath() + ".png");
		Minecraft minecraft = Minecraft.getInstance();
		try {
			Resource resource = minecraft.getResourceManager().getResource(file).orElse(null);
			if (resource == null) {
				return null;
			}
			try (InputStream stream = resource.open(); NativeImage image = NativeImage.read(stream)) {
				return glowColour(image);
			}
		} catch (IOException | RuntimeException e) {
			Luceat.LOGGER.warn("Could not read texture {}", file, e);
			return null;
		}
	}

	private static Identifier textureOf(ItemStack stack) {
		Minecraft minecraft = Minecraft.getInstance();
		ItemStackRenderState state = new ItemStackRenderState();
		minecraft.getItemModelResolver().updateForTopItem(state, stack, ItemDisplayContext.GUI, minecraft.level, null, 0);
		Material.Baked material = state.pickParticleMaterial(RandomSource.create(0L));
		return material == null ? null : material.sprite().contents().name();
	}

	private static float[] glowColour(NativeImage image) {
		int width = image.getWidth();
		int height = image.getHeight();
		float[][] pixels = new float[width * height][];
		int count = 0;
		for (int y = 0; y < height; y++) {
			for (int x = 0; x < width; x++) {
				int argb = image.getPixel(x, y);
				if (ARGB.alpha(argb) < MIN_ALPHA) {
					continue;
				}
				float red = ARGB.red(argb) / 255.0F;
				float green = ARGB.green(argb) / 255.0F;
				float blue = ARGB.blue(argb) / 255.0F;
				float max = Math.max(red, Math.max(green, blue));
				float min = Math.min(red, Math.min(green, blue));
				float saturation = max <= 0.0F ? 0.0F : (max - min) / max;
				float weight = max * max * (0.35F + saturation);
				pixels[count++] = new float[]{weight, red, green, blue};
			}
		}
		if (count == 0) {
			return null;
		}
		Arrays.sort(pixels, 0, count, (a, b) -> Float.compare(b[0], a[0]));
		int used = Math.max(1, Math.round(count * TOP_FRACTION));
		float red = 0.0F;
		float green = 0.0F;
		float blue = 0.0F;
		for (int i = 0; i < used; i++) {
			red += pixels[i][1];
			green += pixels[i][2];
			blue += pixels[i][3];
		}
		float peak = Math.max(red, Math.max(green, blue));
		if (peak <= 0.0F) {
			return null;
		}
		return new float[]{red / peak, green / peak, blue / peak};
	}
}
