package me.bewf.luceat.client.light;

import java.util.Arrays;

import com.mojang.blaze3d.platform.NativeImage;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.client.resources.model.sprite.Material;
import net.minecraft.resources.Identifier;
import net.minecraft.util.ARGB;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

public final class ColourEstimator {
	private static final int MIN_ALPHA = 128;
	private static final float TOP_FRACTION = 0.12F;

	private ColourEstimator() {
	}

	public static float[] estimate(ItemStack stack) {
		return estimateTexture(textureOf(stack));
	}

	public static float[] estimateBlock(BlockState state) {
		if (state.is(Blocks.LAVA)) {
			return estimateTexture(Identifier.withDefaultNamespace("block/lava_still"));
		}
		Material.Baked material = Minecraft.getInstance().getModelManager().getBlockStateModelSet().getParticleMaterial(state);
		return material == null ? null : estimateTexture(material.sprite().contents().name());
	}

	public static float[] estimateTexture(Identifier texture) {
		if (texture == null || texture.getPath().equals("missingno")) {
			return null;
		}
		try (NativeImage image = TextureFiles.read(texture)) {
			if (image == null) {
				return null;
			}
			float[] fromPack = packEmissive(texture, image);
			return fromPack != null ? fromPack : glowColour(image);
		}
	}

	private static float[] packEmissive(Identifier texture, NativeImage albedo) {
		float[] optifine = emissiveColour(texture, albedo, "_e", false);
		return optifine != null ? optifine : emissiveColour(texture, albedo, "_s", true);
	}

	private static float[] emissiveColour(Identifier texture, NativeImage albedo, String suffix, boolean alphaChannel) {
		Identifier companion = Identifier.fromNamespaceAndPath(texture.getNamespace(), texture.getPath() + suffix);
		try (NativeImage mask = TextureFiles.read(companion)) {
			if (mask == null) {
				return null;
			}
			float red = 0.0F;
			float green = 0.0F;
			float blue = 0.0F;
			float total = 0.0F;
			for (int y = 0; y < mask.getHeight(); y++) {
				for (int x = 0; x < mask.getWidth(); x++) {
					int argb = mask.getPixel(x, y);
					float weight;
					if (alphaChannel) {
						int alpha = ARGB.alpha(argb);
						weight = alpha >= 255 ? 0.0F : alpha / 254.0F;
					} else {
						weight = ARGB.alpha(argb) < MIN_ALPHA ? 0.0F : Math.max(ARGB.red(argb), Math.max(ARGB.green(argb), ARGB.blue(argb))) / 255.0F;
					}
					if (weight <= 0.0F) {
						continue;
					}
					int source = albedo.getPixel(x * albedo.getWidth() / mask.getWidth(), y * albedo.getHeight() / mask.getHeight());
					if (ARGB.alpha(source) < MIN_ALPHA) {
						continue;
					}
					red += weight * ARGB.red(source) / 255.0F;
					green += weight * ARGB.green(source) / 255.0F;
					blue += weight * ARGB.blue(source) / 255.0F;
					total += weight;
				}
			}
			float peak = Math.max(red, Math.max(green, blue));
			if (total <= 0.0F || peak <= 0.0F) {
				return null;
			}
			return new float[]{red / peak, green / peak, blue / peak};
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
