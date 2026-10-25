
package me.bewf.luceat.client.extras;

import java.util.UUID;

import me.bewf.luceat.client.config.LuceatConfig;

import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.TextColor;

public final class Sparkle {
	private static final UUID PLAYER_UUID = UUID.fromString("f299854c-d695-4a3c-baae-6ab0e4b62dc8");

	private static final String PREFIX = "your lord and saviour ";

	private static final int BASE_COLOR = 0xBE3FFF;
	private static final int SHINE_COLOR = 0xFFF5FF;

	private static final long CACHE_MS = 50L;
	private static final long PERIOD_MS = 2400L;
	private static final long SHINE_MS = 480L;

	private static final float SHINE_RADIUS = 1.5F;

	private static Component cached;
	private static String cachedName;
	private static long builtAt;

	private Sparkle() {
	}

	public static boolean wants(UUID uuid) {
		if (!PLAYER_UUID.equals(uuid)) {
			return false;
		}

		LuceatConfig config = LuceatConfig.get();
		return config.enabled && config.fancyNames;
	}

	public static Component name(String playerName) {
		long now = System.currentTimeMillis();

		if (cached != null
				&& playerName.equals(cachedName)
				&& now - builtAt < CACHE_MS) {
			return cached;
		}

		String text = PREFIX + playerName;
		MutableComponent out = Component.empty();
		int length = text.length();

		long phase = now % PERIOD_MS;
		boolean shining = phase < SHINE_MS;

		float progress = shining
				? phase / (float) SHINE_MS
				: 0.0F;

		float center = -SHINE_RADIUS
				+ progress * (length + SHINE_RADIUS * 2.0F);

		for (int i = 0; i < length; i++) {
			int color = BASE_COLOR;

			if (shining) {
				float distance = Math.abs(i - center);
				float strength = Math.max(
						0.0F,
						1.0F - distance / SHINE_RADIUS
				);

				strength = strength * strength * (3.0F - 2.0F * strength);
				color = blend(BASE_COLOR, SHINE_COLOR, strength);
			}

			out.append(
					Component.literal(String.valueOf(text.charAt(i)))
							.setStyle(Style.EMPTY.withColor(TextColor.fromRgb(color)))
			);
		}

		cached = out;
		cachedName = playerName;
		builtAt = now;

		return out;
	}

	private static int blend(int base, int shine, float amount) {
		int r = mix((base >> 16) & 255, (shine >> 16) & 255, amount);
		int g = mix((base >> 8) & 255, (shine >> 8) & 255, amount);
		int b = mix(base & 255, shine & 255, amount);

		return (r << 16) | (g << 8) | b;
	}

	private static int mix(int a, int b, float amount) {
		return Math.round(a + (b - a) * amount);
	}
}
