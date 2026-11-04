package me.bewf.luceat.client.gui.preview;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import me.bewf.luceat.Luceat;
import me.bewf.luceat.client.gui.preview.Light.Source;

import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Property;

public final class SceneLibrary {
	private static final int MAGIC = 0x4C534331;
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
	private static final Map<String, Scene> CACHE = new LinkedHashMap<>();
	private static JsonObject bindings;

	private SceneLibrary() {
	}

	public static Path folder() {
		return FabricLoader.getInstance().getConfigDir().resolve("luceat").resolve("scenes");
	}

	public static Path bindingsFile() {
		return FabricLoader.getInstance().getConfigDir().resolve("luceat").resolve("previews.json");
	}

	public static void reload() {
		CACHE.clear();
		bindings = null;
		Scenes.clear();
	}

	public static List<String> names() {
		TreeSet<String> found = new TreeSet<>();
		try (var stream = Files.isDirectory(folder()) ? Files.list(folder()) : java.util.stream.Stream.<Path>empty()) {
			stream.forEach(path -> {
				String file = path.getFileName().toString();
				if (file.endsWith(".lscene")) {
					found.add(file.substring(0, file.length() - 7));
				}
			});
		} catch (IOException ignored) {
		}
		for (String bundled : bundledNames()) {
			found.add(bundled);
		}
		return new ArrayList<>(found);
	}

	private static List<String> bundled;

	private static List<String> bundledNames() {
		if (bundled != null) {
			return bundled;
		}
		List<String> out = new ArrayList<>();
		try (InputStream in = SceneLibrary.class.getResourceAsStream("/assets/luceat/scenes/index.json")) {
			if (in != null) {
				JsonArray list = JsonParser.parseString(new String(in.readAllBytes(), StandardCharsets.UTF_8)).getAsJsonArray();
				for (JsonElement entry : list) {
					out.add(entry.getAsString());
				}
			}
		} catch (IOException | RuntimeException ignored) {
		}
		bundled = out;
		return out;
	}

	public static boolean has(String name) {
		return Files.exists(folder().resolve(name + ".lscene")) || bundledNames().contains(name);
	}

	public static Scene load(String name, String shot) {
		String key = name + "#" + (shot == null ? "" : shot);
		Scene known = CACHE.get(key);
		if (known != null) {
			return known;
		}
		try {
			Data data = read(name, 0);
			Scene built = build(name, data, shot);
			CACHE.put(key, built);
			return built;
		} catch (IOException | RuntimeException failure) {
			Luceat.LOGGER.warn("Luceat could not load scene {}", name, failure);
			return null;
		}
	}

	public static SceneSpec forSetting(String setting) {
		JsonObject all = bindings();
		if (all == null || !all.has(setting) || !all.get(setting).isJsonObject()) {
			return null;
		}
		JsonObject bound = all.getAsJsonObject(setting);
		String scene = string(bound, "scene", null);
		if (scene == null || !has(scene)) {
			return null;
		}
		Scene loaded = load(scene, string(bound, "shot", null));
		if (loaded == null) {
			return null;
		}
		Float time = bound.has("time") ? bound.get("time").getAsFloat() : null;
		return new SceneSpec(loaded, string(bound, "caption", loaded.caption), time);
	}

	private static JsonObject bindings() {
		if (bindings == null) {
			bindings = new JsonObject();
			try (InputStream in = SceneLibrary.class.getResourceAsStream("/assets/luceat/scenes/previews.json")) {
				if (in != null) {
					merge(bindings, JsonParser.parseString(new String(in.readAllBytes(), StandardCharsets.UTF_8)).getAsJsonObject());
				}
			} catch (IOException | RuntimeException ignored) {
			}
			try {
				if (Files.exists(bindingsFile())) {
					merge(bindings, JsonParser.parseString(Files.readString(bindingsFile())).getAsJsonObject());
				}
			} catch (IOException | RuntimeException failure) {
				Luceat.LOGGER.warn("Luceat could not read previews.json", failure);
			}
		}
		return bindings;
	}

	private static void merge(JsonObject into, JsonObject from) {
		for (Map.Entry<String, JsonElement> entry : from.entrySet()) {
			into.add(entry.getKey(), entry.getValue());
		}
	}

	public static void bind(String setting, String scene, String shot) throws IOException {
		JsonObject mine = new JsonObject();
		if (Files.exists(bindingsFile())) {
			mine = JsonParser.parseString(Files.readString(bindingsFile())).getAsJsonObject();
		}
		JsonObject entry = new JsonObject();
		entry.addProperty("scene", scene);
		if (shot != null) {
			entry.addProperty("shot", shot);
		}
		mine.add(setting, entry);
		Files.createDirectories(bindingsFile().getParent());
		Files.writeString(bindingsFile(), GSON.toJson(mine));
		reload();
	}

	static final class Data {
		int width;
		int height;
		int depth;
		BlockState[] cells;
		JsonObject meta = new JsonObject();
	}

	private static Data read(String name, int depth) throws IOException {
		if (depth > 4) {
			throw new IOException("scene base chain is too deep");
		}
		JsonObject meta = new JsonObject();
		Path metaFile = folder().resolve(name + ".json");
		if (Files.exists(metaFile)) {
			meta = JsonParser.parseString(Files.readString(metaFile)).getAsJsonObject();
		} else {
			try (InputStream in = SceneLibrary.class.getResourceAsStream("/assets/luceat/scenes/" + name + ".json")) {
				if (in != null) {
					meta = JsonParser.parseString(new String(in.readAllBytes(), StandardCharsets.UTF_8)).getAsJsonObject();
				}
			}
		}
		Data data;
		String base = string(meta, "base", null);
		if (base != null) {
			data = read(base, depth + 1);
			JsonObject inherited = data.meta.deepCopy();
			merge(inherited, meta);
			data.meta = inherited;
			applyPalette(data, meta);
			applyEdits(data, meta);
			return data;
		}
		data = new Data();
		Path blocks = folder().resolve(name + ".lscene");
		try (InputStream raw = Files.exists(blocks) ? Files.newInputStream(blocks) : SceneLibrary.class.getResourceAsStream("/assets/luceat/scenes/" + name + ".lscene")) {
			if (raw == null) {
				throw new IOException("no scene named " + name);
			}
			readBlocks(raw, data);
		}
		data.meta = meta;
		applyPalette(data, meta);
		applyEdits(data, meta);
		return data;
	}

	private static void readBlocks(InputStream raw, Data data) throws IOException {
		try (DataInputStream in = new DataInputStream(new BufferedInputStream(new GZIPInputStream(raw)))) {
			if (in.readInt() != MAGIC) {
				throw new IOException("not a Luceat scene file");
			}
			data.width = in.readUnsignedShort();
			data.height = in.readUnsignedShort();
			data.depth = in.readUnsignedShort();
			int paletteSize = readVar(in);
			BlockState[] palette = new BlockState[paletteSize + 1];
			for (int i = 1; i <= paletteSize; i++) {
				palette[i] = parseState(in.readUTF());
			}
			data.cells = new BlockState[data.width * data.height * data.depth];
			int at = 0;
			while (at < data.cells.length) {
				int index = readVar(in);
				int run = readVar(in);
				BlockState state = index < palette.length ? palette[index] : null;
				for (int i = 0; i < run && at < data.cells.length; i++) {
					data.cells[at++] = state;
				}
			}
		}
	}

	public static void write(Path file, int width, int height, int depth, BlockState[] cells) throws IOException {
		Files.createDirectories(file.getParent());
		Map<String, Integer> index = new LinkedHashMap<>();
		int[] ids = new int[cells.length];
		for (int i = 0; i < cells.length; i++) {
			BlockState state = cells[i];
			if (state == null || state.isAir()) {
				continue;
			}
			ids[i] = index.computeIfAbsent(stateString(state), key -> index.size() + 1);
		}
		try (OutputStream raw = Files.newOutputStream(file);
			DataOutputStream out = new DataOutputStream(new BufferedOutputStream(new GZIPOutputStream(raw)))) {
			out.writeInt(MAGIC);
			out.writeShort(width);
			out.writeShort(height);
			out.writeShort(depth);
			writeVar(out, index.size());
			for (String key : index.keySet()) {
				out.writeUTF(key);
			}
			int i = 0;
			while (i < ids.length) {
				int run = 1;
				while (i + run < ids.length && ids[i + run] == ids[i]) {
					run++;
				}
				writeVar(out, ids[i]);
				writeVar(out, run);
				i += run;
			}
		}
	}

	public static void writeMeta(String name, JsonObject meta) throws IOException {
		Files.createDirectories(folder());
		Files.writeString(folder().resolve(name + ".json"), GSON.toJson(meta));
	}

	public static JsonObject readMeta(String name) throws IOException {
		Path file = folder().resolve(name + ".json");
		return Files.exists(file) ? JsonParser.parseString(Files.readString(file)).getAsJsonObject() : new JsonObject();
	}

	public static void copy(String from, String to) throws IOException {
		Files.createDirectories(folder());
		Path source = folder().resolve(from + ".lscene");
		if (Files.exists(source)) {
			Files.copy(source, folder().resolve(to + ".lscene"), java.nio.file.StandardCopyOption.REPLACE_EXISTING);
			Path meta = folder().resolve(from + ".json");
			if (Files.exists(meta)) {
				Files.copy(meta, folder().resolve(to + ".json"), java.nio.file.StandardCopyOption.REPLACE_EXISTING);
			}
		} else {
			JsonObject meta = new JsonObject();
			meta.addProperty("base", from);
			writeMeta(to, meta);
		}
		reload();
	}

	public static int[] size(String name) {
		try {
			Data data = read(name, 0);
			return new int[]{data.width, data.height, data.depth};
		} catch (IOException | RuntimeException failure) {
			return null;
		}
	}

	public static BlockState[] cells(String name) {
		try {
			return read(name, 0).cells;
		} catch (IOException | RuntimeException failure) {
			return null;
		}
	}

	public static void export(Scene scene, String name) throws IOException {
		write(folder().resolve(name + ".lscene"), scene.width, scene.height, scene.depth, scene.cells);
		JsonObject meta = new JsonObject();
		meta.addProperty("title", scene.title);
		meta.addProperty("caption", scene.caption);
		meta.addProperty("time", scene.time);
		meta.addProperty("sun", scene.sunYaw);
		meta.addProperty("fog", scene.fog);
		JsonObject shots = new JsonObject();
		JsonObject shot = new JsonObject();
		shot.add("eye", array(scene.eye));
		shot.add("target", array(scene.target));
		shot.addProperty("fov", scene.fov);
		shots.add("default", shot);
		meta.add("shots", shots);
		JsonArray extra = new JsonArray();
		for (Light light : scene.lights) {
			if (light.source() == Source.PLACED) {
				continue;
			}
			JsonObject entry = new JsonObject();
			entry.addProperty("source", light.source().name());
			entry.add("at", array(new double[]{light.x(), light.y(), light.z()}));
			entry.addProperty("level", light.radius());
			entry.add("colour", array(new double[]{light.red(), light.green(), light.blue()}));
			extra.add(entry);
		}
		if (extra.size() > 0) {
			meta.add("lights", extra);
		}
		writeMeta(name, meta);
	}

	private static Scene build(String name, Data data, String shot) {
		JsonObject meta = data.meta;
		Scene.Builder b = new Scene.Builder(name, string(meta, "title", name), data.width, data.height, data.depth);
		System.arraycopy(data.cells, 0, b.cells, 0, data.cells.length);
		b.caption(string(meta, "caption", ""));
		if (meta.has("time")) {
			b.time(meta.get("time").getAsFloat());
		}
		if (meta.has("sun")) {
			b.sun(meta.get("sun").getAsDouble());
		}
		if (meta.has("fog")) {
			b.fog(meta.get("fog").getAsFloat());
		}
		JsonObject shots = meta.has("shots") && meta.get("shots").isJsonObject() ? meta.getAsJsonObject("shots") : new JsonObject();
		String wanted = shot != null && shots.has(shot) ? shot : shots.has("default") ? "default" : null;
		if (wanted != null) {
			JsonObject view = shots.getAsJsonObject(wanted);
			b.camera(doubles(view.getAsJsonArray("eye")), doubles(view.getAsJsonArray("target")), view.has("fov") ? view.get("fov").getAsDouble() : 60.0);
		} else {
			b.camera(new double[]{data.width / 2.0, data.height * 0.4, -3.0}, new double[]{data.width / 2.0, data.height * 0.3, data.depth * 0.5}, 60.0);
		}
		if (meta.has("lights")) {
			for (JsonElement element : meta.getAsJsonArray("lights")) {
				JsonObject entry = element.getAsJsonObject();
				double[] at = doubles(entry.getAsJsonArray("at"));
				double[] colour = entry.has("colour") ? doubles(entry.getAsJsonArray("colour")) : new double[]{1.0, 0.72, 0.38};
				Source source;
				try {
					source = Source.valueOf(string(entry, "source", "ALWAYS"));
				} catch (IllegalArgumentException unknown) {
					source = Source.ALWAYS;
				}
				b.light(Light.of(source, at[0], at[1], at[2], entry.has("level") ? entry.get("level").getAsFloat() : 12.0F, (float) colour[0], (float) colour[1], (float) colour[2]));
			}
		}
		if (meta.has("movable") && meta.get("movable").getAsBoolean()) {
			b.movable();
		}
		return b.build();
	}

	private static void applyPalette(Data data, JsonObject meta) {
		if (!meta.has("palette") || !meta.get("palette").isJsonObject()) {
			return;
		}
		Map<Block, BlockState> swaps = new LinkedHashMap<>();
		for (Map.Entry<String, JsonElement> entry : meta.getAsJsonObject("palette").entrySet()) {
			BlockState from = parseState(entry.getKey());
			BlockState to = parseState(entry.getValue().getAsString());
			if (from != null && to != null) {
				swaps.put(from.getBlock(), to);
			}
		}
		for (int i = 0; i < data.cells.length; i++) {
			BlockState state = data.cells[i];
			if (state != null && swaps.containsKey(state.getBlock())) {
				data.cells[i] = swaps.get(state.getBlock());
			}
		}
	}

	private static void applyEdits(Data data, JsonObject meta) {
		if (!meta.has("set")) {
			return;
		}
		for (JsonElement element : meta.getAsJsonArray("set")) {
			JsonObject edit = element.getAsJsonObject();
			double[] at = doubles(edit.getAsJsonArray("at"));
			int x = (int) at[0];
			int y = (int) at[1];
			int z = (int) at[2];
			if (x < 0 || y < 0 || z < 0 || x >= data.width || y >= data.height || z >= data.depth) {
				continue;
			}
			data.cells[x + data.width * (y + data.height * z)] = edit.get("block").getAsString().equals("air") ? null : parseState(edit.get("block").getAsString());
		}
	}

	@SuppressWarnings({"unchecked", "rawtypes"})
	static String stateString(BlockState state) {
		StringBuilder out = new StringBuilder(BuiltInRegistries.BLOCK.getKey(state.getBlock()).toString());
		List<Property<?>> properties = new ArrayList<>(state.getBlock().getStateDefinition().getProperties());
		if (!properties.isEmpty()) {
			out.append('[');
			for (int i = 0; i < properties.size(); i++) {
				Property property = properties.get(i);
				if (i > 0) {
					out.append(',');
				}
				out.append(property.getName()).append('=').append(property.getName((Comparable) state.getValue(property)));
			}
			out.append(']');
		}
		return out.toString();
	}

	static BlockState parseState(String text) {
		int open = text.indexOf('[');
		String id = open < 0 ? text : text.substring(0, open);
		Identifier key = Identifier.tryParse(id.contains(":") ? id : "minecraft:" + id);
		if (key == null || !BuiltInRegistries.BLOCK.containsKey(key)) {
			return null;
		}
		BlockState state = BuiltInRegistries.BLOCK.getValue(key).defaultBlockState();
		if (open >= 0 && text.endsWith("]")) {
			for (String pair : text.substring(open + 1, text.length() - 1).split(",")) {
				int eq = pair.indexOf('=');
				if (eq > 0) {
					state = Bs.with(state, pair.substring(0, eq), pair.substring(eq + 1));
				}
			}
		}
		return state;
	}

	private static String string(JsonObject object, String key, String fallback) {
		return object.has(key) && !object.get(key).isJsonNull() ? object.get(key).getAsString() : fallback;
	}

	private static JsonArray array(double[] values) {
		JsonArray out = new JsonArray();
		for (double value : values) {
			out.add(Math.round(value * 100.0) / 100.0);
		}
		return out;
	}

	private static double[] doubles(JsonArray array) {
		double[] out = new double[array.size()];
		for (int i = 0; i < out.length; i++) {
			out[i] = array.get(i).getAsDouble();
		}
		return out;
	}

	private static void writeVar(DataOutputStream out, int value) throws IOException {
		while ((value & ~0x7F) != 0) {
			out.writeByte((value & 0x7F) | 0x80);
			value >>>= 7;
		}
		out.writeByte(value);
	}

	private static int readVar(DataInputStream in) throws IOException {
		int value = 0;
		int shift = 0;
		int next;
		do {
			next = in.readUnsignedByte();
			value |= (next & 0x7F) << shift;
			shift += 7;
		} while ((next & 0x80) != 0 && shift < 35);
		return value;
	}
}
