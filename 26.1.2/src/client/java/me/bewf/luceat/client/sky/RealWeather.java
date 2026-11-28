package me.bewf.luceat.client.sky;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicBoolean;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import me.bewf.luceat.Luceat;
import me.bewf.luceat.client.config.LuceatConfig;

public final class RealWeather {
	private static final long REFRESH_MILLIS = 10L * 60L * 1000L;
	private static final HttpClient CLIENT = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(6)).build();
	private static final AtomicBoolean BUSY = new AtomicBoolean();
	private static volatile CloudType type = CloudType.CUMULUS;
	private static volatile String status = "Not loaded yet.";
	private static volatile long loadedAt;
	private static volatile long failedAt;
	private static volatile double autoLat = Double.NaN;
	private static volatile double autoLon = Double.NaN;
	private static volatile String autoPlace = "";
	private static volatile String lastKey = "";

	private RealWeather() {
	}

	public static CloudType type() {
		refreshIfDue();
		return type;
	}

	public static String status() {
		return status;
	}

	public static void refreshIfDue() {
		LuceatConfig config = LuceatConfig.get();
		String key = config.cloudAutoLocation + ":" + config.cloudLat + ":" + config.cloudLon;
		boolean moved = !key.equals(lastKey);
		long now = System.currentTimeMillis();
		boolean due = moved || now - loadedAt > REFRESH_MILLIS;
		if (!due || (!moved && now - failedAt < 60_000L) || !BUSY.compareAndSet(false, true)) {
			return;
		}
		lastKey = key;
		boolean auto = config.cloudAutoLocation;
		double lat = config.cloudLat;
		double lon = config.cloudLon;
		Thread worker = new Thread(() -> load(auto, lat, lon), "Luceat weather");
		worker.setDaemon(true);
		worker.start();
	}

	private static void load(boolean auto, double lat, double lon) {
		try {
			String place = "";
			if (auto) {
				if (Double.isNaN(autoLat)) {
					JsonObject where = get("https://ipwho.is/?fields=success,latitude,longitude,city,country");
					if (!where.has("success") || !where.get("success").getAsBoolean()) {
						throw new IllegalStateException("location lookup failed");
					}
					autoLat = where.get("latitude").getAsDouble();
					autoLon = where.get("longitude").getAsDouble();
					autoPlace = (where.has("city") ? where.get("city").getAsString() : "") + (where.has("country") ? ", " + where.get("country").getAsString() : "");
				}
				lat = autoLat;
				lon = autoLon;
				place = autoPlace;
			}
			String url = String.format(Locale.ROOT, "https://api.open-meteo.com/v1/forecast?latitude=%.3f&longitude=%.3f&current=cloud_cover,cloud_cover_low,cloud_cover_high,weather_code", lat, lon);
			JsonObject current = get(url).getAsJsonObject("current");
			int cover = current.get("cloud_cover").getAsInt();
			int low = current.get("cloud_cover_low").getAsInt();
			int high = current.get("cloud_cover_high").getAsInt();
			int code = current.get("weather_code").getAsInt();
			CloudType found = pick(cover, low, high, code);
			type = found;
			status = found.label() + ", " + cover + "% cloud cover" + (place.isEmpty() ? "" : " in " + place) + ".";
			loadedAt = System.currentTimeMillis();
		} catch (Exception error) {
			failedAt = System.currentTimeMillis();
			status = "Could not get the weather, keeping the last one.";
			Luceat.LOGGER.warn("Luceat weather: {}", error.toString());
		} finally {
			BUSY.set(false);
		}
	}

	private static JsonObject get(String url) throws Exception {
		HttpRequest request = HttpRequest.newBuilder(URI.create(url)).timeout(Duration.ofSeconds(8)).header("User-Agent", "Luceat").GET().build();
		HttpResponse<String> response = CLIENT.send(request, HttpResponse.BodyHandlers.ofString());
		if (response.statusCode() != 200) {
			throw new IllegalStateException("HTTP " + response.statusCode());
		}
		return JsonParser.parseString(response.body()).getAsJsonObject();
	}

	static CloudType pick(int cover, int low, int high, int code) {
		if (code >= 95) {
			return CloudType.STORM;
		}
		if (code >= 80 && code <= 82) {
			return CloudType.CUMULONIMBUS;
		}
		if (code >= 51 || code == 45 || code == 48) {
			if (code == 45 || code == 48) {
				return CloudType.STRATUS;
			}
			return code >= 71 && code <= 77 || code == 85 || code == 86 ? CloudType.OVERCAST : CloudType.NIMBOSTRATUS;
		}
		if (high >= 60 && low < 25) {
			return cover < 70 ? CloudType.CIRROCUMULUS : CloudType.CIRRUS;
		}
		if (cover >= 45 && cover < 75 && low < 15 && high < 40) {
			return CloudType.ALTOCUMULUS;
		}
		if (cover < 45) {
			return CloudType.FEW;
		}
		if (cover < 75) {
			return CloudType.CUMULUS;
		}
		return cover < 92 ? CloudType.STRATUS : CloudType.OVERCAST;
	}
}
