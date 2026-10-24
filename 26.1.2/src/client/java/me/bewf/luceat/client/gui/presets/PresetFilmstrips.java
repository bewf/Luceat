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

// short looping videos of each preset, made once in the background and kept on disk
public final class PresetFilmstrips {
	public static final int WIDTH = 288;
	public static final int HEIGHT = 192;
	public static final int FRAMES = 24;
	private static final int VERSION = 4;
	private static final int MAGIC = 0x4C554331;
	private static final double FRAMES_PER_SECOND = FRAMES / PreviewRenderer.LOOP_SECONDS;

	public static final class Strip {
		private final Presets.Preset preset;
		private volatile int[][] frames;
		private DynamicTexture texture;
		private Identifier id;
		private int shown = -1;
		private boolean queued;

		private Strip(Presets.Preset preset) {
			this.preset = preset;
		}

		public boolean ready() {
			return frames != null;
		}

		// the texture showing the right frame for now, or null while the video is still being made
		public Identifier texture(long nowNanos) {
			int[][] have = frames;
			if (have == null) {
				return null;
			}
			if (texture == null) {
				id = Identifier.fromNamespaceAndPath(Luceat.MOD_ID, "presets/" + preset.name().toLowerCase());
				texture = new DynamicTexture("luceat preset", WIDTH, HEIGHT, false);
				Minecraft.getInstance().getTextureManager().register(id, texture);
			}
			int index = (int) ((nowNanos / 1.0E9 * FRAMES_PER_SECOND) % FRAMES);
			if (index != shown) {
				int[] pixels = have[index];
				NativeImage image = texture.getPixels();
				for (int y = 0; y < HEIGHT; y++) {
					for (int x = 0; x < WIDTH; x++) {
						image.setPixel(x, y, pixels[x + y * WIDTH]);
					}
				}
				texture.upload();
				shown = index;
			}
			return id;
		}

		public void release() {
			if (texture != null) {
				Minecraft.getInstance().getTextureManager().release(id);
				texture = null;
				shown = -1;
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
		int[][] loaded = read(file);
		if (loaded != null) {
			strip.frames = loaded;
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
				int[][] made = new int[FRAMES][WIDTH * HEIGHT];
				for (int i = 0; i < FRAMES; i++) {
					prepared.render((float) (i / FRAMES_PER_SECOND), WIDTH, HEIGHT, made[i]);
					Thread.sleep(30L);
				}
				write(file, made);
				strip.frames = made;
			} catch (InterruptedException error) {
				Thread.currentThread().interrupt();
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

	private static int[][] read(Path file) {
		if (!Files.exists(file)) {
			return null;
		}
		try (DataInputStream in = new DataInputStream(new BufferedInputStream(new GZIPInputStream(Files.newInputStream(file))))) {
			if (in.readInt() != MAGIC || in.readInt() != WIDTH || in.readInt() != HEIGHT || in.readInt() != FRAMES) {
				return null;
			}
			int[][] frames = new int[FRAMES][WIDTH * HEIGHT];
			byte[] row = new byte[WIDTH * 3];
			for (int[] frame : frames) {
				for (int y = 0; y < HEIGHT; y++) {
					in.readFully(row);
					for (int x = 0; x < WIDTH; x++) {
						frame[x + y * WIDTH] = 0xFF000000 | (row[x * 3] & 255) << 16 | (row[x * 3 + 1] & 255) << 8 | (row[x * 3 + 2] & 255);
					}
				}
			}
			return frames;
		} catch (IOException | RuntimeException error) {
			return null;
		}
	}

	private static void write(Path file, int[][] frames) {
		try {
			Files.createDirectories(file.getParent());
			try (DataOutputStream out = new DataOutputStream(new BufferedOutputStream(new GZIPOutputStream(Files.newOutputStream(file))))) {
				out.writeInt(MAGIC);
				out.writeInt(WIDTH);
				out.writeInt(HEIGHT);
				out.writeInt(FRAMES);
				byte[] row = new byte[WIDTH * 3];
				for (int[] frame : frames) {
					for (int y = 0; y < HEIGHT; y++) {
						for (int x = 0; x < WIDTH; x++) {
							int argb = frame[x + y * WIDTH];
							row[x * 3] = (byte) (argb >> 16);
							row[x * 3 + 1] = (byte) (argb >> 8);
							row[x * 3 + 2] = (byte) argb;
						}
						out.write(row);
					}
				}
			}
		} catch (IOException error) {
			Luceat.LOGGER.warn("Could not save the preset previews", error);
		}
	}
}
