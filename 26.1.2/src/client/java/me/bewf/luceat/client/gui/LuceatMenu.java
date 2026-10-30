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
		GLFW.GLFW_KEY_J,
		KeyMapping.Category.register(Identifier.fromNamespaceAndPath("luceat", "main"))
	));

	private static boolean requested;
	private static boolean requestedSpeed;

	private LuceatMenu() {
	}

	public static String keyLabel() {
		return OPEN.getTranslatedKeyMessage().getString();
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
			}))
			.then(ClientCommands.literal("resume").executes(context -> {
				me.bewf.luceat.client.light.Panic.resume();
				context.getSource().sendFeedback(prefix().append(Component.literal("Lights are back on.")));
				return 1;
			}))
			.then(ClientCommands.literal("stats").executes(context -> {
				var variant = me.bewf.luceat.client.shader.TerrainShaderPatch.built();
				context.getSource().sendFeedback(prefix().append(Component.literal("Shader " + (variant == null ? "not built yet" : variant.name().toLowerCase(java.util.Locale.ROOT)) + ", " + me.bewf.luceat.client.light.Stats.lights() + " lights, " + me.bewf.luceat.client.light.LightGrid.cellsUsed() + " grid cells, shadows " + (me.bewf.luceat.client.config.LuceatConfig.get().shadows ? me.bewf.luceat.client.config.LuceatConfig.get().shadowedLights + " lights, " + me.bewf.luceat.client.shadow.ShadowVolume.cellsFilled() + " map cells" : "off") + ", quality " + Math.round(me.bewf.luceat.client.light.Governor.quality() * 100.0F) + "%, frame " + Math.round(me.bewf.luceat.client.light.Stats.frameMillis()) + " ms" + (me.bewf.luceat.client.light.Panic.paused() ? ", lights paused" : "") + ".")));
				StringBuilder line = new StringBuilder();
				for (int i = 0; i < me.bewf.luceat.client.light.Stats.NAMES.length; i++) {
					line.append(me.bewf.luceat.client.light.Stats.NAMES[i]).append(' ').append(String.format(java.util.Locale.ROOT, "%.2f", me.bewf.luceat.client.light.Stats.stage(i))).append(" ms");
					line.append(i + 1 < me.bewf.luceat.client.light.Stats.NAMES.length ? ", " : ".");
				}
				context.getSource().sendFeedback(prefix().append(Component.literal(line.toString())));
				return 1;
			}))
			.then(ClientCommands.literal("perf").executes(context -> {
				requested = true;
				requestedSpeed = true;
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
			LuceatScreen screen = new LuceatScreen(minecraft.screen);
			if (requestedSpeed) {
				requestedSpeed = false;
				screen.showSpeedTest();
			}
			minecraft.setScreen(screen);
		}
	}

	private static net.minecraft.network.chat.MutableComponent prefix() {
		return Component.literal("[Luceat] ").withStyle(Style.EMPTY.withColor(LuceatLook.ACCENT & 0xFFFFFF));
	}
}
