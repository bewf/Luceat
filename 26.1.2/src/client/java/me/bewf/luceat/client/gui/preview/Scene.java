package me.bewf.luceat.client.gui.preview;

import java.util.List;
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

	private int[][] voxels;

	Scene(Builder b) {
		this.id = b.id;
		this.title = b.title;
		this.caption = b.caption;
		this.width = b.width;
		this.height = b.height;
		this.depth = b.depth;
		this.cells = b.cells;
		this.lights = List.copyOf(b.lights);
		this.eye = b.eye;
		this.target = b.target;
		this.fov = b.fov;
		this.movable = b.movable;
		this.keepAway = b.keepAway;
		this.vanilla = b.vanilla;
		this.capLights = b.capLights;
		this.shadowScale = b.shadowScale;
		this.rangeScale = b.rangeScale;
	}

	public int index(int x, int y, int z) {
		return x + width * (y + height * z);
	}

	public int[][] voxels() {
		if (voxels == null) {
			voxels = new int[cells.length][];
			for (int i = 0; i < cells.length; i++) {
				if (cells[i] != null) {
					voxels[i] = BlockVoxels.of(cells[i]);
				}
			}
		}
		return voxels;
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
		double[] eye;
		double[] target;
		double fov = 58.0;
		boolean movable;
		boolean keepAway;
		boolean vanilla;
		boolean capLights;
		float shadowScale = 1.0F;
		float rangeScale = 0.0F;

		Builder(String id, String title, int width, int height, int depth) {
			this.id = id;
			this.title = title;
			this.width = width;
			this.height = height;
			this.depth = depth;
			this.cells = new BlockState[width * height * depth];
			this.eye = new double[]{width / 2.0, 3.4, -4.2};
			this.target = new double[]{width / 2.0, 0.9, depth * 0.46};
		}

		Builder caption(String text) {
			this.caption = text;
			return this;
		}

		Builder set(int x, int y, int z, BlockState state) {
			cells[x + width * (y + height * z)] = state;
			return this;
		}

		Builder fill(int x0, int y0, int z0, int x1, int y1, int z1, BlockState state) {
			for (int z = z0; z <= z1; z++) {
				for (int y = y0; y <= y1; y++) {
					for (int x = x0; x <= x1; x++) {
						set(x, y, z, state);
					}
				}
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

		Builder movable() {
			this.movable = true;
			return this;
		}

		Scene build() {
			return new Scene(this);
		}
	}
}
