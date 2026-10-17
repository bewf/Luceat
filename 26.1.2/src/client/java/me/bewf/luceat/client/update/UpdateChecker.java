package me.bewf.luceat.client.update;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import me.bewf.luceat.Luceat;
import me.bewf.luceat.client.config.LuceatConfig;
import me.bewf.luceat.client.gui.LuceatLook;

import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.toasts.SystemToast;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;

public final class UpdateChecker {
	private static final String PROJECT = "luceat";
	private static final Pattern NUMBERS = Pattern.compile("\\d+(\\.\\d+)*");

	private static volatile String latestNumber;
	private static volatile String latestId;
	private static volatile boolean announced;

	private UpdateChecker() {
	}

	public static void check() {
		if (!LuceatConfig.get().updateChecker) {
			return;
		}
		String local = localVersion();
		String minecraft = minecraftVersion();
		String query = "?game_versions=" + encode("[\"" + minecraft + "\"]") + "&loaders=" + encode("[\"fabric\"]");
		HttpRequest request = HttpRequest.newBuilder(URI.create("https://api.modrinth.com/v2/project/" + PROJECT + "/version" + query))
			.timeout(Duration.ofSeconds(10))
			.header("User-Agent", "bewf/Luceat/" + local)
			.GET()
			.build();
		HttpClient.newHttpClient().sendAsync(request, HttpResponse.BodyHandlers.ofString()).thenAccept(response -> {
			if (response.statusCode() != 200) {
				return;
			}
			try {
				JsonArray versions = JsonParser.parseString(response.body()).getAsJsonArray();
				if (versions.isEmpty()) {
					return;
				}
				JsonObject newest = versions.get(0).getAsJsonObject();
				String number = newest.get("version_number").getAsString();
				if (isNewer(number, local)) {
					latestNumber = number;
					latestId = newest.get("id").getAsString();
					Luceat.LOGGER.info("Update found for {}: {}", minecraft, number);
				}
			} catch (RuntimeException e) {
				Luceat.LOGGER.warn("Could not read the update list", e);
			}
		}).exceptionally(error -> {
			Luceat.LOGGER.warn("Update check failed: {}", error.getMessage());
			return null;
		});
	}

	public static void tick(Minecraft minecraft) {
		if (announced || latestNumber == null || minecraft.player == null) {
			return;
		}
		announced = true;
		String url = "https://modrinth.com/mod/" + PROJECT + "/version/" + latestId;
		MutableComponent message = Component.literal("[Luceat] ").withStyle(Style.EMPTY.withColor(LuceatLook.ACCENT & 0xFFFFFF))
			.append(Component.literal("Update available: " + latestNumber + " ").withStyle(Style.EMPTY.withColor(0xFFFFFF)))
			.append(Component.literal("[Open on Modrinth]").withStyle(Style.EMPTY
				.withColor(0x7AE0FF)
				.withClickEvent(new ClickEvent.OpenUrl(URI.create(url)))
				.withHoverEvent(new HoverEvent.ShowText(Component.literal("Opens the download page")))));
		minecraft.gui.getChat().addClientSystemMessage(message);
		SystemToast.add(
			minecraft.getToastManager(),
			SystemToast.SystemToastId.PERIODIC_NOTIFICATION,
			Component.literal("Luceat").withStyle(Style.EMPTY.withColor(LuceatLook.ACCENT & 0xFFFFFF)),
			Component.literal("Version " + latestNumber + " is out")
		);
	}

	public static boolean isNewer(String candidate, String local) {
		List<Integer> a = parts(candidate);
		List<Integer> b = parts(local);
		int size = Math.max(a.size(), b.size());
		for (int i = 0; i < size; i++) {
			int left = i < a.size() ? a.get(i) : 0;
			int right = i < b.size() ? b.get(i) : 0;
			if (left != right) {
				return left > right;
			}
		}
		return false;
	}

	private static List<Integer> parts(String version) {
		List<Integer> values = new ArrayList<>();
		Matcher matcher = NUMBERS.matcher(version);
		if (matcher.find()) {
			for (String piece : matcher.group().split("\\.")) {
				values.add(Integer.parseInt(piece));
			}
		}
		return values;
	}

	private static String localVersion() {
		return FabricLoader.getInstance().getModContainer(Luceat.MOD_ID)
			.map(container -> container.getMetadata().getVersion().getFriendlyString())
			.orElse("0");
	}

	private static String minecraftVersion() {
		return FabricLoader.getInstance().getModContainer("minecraft")
			.map(container -> container.getMetadata().getVersion().getFriendlyString())
			.orElse("26.1.2");
	}

	private static String encode(String text) {
		return URLEncoder.encode(text, StandardCharsets.UTF_8);
	}
}
