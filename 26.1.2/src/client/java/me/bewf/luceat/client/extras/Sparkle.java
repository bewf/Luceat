package me.bewf.luceat.client.extras;

import java.io.IOException;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.google.gson.JsonParser;

import me.bewf.luceat.Luceat;
import me.bewf.luceat.client.gui.Say;

import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.TextColor;

public final class Sparkle {
	private record Look(String prefix, int base, int shine, int shine2) {
	}

	private record Cached(String name, long builtAt, Component component) {
	}

	private static final Look LORD = new Look("Lord and Saviour ", 0xBE3FFF, 0xFFF5FF, -1);
	private static final Look SCOOP = new Look("Scoop Master ", 0x9932CC, 0xFFF5FF, -1);

	private static final Map<UUID, Look> PLAYERS = Map.of(
			uuid("2913ac77e515490a96eb5183963f6169"), LORD,
			uuid("f299854cd6954a3cbaae6ab0e4b62dc8"), LORD,
			uuid("e02ca8dea5d740ffaa85d29a83a2eefb"), SCOOP
	);

	private static final Map<String, Look> NAMES = new ConcurrentHashMap<>(Map.of(
			"biwf", LORD,
			"bewf", LORD,
			"stalbet", SCOOP
	));

	private static final Set<UUID> OWNERS = Set.of(
			uuid("2913ac77e515490a96eb5183963f6169"),
			uuid("f299854cd6954a3cbaae6ab0e4b62dc8")
	);

	private static final Map<String, Cached> CACHE = new HashMap<>();
	private static final StackWalker WALKER = StackWalker.getInstance();
	private static final Path MET_MARKER = FabricLoader.getInstance().getConfigDir().resolve("luceat-met-owner");

	private static boolean met = Files.exists(MET_MARKER);
	private static long metCheckAt;

	private static final long CACHE_MS = 50L;
	private static final long PERIOD_MS = 2400L;
	private static final long SHINE_MS = 480L;

	private static final float SHINE_RADIUS = 1.5F;

	private static final Pattern LIST_HEADER = Pattern.compile(
			"(party (leader|members|moderators)|partying with)\\s*$", Pattern.CASE_INSENSITIVE);

	private static volatile Pattern namePattern;

	static {
		rebuildPattern();
	}

	private Sparkle() {
	}

	private static UUID uuid(String hex) {
		return UUID.fromString(hex.replaceFirst(
				"(\\p{XDigit}{8})(\\p{XDigit}{4})(\\p{XDigit}{4})(\\p{XDigit}{4})(\\p{XDigit}+)",
				"$1-$2-$3-$4-$5"));
	}

	public static boolean wants(UUID uuid) {
		return uuid != null && PLAYERS.containsKey(uuid);
	}

	public static Component name(UUID uuid, String playerName, String hookedMethod) {
		Look look = PLAYERS.get(uuid);
		if (look == null) {
			return Component.literal(playerName);
		}
		Reader reader = reader(hookedMethod);
		return reader == Reader.GAME ? render(look, playerName) : legacyName(look, playerName, reader == Reader.OTHER);
	}

	public static Component tabName(Component message) {
		return replaceNames(message, true, false, reader("getNameForDisplay"));
	}

	public static Component restoreAfterSkyHanni(Component line) {
		return replaceNames(line, true, false, Reader.GAME);
	}

	private enum Reader {
		GAME, SKYHANNI, OTHER
	}

	private static Reader reader(String hookedMethod) {
		try {
			return WALKER.walk(frames -> {
				boolean seen = false;
				Reader found = null;
				for (StackWalker.StackFrame frame : (Iterable<StackWalker.StackFrame>) frames.limit(16)::iterator) {
					boolean hooked = frame.getMethodName().equals(hookedMethod);
					if (!seen) {
						seen = hooked;
						continue;
					}
					if (hooked) {
						continue;
					}
					String owner = frame.getClassName();
					if (owner.startsWith("at.hannibal2.skyhanni.")) {
						return Reader.SKYHANNI;
					}
					if (found == null) {
						found = owner.startsWith("net.minecraft.") ? Reader.GAME : Reader.OTHER;
						if (found == Reader.GAME) {
							return found;
						}
					}
				}
				return found == null ? Reader.GAME : found;
			});
		} catch (Throwable error) {
			return Reader.GAME;
		}
	}

	private static Component legacyName(Look look, String playerName, boolean withPrefix) {
		ChatFormatting best = ChatFormatting.WHITE;
		long bestDistance = Long.MAX_VALUE;
		for (ChatFormatting format : ChatFormatting.values()) {
			TextColor legacy = TextColor.fromLegacyFormat(format);
			if (legacy == null) {
				continue;
			}
			int colour = legacy.getValue();
			long dr = ((colour >> 16) & 255) - ((look.base() >> 16) & 255);
			long dg = ((colour >> 8) & 255) - ((look.base() >> 8) & 255);
			long db = (colour & 255) - (look.base() & 255);
			long distance = dr * dr + dg * dg + db * db;
			if (distance < bestDistance) {
				bestDistance = distance;
				best = format;
			}
		}
		return Component.literal(withPrefix ? look.prefix() + playerName : playerName).withStyle(best);
	}

	public static void meetOwner(Minecraft minecraft) {
		long now = System.currentTimeMillis();
		if (met || minecraft.player == null || minecraft.getConnection() == null || now - metCheckAt < 2000L) {
			return;
		}
		metCheckAt = now;
		if (OWNERS.contains(minecraft.player.getUUID())) {
			remember();
			return;
		}
		for (PlayerInfo info : minecraft.getConnection().getOnlinePlayers()) {
			if (OWNERS.contains(info.getProfile().id())) {
				Say.chat(Say.line(
						Say.text("Congrats, you've met "),
						Component.empty().append(staticName(PLAYERS.get(info.getProfile().id()), info.getProfile().name())),
						Say.text(", the owner of this mod!")
				));
				remember();
				return;
			}
		}
	}

	private static void remember() {
		met = true;
		try {
			Files.writeString(MET_MARKER, "");
		} catch (IOException error) {
			Luceat.LOGGER.warn("Could not write {}", MET_MARKER, error);
		}
	}

	public static Component decorateChat(Component message) {
		return replaceNames(message, false, true);
	}

	private static synchronized Component render(Look look, String playerName) {
		long now = System.currentTimeMillis();

		String key = playerName.toLowerCase();
		Cached hit = CACHE.get(key);
		if (hit != null && playerName.equals(hit.name()) && now - hit.builtAt() < CACHE_MS) {
			return hit.component();
		}

		String text = look.prefix() + playerName;
		MutableComponent out = Component.empty();
		int length = text.length();

		long phase = now % PERIOD_MS;
		boolean shining = phase < SHINE_MS;

		float trail = look.shine2() >= 0 ? 4.0F : 0.0F;
		float progress = shining ? phase / (float) SHINE_MS : 0.0F;
		float center = -SHINE_RADIUS + progress * (length + SHINE_RADIUS * 2.0F + trail);

		int runStart = 0;
		int runColour = look.base();
		for (int i = 0; i < length; i++) {
			int color = look.base();

			if (shining) {
				color = blend(color, look.shine(), sweep(i, center));
				if (look.shine2() >= 0) {
					color = blend(color, look.shine2(), sweep(i, center - trail));
				}
			}

			if (i == 0) {
				runColour = color;
			} else if (color != runColour) {
				out.append(Component.literal(text.substring(runStart, i)).setStyle(Style.EMPTY.withColor(TextColor.fromRgb(runColour))));
				runStart = i;
				runColour = color;
			}
		}
		out.append(Component.literal(text.substring(runStart)).setStyle(Style.EMPTY.withColor(TextColor.fromRgb(runColour))));

		CACHE.put(key, new Cached(playerName, now, out));

		return out;
	}

	private static float sweep(int index, float center) {
		float strength = Math.max(0.0F, 1.0F - Math.abs(index - center) / SHINE_RADIUS);
		return strength * strength * (3.0F - 2.0F * strength);
	}

	private static Component staticName(Look look, String playerName) {
		return Component.literal(look.prefix() + playerName)
				.setStyle(Style.EMPTY.withColor(TextColor.fromRgb(look.base())));
	}

	private static void rebuildPattern() {
		StringBuilder alt = new StringBuilder();
		for (String n : NAMES.keySet()) {
			if (alt.length() > 0) {
				alt.append('|');
			}
			alt.append(Pattern.quote(n));
		}
		namePattern = Pattern.compile("(?<![A-Za-z0-9_])(" + alt + ")(?![A-Za-z0-9_])", Pattern.CASE_INSENSITIVE);
	}

	public static void startNameLookup() {
		Thread thread = new Thread(() -> {
			for (Map.Entry<UUID, Look> entry : PLAYERS.entrySet()) {
				try {
					String id = entry.getKey().toString().replace("-", "");
					HttpURLConnection connection = (HttpURLConnection) URI
							.create("https://sessionserver.mojang.com/session/minecraft/profile/" + id)
							.toURL().openConnection();
					connection.setConnectTimeout(5000);
					connection.setReadTimeout(5000);
					connection.setRequestProperty("User-Agent", "Luceat");
					if (connection.getResponseCode() != 200) {
						continue;
					}
					try (InputStreamReader reader = new InputStreamReader(connection.getInputStream(), StandardCharsets.UTF_8)) {
						String name = JsonParser.parseReader(reader).getAsJsonObject().get("name").getAsString();
						NAMES.put(name.toLowerCase(), entry.getValue());
					}
				} catch (Throwable ignored) {
				}
			}
			rebuildPattern();
		}, "Luceat-NameLookup");
		thread.setDaemon(true);
		thread.start();
	}

	public static Component replaceNames(Component message, boolean animated, boolean senderOnly) {
		return replaceNames(message, animated, senderOnly, Reader.GAME);
	}

	private static Component replaceNames(Component message, boolean animated, boolean senderOnly, Reader reader) {
		try {
			if (message == null) {
				return message;
			}

			String flat = message.getString();
			if (!namePattern.matcher(flat).find()) {
				return message;
			}

			int limit = Integer.MAX_VALUE;
			if (senderOnly) {
				int colon = flat.indexOf(':');
				if (colon >= 0 && !LIST_HEADER.matcher(flat.substring(0, colon)).find()) {
					limit = colon;
				}
			}
			final int senderEnd = limit;

			MutableComponent out = Component.empty();
			boolean[] changed = {false};
			int[] offset = {0};
			message.visit((style, text) -> {
				Matcher m = namePattern.matcher(text);
				int last = 0;
				while (m.find()) {
					if (offset[0] + m.start() >= senderEnd) {
						break;
					}
					Look look = NAMES.get(m.group(1).toLowerCase());
					if (look == null || text.substring(0, m.start()).endsWith(look.prefix())) {
						continue;
					}
					if (m.start() > last) {
						out.append(Component.literal(text.substring(last, m.start())).setStyle(style));
					}
					out.append(reader != Reader.GAME ? legacyName(look, m.group(1), reader == Reader.OTHER) : animated ? render(look, m.group(1)) : staticName(look, m.group(1)));
					changed[0] = true;
					last = m.end();
				}
				if (last < text.length()) {
					out.append(Component.literal(text.substring(last)).setStyle(style));
				}
				offset[0] += text.length();
				return Optional.empty();
			}, Style.EMPTY);

			return changed[0] ? out : message;
		} catch (Throwable error) {
			return message;
		}
	}

	private static int blend(int base, int shine, float amount) {
		int r = mix((base >> 16) & 255, (shine >> 16) & 255, amount);
		int g = mix((base >> 8) & 255, (shine >> 8) & 255, amount);
		int b = mix(base & 255, shine & 255, amount);

		return (r << 16) | (g << 8) | b;
	}

	private static int mix(int a, int b, float amount) {
		return Math.round(a + (b - a) * amount);
	}
}
