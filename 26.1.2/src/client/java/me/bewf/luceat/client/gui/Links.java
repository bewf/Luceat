package me.bewf.luceat.client.gui;

import java.net.URI;

import net.minecraft.util.Util;

public final class Links {
	public static final String MOD = "https://modrinth.com/mod/luceat";
	public static final String AUTHOR = "https://modrinth.com/user/bewf";
	public static final String ISSUES = "https://github.com/bewf/Luceat/issues/new";
	public static final String DONATE = "https://ko-fi.com/bewfontop";

	private Links() {
	}

	public static boolean open(String url) {
		try {
			Util.getPlatform().openUri(URI.create(url));
			return true;
		} catch (RuntimeException error) {
			return false;
		}
	}
}
