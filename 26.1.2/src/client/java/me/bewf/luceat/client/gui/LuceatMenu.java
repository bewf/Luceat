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
				Say.chat(Say.line(Say.text("Luceat is "), Say.colour(config.enabled ? "on" : "off", config.enabled ? Say.VALUE : Say.WARN), Say.text(".")));
				return 1;
			}))
			.then(ClientCommands.literal("resume").executes(context -> {
				me.bewf.luceat.client.light.Panic.resume();
				Say.chat(Say.line(Say.text("Lights are back on.")));
				return 1;
			}))
			.then(ClientCommands.literal("stats").executes(context -> {
				printStats();
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

	private static void printStats() {
		LuceatConfig config = LuceatConfig.get();
		var variant = me.bewf.luceat.client.shader.TerrainShaderPatch.built();
		boolean paused = me.bewf.luceat.client.light.Panic.paused();
		Say.chat(Say.line(
			Say.stat("Shader", variant == null ? "not built" : variant.name().toLowerCase(java.util.Locale.ROOT)), Say.gap(),
			Say.stat("Lights", Integer.toString(me.bewf.luceat.client.light.Stats.lights())), Say.gap(),
			Say.stat("Grid cells", Integer.toString(me.bewf.luceat.client.light.LightGrid.cellsUsed())), Say.gap(),
			Say.stat("Frame", Math.round(me.bewf.luceat.client.light.Stats.frameMillis()) + " ms")
		));
		Say.chat(Say.line(
			Say.stat("Shadows", config.shadows ? config.shadowedLights + " lights, " + me.bewf.luceat.client.shadow.ShadowVolume.cellsFilled() + " map cells" : "off"), Say.gap(),
			Say.stat("Quality", Math.round(me.bewf.luceat.client.light.Governor.quality() * 100.0F) + "%"),
			paused ? Say.gap().append(Say.colour("lights paused", Say.WARN)) : Say.text("")
		));
		var names = me.bewf.luceat.client.light.Stats.NAMES;
		net.minecraft.network.chat.MutableComponent times = Say.prefix();
		for (int i = 0; i < names.length; i++) {
			times.append(Say.stat(names[i], String.format(java.util.Locale.ROOT, "%.2f ms", me.bewf.luceat.client.light.Stats.stage(i))));
			if (i + 1 < names.length) {
				times.append(Say.gap());
			}
		}
		Say.chat(times);
	}
}
