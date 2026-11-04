package me.bewf.luceat.client.gui;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;

import me.bewf.luceat.client.gui.preview.Scene;
import me.bewf.luceat.client.gui.preview.SceneLibrary;
import me.bewf.luceat.client.gui.preview.Scenes;

import net.fabricmc.fabric.api.client.command.v2.ClientCommands;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.client.Minecraft;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

final class SceneCommands {
	private static final int MAX_SIDE = 128;
	private static final int MAX_CELLS = 1_500_000;
	private static final Map<String, int[]> ORIGINS = new HashMap<>();
	private static BlockPos first;
	private static BlockPos second;

	private SceneCommands() {
	}

	static LiteralArgumentBuilder<FabricClientCommandSource> tree() {
		return ClientCommands.literal("scene")
			.then(ClientCommands.literal("list").executes(context -> list()))
			.then(ClientCommands.literal("pos1").executes(context -> mark(true)))
			.then(ClientCommands.literal("pos2").executes(context -> mark(false)))
			.then(ClientCommands.literal("capture")
				.then(ClientCommands.argument("name", StringArgumentType.word()).executes(context -> capture(StringArgumentType.getString(context, "name")))))
			.then(ClientCommands.literal("shot")
				.then(ClientCommands.argument("name", StringArgumentType.word()).suggests((context, builder) -> SharedSuggestionProvider.suggest(SceneLibrary.names(), builder))
					.executes(context -> shot(StringArgumentType.getString(context, "name"), "default"))
					.then(ClientCommands.argument("shot", StringArgumentType.word()).executes(context -> shot(StringArgumentType.getString(context, "name"), StringArgumentType.getString(context, "shot"))))))
			.then(ClientCommands.literal("set")
				.then(ClientCommands.argument("name", StringArgumentType.word()).suggests((context, builder) -> SharedSuggestionProvider.suggest(SceneLibrary.names(), builder))
					.then(ClientCommands.argument("key", StringArgumentType.word()).suggests((context, builder) -> SharedSuggestionProvider.suggest(java.util.List.of("title", "caption", "time", "sun", "fog"), builder))
						.then(ClientCommands.argument("value", StringArgumentType.greedyString()).executes(context -> set(StringArgumentType.getString(context, "name"), StringArgumentType.getString(context, "key"), StringArgumentType.getString(context, "value")))))))
			.then(ClientCommands.literal("spawn")
				.then(ClientCommands.argument("name", StringArgumentType.word()).suggests((context, builder) -> SharedSuggestionProvider.suggest(SceneLibrary.names(), builder))
					.executes(context -> spawn(StringArgumentType.getString(context, "name")))))
			.then(ClientCommands.literal("duplicate")
				.then(ClientCommands.argument("from", StringArgumentType.word()).suggests((context, builder) -> SharedSuggestionProvider.suggest(SceneLibrary.names(), builder))
					.then(ClientCommands.argument("to", StringArgumentType.word()).executes(context -> duplicate(StringArgumentType.getString(context, "from"), StringArgumentType.getString(context, "to"))))))
			.then(ClientCommands.literal("bind")
				.then(ClientCommands.argument("setting", StringArgumentType.string())
					.then(ClientCommands.argument("scene", StringArgumentType.word()).suggests((context, builder) -> SharedSuggestionProvider.suggest(SceneLibrary.names(), builder))
						.executes(context -> bind(StringArgumentType.getString(context, "setting"), StringArgumentType.getString(context, "scene"), null))
						.then(ClientCommands.argument("shot", StringArgumentType.word()).executes(context -> bind(StringArgumentType.getString(context, "setting"), StringArgumentType.getString(context, "scene"), StringArgumentType.getString(context, "shot")))))))
			.then(ClientCommands.literal("pack").executes(context -> pack()))
			.then(ClientCommands.literal("export-builtin").executes(context -> exportBuiltin()))
			.then(ClientCommands.literal("reload").executes(context -> {
				SceneLibrary.reload();
				say("Scenes and bindings reloaded. Reopen the menu to see them.");
				return 1;
			}));
	}

	private static void say(String text) {
		Say.chat(Say.line(Say.text(text)));
	}

	private static void warn(String text) {
		Say.chat(Say.line(Say.colour(text, Say.WARN)));
	}

	private static int list() {
		var names = SceneLibrary.names();
		if (names.isEmpty()) {
			say("No scenes yet. Run /luceat scene export-builtin or capture one.");
			return 1;
		}
		for (String name : names) {
			int[] size = SceneLibrary.size(name);
			say(name + (size == null ? "" : "  " + size[0] + " x " + size[1] + " x " + size[2]));
		}
		return names.size();
	}

	private static BlockPos here(Minecraft minecraft) {
		if (minecraft.hitResult instanceof BlockHitResult hit && hit.getType() == HitResult.Type.BLOCK) {
			return hit.getBlockPos();
		}
		return minecraft.player.blockPosition();
	}

	private static int mark(boolean one) {
		Minecraft minecraft = Minecraft.getInstance();
		if (minecraft.player == null || minecraft.level == null) {
			return 0;
		}
		BlockPos pos = here(minecraft);
		if (one) {
			first = pos.immutable();
		} else {
			second = pos.immutable();
		}
		say((one ? "Corner 1 " : "Corner 2 ") + pos.getX() + ", " + pos.getY() + ", " + pos.getZ());
		return 1;
	}

	private static int capture(String name) {
		Minecraft minecraft = Minecraft.getInstance();
		Level level = minecraft.level;
		if (level == null || minecraft.player == null) {
			return 0;
		}
		if (first == null || second == null) {
			warn("Set both corners first with /luceat scene pos1 and pos2.");
			return 0;
		}
		int x0 = Math.min(first.getX(), second.getX());
		int y0 = Math.min(first.getY(), second.getY());
		int z0 = Math.min(first.getZ(), second.getZ());
		int w = Math.abs(first.getX() - second.getX()) + 1;
		int h = Math.abs(first.getY() - second.getY()) + 1;
		int d = Math.abs(first.getZ() - second.getZ()) + 1;
		if (w > MAX_SIDE || h > MAX_SIDE || d > MAX_SIDE || (long) w * h * d > MAX_CELLS) {
			warn("That box is " + w + " x " + h + " x " + d + ". The limit is " + MAX_SIDE + " per side and " + MAX_CELLS + " blocks.");
			return 0;
		}
		BlockState[] cells = new BlockState[w * h * d];
		BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
		for (int z = 0; z < d; z++) {
			for (int y = 0; y < h; y++) {
				for (int x = 0; x < w; x++) {
					BlockState state = level.getBlockState(pos.set(x0 + x, y0 + y, z0 + z));
					cells[x + w * (y + h * z)] = state.isAir() ? null : state;
				}
			}
		}
		try {
			SceneLibrary.write(SceneLibrary.folder().resolve(name + ".lscene"), w, h, d, cells);
			JsonObject meta = SceneLibrary.readMeta(name);
			if (!meta.has("title")) {
				meta.addProperty("title", name);
			}
			if (!meta.has("time")) {
				meta.addProperty("time", 0.37);
			}
			JsonArray origin = new JsonArray();
			origin.add(x0);
			origin.add(y0);
			origin.add(z0);
			meta.add("origin", origin);
			SceneLibrary.writeMeta(name, meta);
			ORIGINS.put(name, new int[]{x0, y0, z0});
			SceneLibrary.reload();
		} catch (IOException failure) {
			warn("Could not save the scene: " + failure.getMessage());
			return 0;
		}
		say("Saved " + name + " (" + w + " x " + h + " x " + d + "). Stand where the camera should be, look at the subject and run /luceat scene shot " + name + ".");
		return 1;
	}

	private static int shot(String name, String shotName) {
		Minecraft minecraft = Minecraft.getInstance();
		if (minecraft.player == null) {
			return 0;
		}
		int[] origin = ORIGINS.get(name);
		try {
			JsonObject meta = SceneLibrary.readMeta(name);
			if (origin == null && meta.has("origin")) {
				JsonArray stored = meta.getAsJsonArray("origin");
				origin = new int[]{stored.get(0).getAsInt(), stored.get(1).getAsInt(), stored.get(2).getAsInt()};
			}
			if (origin == null) {
				warn("Capture or spawn " + name + " first so I know where it sits in the world.");
				return 0;
			}
			Vec3 eye = minecraft.player.getEyePosition();
			Vec3 look = minecraft.player.getLookAngle();
			Vec3 target = eye.add(look.scale(14.0));
			if (minecraft.hitResult != null && minecraft.hitResult.getType() == HitResult.Type.BLOCK && minecraft.hitResult.getLocation().distanceTo(eye) > 2.0) {
				target = minecraft.hitResult.getLocation();
			}
			JsonObject shots = meta.has("shots") ? meta.getAsJsonObject("shots") : new JsonObject();
			JsonObject view = new JsonObject();
			view.add("eye", relative(eye, origin));
			view.add("target", relative(target, origin));
			view.addProperty("fov", 60);
			shots.add(shotName, view);
			meta.add("shots", shots);
			SceneLibrary.writeMeta(name, meta);
			SceneLibrary.reload();
		} catch (IOException failure) {
			warn("Could not save the shot: " + failure.getMessage());
			return 0;
		}
		say("Saved shot " + shotName + " for " + name + ".");
		return 1;
	}

	private static JsonArray relative(Vec3 point, int[] origin) {
		JsonArray out = new JsonArray();
		out.add(Math.round((point.x - origin[0]) * 100.0) / 100.0);
		out.add(Math.round((point.y - origin[1]) * 100.0) / 100.0);
		out.add(Math.round((point.z - origin[2]) * 100.0) / 100.0);
		return out;
	}

	private static int set(String name, String key, String value) {
		try {
			JsonObject meta = SceneLibrary.readMeta(name);
			switch (key) {
				case "title", "caption" -> meta.addProperty(key, value);
				case "time", "sun", "fog" -> meta.addProperty(key, Double.parseDouble(value.trim()));
				default -> {
					warn("Keys are title, caption, time, sun and fog.");
					return 0;
				}
			}
			SceneLibrary.writeMeta(name, meta);
			SceneLibrary.reload();
		} catch (IOException | NumberFormatException failure) {
			warn("Could not set that: " + failure.getMessage());
			return 0;
		}
		say(name + " " + key + " is now " + value + ".");
		return 1;
	}

	private static int spawn(String name) {
		Minecraft minecraft = Minecraft.getInstance();
		var server = minecraft.getSingleplayerServer();
		if (server == null || minecraft.level == null || minecraft.player == null) {
			warn("Spawning only works in a singleplayer world.");
			return 0;
		}
		int[] size = SceneLibrary.size(name);
		BlockState[] cells = SceneLibrary.cells(name);
		if (size == null || cells == null) {
			Scene builtin = Scenes.builtin(name);
			if (builtin == null) {
				warn("No scene named " + name + ".");
				return 0;
			}
			size = new int[]{builtin.width, builtin.height, builtin.depth};
			cells = builtin.cells;
		}
		final int[] extent = size;
		final BlockState[] blocks = cells;
		BlockPos base = minecraft.player.blockPosition().offset(3, 0, 3);
		var dimension = minecraft.level.dimension();
		server.execute(() -> {
			ServerLevel target = server.getLevel(dimension);
			if (target == null) {
				return;
			}
			BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
			for (int z = 0; z < extent[2]; z++) {
				for (int y = 0; y < extent[1]; y++) {
					for (int x = 0; x < extent[0]; x++) {
						BlockState state = blocks[x + extent[0] * (y + extent[1] * z)];
						target.setBlock(pos.set(base.getX() + x, base.getY() + y, base.getZ() + z), state == null ? Blocks.AIR.defaultBlockState() : state, 2);
					}
				}
			}
		});
		ORIGINS.put(name, new int[]{base.getX(), base.getY(), base.getZ()});
		say("Placing " + name + " from " + base.getX() + ", " + base.getY() + ", " + base.getZ() + ". Edit it, then run pos1, pos2 and capture " + name + " again.");
		return 1;
	}

	private static int duplicate(String from, String to) {
		try {
			SceneLibrary.copy(from, to);
		} catch (IOException failure) {
			warn("Could not copy: " + failure.getMessage());
			return 0;
		}
		say("Copied " + from + " to " + to + ".");
		return 1;
	}

	private static int bind(String setting, String scene, String shot) {
		if (!SceneLibrary.has(scene)) {
			warn("No scene named " + scene + ".");
			return 0;
		}
		try {
			SceneLibrary.bind(setting, scene, shot);
		} catch (IOException failure) {
			warn("Could not save previews.json: " + failure.getMessage());
			return 0;
		}
		say(setting + " now shows " + scene + (shot == null ? "" : " (" + shot + ")") + ".");
		return 1;
	}

	private static int pack() {
		JsonArray names = new JsonArray();
		for (String name : SceneLibrary.names()) {
			names.add(name);
		}
		try {
			java.nio.file.Files.writeString(SceneLibrary.folder().resolve("index.json"), names.toString());
		} catch (IOException failure) {
			warn("Could not write index.json: " + failure.getMessage());
			return 0;
		}
		say("Wrote index.json. Copy everything in " + SceneLibrary.folder() + " into src/client/resources/assets/luceat/scenes/ to ship these scenes.");
		return 1;
	}

	private static int exportBuiltin() {
		int written = 0;
		StringBuilder renamed = new StringBuilder();
		for (String id : Scenes.builtinIds()) {
			Scene scene = Scenes.builtin(id);
			if (scene == null) {
				continue;
			}
			String name = scene.hasCustomVoxels() ? id + "_blocks" : id;
			try {
				SceneLibrary.export(scene, name);
				written++;
				if (scene.hasCustomVoxels()) {
					renamed.append(renamed.length() == 0 ? "" : ", ").append(name);
				}
			} catch (IOException failure) {
				warn("Could not export " + id + ": " + failure.getMessage());
			}
		}
		SceneLibrary.reload();
		say("Exported " + written + " scenes to " + SceneLibrary.folder() + ".");
		if (renamed.length() > 0) {
			warn("These contain figures that only exist in the built-in version, so they were saved as copies: " + renamed + ".");
		}
		return written;
	}
}
