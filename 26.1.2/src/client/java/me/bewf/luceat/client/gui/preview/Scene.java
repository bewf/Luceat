package me.bewf.luceat.client.gui.preview;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import me.bewf.luceat.client.shadow.Shine;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.block.state.BlockState;

public final class Scene {
	public final String id;
	public final String title;
	public final String caption;
	public final int width;
	public final int height;
	public final int depth;
	public final BlockState[] cells;
	public final List<Light> lights;
	public final double[] eye;
	public final double[] target;
	public final double fov;
	public final boolean movable;
	public final boolean keepAway;
	public final boolean vanilla;
	public final boolean capLights;
	public final float shadowScale;
	public final float rangeScale;
	public final float time;
	public final double sunYaw;
	public final float fog;
	public final float reflectivity;
	public final double panCentre;
	public final double panSpan;
	public final double panTan;
	public final boolean animated;

	private final Map<Integer, int[]> custom;
	private final byte[] waterKinds;
	private int[][] voxels;
	private float[] shine;
	private float[] emit;
	private byte[] water;
	private byte[] kind;
	private float[] sky;
	private float[] blockLight;

	Scene(Builder b) {
		this.id = b.id;
		this.title = b.title;
		this.caption = b.caption;
		this.width = b.width;
		this.height = b.height;
		this.depth = b.depth;
		this.cells = b.cells;
		this.eye = b.eye;
		this.target = b.target;
		this.fov = b.fov;
		this.movable = b.movable;
		this.keepAway = b.keepAway;
		this.vanilla = b.vanilla;
		this.capLights = b.capLights;
		this.shadowScale = b.shadowScale;
		this.rangeScale = b.rangeScale;
		this.time = b.time;
		this.sunYaw = b.sunYaw;
		this.fog = b.fog;
		this.reflectivity = b.reflectivity;
		this.panCentre = b.panCentre;
		this.panSpan = b.panSpan;
		this.panTan = b.panTan;
		this.custom = b.custom;
		this.waterKinds = b.waterKinds;
		java.util.ArrayList<Light> all = new java.util.ArrayList<>(b.lights);
		boolean any = false;
		for (int z = 0; z < depth; z++) {
			for (int y = 0; y < height; y++) {
				for (int x = 0; x < width; x++) {
					BlockState state = cells[x + width * (y + height * z)];
					if (state == null) {
						continue;
					}
					if (state.is(Blocks.WATER)) {
						any = true;
					}
					if (b.autoLights) {
						Light made = Emitters.light(state, x, y, z);
						if (made != null) {
							all.add(made);
						}
					}
				}
			}
		}
		this.lights = List.copyOf(all);
		this.animated = any || b.animated;
	}

	public int index(int x, int y, int z) {
		return x + width * (y + height * z);
	}

	public int[][] voxels() {
		if (voxels == null) {
			voxels = new int[cells.length][];
			for (int i = 0; i < cells.length; i++) {
				int[] own = custom.get(i);
				if (own != null) {
					voxels[i] = own;
				} else if (cells[i] != null) {
					voxels[i] = BlockVoxels.of(cells[i]);
				}
			}
		}
		return voxels;
	}

	public float[] shine() {
		if (shine == null) {
			shine = new float[cells.length];
			for (int i = 0; i < cells.length; i++) {
				if (cells[i] != null) {
					shine[i] = cells[i].is(Blocks.WATER) ? 0.0F : Shine.material(BuiltInRegistries.BLOCK.getKey(cells[i].getBlock()).getPath());
				}
			}
		}
		return shine;
	}

	public float[] emit() {
		if (emit == null) {
			emit = new float[cells.length];
			for (int i = 0; i < cells.length; i++) {
				if (cells[i] != null && !cells[i].is(Blocks.WATER)) {
					emit[i] = Math.min(1.0F, cells[i].getLightEmission() / 15.0F);
				}
			}
		}
		return emit;
	}

	public byte[] water() {
		if (water == null) {
			water = new byte[cells.length];
			for (int i = 0; i < cells.length; i++) {
				if (cells[i] != null && cells[i].is(Blocks.WATER)) {
					water[i] = waterKinds[i] != 0 ? waterKinds[i] : Extras.STILL;
				}
			}
		}
		return water;
	}

	public boolean hasWater() {
		for (byte value : water()) {
			if (value != Extras.DRY) {
				return true;
			}
		}
		return false;
	}

	public byte[] kind() {
		if (kind == null) {
			kind = new byte[cells.length];
			for (int i = 0; i < cells.length; i++) {
				BlockState state = cells[i];
				if (state == null || custom.containsKey(i)) {
					continue;
				}
				if (state.isSolidRender()) {
					kind[i] = 1;
				} else if (state.getBlock() instanceof LeavesBlock) {
					kind[i] = 2;
				}
			}
		}
		return kind;
	}

	public float[] sky() {
		if (sky == null) {
			sky = Extras.skyLight(width, height, depth, kind());
		}
		return sky;
	}

	public float[] blockLight() {
		if (blockLight == null) {
			java.util.ArrayList<Light> placed = new java.util.ArrayList<>();
			for (Light light : lights) {
				if (light.placed()) {
					placed.add(light);
				}
			}
			blockLight = Extras.blockLight(width, height, depth, kind(), placed.toArray(new Light[0]));
		}
		return blockLight;
	}

	public boolean solidAt(int x, int y, int z) {
		if (x < 0 || y < 0 || z < 0 || x >= width || y >= height || z >= depth) {
			return false;
		}
		BlockState state = cells[index(x, y, z)];
		return state != null && state.isSolidRender();
	}

	static final class Builder {
		final String id;
		final String title;
		String caption = "";
		final int width;
		final int height;
		final int depth;
		final BlockState[] cells;
		final java.util.ArrayList<Light> lights = new java.util.ArrayList<>();
		final Map<Integer, int[]> custom = new HashMap<>();
		final byte[] waterKinds;
		double[] eye;
		double[] target;
		double fov = 58.0;
		boolean movable;
		boolean keepAway;
		boolean vanilla = true;
		boolean capLights;
		boolean autoLights = true;
		boolean animated;
		float shadowScale = 1.0F;
		float rangeScale = 0.0F;
		float time = 0.04F;
		double sunYaw = 0.0;
		float fog = 0.0F;
		float reflectivity = 1.0F;
		double panCentre;
		double panSpan = Math.toRadians(120.0);
		double panTan = 0.42;

		Builder(String id, String title, int width, int height, int depth) {
			this.id = id;
			this.title = title;
			this.width = width;
			this.height = height;
			this.depth = depth;
			this.cells = new BlockState[width * height * depth];
			this.waterKinds = new byte[width * height * depth];
			this.eye = new double[]{width / 2.0, 3.4, -4.2};
			this.target = new double[]{width / 2.0, 0.9, depth * 0.46};
		}

		Builder caption(String text) {
			this.caption = text;
			return this;
		}

		boolean in(int x, int y, int z) {
			return x >= 0 && y >= 0 && z >= 0 && x < width && y < height && z < depth;
		}

		Builder set(int x, int y, int z, BlockState state) {
			if (in(x, y, z)) {
				cells[x + width * (y + height * z)] = state;
				custom.remove(x + width * (y + height * z));
			}
			return this;
		}

		BlockState get(int x, int y, int z) {
			return in(x, y, z) ? cells[x + width * (y + height * z)] : null;
		}

		Builder clear(int x, int y, int z) {
			return set(x, y, z, null);
		}

		Builder fill(int x0, int y0, int z0, int x1, int y1, int z1, BlockState state) {
			for (int z = Math.min(z0, z1); z <= Math.max(z0, z1); z++) {
				for (int y = Math.min(y0, y1); y <= Math.max(y0, y1); y++) {
					for (int x = Math.min(x0, x1); x <= Math.max(x0, x1); x++) {
						set(x, y, z, state);
					}
				}
			}
			return this;
		}

		Builder custom(int x, int y, int z, int[] voxels) {
			if (in(x, y, z)) {
				int at = x + width * (y + height * z);
				cells[at] = null;
				custom.put(at, voxels);
			}
			return this;
		}

		Builder waterKind(int x, int y, int z, byte kind) {
			if (in(x, y, z)) {
				waterKinds[x + width * (y + height * z)] = kind;
			}
			return this;
		}

		Builder light(Light light) {
			lights.add(light);
			return this;
		}

		Builder camera(double[] newEye, double[] newTarget, double newFov) {
			this.eye = newEye;
			this.target = newTarget;
			this.fov = newFov;
			return this;
		}

		Builder time(float value) {
			this.time = value;
			return this;
		}

		Builder sun(double yaw) {
			this.sunYaw = yaw;
			return this;
		}

		Builder fog(float density) {
			this.fog = density;
			return this;
		}

		Builder pan(double centreDegrees, double spanDegrees, double tanVertical) {
			this.panCentre = Math.toRadians(centreDegrees);
			this.panSpan = Math.toRadians(spanDegrees);
			this.panTan = tanVertical;
			return this;
		}

		Builder movable() {
			this.movable = true;
			return this;
		}

		Scene build() {
			return new Scene(this);
		}
	}
}
