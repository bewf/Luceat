package me.bewf.luceat.client;

import me.bewf.luceat.Luceat;
import me.bewf.luceat.client.config.LuceatConfig;
import me.bewf.luceat.client.light.item.ItemLightRules;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.resource.v1.ResourceLoader;
import net.minecraft.server.packs.PackType;

public class LuceatClient implements ClientModInitializer {
	@Override
	public void onInitializeClient() {
		LuceatConfig.load();
		ResourceLoader.get(PackType.CLIENT_RESOURCES).registerReloadListener(ItemLightRules.ID, new ItemLightRules());
		Luceat.LOGGER.info("Luceat client loaded");
	}
}
