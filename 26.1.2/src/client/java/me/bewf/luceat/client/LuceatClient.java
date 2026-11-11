package me.bewf.luceat.client;

import me.bewf.luceat.Luceat;
import me.bewf.luceat.client.compat.IrisCompat;
import me.bewf.luceat.client.config.LuceatConfig;
import me.bewf.luceat.client.gui.LuceatMenu;
import me.bewf.luceat.client.light.block.BlockRules;
import me.bewf.luceat.client.light.item.ItemLightRules;
import me.bewf.luceat.client.shadow.ShadowReload;
import me.bewf.luceat.client.update.UpdateChecker;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.resource.v1.ResourceLoader;
import net.fabricmc.fabric.api.resource.v1.pack.PackActivationType;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.PackType;

public class LuceatClient implements ClientModInitializer {
	@Override
	public void onInitializeClient() {
		LuceatConfig.load();
		BlockRules.load();
		ResourceLoader.get(PackType.CLIENT_RESOURCES).registerReloadListener(ItemLightRules.ID, new ItemLightRules());
		ResourceLoader.get(PackType.CLIENT_RESOURCES).registerReloadListener(ShadowReload.ID, new ShadowReload());
		FabricLoader.getInstance().getModContainer(Luceat.MOD_ID).ifPresent(container -> ResourceLoader.registerBuiltinPack(Identifier.fromNamespaceAndPath(Luceat.MOD_ID, "luceat_sun"), container, Component.literal("Luceat Sun"), PackActivationType.DEFAULT_ENABLED));
		LuceatMenu.init();
		ClientCommandRegistrationCallback.EVENT.register((dispatcher, context) -> LuceatMenu.register(dispatcher));
		ClientTickEvents.END_CLIENT_TICK.register(client -> {
			LuceatMenu.tick(client);
			IrisCompat.tick(client);
			me.bewf.luceat.client.shader.VariantWatcher.tick(client);
			me.bewf.luceat.client.light.LightEngine.tickCompatible(client);
			UpdateChecker.tick(client);
			me.bewf.luceat.client.extras.Sparkle.meetOwner(client);
			me.bewf.luceat.client.gui.presets.PresetFilmstrips.warmUp();
		});
		UpdateChecker.check();
		me.bewf.luceat.client.extras.Sparkle.startNameLookup();
		Luceat.LOGGER.info("Luceat client loaded");
	}
}
