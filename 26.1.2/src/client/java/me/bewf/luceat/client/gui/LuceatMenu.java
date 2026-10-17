package me.bewf.luceat.client.gui;

import com.mojang.brigadier.CommandDispatcher;

import me.bewf.luceat.Luceat;
import me.bewf.luceat.client.config.LuceatConfig;

import net.fabricmc.fabric.api.client.command.v2.ClientCommands;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.Identifier;
import org.lwjgl.glfw.GLFW;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;

public final class LuceatMenu {
	private static final KeyMapping OPEN = KeyMappingHelper.registerKeyMapping(new KeyMapping(
		"key.luceat.open",
		GLFW.GLFW_KEY_K,
		KeyMapping.Category.register(Identifier.fromNamespaceAndPath("luceat", "main"))
	));

	private static boolean requested;

	private LuceatMenu() {
	}

	public static void init() {
		Luceat.LOGGER.info("Luceat settings key is {}", OPEN.getName());
	}

	public static void register(CommandDispatcher<FabricClientCommandSource> dispatcher) {
		dispatcher.register(ClientCommands.literal("luceat")
			.executes(context -> {
				requested = true;
				return 1;
			})
			.then(ClientCommands.literal("toggle").executes(context -> {
				LuceatConfig config = LuceatConfig.get();
				config.enabled = !config.enabled;
				LuceatConfig.save();
				context.getSource().sendFeedback(prefix().append(Component.literal(config.enabled ? "Luceat is on." : "Luceat is off.")));
				return 1;
			})));
	}

	public static void tick(Minecraft minecraft) {
		while (OPEN.consumeClick()) {
			if (minecraft.screen == null) {
				requested = true;
			}
		}
		if (requested) {
			requested = false;
			minecraft.setScreen(new LuceatScreen(minecraft.screen));
		}
	}

	private static net.minecraft.network.chat.MutableComponent prefix() {
		return Component.literal("[Luceat] ").withStyle(Style.EMPTY.withColor(LuceatLook.ACCENT & 0xFFFFFF));
	}
}
