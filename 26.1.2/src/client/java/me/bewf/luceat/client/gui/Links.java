package me.bewf.luceat.client.gui;

import java.net.URI;

import net.minecraft.client.Minecraft;

public final class Links {
	public static final String MOD = "https://modrinth.com/mod/luceat";
	public static final String AUTHOR = "https://modrinth.com/user/bewf";
	public static final String ISSUES = "https://github.com/bewf/Luceat/issues/new";

	private Links() {
	}

	public static boolean open(String url) {
		try {
			Class<?> util = Class.forName("net.minecraft.Util");
			Object platform = util.getMethod("getPlatform").invoke(null);
			platform.getClass().getMethod("openUri", URI.class).invoke(platform, URI.create(url));
			return true;
		} catch (ReflectiveOperationException | RuntimeException error) {
			Minecraft.getInstance().keyboardHandler.setClipboard(url);
			return false;
		}
	}
}
