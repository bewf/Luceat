package me.bewf.luceat.client.compat;

import me.bewf.luceat.client.gui.LuceatScreen;

import net.caffeinemc.mods.sodium.api.config.ConfigEntryPoint;
import net.caffeinemc.mods.sodium.api.config.structure.ConfigBuilder;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

public final class LuceatSodiumConfig implements ConfigEntryPoint {
	@Override
	public void registerConfigLate(ConfigBuilder builder) {
		builder.registerOwnModOptions()
			.setName("Luceat")
			.setNonTintedIcon(Identifier.fromNamespaceAndPath("luceat", "textures/gui/logo_small.png"))
			.addPage(builder.createExternalPage()
				.setName(Component.literal("Luceat settings"))
				.setScreenConsumer(parent -> Minecraft.getInstance().setScreen(new LuceatScreen(parent))));
	}
}
