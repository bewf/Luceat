package me.bewf.luceat.client.gui.presets;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

import me.bewf.luceat.Luceat;
import me.bewf.luceat.client.config.LuceatConfig;
import me.bewf.luceat.client.config.Presets;
import me.bewf.luceat.client.gui.preview.PreviewRenderer;
import me.bewf.luceat.client.gui.preview.SceneId;

import com.mojang.blaze3d.platform.NativeImage;

import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.Identifier;

// a wide still of each preset, made once in the background and kept on disk. The picker pans across it with the mouse
public final class PresetFilmstrips {
	public static final int WIDTH = 352;
	public static final int HEIGHT = 192;
	private static final int VERSION = 5;
	private static final int MAGIC = 0x4C554332;

	public static final class Strip {
		private final Presets.Preset preset;
		private volatile int[] pixels;
		private DynamicTexture texture;
		private Identifier id;
		private boolean uploaded;
		private boolean queued;

		private Strip(Presets.Preset preset) {
			this.preset = preset;
		}

		public boolean ready() {
			return pixels != null;
		}

		// the texture holding the wide still, or null while it is still being made
		public Identifier texture() {
			int[] have = pixels;
			if (have == null) {
				return null;
			}
			if (texture == null) {
				id = Identifier.fromNamespaceAndPath(Luceat.MOD_ID, "presets/" + preset.name().toLowerCase());
				texture = new DynamicTexture("luceat preset", WIDTH, HEIGHT, false);
				Minecraft.getInstance().getTextureManager().register(id, texture);
			}
			if (!uploaded) {
				NativeImage image = texture.getPixels();
				for (int y = 0; y < HEIGHT; y++) {
					for (int x = 0; x < WIDTH; x++) {
						image.setPixel(x, y, have[x + y * WIDTH]);
					}
				}
				texture.upload();
				uploaded = true;
			}
			return id;
		}

		public void release() {
			if (texture != null) {
				Minecraft.getInstance().getTextureManager().release(id);
				texture = null;
				uploaded = false;
			}
		}
	}

	private static final Map<String, Strip> STRIPS = new LinkedHashMap<>();
	private static final Deque<Strip> PENDING = new ArrayDeque<>();
	private static volatile boolean working;
	private static boolean seeded;
	private static int goodTicks;
	private static long lastTick;

	private PresetFilmstrips() {
	}

	public static Strip of(Presets.Preset preset) {
		Strip strip = STRIPS.get(preset.name());
		if (strip == null) {
			strip = new Strip(preset);
			STRIPS.put(preset.name(), strip);
		}
		if (!strip.ready() && !strip.queued) {
			strip.queued = true;
			PENDING.add(strip);
		}
		return strip;
	}

	public static void releaseTextures() {
		for (Strip strip : STRIPS.values()) {
			strip.release();
		}
	}

	// called every client tick, begins the work after the game has been running smoothly for a moment
	public static void warmUp() {
		long now = System.nanoTime();
		goodTicks = lastTick != 0L && now - lastTick < 70_000_000L ? goodTicks + 1 : 0;
		lastTick = now;
		if (!seeded && goodTicks >= 60) {
			seeded = true;
			for (Presets.Preset preset : Presets.all()) {
				if (!preset.name().equals("HARDCORE") || LuceatConfig.get().hardcoreUnlocked) {
					of(preset);
				}
			}
		}
		step();
	}

	// the screen calls this each frame so work continues while it is open
	public static void step() {
		if (working || PENDING.isEmpty()) {
			return;
		}
		Strip strip = PENDING.poll();
		if (strip == null || strip.ready()) {
			return;
		}
		LuceatConfig config = new LuceatConfig();
		strip.preset.apply().accept(config);
		Path file = cacheFile(strip.preset, config);
		int[] loaded = read(file);
		if (loaded != null) {
			strip.pixels = loaded;
			return;
		}
		PreviewRenderer.Prepared prepared;
		try {
			prepared = PreviewRenderer.prepare(SceneId.CAMPFIRE, config);
		} catch (RuntimeException error) {
			Luceat.LOGGER.warn("Could not prepare the {} preview", strip.preset.name(), error);
			return;
		}
		working = true;
		Thread thread = new Thread(() -> {
			try {
				int[] made = new int[WIDTH * HEIGHT];
				prepared.panorama(WIDTH, HEIGHT, made);
				write(file, made);
				strip.pixels = made;
			} catch (RuntimeException error) {
				Luceat.LOGGER.warn("Could not render the {} preview", strip.preset.name(), error);
			} finally {
				working = false;
			}
		}, "luceat preset previews");
		thread.setDaemon(true);
		thread.setPriority(Thread.MIN_PRIORITY);
		thread.start();
	}

	private static Path cacheFile(Presets.Preset preset, LuceatConfig config) {
		int hash = config.asJson().hashCode() * 31 + VERSION;
		return FabricLoader.getInstance().getConfigDir().resolve("luceat").resolve("preview-cache").resolve(preset.name().toLowerCase() + "-" + Integer.toHexString(hash) + ".bin");
	}

	private static int[] read(Path file) {
		if (!Files.exists(file)) {
			return null;
		}
		try (DataInputStream in = new DataInputStream(new BufferedInputStream(new GZIPInputStream(Files.newInputStream(file))))) {
			if (in.readInt() != MAGIC || in.readInt() != WIDTH || in.readInt() != HEIGHT) {
				return null;
			}
			int[] pixels = new int[WIDTH * HEIGHT];
			byte[] row = new byte[WIDTH * 3];
			for (int y = 0; y < HEIGHT; y++) {
				in.readFully(row);
				for (int x = 0; x < WIDTH; x++) {
					pixels[x + y * WIDTH] = 0xFF000000 | (row[x * 3] & 255) << 16 | (row[x * 3 + 1] & 255) << 8 | (row[x * 3 + 2] & 255);
				}
			}
			return pixels;
		} catch (IOException | RuntimeException error) {
			return null;
		}
	}

	private static void write(Path file, int[] pixels) {
		try {
			Files.createDirectories(file.getParent());
			try (DataOutputStream out = new DataOutputStream(new BufferedOutputStream(new GZIPOutputStream(Files.newOutputStream(file))))) {
				out.writeInt(MAGIC);
				out.writeInt(WIDTH);
				out.writeInt(HEIGHT);
				byte[] row = new byte[WIDTH * 3];
				for (int y = 0; y < HEIGHT; y++) {
					for (int x = 0; x < WIDTH; x++) {
						int argb = pixels[x + y * WIDTH];
						row[x * 3] = (byte) (argb >> 16);
						row[x * 3 + 1] = (byte) (argb >> 8);
						row[x * 3 + 2] = (byte) argb;
					}
					out.write(row);
				}
			}
		} catch (IOException error) {
			Luceat.LOGGER.warn("Could not save the preset previews", error);
		}
	}
}
