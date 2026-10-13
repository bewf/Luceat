package me.bewf.luceat.client.shadow;

import java.nio.ByteBuffer;
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

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;

public final class ShadowVolume {
	public static final int CELLS = 5;
	public static final int CELL_SIZE = 16;
	public static final int DIMENSION = CELLS * CELL_SIZE;
	public static final String SAMPLER = "u_LuceatVoxels";

	private static final int UNIT = 9;
	private static final int TOTAL = CELLS * CELLS * CELLS;
	private static final int FILLS_PER_FRAME = 3;
	private static final int DIRTY_PER_FRAME = 8;
	private static final int REFRESH_INTERVAL = 4;
	private static final int[] UNPACK = {
		GL11.GL_UNPACK_ALIGNMENT, GL11.GL_UNPACK_ROW_LENGTH, GL11.GL_UNPACK_SKIP_ROWS,
		GL11.GL_UNPACK_SKIP_PIXELS, GL12.GL_UNPACK_IMAGE_HEIGHT, GL12.GL_UNPACK_SKIP_IMAGES
	};

	private static final int[] cellX = new int[TOTAL];
	private static final int[] cellY = new int[TOTAL];
	private static final int[] cellZ = new int[TOTAL];
	private static final boolean[] valid = new boolean[TOTAL];
	private static final Set<Long> dirty = new HashSet<>();
	private static final ByteBuffer cellData = BufferUtils.createByteBuffer(CELL_SIZE * CELL_SIZE * CELL_SIZE);

	private static ClientLevel level;
	private static int texture;
	private static int frame;
	private static int refreshCursor;

	private ShadowVolume() {
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
			Arrays.fill(valid, false);
			dirty.clear();
		}
		ensureTexture();
		int centreX = Math.floorDiv((int) Math.floor(camera.x), CELL_SIZE);
		int centreY = Math.floorDiv((int) Math.floor(camera.y), CELL_SIZE);
		int centreZ = Math.floorDiv((int) Math.floor(camera.z), CELL_SIZE);
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
		GL12.glTexImage3D(GL12.GL_TEXTURE_3D, 0, GL30.GL_R8, DIMENSION, DIMENSION, DIMENSION, 0, GL11.GL_RED, GL11.GL_UNSIGNED_BYTE, (ByteBuffer) null);
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
		List<int[]> missing = null;
		for (int dx = -2; dx <= 2; dx++) {
			for (int dy = -2; dy <= 2; dy++) {
				for (int dz = -2; dz <= 2; dz++) {
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
			return;
		}
		missing.sort((a, b) -> Integer.compare(a[3], b[3]));
		int count = Math.min(FILLS_PER_FRAME, missing.size());
		for (int i = 0; i < count; i++) {
			int[] cell = missing.get(i);
			scan(cell[0], cell[1], cell[2]);
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
		if (frame % REFRESH_INTERVAL != 0) {
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

	private static void scan(int cx, int cy, int cz) {
		BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
		int originX = cx * CELL_SIZE;
		int originY = cy * CELL_SIZE;
		int originZ = cz * CELL_SIZE;
		cellData.clear();
		for (int z = 0; z < CELL_SIZE; z++) {
			for (int y = 0; y < CELL_SIZE; y++) {
				for (int x = 0; x < CELL_SIZE; x++) {
					pos.set(originX + x, originY + y, originZ + z);
					cellData.put((byte) Occluders.opacity(level.getBlockState(pos)));
				}
			}
		}
		cellData.flip();
		GL12.glTexSubImage3D(
			GL12.GL_TEXTURE_3D, 0,
			Math.floorMod(cx, CELLS) * CELL_SIZE, Math.floorMod(cy, CELLS) * CELL_SIZE, Math.floorMod(cz, CELLS) * CELL_SIZE,
			CELL_SIZE, CELL_SIZE, CELL_SIZE, GL11.GL_RED, GL11.GL_UNSIGNED_BYTE, cellData
		);
		int index = index(cx, cy, cz);
		cellX[index] = cx;
		cellY[index] = cy;
		cellZ[index] = cz;
		valid[index] = true;
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
