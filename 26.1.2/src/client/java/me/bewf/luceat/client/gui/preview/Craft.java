package me.bewf.luceat.client.gui.preview;

import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

final class Craft {
	private Craft() {
	}

	static float rand(int a, int b, int c) {
		return Atmosphere.hash(a * 73856093 ^ c * 19349663, b * 83492791 + c);
	}

	static double wave(double x, double z, double seed) {
		return 0.5 * Atmosphere.noise(x * 0.11 + seed, z * 0.11 - seed) + 0.3 * Atmosphere.noise(x * 0.23 + seed * 2, z * 0.23) + 0.2 * Atmosphere.noise(x * 0.47, z * 0.47 + seed);
	}

	static int top(Scene.Builder s, int x, int z) {
		for (int y = s.height - 1; y >= 0; y--) {
			BlockState state = s.get(x, y, z);
			if (state != null && state.isSolidRender()) {
				return y;
			}
		}
		return -1;
	}

	static void ground(Scene.Builder s, int[] heights, Block top, Block under, Block deep) {
		for (int z = 0; z < s.depth; z++) {
			for (int x = 0; x < s.width; x++) {
				int h = heights[x + s.width * z];
				for (int y = 0; y <= h && y < s.height; y++) {
					Block b = y == h ? top : y >= h - 3 ? under : deep;
					s.set(x, y, z, Bs.b(b));
				}
			}
		}
	}

	static int[] heights(Scene.Builder s, double base, double amplitude, double seed, java.util.function.DoubleBinaryOperator extra) {
		int[] h = new int[s.width * s.depth];
		for (int z = 0; z < s.depth; z++) {
			for (int x = 0; x < s.width; x++) {
				double v = base + amplitude * wave(x, z, seed) + (extra == null ? 0.0 : extra.applyAsDouble(x, z));
				h[x + s.width * z] = Math.max(1, (int) Math.round(v));
			}
		}
		return h;
	}

	static double hill(double x, double z, double cx, double cz, double radius, double height) {
		double d = ((x - cx) * (x - cx) + (z - cz) * (z - cz)) / (radius * radius);
		return height * Math.exp(-d * 1.6);
	}

	static BlockState leaves(Block block) {
		return Bs.with(Bs.b(block), "persistent", "true");
	}

	static void blob(Scene.Builder s, double cx, double cy, double cz, double rx, double ry, double rz, BlockState leaves, int seed, float hole) {
		for (int y = (int) Math.floor(cy - ry); y <= Math.ceil(cy + ry); y++) {
			for (int z = (int) Math.floor(cz - rz); z <= Math.ceil(cz + rz); z++) {
				for (int x = (int) Math.floor(cx - rx); x <= Math.ceil(cx + rx); x++) {
					double dx = (x - cx) / rx;
					double dy = (y - cy) / ry;
					double dz = (z - cz) / rz;
					double d = dx * dx + dy * dy + dz * dz;
					double edge = 1.0 + (rand(x + seed, y, z) - 0.5) * 0.5;
					if (d <= edge && s.in(x, y, z) && s.get(x, y, z) == null && rand(x, y + seed, z) > hole) {
						s.set(x, y, z, leaves);
					}
				}
			}
		}
	}

	static void log(Scene.Builder s, int x, int y, int z, Block block, String axis) {
		s.set(x, y, z, Bs.with(Bs.b(block), "axis", axis));
	}

	static void cherry(Scene.Builder s, int x, int ground, int z, int seed, double scale) {
		Block log = Blocks.CHERRY_LOG;
		BlockState leaves = leaves(Blocks.CHERRY_LEAVES);
		int trunk = (int) Math.round(6 * scale);
		int lean = rand(seed, 1, 2) > 0.5F ? 1 : -1;
		int cx = x;
		int cz = z;
		for (int i = 1; i <= trunk; i++) {
			if (i == 3 || i == 5) {
				cx += lean;
				log(s, cx, ground + i, cz, log, "y");
			}
			log(s, cx, ground + i, cz, log, "y");
			if (i == 1) {
				log(s, cx + 1, ground, cz, log, "y");
				log(s, cx - 1, ground, cz, log, "y");
				log(s, cx, ground, cz + 1, log, "y");
			}
		}
		int top = ground + trunk;
		int[][] branches = {{lean * 2, 1, 1}, {-lean * 2, 0, 1}, {1, 1, -2}, {-1, 2, 2}};
		double reach = 4.4 * scale;
		blob(s, cx, top + 2.2 * scale, cz, reach, 2.7 * scale, reach * 0.95, leaves, seed, 0.1F);
		for (int[] b : branches) {
			int bx = cx;
			int by = top - 1 + b[1];
			int bz = cz;
			for (int k = 1; k <= 2; k++) {
				bx += Integer.signum(b[0]);
				bz += Integer.signum(b[2]) * (k % 2);
				by += k - 1;
				log(s, bx, by, bz, log, Math.abs(b[0]) > 0 ? "x" : "z");
			}
			blob(s, bx + b[0] * 0.8, by + 1.2, bz + b[2] * 0.8, 3.0 * scale, 1.9 * scale, 3.0 * scale, leaves, seed + 7, 0.14F);
		}
		blob(s, cx, top + 0.6, cz, 2.4 * scale, 1.2 * scale, 2.4 * scale, leaves, seed + 3, 0.2F);
	}

	static void oak(Scene.Builder s, int x, int ground, int z, int seed, double scale, Block log, Block leafBlock) {
		BlockState leaves = leaves(leafBlock);
		int trunk = (int) Math.round(5 * scale);
		for (int i = 1; i <= trunk; i++) {
			log(s, x, ground + i, z, log, "y");
		}
		blob(s, x, ground + trunk + 1.2, z, 2.9 * scale, 2.4 * scale, 2.9 * scale, leaves, seed, 0.08F);
		blob(s, x + 1.5, ground + trunk - 0.2, z - 1, 2.2 * scale, 1.6 * scale, 2.2 * scale, leaves, seed + 4, 0.12F);
		blob(s, x - 1.5, ground + trunk, z + 1, 2.2 * scale, 1.6 * scale, 2.2 * scale, leaves, seed + 9, 0.12F);
	}

	static void spruce(Scene.Builder s, int x, int ground, int z, int seed, int heightBlocks) {
		BlockState leaves = leaves(Blocks.SPRUCE_LEAVES);
		for (int i = 1; i <= heightBlocks; i++) {
			log(s, x, ground + i, z, Blocks.SPRUCE_LOG, "y");
		}
		for (int layer = 0; layer < heightBlocks - 1; layer++) {
			int y = ground + 2 + layer;
			double radius = Math.max(0.6, (heightBlocks - 1 - layer) * 0.62);
			if (layer % 2 == 1) {
				radius -= 0.7;
			}
			blob(s, x, y, z, Math.max(0.5, radius), 0.55, Math.max(0.5, radius), leaves, seed + layer, 0.05F);
		}
		s.set(x, ground + heightBlocks + 1, z, leaves);
	}

	static void scatter(Scene.Builder s, int x0, int z0, int x1, int z1, float density, int seed, Block... plants) {
		for (int z = z0; z <= z1; z++) {
			for (int x = x0; x <= x1; x++) {
				int y = top(s, x, z);
				if (y < 0 || !s.in(x, y + 1, z) || s.get(x, y + 1, z) != null) {
					continue;
				}
				BlockState ground = s.get(x, y, z);
				if (ground == null || !ground.is(Blocks.GRASS_BLOCK)) {
					continue;
				}
				float r = rand(x + seed, 5, z);
				if (r < density) {
					int pick = (int) (rand(x, seed, z + 3) * plants.length) % plants.length;
					s.set(x, y + 1, z, Bs.b(plants[pick]));
				}
			}
		}
	}

	static void petals(Scene.Builder s, int cx, int cz, double radius, float density, int seed) {
		for (int z = (int) (cz - radius); z <= cz + radius; z++) {
			for (int x = (int) (cx - radius); x <= cx + radius; x++) {
				double d = Math.hypot(x - cx, z - cz);
				if (d > radius || !s.in(x, 0, z)) {
					continue;
				}
				int y = top(s, x, z);
				if (y < 0 || s.get(x, y + 1, z) != null || !s.get(x, y, z).is(Blocks.GRASS_BLOCK)) {
					continue;
				}
				if (rand(x + seed, 9, z) < density * (1.0 - d / radius * 0.5)) {
					String amount = String.valueOf(1 + (int) (rand(x, seed, z) * 4));
					s.set(x, y + 1, z, Bs.with(Bs.b(Blocks.PINK_PETALS), "flower_amount", amount));
				}
			}
		}
	}

	static void lampPost(Scene.Builder s, int x, int ground, int z, Block post, int height) {
		for (int i = 1; i <= height; i++) {
			s.set(x, ground + i, z, Bs.b(post));
		}
		s.set(x, ground + height + 1, z, Bs.b(Blocks.LANTERN));
	}
}
