package me.bewf.luceat.client.light;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;
import me.bewf.luceat.client.light.item.ItemLightRule;
import me.bewf.luceat.client.light.item.ItemLightRules;

import me.bewf.luceat.client.config.LuceatConfig;
import me.bewf.luceat.client.light.block.BlockRule;
import me.bewf.luceat.client.light.block.BlockRules;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

// finds every placed light and keeps the ones that matter for the screen
public final class PlacedLights {
	private static final int SECTION = 16;
	private static final int MAX_SCANS = 24;
	private static final int SWEEP_FRAMES = 20;
	private static final int MAX_CANDIDATES = 16384;
	private static final int TABLE = 8192;
	private static final double CLOSE = 7.0;
	private static final double SETTLE = 3.0;
	private static final int[] SIZES = {0, 2, 4, 8};
	private static final int DENSE_HARD = 24;
	private static final int MASSIVE = 40;
	private static final float KEEP_BONUS = 1.3F;
	private static final double SECTION_RADIUS = 14.0;
	private static final float[] DEFAULT_COLOUR = {1.0F, 0.7F, 0.35F};
	private static final Direction[] SIDES = Direction.values();

	private static final class Section {
		int count;
		float[] x = new float[4];
		float[] y = new float[4];
		float[] z = new float[4];
		float[] red = new float[4];
		float[] green = new float[4];
		float[] blue = new float[4];
		float[] level = new float[4];
		boolean[] fire = new boolean[4];
		boolean[] noShadow = new boolean[4];
		byte[] vanilla = new byte[4];
		byte[] tier = new byte[4];

		void add(float px, float py, float pz, float[] colour, float power, boolean flame, boolean skipShadow, int natural) {
			if (count == x.length) {
				int size = count * 2;
				x = Arrays.copyOf(x, size);
				y = Arrays.copyOf(y, size);
				z = Arrays.copyOf(z, size);
				red = Arrays.copyOf(red, size);
				green = Arrays.copyOf(green, size);
				blue = Arrays.copyOf(blue, size);
				level = Arrays.copyOf(level, size);
				fire = Arrays.copyOf(fire, size);
				noShadow = Arrays.copyOf(noShadow, size);
				vanilla = Arrays.copyOf(vanilla, size);
				tier = Arrays.copyOf(tier, size);
			}
			x[count] = px;
			y[count] = py;
			z[count] = pz;
			red[count] = colour[0];
			green[count] = colour[1];
			blue[count] = colour[2];
			level[count] = power;
			fire[count] = flame;
			noShadow[count] = skipShadow;
			vanilla[count] = (byte) Math.min(15, Math.max(0, natural));
			count++;
		}
	}

	private static final Map<Long, Section> SECTIONS = new HashMap<>();
	private static final Set<Long> dirty = new HashSet<>();
	private static final List<long[]> queue = new ArrayList<>();
	private static final Map<BlockState, float[]> colours = new IdentityHashMap<>();
	private static final BlockPos.MutableBlockPos probe = new BlockPos.MutableBlockPos();
	private static int minLevel = 1;
	private static final Predicate<BlockState> EMITTER = state -> BlockRules.emission(state) >= minLevel;


	private static final float[] cx = new float[MAX_CANDIDATES];
	private static final float[] cy = new float[MAX_CANDIDATES];
	private static final float[] cz = new float[MAX_CANDIDATES];
	private static final float[] cr = new float[MAX_CANDIDATES];
	private static final float[] cg = new float[MAX_CANDIDATES];
	private static final float[] cb = new float[MAX_CANDIDATES];
	private static final float[] cl = new float[MAX_CANDIDATES];
	private static final boolean[] cf = new boolean[MAX_CANDIDATES];
	private static final boolean[] cn = new boolean[MAX_CANDIDATES];
	private static final int[] cv = new int[MAX_CANDIDATES];
	private static final long[] cid = new long[MAX_CANDIDATES];
	private static final long[] ck = new long[MAX_CANDIDATES];
	private static long[] lastPicked = new long[0];
	private static final float[] cs = new float[MAX_CANDIDATES];
	private static final long[] order = new long[MAX_CANDIDATES];

	private static final long[] tableKey = new long[TABLE];
	private static final boolean[] tableUsed = new boolean[TABLE];
	private static final float[] sumW = new float[TABLE];
	private static final float[] sumX = new float[TABLE];
	private static final float[] sumY = new float[TABLE];
	private static final float[] sumZ = new float[TABLE];
	private static final float[] sumR = new float[TABLE];
	private static final float[] sumG = new float[TABLE];
	private static final float[] sumB = new float[TABLE];
	private static final float[] maxLevel = new float[TABLE];
	private static final int[] members = new int[TABLE];
	private static final int[] natural = new int[TABLE];
	private static final boolean[] open = new boolean[TABLE];
	private static final int[] spans = new int[TABLE];
	private static final boolean[] flame = new boolean[TABLE];
	private static final int[] touched = new int[TABLE];

	private static int frame;
	private static int sweepCellX = Integer.MIN_VALUE;
	private static int sweepCellY;
	private static int sweepCellZ;
	private static int version;
	private static ClientLevel lastLevel;

	private static List<LightSource> cached = List.of();
	private static int cachedVersion = -1;
	private static int cachedLimit = -1;
	private static int cachedSignature;
	private static double cachedX;
	private static double cachedY;
	private static double cachedZ;
	private static double cachedFx;
	private static double cachedFy;
	private static double cachedFz;

	private PlacedLights() {
	}

	public static void clear() {
		SECTIONS.clear();
		dirty.clear();
		queue.clear();
		colours.clear();
		sweepCellX = Integer.MIN_VALUE;
		version++;
	}

	public static int sectionCount() {
		return SECTIONS.size();
	}

	public static void markDirty(BlockPos pos) {
		dirty.add(key(Math.floorDiv(pos.getX(), SECTION), Math.floorDiv(pos.getY(), SECTION), Math.floorDiv(pos.getZ(), SECTION)));
	}

	public static void update(ClientLevel level, Vec3 camera) {
		if (level == null) {
			return;
		}
		LuceatConfig config = LuceatConfig.get();
		if (level != lastLevel) {
			lastLevel = level;
			clear();
		}
		minLevel = config.placedMinLevel;
		frame++;
		int sx = Math.floorDiv((int) Math.floor(camera.x), SECTION);
		int sy = Math.floorDiv((int) Math.floor(camera.y), SECTION);
		int sz = Math.floorDiv((int) Math.floor(camera.z), SECTION);
		boolean moved = sx != sweepCellX || sy != sweepCellY || sz != sweepCellZ;
		if (moved || frame % SWEEP_FRAMES == 0 || !dirty.isEmpty()) {
			sweep(level, sx, sy, sz, config);
			sweepCellX = sx;
			sweepCellY = sy;
			sweepCellZ = sz;
		}
		long started = System.nanoTime();
		int scans = 0;
		long budget = (long) (config.scanBudget * 1_000_000.0F);
		while (scans < MAX_SCANS && !queue.isEmpty() && (scans == 0 || System.nanoTime() - started < budget)) {
			long[] next = queue.remove(queue.size() - 1);
			scan(level, (int) next[0], (int) next[1], (int) next[2]);
			scans++;
		}
	}

	private static void sweep(ClientLevel level, int sx, int sy, int sz, LuceatConfig config) {
		int reach = (int) Math.ceil(Math.min(config.lightRange, LightGrid.REACH_XZ) / (double) SECTION) + 1;
		int reachY = (int) Math.ceil(LightGrid.REACH_Y / (double) SECTION) + 1;
		int lowest = level.getMinSectionY();
		int highest = level.getMaxSectionY();
		queue.clear();
		Set<Long> queued = new HashSet<>();
		for (int dx = -reach; dx <= reach; dx++) {
			for (int dz = -reach; dz <= reach; dz++) {
				for (int dy = -reachY; dy <= reachY; dy++) {
					int y = sy + dy;
					if (y < lowest || y > highest) {
						continue;
					}
					long key = key(sx + dx, y, sz + dz);
					if (SECTIONS.containsKey(key) && !dirty.contains(key)) {
						continue;
					}
					if (queued.add(key)) {
						queue.add(new long[]{sx + dx, y, sz + dz, dx * dx + dy * dy + dz * dz});
					}
				}
			}
		}
		// nearest last so removing from the end scans nearest first
		queue.sort((a, b) -> Long.compare(b[3], a[3]));
		dirty.clear();
		int evict = 0;
		var iterator = SECTIONS.keySet().iterator();
		while (iterator.hasNext()) {
			long key = iterator.next();
			if (Math.abs(keyX(key) - sx) > reach + 2 || Math.abs(keyY(key) - sy) > reachY + 2 || Math.abs(keyZ(key) - sz) > reach + 2) {
				iterator.remove();
				evict++;
			}
		}
		if (evict > 0) {
			version++;
		}
	}

	private static void scan(ClientLevel level, int sx, int sy, int sz) {
		LevelChunk chunk = level.getChunkSource().getChunkNow(sx, sz);
		if (chunk == null) {
			return;
		}
		int index = chunk.getSectionIndexFromSectionY(sy);
		if (index < 0 || index >= chunk.getSections().length) {
			SECTIONS.put(key(sx, sy, sz), new Section());
			version++;
			return;
		}
		LevelChunkSection section = chunk.getSection(index);
		Section found = new Section();
		if (!section.hasOnlyAir() && section.maybeHas(EMITTER)) {
			int originX = sx * SECTION;
			int originY = sy * SECTION;
			int originZ = sz * SECTION;
			for (int y = 0; y < SECTION; y++) {
				for (int z = 0; z < SECTION; z++) {
					for (int x = 0; x < SECTION; x++) {
						BlockState state = section.getBlockState(x, y, z);
						if (BlockRules.emission(state) >= minLevel) {
							offer(level, found, originX + x, originY + y, originZ + z, state);
						}
					}
				}
			}
		}
		SECTIONS.put(key(sx, sy, sz), found);
		version++;
	}

	private static final Map<Block, Boolean> SENSITIVE = new IdentityHashMap<>();

	private static boolean waterSensitive(Block block) {
		return SENSITIVE.computeIfAbsent(block, key -> {
			if (key.asItem() == net.minecraft.world.item.Items.AIR) {
				return false;
			}
			for (ItemLightRule found : ItemLightRules.matching(new ItemStack(key.asItem()))) {
				if (found.waterSensitive()) {
					return true;
				}
			}
			return false;
		});
	}

	private static void offer(ClientLevel level, Section into, int x, int y, int z, BlockState state) {
		BlockRule rule = BlockRules.rule(state);
		if (rule != null && !rule.whenPlaced) {
			return;
		}
		if (!state.getFluidState().isEmpty()) {
			boolean out = rule != null && rule.water >= 0 ? rule.water == 1 : LuceatConfig.get().waterPutsOutLights && waterSensitive(state.getBlock());
			if (out) {
				return;
			}
		}
		boolean buried = LuceatConfig.get().skipBuried;
		double openX = 0.0;
		double openY = 0.0;
		double openZ = 0.0;
		boolean exposed = false;
		for (Direction side : SIDES) {
			probe.set(x + side.getStepX(), y + side.getStepY(), z + side.getStepZ());
			BlockState next = level.getBlockState(probe);
			if (!next.isSolidRender() && !(buried && next.is(state.getBlock()))) {
				exposed = true;
				openX += side.getStepX();
				openY += side.getStepY();
				openZ += side.getStepZ();
			}
		}
		if (!exposed) {
			return;
		}
		double px = x + 0.5;
		double py = y + 0.5;
		double pz = z + 0.5;
		double length = Math.sqrt(openX * openX + openY * openY + openZ * openZ);
		if (state.isSolidRender() && length > 1.0E-6) {
			px += openX / length * 0.62;
			py += openY / length * 0.62;
			pz += openZ / length * 0.62;
		}
		float power = Math.min(24.0F, BlockRules.emission(state) * LuceatConfig.get().placedReach);
		boolean flames = LightSource.warm(colourOf(state)[0], colourOf(state)[2], power);
		if (rule != null) {
			power = Math.min(24.0F, power * rule.radius);
			if (rule.flicker >= 0) {
				flames = rule.flicker > 0;
			}
		}
		boolean skip = BlockRules.noShadow(state.getBlock(), rule);
		into.add((float) px, (float) py, (float) pz, colourOf(state), power, flames, skip, state.getLightEmission());
	}

	private static float[] colourOf(BlockState state) {
		float[] forced = BlockRules.colour(state);
		if (forced != null) {
			return forced;
		}
		if (!LuceatConfig.get().textureColours) {
			return DEFAULT_COLOUR;
		}
		return colours.computeIfAbsent(state, key -> {
			float[] estimate = ColourEstimator.estimateBlock(key);
			return estimate == null ? DEFAULT_COLOUR : estimate;
		});
	}

	public static void collect(Vec3 camera, int limit, List<LightSource> out) {
		if (limit <= 0) {
			return;
		}
		LuceatConfig config = LuceatConfig.get();
		int signature = config.lightRange * 31 + (config.mergeFarLights ? 1 : 0) + config.placedMinLevel * 7 + Math.round(config.placedReach * 20.0F) * 131 + (config.placedLights ? 3 : 0) + (config.clusterLights ? 977 : 0) + config.clusterSize * 4099 + config.joinBeyond * 8191;
		double fx = LightCull.forwardX();
		double fy = LightCull.forwardY();
		double fz = LightCull.forwardZ();
		double moved = Math.abs(camera.x - cachedX) + Math.abs(camera.y - cachedY) + Math.abs(camera.z - cachedZ);
		double turned = fx * cachedFx + fy * cachedFy + fz * cachedFz;
		if (version == cachedVersion && limit == cachedLimit && signature == cachedSignature && moved < 1.5 && turned > 0.99) {
			out.addAll(cached);
			return;
		}
		cached = build(camera, limit, config);
		cachedVersion = version;
		cachedLimit = limit;
		cachedSignature = signature;
		cachedX = camera.x;
		cachedY = camera.y;
		cachedZ = camera.z;
		cachedFx = fx;
		cachedFy = fy;
		cachedFz = fz;
		out.addAll(cached);
	}

	private static List<LightSource> build(Vec3 camera, int limit, LuceatConfig config) {
		double range = Math.min(config.lightRange, LightGrid.REACH_XZ);
		int total = 0;
		int groups = 0;
		for (var entry : SECTIONS.entrySet()) {
			Section section = entry.getValue();
			if (section.count == 0) {
				continue;
			}
			long key = entry.getKey();
			double mx = keyX(key) * SECTION + 8.0 - camera.x;
			double my = keyY(key) * SECTION + 8.0 - camera.y;
			double mz = keyZ(key) * SECTION + 8.0 - camera.z;
			double centre = Math.sqrt(mx * mx + my * my + mz * mz);
			if (centre - SECTION_RADIUS > range || Math.abs(my) - SECTION_RADIUS > LightGrid.REACH_Y + 15.0) {
				continue;
			}
			if (!LightCull.sees(camera.x + mx, camera.y + my, camera.z + mz, SECTION_RADIUS + 15.0)) {
				continue;
			}
			int crowd = config.clusterLights ? config.clusterSize : Integer.MAX_VALUE;
			boolean dense = section.count > (config.mergeFarLights ? crowd : Math.max(crowd, DENSE_HARD));
			boolean massive = dense && section.count > MASSIVE;
			for (int i = 0; i < section.count; i++) {
				double dx = section.x[i] - camera.x;
				double dy = section.y[i] - camera.y;
				double dz = section.z[i] - camera.z;
				double distance = Math.sqrt(dx * dx + dy * dy + dz * dz);
				float level = section.level[i];
				if (distance > range || Math.abs(dy) > LightGrid.REACH_Y + level) {
					continue;
				}
				int tier = settle(section, i, distance, config.mergeFarLights, dense, massive, config.joinBeyond);
				if (tier == 0 && !LightCull.sees(section.x[i], section.y[i], section.z[i], level)) {
					continue;
				}
				if (tier == 0) {
					if (total < MAX_CANDIDATES) {
						cx[total] = section.x[i];
						cy[total] = section.y[i];
						cz[total] = section.z[i];
						cr[total] = section.red[i];
						cg[total] = section.green[i];
						cb[total] = section.blue[i];
						cl[total] = level;
						cf[total] = section.fire[i];
						cn[total] = section.noShadow[i];
						cv[total] = section.vanilla[i];
						cid[total] = 0L;
						ck[total] = key((int) Math.floor(section.x[i]), (int) Math.floor(section.y[i]), (int) Math.floor(section.z[i]));
						cs[total] = score(level, distance) * kept(ck[total]);
						total++;
					}
				} else {
					groups = join(groups, section, i, tier);
				}
			}
		}
		for (int g = 0; g < groups && total < MAX_CANDIDATES; g++) {
			int slot = touched[g];
			float weight = sumW[slot];
			double px = sumX[slot] / weight;
			double py = sumY[slot] / weight;
			double pz = sumZ[slot] / weight;
			double dx = px - camera.x;
			double dy = py - camera.y;
			double dz = pz - camera.z;
			double distance = Math.sqrt(dx * dx + dy * dy + dz * dz);
			float level = Math.min(Math.max(15.0F, maxLevel[slot]), maxLevel[slot] + Math.min(4.5F, 1.2F * (float) (Math.log(members[slot]) / Math.log(2.0))));
			cx[total] = (float) px;
			cy[total] = (float) py;
			cz[total] = (float) pz;
			cr[total] = sumR[slot] / weight;
			cg[total] = sumG[slot] / weight;
			cb[total] = sumB[slot] / weight;
			cl[total] = level;
			cf[total] = flame[slot];
			cn[total] = true;
			cv[total] = open[slot] ? 0 : Math.max(0, natural[slot] - spans[slot]);
			cid[total] = LightSmoother.key(6, (int) (tableKey[slot] ^ (tableKey[slot] >>> 32)), (int) (tableKey[slot] >>> 62));
			ck[total] = tableKey[slot];
			cs[total] = score(level, distance) * kept(ck[total]);
			total++;
		}
		for (int g = 0; g < groups; g++) {
			tableUsed[touched[g]] = false;
		}
		int take = Math.min(limit, total);
		for (int i = 0; i < total; i++) {
			order[i] = ((long) Float.floatToIntBits(cs[i]) << 32) | i;
		}
		Arrays.sort(order, 0, total);
		boolean[] picked = new boolean[total];
		for (int k = 0; k < take; k++) {
			picked[(int) (order[total - 1 - k] & 0xFFFFFFFFL)] = true;
		}
		List<LightSource> result = new ArrayList<>(take);
		long[] keys = new long[take];
		int written = 0;
		for (int i = 0; i < total; i++) {
			if (picked[i]) {
				result.add(new LightSource(cx[i], cy[i], cz[i], cr[i], cg[i], cb[i], cl[i], true, cf[i], cid[i], cn[i], cv[i]));
				keys[written++] = ck[i];
			}
		}
		Arrays.sort(keys, 0, written);
		lastPicked = Arrays.copyOf(keys, written);
		return result;
	}

	private static float kept(long candidate) {
		return Arrays.binarySearch(lastPicked, candidate) >= 0 ? KEEP_BONUS : 1.0F;
	}

	private static int wanted(double distance, boolean merge, boolean dense, boolean massive, double near) {
		int tier = 0;
		if (merge) {
			tier = distance <= near ? 0 : distance <= near * 2.0 ? 2 : 3;
		}
		if (dense) {
			tier = Math.max(tier, massive ? (distance <= CLOSE ? 2 : 3) : (distance <= CLOSE ? 1 : 2));
		}
		return tier;
	}

	private static int settle(Section section, int i, double distance, boolean merge, boolean dense, boolean massive, double near) {
		int low = wanted(distance - SETTLE, merge, dense, massive, near);
		int high = wanted(distance + SETTLE, merge, dense, massive, near);
		int tier = section.tier[i];
		if (tier < low) {
			tier = low;
		} else if (tier > high) {
			tier = high;
		}
		section.tier[i] = (byte) tier;
		return tier;
	}

	private static float score(float level, double distance) {
		return (float) (level * level / (distance * distance + 16.0) + 0.001 * level);
	}

	private static int join(int groups, Section section, int i, int tier) {
		int size = SIZES[tier];
		long key = ((long) tier << 62)
			^ ((long) (Math.floorDiv((int) Math.floor(section.x[i]), size) & 0xFFFFF) << 40)
			^ ((long) (Math.floorDiv((int) Math.floor(section.y[i]), size) & 0xFFFFF) << 20)
			^ (long) (Math.floorDiv((int) Math.floor(section.z[i]), size) & 0xFFFFF);
		int slot = (int) ((key * 0x9E3779B97F4A7C15L) >>> 51) & (TABLE - 1);
		for (int probes = 0; probes < TABLE; probes++) {
			if (!tableUsed[slot]) {
				if (groups >= TABLE / 2) {
					return groups;
				}
				tableUsed[slot] = true;
				tableKey[slot] = key;
				sumW[slot] = 0.0F;
				sumX[slot] = 0.0F;
				sumY[slot] = 0.0F;
				sumZ[slot] = 0.0F;
				sumR[slot] = 0.0F;
				sumG[slot] = 0.0F;
				sumB[slot] = 0.0F;
				maxLevel[slot] = 0.0F;
				members[slot] = 0;
				natural[slot] = 0;
				open[slot] = false;
				spans[slot] = size;
				flame[slot] = false;
				touched[groups++] = slot;
				break;
			}
			if (tableKey[slot] == key) {
				break;
			}
			slot = (slot + 1) & (TABLE - 1);
		}
		float weight = section.level[i];
		sumW[slot] += weight;
		sumX[slot] += section.x[i] * weight;
		sumY[slot] += section.y[i] * weight;
		sumZ[slot] += section.z[i] * weight;
		sumR[slot] += section.red[i] * weight;
		sumG[slot] += section.green[i] * weight;
		sumB[slot] += section.blue[i] * weight;
		maxLevel[slot] = Math.max(maxLevel[slot], section.level[i]);
		members[slot]++;
		natural[slot] = Math.max(natural[slot], section.vanilla[i]);
		open[slot] |= section.vanilla[i] == 0;
		flame[slot] |= section.fire[i];
		return groups;
	}

	private static long key(int x, int y, int z) {
		return ((long) (x & 0x1FFFFF) << 42) | ((long) (y & 0x1FFFFF) << 21) | (z & 0x1FFFFF);
	}

	private static int keyX(long key) {
		return (int) (key >> 42) << 11 >> 11;
	}

	private static int keyY(long key) {
		return (int) (key >> 21 & 0x1FFFFF) << 11 >> 11;
	}

	private static int keyZ(long key) {
		return (int) (key & 0x1FFFFF) << 11 >> 11;
	}
}
