package me.bewf.luceat.client.gui;

import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;

public final class Say {
	public static final int LABEL = 0x9A9AA6;
	public static final int TEXT = 0xD6D6DE;
	public static final int VALUE = 0x6BE38E;
	public static final int WARN = 0xFFC857;
	public static final int BAD = 0xFF7A7A;

	private Say() {
	}

	public static MutableComponent prefix() {
		return Component.literal("[Luceat] ").withStyle(Style.EMPTY.withColor(LuceatLook.ACCENT & 0xFFFFFF));
	}

	public static MutableComponent text(String value) {
		return Component.literal(value).withStyle(Style.EMPTY.withColor(TEXT));
	}

	public static MutableComponent colour(String value, int rgb) {
		return Component.literal(value).withStyle(Style.EMPTY.withColor(rgb));
	}

	public static MutableComponent stat(String label, String value) {
		return Component.empty().append(colour(label + " ", LABEL)).append(colour(value, VALUE));
	}

	public static MutableComponent line(MutableComponent... parts) {
		MutableComponent out = prefix();
		for (MutableComponent part : parts) {
			out.append(part);
		}
		return out;
	}

	public static MutableComponent gap() {
		return colour("  ", LABEL);
	}

	public static void chat(MutableComponent message) {
		Minecraft minecraft = Minecraft.getInstance();
		if (minecraft.gui != null) {
			minecraft.gui.getChat().addClientSystemMessage(message);
		}
	}
}
