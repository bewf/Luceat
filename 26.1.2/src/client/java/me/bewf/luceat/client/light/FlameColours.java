package me.bewf.luceat.client.light;

import java.util.Map;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.block.state.BlockState;

import me.bewf.luceat.client.config.LuceatConfig;

public final class FlameColours {
	public static final float[] WARM = {1.0F, 0.7F, 0.35F};
	public static final float[] SOUL = {0.3F, 0.8F, 1.0F};

	private static final Map<String, float[]> DYES = Map.ofEntries(
		Map.entry("white", new float[]{1.0F, 0.95F, 0.85F}),
		Map.entry("orange", new float[]{1.0F, 0.55F, 0.1F}),
		Map.entry("magenta", new float[]{0.9F, 0.3F, 0.85F}),
		Map.entry("light_blue", new float[]{0.35F, 0.7F, 1.0F}),
		Map.entry("yellow", new float[]{1.0F, 0.9F, 0.2F}),
		Map.entry("lime", new float[]{0.5F, 0.95F, 0.2F}),
		Map.entry("pink", new float[]{1.0F, 0.5F, 0.7F}),
		Map.entry("gray", new float[]{0.6F, 0.6F, 0.65F}),
		Map.entry("light_gray", new float[]{0.8F, 0.8F, 0.82F}),
		Map.entry("cyan", new float[]{0.15F, 0.85F, 0.85F}),
		Map.entry("purple", new float[]{0.6F, 0.25F, 0.95F}),
		Map.entry("blue", new float[]{0.2F, 0.35F, 1.0F}),
		Map.entry("brown", new float[]{0.7F, 0.4F, 0.2F}),
		Map.entry("green", new float[]{0.2F, 0.8F, 0.25F}),
		Map.entry("red", new float[]{1.0F, 0.15F, 0.15F}),
		Map.entry("black", new float[]{0.45F, 0.4F, 0.6F})
	);

	private FlameColours() {
	}

	public static float[] of(BlockState state, boolean fromTexture) {
		String path = BuiltInRegistries.BLOCK.getKey(state.getBlock()).getPath();
		if (path.contains("candle")) {
			return candle(path);
		}
		if (path.contains("campfire")) {
			boolean soul = path.contains("soul");
			if (fromTexture) {
				float[] flame = ColourEstimator.estimateTexture(Identifier.withDefaultNamespace(soul ? "block/soul_campfire_fire" : "block/campfire_fire"));
				if (flame != null && (!soul || flame[2] > flame[0])) {
					return flame;
				}
			}
			return soul ? SOUL : WARM;
		}
		if (path.contains("soul")) {
			float[] estimate = fromTexture ? ColourEstimator.estimateBlock(state) : null;
			return estimate != null && estimate[2] > estimate[0] ? estimate : SOUL;
		}
		return null;
	}

	private static float[] candle(String path) {
		if (!LuceatConfig.get().candleColours) {
			return WARM;
		}
		int at = path.indexOf("_candle");
		if (at < 0) {
			return WARM;
		}
		return DYES.getOrDefault(path.substring(0, at), WARM);
	}
}
