package me.bewf.luceat.client.gui;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FontDescription;
import net.minecraft.network.chat.Style;
import net.minecraft.resources.Identifier;

public final class LuceatFonts {
	private static final int MAX_SCALE = 8;
	private static final Map<String, Style> STYLES = new HashMap<>();

	private LuceatFonts() {
	}

	private static Style styled(String name) {
		int scale = Math.max(1, Math.min(MAX_SCALE, GuiFit.raster()));
		return STYLES.computeIfAbsent(name + "_" + scale, key -> Style.EMPTY.withFont(new FontDescription.Resource(Identifier.fromNamespaceAndPath("luceat", key))));
	}

	public static Component title(String text) {
		return Component.literal(text).withStyle(styled("title"));
	}

	public static Component wordmark(String text) {
		return Component.literal(text).withStyle(styled("wordmark"));
	}

	public static Component heading(String text) {
		return Component.literal(text).withStyle(styled("heading"));
	}

	public static Component preset(String preset, String text) {
		String name = switch (preset.toLowerCase(Locale.ROOT)) {
			case "potato" -> "preset_potato";
			case "balanced" -> "preset_balanced";
			case "phenomenal" -> "preset_phenomenal";
			case "hardcore" -> "preset_hardcore";
			default -> "title";
		};
		return Component.literal(text).withStyle(styled(name));
	}

	public static Component small(String text) {
		return Component.literal(text).withStyle(styled("small"));
	}

	public static Component body(String text) {
		return Component.literal(text).withStyle(styled("body"));
	}

	public static Component oversampled(String text) {
		return Component.literal(text).withStyle(styled("test"));
	}

	public static Component quote(String text) {
		return Component.literal(text).withStyle(styled("quote"));
	}

	public static int width(Font font, Component text) {
		return font.width(text.getVisualOrderText());
	}

	public static void draw(GuiGraphicsExtractor graphics, Font font, Component text, int x, int y, int colour, boolean shadow) {
		graphics.text(font, text.getVisualOrderText(), x, y, colour, shadow);
	}

	public static void centred(GuiGraphicsExtractor graphics, Font font, Component text, int centreX, int y, int colour, boolean shadow) {
		draw(graphics, font, text, centreX - width(font, text) / 2, y, colour, shadow);
	}
}
