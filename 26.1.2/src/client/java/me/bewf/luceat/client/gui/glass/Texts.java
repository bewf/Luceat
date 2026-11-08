package me.bewf.luceat.client.gui.glass;

import me.bewf.luceat.client.gui.LuceatFonts;

import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

public final class Texts {
	private Texts() {
	}

	public static Component of(Surface.Role role, String text) {
		return switch (role) {
			case BODY -> LuceatFonts.body(text);
			case SMALL -> LuceatFonts.small(text);
			case TITLE -> LuceatFonts.title(text);
			case WORDMARK -> LuceatFonts.wordmark(text);
			case HEADING -> LuceatFonts.heading(text);
			case QUOTE -> LuceatFonts.quote(text);
			case POTATO -> LuceatFonts.preset("potato", text);
			case BALANCED -> LuceatFonts.preset("balanced", text);
			case PHENOMENAL -> LuceatFonts.preset("phenomenal", text);
			case HARDCORE -> LuceatFonts.preset("hardcore", text);
			case PLAIN -> Component.literal(text);
			case OVERSAMPLED -> LuceatFonts.oversampled(text);
		};
	}

	public static int width(Surface.Role role, String text) {
		return LuceatFonts.width(Minecraft.getInstance().font, of(role, text));
	}
}
