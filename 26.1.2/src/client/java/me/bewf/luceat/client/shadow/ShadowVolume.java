package me.bewf.luceat.client.shadow;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL12;
import org.lwjgl.opengl.GL13;
import org.lwjgl.opengl.GL20;
import org.lwjgl.opengl.GL30;

import me.bewf.luceat.client.light.PlacedLights;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.phys.Vec3;

public final class ShadowVolume {
	public static final int CELLS = 9;
	public static final int CELL_SIZE = 16;
	public static final int DIMENSION = CELLS * CELL_SIZE;
	public static final int HALF = CELLS / 2;
	public static final int RELIABLE = HALF * CELL_SIZE;
	public static final String SAMPLER = "u_LuceatVoxels";
	public static final String SHINE_SAMPLER = "u_LuceatShine";
	public static final String BRICK_SAMPLER = "u_LuceatBricks";
	public static final int BRICK = 4;
	public static final int BRICK_DIM = DIMENSION / BRICK;

	public static final int UNIT = 14;
	private static final int SHINE_UNIT = 11;
	private static final int BRICK_UNIT = 10;
	private static final int PER_CELL = CELL_SIZE / BRICK;
	private static final int TOTAL = CELLS * CELLS * CELLS;
	private static final int DIRTY_PER_FRAME = 8;
	private static final int REFRESH_INTERVAL = 20;
	private static final int[] UNPACK = {
		GL11.GL_UNPACK_ALIGNMENT, GL11.GL_UNPACK_ROW_LENGTH, GL11.GL_UNPACK_SKIP_ROWS,
		GL11.GL_UNPACK_SKIP_PIXELS, GL12.GL_UNPACK_IMAGE_HEIGHT, GL12.GL_UNPACK_SKIP_IMAGES
	};

	private static final int[] cellX = new int[TOTAL];
	private static final int[] cellY = new int[TOTAL];
	private static final int[] cellZ = new int[TOTAL];
	private static final boolean[] valid = new boolean[TOTAL];
	private static final long[] sums = new long[TOTAL];
	private static final Set<Long> dirty = new HashSet<>();
	private static final ByteBuffer cellData = BufferUtils.createByteBuffer(CELL_SIZE * CELL_SIZE * CELL_SIZE * 2).order(ByteOrder.nativeOrder());
	private static final ByteBuffer shineData = BufferUtils.createByteBuffer(CELL_SIZE * CELL_SIZE * CELL_SIZE);
	private static final ByteBuffer emptyIds = BufferUtils.createByteBuffer(CELL_SIZE * CELL_SIZE * CELL_SIZE * 2);
	private static final ByteBuffer brickData = BufferUtils.createByteBuffer(PER_CELL * PER_CELL * PER_CELL);
	private static final ByteBuffer emptyBricks = BufferUtils.createByteBuffer(PER_CELL * PER_CELL * PER_CELL);
	private static final ByteBuffer emptyShine = BufferUtils.createByteBuffer(CELL_SIZE * CELL_SIZE * CELL_SIZE);

	private static ClientLevel level;
	private static int texture;
	private static int brickTexture;
	private static int shineTexture;
	private static int frame;
	private static int refreshCursor;
	private static int centreX;
	private static int centreY;
	private static int centreZ;
	private static int filledX = Integer.MIN_VALUE;
	private static int filledY;
	private static int filledZ;
	private static boolean complete;
	private static int revision;

	private ShadowVolume() {
	}

	public static int texture() {
		return texture;
	}

	public static int revision() {
		return revision;
	}

	public static int centreX() {
		return centreX;
	}

	public static int centreY() {
		return centreY;
	}

	public static int centreZ() {
		return centreZ;
	}

	public static long bytes() {
		return texture == 0 ? 0L : (long) DIMENSION * DIMENSION * DIMENSION * 3L + (long) BRICK_DIM * BRICK_DIM * BRICK_DIM;
	}

	public static void reset() {
		Arrays.fill(valid, false);
		complete = false;
		dirty.clear();
		Occluders.clear();
		Shine.clear();
		PatternAtlas.clear();
		PlacedLights.clear();
	}

	public static void markDirty(BlockPos pos) {
		dirty.add(key(Math.floorDiv(pos.getX(), CELL_SIZE), Math.floorDiv(pos.getY(), CELL_SIZE), Math.floorDiv(pos.getZ(), CELL_SIZE)));
	}

	public static void update(ClientLevel clientLevel, Vec3 camera) {
		if (clientLevel == null) {
			return;
		}
		if (clientLevel != level) {
			level = clientLevel;
			reset();
		}
		ensureTexture();
		centreX = Math.floorDiv((int) Math.floor(camera.x), CELL_SIZE);
		centreY = Math.floorDiv((int) Math.floor(camera.y), CELL_SIZE);
		centreZ = Math.floorDiv((int) Math.floor(camera.z), CELL_SIZE);
		bindForUpload(() -> {
			fillMissing(centreX, centreY, centreZ);
			refreshDirty();
			refreshRotating();
		});
	}

	public static void bind(int program) {
		if (texture == 0) {
			return;
		}
		int location = GL20.glGetUniformLocation(program, SAMPLER);
		if (location < 0) {
			return;
		}
		GL20.glUniform1i(location, UNIT);
		int previousUnit = GL11.glGetInteger(GL13.GL_ACTIVE_TEXTURE);
		GL13.glActiveTexture(GL13.GL_TEXTURE0 + UNIT);
		GL11.glBindTexture(GL12.GL_TEXTURE_3D, texture);
		int shineLocation = GL20.glGetUniformLocation(program, SHINE_SAMPLER);
		if (shineLocation >= 0) {
			GL20.glUniform1i(shineLocation, SHINE_UNIT);
			GL13.glActiveTexture(GL13.GL_TEXTURE0 + SHINE_UNIT);
			GL11.glBindTexture(GL12.GL_TEXTURE_3D, shineTexture);
		}
		int brickLocation = GL20.glGetUniformLocation(program, BRICK_SAMPLER);
		if (brickLocation >= 0) {
			GL20.glUniform1i(brickLocation, BRICK_UNIT);
			GL13.glActiveTexture(GL13.GL_TEXTURE0 + BRICK_UNIT);
			GL11.glBindTexture(GL12.GL_TEXTURE_3D, brickTexture);
		}
		GL13.glActiveTexture(previousUnit);
	}

	private static void ensureTexture() {
		if (texture != 0) {
			return;
		}
		int previousUnit = GL11.glGetInteger(GL13.GL_ACTIVE_TEXTURE);
		GL13.glActiveTexture(GL13.GL_TEXTURE0 + UNIT);
		texture = GL11.glGenTextures();
		GL11.glBindTexture(GL12.GL_TEXTURE_3D, texture);
		GL11.glTexParameteri(GL12.GL_TEXTURE_3D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_NEAREST);
		GL11.glTexParameteri(GL12.GL_TEXTURE_3D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_NEAREST);
		GL11.glTexParameteri(GL12.GL_TEXTURE_3D, GL11.GL_TEXTURE_WRAP_S, GL12.GL_CLAMP_TO_EDGE);
		GL11.glTexParameteri(GL12.GL_TEXTURE_3D, GL11.GL_TEXTURE_WRAP_T, GL12.GL_CLAMP_TO_EDGE);
		GL11.glTexParameteri(GL12.GL_TEXTURE_3D, GL12.GL_TEXTURE_WRAP_R, GL12.GL_CLAMP_TO_EDGE);
		GL12.glTexImage3D(GL12.GL_TEXTURE_3D, 0, GL30.GL_R16UI, DIMENSION, DIMENSION, DIMENSION, 0, GL30.GL_RED_INTEGER, GL11.GL_UNSIGNED_SHORT, (ByteBuffer) null);
		GL13.glActiveTexture(GL13.GL_TEXTURE0 + SHINE_UNIT);
		shineTexture = GL11.glGenTextures();
		GL11.glBindTexture(GL12.GL_TEXTURE_3D, shineTexture);
		GL11.glTexParameteri(GL12.GL_TEXTURE_3D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_NEAREST);
		GL11.glTexParameteri(GL12.GL_TEXTURE_3D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_NEAREST);
		GL11.glTexParameteri(GL12.GL_TEXTURE_3D, GL11.GL_TEXTURE_WRAP_S, GL12.GL_CLAMP_TO_EDGE);
		GL11.glTexParameteri(GL12.GL_TEXTURE_3D, GL11.GL_TEXTURE_WRAP_T, GL12.GL_CLAMP_TO_EDGE);
		GL11.glTexParameteri(GL12.GL_TEXTURE_3D, GL12.GL_TEXTURE_WRAP_R, GL12.GL_CLAMP_TO_EDGE);
		GL12.glTexImage3D(GL12.GL_TEXTURE_3D, 0, GL30.GL_R8, DIMENSION, DIMENSION, DIMENSION, 0, GL11.GL_RED, GL11.GL_UNSIGNED_BYTE, (ByteBuffer) null);
		GL13.glActiveTexture(GL13.GL_TEXTURE0 + BRICK_UNIT);
		brickTexture = GL11.glGenTextures();
		GL11.glBindTexture(GL12.GL_TEXTURE_3D, brickTexture);
		GL11.glTexParameteri(GL12.GL_TEXTURE_3D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_NEAREST);
		GL11.glTexParameteri(GL12.GL_TEXTURE_3D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_NEAREST);
		GL11.glTexParameteri(GL12.GL_TEXTURE_3D, GL11.GL_TEXTURE_WRAP_S, GL12.GL_CLAMP_TO_EDGE);
		GL11.glTexParameteri(GL12.GL_TEXTURE_3D, GL11.GL_TEXTURE_WRAP_T, GL12.GL_CLAMP_TO_EDGE);
		GL11.glTexParameteri(GL12.GL_TEXTURE_3D, GL12.GL_TEXTURE_WRAP_R, GL12.GL_CLAMP_TO_EDGE);
		GL12.glTexImage3D(GL12.GL_TEXTURE_3D, 0, GL30.GL_R8, BRICK_DIM, BRICK_DIM, BRICK_DIM, 0, GL11.GL_RED, GL11.GL_UNSIGNED_BYTE, (ByteBuffer) null);
		GL13.glActiveTexture(previousUnit);
	}

	private static void bindForUpload(Runnable work) {
		int previousUnit = GL11.glGetInteger(GL13.GL_ACTIVE_TEXTURE);
		int[] previousStore = new int[UNPACK.length];
		for (int i = 0; i < UNPACK.length; i++) {
			previousStore[i] = GL11.glGetInteger(UNPACK[i]);
			GL11.glPixelStorei(UNPACK[i], i == 0 ? 1 : 0);
		}
		GL13.glActiveTexture(GL13.GL_TEXTURE0 + UNIT);
		GL11.glBindTexture(GL12.GL_TEXTURE_3D, texture);
		work.run();
		GL13.glActiveTexture(previousUnit);
		for (int i = 0; i < UNPACK.length; i++) {
			GL11.glPixelStorei(UNPACK[i], previousStore[i]);
		}
	}

	private static void fillMissing(int centreX, int centreY, int centreZ) {
		if (complete && centreX == filledX && centreY == filledY && centreZ == filledZ) {
			return;
		}
		List<int[]> missing = null;
		for (int dx = -HALF; dx <= HALF; dx++) {
			for (int dy = -HALF; dy <= HALF; dy++) {
				for (int dz = -HALF; dz <= HALF; dz++) {
					int x = centreX + dx;
					int y = centreY + dy;
					int z = centreZ + dz;
					if (holds(x, y, z)) {
						continue;
					}
					if (missing == null) {
						missing = new ArrayList<>();
					}
					missing.add(new int[]{x, y, z, dx * dx + dy * dy + dz * dz});
				}
			}
		}
		if (missing == null) {
			complete = true;
			filledX = centreX;
			filledY = centreY;
			filledZ = centreZ;
			return;
		}
		complete = false;
		missing.sort((a, b) -> Integer.compare(a[3], b[3]));
		int fills = me.bewf.luceat.client.config.LuceatConfig.get().shadowUpdates;
		int want = missing.size() > 30 ? fills * 2 : fills;
		int done = 0;
		for (int i = 0; i < missing.size() && done < want; i++) {
			int[] cell = missing.get(i);
			if (scan(cell[0], cell[1], cell[2])) {
				done++;
			}
		}
	}

	private static void refreshDirty() {
		if (dirty.isEmpty()) {
			return;
		}
		int budget = DIRTY_PER_FRAME;
		Iterator<Long> iterator = dirty.iterator();
		while (iterator.hasNext() && budget > 0) {
			long key = iterator.next();
			iterator.remove();
			int x = keyX(key);
			int y = keyY(key);
			int z = keyZ(key);
			if (holds(x, y, z)) {
				scan(x, y, z);
				budget--;
			}
		}
	}

	private static void refreshRotating() {
		frame++;
		if (frame % REFRESH_INTERVAL != 0 || !complete) {
			return;
		}
		for (int tries = 0; tries < TOTAL; tries++) {
			int index = refreshCursor++ % TOTAL;
			if (valid[index]) {
				scan(cellX[index], cellY[index], cellZ[index]);
				return;
			}
		}
	}

	private static boolean holds(int x, int y, int z) {
		int index = index(x, y, z);
		return valid[index] && cellX[index] == x && cellY[index] == y && cellZ[index] == z;
	}

	private static boolean scan(int cx, int cy, int cz) {
		LevelChunk chunk = level.getChunkSource().getChunkNow(cx, cz);
		if (chunk == null) {
			return false;
		}
		int sectionIndex = chunk.getSectionIndexFromSectionY(cy);
		LevelChunkSection section = sectionIndex >= 0 && sectionIndex < chunk.getSections().length ? chunk.getSection(sectionIndex) : null;
		ByteBuffer ids = emptyIds;
		ByteBuffer shine = emptyShine;
		ByteBuffer bricks = emptyBricks;
		long hash = 7L;
		if (section != null && !section.hasOnlyAir()) {
			BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
			int originX = cx * CELL_SIZE;
			int originY = cy * CELL_SIZE;
			int originZ = cz * CELL_SIZE;
			cellData.clear();
			shineData.clear();
			for (int i = 0; i < PER_CELL * PER_CELL * PER_CELL; i++) {
				brickData.put(i, (byte) 0);
			}
			for (int z = 0; z < CELL_SIZE; z++) {
				for (int y = 0; y < CELL_SIZE; y++) {
					for (int x = 0; x < CELL_SIZE; x++) {
						BlockState state = section.getBlockState(x, y, z);
						if (state.isAir()) {
							cellData.putShort((short) 0);
							shineData.put((byte) 0);
							hash *= 31L;
							continue;
						}
						pos.set(originX + x, originY + y, originZ + z);
						int id = Occluders.id(state, level, pos);
						cellData.putShort((short) id);
						if (id != 0) {
							brickData.put((z / BRICK * PER_CELL + y / BRICK) * PER_CELL + x / BRICK, (byte) 1);
						}
						int shineValue = Shine.of(state);
						shineData.put((byte) shineValue);
						hash = hash * 31L + id * 257L + shineValue;
					}
				}
			}
			cellData.flip();
			shineData.flip();
			ids = cellData;
			shine = shineData;
			bricks = brickData;
		}
		int slot = index(cx, cy, cz);
		if (valid[slot] && cellX[slot] == cx && cellY[slot] == cy && cellZ[slot] == cz && sums[slot] == hash) {
			return true;
		}
		sums[slot] = hash;
		revision++;
		GL12.glTexSubImage3D(
			GL12.GL_TEXTURE_3D, 0,
			Math.floorMod(cx, CELLS) * CELL_SIZE, Math.floorMod(cy, CELLS) * CELL_SIZE, Math.floorMod(cz, CELLS) * CELL_SIZE,
			CELL_SIZE, CELL_SIZE, CELL_SIZE, GL30.GL_RED_INTEGER, GL11.GL_UNSIGNED_SHORT, ids
		);
		GL13.glActiveTexture(GL13.GL_TEXTURE0 + SHINE_UNIT);
		GL11.glBindTexture(GL12.GL_TEXTURE_3D, shineTexture);
		GL12.glTexSubImage3D(
			GL12.GL_TEXTURE_3D, 0,
			Math.floorMod(cx, CELLS) * CELL_SIZE, Math.floorMod(cy, CELLS) * CELL_SIZE, Math.floorMod(cz, CELLS) * CELL_SIZE,
			CELL_SIZE, CELL_SIZE, CELL_SIZE, GL11.GL_RED, GL11.GL_UNSIGNED_BYTE, shine
		);
		GL13.glActiveTexture(GL13.GL_TEXTURE0 + BRICK_UNIT);
		GL11.glBindTexture(GL12.GL_TEXTURE_3D, brickTexture);
		GL12.glTexSubImage3D(
			GL12.GL_TEXTURE_3D, 0,
			Math.floorMod(cx, CELLS) * PER_CELL, Math.floorMod(cy, CELLS) * PER_CELL, Math.floorMod(cz, CELLS) * PER_CELL,
			PER_CELL, PER_CELL, PER_CELL, GL11.GL_RED, GL11.GL_UNSIGNED_BYTE, bricks
		);
		GL13.glActiveTexture(GL13.GL_TEXTURE0 + UNIT);
		int index = index(cx, cy, cz);
		cellX[index] = cx;
		cellY[index] = cy;
		cellZ[index] = cz;
		valid[index] = true;
		return true;
	}

	private static int index(int x, int y, int z) {
		return Math.floorMod(x, CELLS) + CELLS * (Math.floorMod(y, CELLS) + CELLS * Math.floorMod(z, CELLS));
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
