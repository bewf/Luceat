package me.bewf.luceat.client;

import me.bewf.luceat.Luceat;

import net.fabricmc.api.ClientModInitializer;

public class LuceatClient implements ClientModInitializer {
	@Override
	public void onInitializeClient() {
		Luceat.LOGGER.info("Luceat client loaded");
	}
}
