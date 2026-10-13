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

	public float brightness = 1.2F;
	public float maxBrightness = 1.0F;
	public boolean shadows = true;
	public float leafShadow = 0.6F;

	public static LuceatConfig get() {
		return current;
	}

	public static void load() {
		Path path = FabricLoader.getInstance().getConfigDir().resolve("luceat.json");
		if (Files.exists(path)) {
			try (Reader reader = Files.newBufferedReader(path)) {
				LuceatConfig loaded = GSON.fromJson(reader, LuceatConfig.class);
				if (loaded != null) {
					current = loaded;
				}
			} catch (IOException | RuntimeException e) {
				Luceat.LOGGER.warn("Could not read luceat.json", e);
			}
		}
		try (Writer writer = Files.newBufferedWriter(path)) {
			GSON.toJson(current, writer);
		} catch (IOException e) {
			Luceat.LOGGER.warn("Could not write luceat.json", e);
		}
	}
}
