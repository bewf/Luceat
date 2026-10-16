package me.bewf.luceat.client.light;

import java.util.Arrays;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

import me.bewf.luceat.client.config.LuceatConfig;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

public final class PlacedLights {
	private static final int TOTAL = 125;
	private static final int PER_CELL = 40;
	private static final int MAX_CANDIDATES = 1024;
	private static final double MERGE_DISTANCE_SQUARED = 1.0;
	private static final float[] DEFAULT_COLOUR = {1.0F, 0.7F, 0.35F};
	private static final Direction[] SIDES = Direction.values();

	private static final float[][] positions = new float[TOTAL][PER_CELL * 3];
	private static final byte[][] levels = new byte[TOTAL][PER_CELL];
	private static final BlockState[][] states = new BlockState[TOTAL][PER_CELL];
	private static final int[] counts = new int[TOTAL];
	private static final Map<BlockState, float[]> colours = new IdentityHashMap<>();
	private static final BlockPos.MutableBlockPos probe = new BlockPos.MutableBlockPos();

	private static final int[] candidateCell = new int[MAX_CANDIDATES];
	private static final int[] candidateSlot = new int[MAX_CANDIDATES];
	private static final double[] candidateDistance = new double[MAX_CANDIDATES];
	private static final float[] acceptedX = new float[LightBuffer.MAX_LIGHTS];
	private static final float[] acceptedY = new float[LightBuffer.MAX_LIGHTS];
	private static final float[] acceptedZ = new float[LightBuffer.MAX_LIGHTS];

	private PlacedLights() {
	}

	public static void clear() {
		Arrays.fill(counts, 0);
		colours.clear();
	}

	public static void forget(int cell) {
		counts[cell] = 0;
	}

	public static void offer(ClientLevel level, int cell, int x, int y, int z, BlockState state) {
		int count = counts[cell];
		if (count >= PER_CELL) {
			return;
		}
		double openX = 0.0;
		double openY = 0.0;
		double openZ = 0.0;
		boolean exposed = false;
		for (Direction side : SIDES) {
			probe.set(x + side.getStepX(), y + side.getStepY(), z + side.getStepZ());
			if (!level.getBlockState(probe).isSolidRender()) {
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
		float[] slot = positions[cell];
		slot[count * 3] = (float) px;
		slot[count * 3 + 1] = (float) py;
		slot[count * 3 + 2] = (float) pz;
		levels[cell][count] = (byte) state.getLightEmission();
		states[cell][count] = state;
		counts[cell] = count + 1;
	}

	public static void collect(Vec3 camera, int limit, List<LightSource> out) {
		if (limit <= 0) {
			return;
		}
		LuceatConfig config = LuceatConfig.get();
		double range = config.lightRange;
		double rangeSquared = range * range;
		int total = 0;
		for (int cell = 0; cell < TOTAL && total < MAX_CANDIDATES; cell++) {
			int count = counts[cell];
			float[] slot = positions[cell];
			for (int i = 0; i < count && total < MAX_CANDIDATES; i++) {
				double dx = slot[i * 3] - camera.x;
				double dy = slot[i * 3 + 1] - camera.y;
				double dz = slot[i * 3 + 2] - camera.z;
				double distance = dx * dx + dy * dy + dz * dz;
				if (distance > rangeSquared) {
					continue;
				}
				candidateCell[total] = cell;
				candidateSlot[total] = i;
				candidateDistance[total] = distance;
				total++;
			}
		}
		int accepted = 0;
		limit = Math.min(limit, LightBuffer.MAX_LIGHTS);
		while (accepted < limit) {
			int best = -1;
			double bestDistance = Double.MAX_VALUE;
			for (int i = 0; i < total; i++) {
				if (candidateDistance[i] < bestDistance) {
					bestDistance = candidateDistance[i];
					best = i;
				}
			}
			if (best < 0) {
				break;
			}
			candidateDistance[best] = Double.MAX_VALUE;
			int cell = candidateCell[best];
			int slotIndex = candidateSlot[best];
			float x = positions[cell][slotIndex * 3];
			float y = positions[cell][slotIndex * 3 + 1];
			float z = positions[cell][slotIndex * 3 + 2];
			if (tooClose(acceptedX, acceptedY, acceptedZ, accepted, x, y, z)) {
				continue;
			}
			acceptedX[accepted] = x;
			acceptedY[accepted] = y;
			acceptedZ[accepted] = z;
			accepted++;
			float[] colour = colourOf(states[cell][slotIndex]);
			out.add(new LightSource(x, y, z, colour[0], colour[1], colour[2], levels[cell][slotIndex], true));
		}
	}

	private static boolean tooClose(float[] xs, float[] ys, float[] zs, int count, float x, float y, float z) {
		for (int i = 0; i < count; i++) {
			double dx = xs[i] - x;
			double dy = ys[i] - y;
			double dz = zs[i] - z;
			if (dx * dx + dy * dy + dz * dz < MERGE_DISTANCE_SQUARED) {
				return true;
			}
		}
		return false;
	}

	private static float[] colourOf(BlockState state) {
		if (!LuceatConfig.get().textureColours) {
			return DEFAULT_COLOUR;
		}
		return colours.computeIfAbsent(state, key -> {
			float[] estimate = ColourEstimator.estimateBlock(key);
			return estimate == null ? DEFAULT_COLOUR : estimate;
		});
	}
}
