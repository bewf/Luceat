package me.bewf.luceat.client.config;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import me.bewf.luceat.Luceat;

import net.fabricmc.loader.api.FabricLoader;

public final class LuceatConfig {
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
	private static LuceatConfig current = new LuceatConfig();

	public boolean enabled = true;

	public float brightness = 1.2F;
	public float maxBrightness = 1.0F;
	public float daylightFade = 0.85F;
	public float directionalShading = 0.7F;
	public boolean textureColours = true;

	public boolean heldLights = true;
	public boolean otherPlayers = true;
	public boolean mobs = true;
	public boolean droppedItems = true;
	public boolean enchantGlow = true;
	public boolean waterPutsOutLights = true;
	public int maxLights = 16;
	public int lightRange = 64;

	public boolean placedLights = true;
	public float placedStrength = 1.0F;
	public float vanillaReplace = 0.8F;
	public int placedMinLevel = 3;

	public boolean shadows = true;
	public int shadowDistance = 48;
	public float shadowStrength = 1.0F;
	public boolean keepLightOutOfWalls = true;
	public ShadowQuality foliageShadows = ShadowQuality.FANCY;
	public boolean glassTint = true;
	public float cutoutOpacity = 0.9F;

	public boolean updateChecker = true;

	public static LuceatConfig get() {
		return current;
	}

	public static void load() {
		Path path = path();
		if (Files.exists(path)) {
			try (Reader reader = Files.newBufferedReader(path)) {
				LuceatConfig loaded = GSON.fromJson(reader, LuceatConfig.class);
				if (loaded != null) {
					current = loaded;
					current.clamp();
				}
			} catch (IOException | RuntimeException e) {
				Luceat.LOGGER.warn("Could not read luceat.json", e);
			}
		}
		save();
	}

	public static void save() {
		current.clamp();
		try (Writer writer = Files.newBufferedWriter(path())) {
			GSON.toJson(current, writer);
		} catch (IOException e) {
			Luceat.LOGGER.warn("Could not write luceat.json", e);
		}
	}

	private static Path path() {
		return FabricLoader.getInstance().getConfigDir().resolve("luceat.json");
	}

	private void clamp() {
		brightness = Math.max(0.0F, Math.min(brightness, 4.0F));
		maxBrightness = Math.max(0.1F, Math.min(maxBrightness, 4.0F));
		daylightFade = Math.max(0.0F, Math.min(daylightFade, 1.0F));
		directionalShading = Math.max(0.0F, Math.min(directionalShading, 1.0F));
		shadowStrength = Math.max(0.0F, Math.min(shadowStrength, 1.0F));
		cutoutOpacity = Math.max(0.0F, Math.min(cutoutOpacity, 1.0F));
		maxLights = Math.max(1, Math.min(maxLights, 32));
		lightRange = Math.max(8, Math.min(lightRange, 128));
		placedStrength = Math.max(0.0F, Math.min(placedStrength, 2.0F));
		vanillaReplace = Math.max(0.0F, Math.min(vanillaReplace, 1.0F));
		placedMinLevel = Math.max(1, Math.min(placedMinLevel, 15));
		shadowDistance = Math.max(8, Math.min(shadowDistance, 128));
		if (foliageShadows == null) {
			foliageShadows = ShadowQuality.FANCY;
		}
	}
}
