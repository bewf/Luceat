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
	public boolean presetChosen = false;
	public String preset = "";
	public boolean hardcoreUnlocked = false;

	public float brightness = 1.2F;
	public float maxBrightness = 1.0F;
	public float daylightFade = 0.85F;
	public float directionalShading = 0.7F;
	public float lightBounce = 0.25F;
	public float shine = 0.4F;
	public boolean textureColours = true;

	public boolean heldLights = true;
	public boolean otherPlayers = true;
	public boolean mobs = true;
	public boolean droppedItems = true;
	public boolean enchantGlow = true;
	public boolean itemFrames = true;
	public boolean naturalLights = true;
	public float flicker = 0.0F;
	public float heldHeight = 0.7F;
	public boolean waterPutsOutLights = true;
	public int maxLights = 96;
	public boolean mergeFarLights = true;
	public boolean adaptiveQuality = true;
	public int lightRange = 64;
	public boolean entityLights = true;
	public boolean particleLights = true;

	public boolean placedLights = true;
	public float placedStrength = 1.0F;
	public float vanillaReplace = 0.8F;
	public int placedMinLevel = 3;

	public boolean shadows = true;
	public int shadowDistance = 48;
	public int shadowedLights = 3;
	public float shadowStrength = 1.0F;
	public boolean keepLightOutOfWalls = true;
	public float shadowSoftness = 0.0F;
	public boolean thinShadows = true;
	public boolean plantShadows = true;
	public ShadowQuality foliageShadows = ShadowQuality.FANCY;
	public boolean glassTint = true;
	public float cutoutOpacity = 0.9F;

	public boolean entityShadows = true;
	public float entityShadowStrength = 0.5F;

	public boolean updateChecker = true;
	public ShaderPackMode shaderPacks = ShaderPackMode.LEAVE;
	public boolean fancyNames = true;

	private static int revision;

	public static LuceatConfig get() {
		return current;
	}

	public static int revision() {
		return revision;
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
		revision++;
		try (Writer writer = Files.newBufferedWriter(path())) {
			GSON.toJson(current, writer);
		} catch (IOException e) {
			Luceat.LOGGER.warn("Could not write luceat.json", e);
		}
	}

	public static String snapshot() {
		return GSON.toJson(current);
	}

	public LuceatConfig copy() {
		LuceatConfig made = GSON.fromJson(GSON.toJson(this), LuceatConfig.class);
		made.clamp();
		return made;
	}

	public String asJson() {
		return GSON.toJson(this);
	}

	public static void restore(String json) {
		LuceatConfig loaded = GSON.fromJson(json, LuceatConfig.class);
		if (loaded == null) {
			return;
		}
		loaded.clamp();
		current.copyFrom(loaded);
	}

	public void copyFrom(LuceatConfig other) {
		for (java.lang.reflect.Field field : LuceatConfig.class.getDeclaredFields()) {
			int modifiers = field.getModifiers();
			if (java.lang.reflect.Modifier.isStatic(modifiers) || java.lang.reflect.Modifier.isFinal(modifiers)) {
				continue;
			}
			try {
				field.set(this, field.get(other));
			} catch (IllegalAccessException e) {
				Luceat.LOGGER.warn("Could not copy {}", field.getName(), e);
			}
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
		maxLights = Math.max(1, Math.min(maxLights, 256));
		shadowedLights = Math.max(0, Math.min(shadowedLights, 8));
		lightBounce = Math.max(0.0F, Math.min(lightBounce, 1.0F));
		shine = Math.max(0.0F, Math.min(shine, 1.0F));
		lightRange = Math.max(8, Math.min(lightRange, 128));
		placedStrength = Math.max(0.0F, Math.min(placedStrength, 2.0F));
		vanillaReplace = Math.max(0.0F, Math.min(vanillaReplace, 1.0F));
		placedMinLevel = Math.max(1, Math.min(placedMinLevel, 15));
		shadowDistance = Math.max(8, Math.min(shadowDistance, 128));
		shadowSoftness = Math.max(0.0F, Math.min(shadowSoftness, 1.0F));
		flicker = Math.max(0.0F, Math.min(flicker, 1.0F));
		heldHeight = Math.max(0.3F, Math.min(heldHeight, 1.0F));
		entityShadowStrength = Math.max(0.0F, Math.min(entityShadowStrength, 1.0F));
		if (preset == null) {
			preset = "";
		}
		if (shaderPacks == null) {
			shaderPacks = ShaderPackMode.LEAVE;
		}
		if (foliageShadows == null) {
			foliageShadows = ShadowQuality.FANCY;
		}
	}
}
