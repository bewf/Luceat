package me.bewf.luceat.client.gui;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FontDescription;
import net.minecraft.network.chat.Style;
import net.minecraft.resources.Identifier;

public final class LuceatFonts {
	private static final Style TITLE = styled("title");
	private static final Style BODY = styled("body");
	private static final Style QUOTE = styled("quote");

	private LuceatFonts() {
	}

	private static Style styled(String name) {
		return Style.EMPTY.withFont(new FontDescription.Resource(Identifier.fromNamespaceAndPath("luceat", name)));
	}

	public static Component title(String text) {
		return Component.literal(text).withStyle(TITLE);
	}

	public static Component body(String text) {
		return Component.literal(text).withStyle(BODY);
	}

	public static Component quote(String text) {
		return Component.literal(text).withStyle(QUOTE);
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
