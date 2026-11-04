package me.bewf.luceat.client.gui.preview;

import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

final class Worlds {
	private Worlds() {
	}

	private static BlockState b(Block block) {
		return Bs.b(block);
	}

	private static double riverX(double z) {
		return 34.0 + 7.0 * Math.sin(z * 0.11 + 0.6) + 3.0 * Math.sin(z * 0.27);
	}

	static Scene cherry() {
		int w = 76;
		int h = 32;
		int d = 76;
		Scene.Builder s = new Scene.Builder("cherry", "Cherry hill", w, h, d);
		int level = 8;
		int[] heights = Craft.heights(s, 11, 3.0, 4.0, (x, z) -> {
			double v = Craft.hill(x, z, 14, 38, 17, 11) + Craft.hill(x, z, 62, 44, 13, 7) + Craft.hill(x, z, 36, 74, 40, 9) - 3.5;
			double dist = Math.abs(x - riverX(z));
			v -= 9.0 * Math.exp(-Math.pow(dist / 4.2, 2.0)) * Math.min(1.0, z / 14.0);
			return v;
		});
		for (int z = 0; z < d; z++) {
			for (int x = 0; x < w; x++) {
				int top = heights[x + w * z];
				boolean wet = top <= level;
				for (int y = 0; y <= top && y < h; y++) {
					Block block;
					if (y == top) {
						block = wet ? (top <= level - 1 ? Blocks.GRAVEL : Blocks.SAND) : Blocks.GRASS_BLOCK;
					} else if (y >= top - 3) {
						block = wet ? Blocks.SAND : Blocks.DIRT;
					} else {
						block = Blocks.STONE;
					}
					s.set(x, y, z, b(block));
				}
				for (int y = top + 1; y <= level && y < h; y++) {
					s.set(x, y, z, b(Blocks.WATER));
				}
			}
		}
		Craft.cherry(s, 13, Craft.top(s, 13, 34), 34, 3, 1.25);
		Craft.cherry(s, 21, Craft.top(s, 21, 44), 44, 11, 1.05);
		Craft.cherry(s, 8, Craft.top(s, 8, 46), 46, 19, 1.15);
		Craft.cherry(s, 26, Craft.top(s, 26, 56), 56, 7, 0.95);
		Craft.cherry(s, 6, Craft.top(s, 6, 28), 28, 23, 0.9);
		Craft.oak(s, 60, Craft.top(s, 60, 46), 46, 5, 1.1, Blocks.OAK_LOG, Blocks.OAK_LEAVES);
		Craft.oak(s, 66, Craft.top(s, 66, 54), 54, 9, 0.9, Blocks.OAK_LOG, Blocks.OAK_LEAVES);
		Craft.spruce(s, 55, Craft.top(s, 55, 58), 58, 4, 9);
		Craft.spruce(s, 49, Craft.top(s, 49, 66), 66, 8, 11);
		Craft.scatter(s, 0, 20, 30, 70, 0.28F, 2, Blocks.SHORT_GRASS, Blocks.SHORT_GRASS, Blocks.POPPY, Blocks.DANDELION, Blocks.CORNFLOWER, Blocks.FERN);
		Craft.scatter(s, 44, 30, 75, 72, 0.3F, 5, Blocks.SHORT_GRASS, Blocks.SHORT_GRASS, Blocks.ALLIUM, Blocks.AZURE_BLUET);
		Craft.petals(s, 13, 38, 9.0, 0.5F, 3);
		Craft.petals(s, 22, 46, 6.0, 0.4F, 4);
		for (int i = 0; i < 6; i++) {
			int z = 24 + i * 5;
			int x = (int) Math.round(riverX(z)) - 7 - (i % 2);
			int y = Craft.top(s, x, z);
			if (y > level) {
				Craft.lampPost(s, x, y, z, Blocks.OAK_FENCE, 1 + (i % 2));
			}
		}
		for (int i = 0; i < 5; i++) {
			int z = 26 + i * 6;
			int x = (int) Math.round(riverX(z)) + 7 + (i % 2);
			int y = Craft.top(s, x, z);
			if (y > level) {
				Craft.lampPost(s, x, y, z, Blocks.COBBLESTONE_WALL, 1, i % 2 == 0 ? Blocks.SOUL_LANTERN : Blocks.SEA_LANTERN);
			}
		}
		int campX = (int) Math.round(riverX(40)) + 11;
		int campY = Craft.top(s, campX, 40);
		if (campY > level) {
			s.set(campX, campY + 1, 40, Bs.b(Blocks.CAMPFIRE));
			s.set(campX + 2, campY + 1, 41, Bs.b(Blocks.TORCH));
			s.set(campX - 2, campY + 1, 39, Bs.b(Blocks.TORCH));
			s.set(campX + 1, campY + 1, 38, Bs.b(Blocks.HAY_BLOCK));
			s.set(campX - 1, campY + 1, 42, Bs.b(Blocks.HAY_BLOCK));
		}
		int shroomX = (int) Math.round(riverX(52)) + 9;
		int shroomY = Craft.top(s, shroomX, 52);
		if (shroomY > level) {
			s.set(shroomX, shroomY + 1, 52, Bs.b(Blocks.SHROOMLIGHT));
			s.set(shroomX + 1, shroomY + 1, 53, Bs.b(Blocks.GLOWSTONE));
		}
		s.camera(new double[]{33, 13.2, 6}, new double[]{37, 10.6, 60}, 62.0);
		s.time(0.37F);
		s.sun(0.12);
		s.fog(0.012F);
		return s.caption("A cherry hill at dusk. Lanterns, a campfire and torches light both banks.").build();
	}

	static Scene portal() {
		int w = 46;
		int h = 26;
		int d = 46;
		Scene.Builder s = new Scene.Builder("portal", "Nether cavern", w, h, d);
		double lakeX = 32.0;
		double lakeZ = 30.0;
		for (int z = 0; z < d; z++) {
			for (int x = 0; x < w; x++) {
				double lake = Math.hypot(x - lakeX, (z - lakeZ) * 1.15);
				int floor = 5 + (int) Math.round(Craft.wave(x * 0.3, z * 0.3, 4.0) * 1.4);
				boolean basin = lake < 9.5;
				if (basin) {
					floor = 3;
				}
				int ceiling = 19 + (int) Math.round(Craft.wave(x * 0.22, z * 0.22, 9.0) * 2.0);
				boolean wall = x < 2 || z < 2 || x >= w - 2 || z >= d - 2;
				for (int y = 0; y < h; y++) {
					Block block = null;
					if (y <= floor) {
						block = y == floor && !basin && Craft.rand(x, 3, z) < 0.12F ? Blocks.SOUL_SAND : y == floor && !basin && Craft.rand(x, 5, z) < 0.1F ? Blocks.BLACKSTONE : y < floor - 2 && Craft.rand(x, y, z) < 0.04F ? Blocks.MAGMA_BLOCK : Blocks.NETHERRACK;
					} else if (y >= ceiling || wall && y < ceiling + 2) {
						block = Craft.rand(x, y, z) < 0.05F ? Blocks.BASALT : Blocks.NETHERRACK;
					}
					if (block != null) {
						s.set(x, y, z, b(block));
					}
				}
				if (basin) {
					for (int y = floor + 1; y <= 4; y++) {
						s.set(x, y, z, b(Blocks.LAVA));
					}
				}
			}
		}
		int px = 12;
		int pz = 18;
		int pf = floorAt(s, px, pz);
		s.fill(px - 2, pf, pz - 2, px + 5, pf, pz + 2, b(Blocks.BLACKSTONE));
		for (int y = pf + 1; y <= pf + 8; y++) {
			s.fill(px - 2, y, pz - 2, px + 5, y, pz + 2, null);
		}
		int base = pf + 1;
		s.fill(px + 1, base, pz, px + 2, base, pz, b(Blocks.OBSIDIAN));
		s.fill(px + 1, base + 4, pz, px + 2, base + 4, pz, b(Blocks.OBSIDIAN));
		s.fill(px, base, pz, px, base + 4, pz, b(Blocks.OBSIDIAN));
		s.fill(px + 3, base, pz, px + 3, base + 4, pz, b(Blocks.OBSIDIAN));
		s.fill(px + 1, base + 1, pz, px + 2, base + 3, pz, Bs.with(b(Blocks.NETHER_PORTAL), "axis", "x"));
		s.set(px - 1, pf + 1, pz - 2, b(Blocks.SOUL_TORCH));
		s.set(px + 4, pf + 1, pz - 2, b(Blocks.SOUL_TORCH));
		for (int i = 0; i < 9; i++) {
			int gx = 6 + (int) Math.round(Craft.rand(i, 1, 7) * 32.0);
			int gz = 6 + (int) Math.round(Craft.rand(i, 2, 7) * 32.0);
			int top = ceilingAt(s, gx, gz);
			if (top < 0 || Math.hypot(gx - px - 1.5, gz - pz) < 5.0) {
				continue;
			}
			int len = 1 + (int) (Craft.rand(i, 3, 7) * 3.0);
			for (int k = 1; k <= len; k++) {
				s.set(gx, top - k, gz, b(k == len && i % 3 == 0 ? Blocks.SHROOMLIGHT : Blocks.GLOWSTONE));
			}
		}
		for (int i = 0; i < 7; i++) {
			int x = (int) Math.round(lakeX - 13 + Craft.rand(i, 4, 8) * 8.0);
			int z = (int) Math.round(lakeZ - 12 + Craft.rand(i, 5, 8) * 24.0);
			int y = floorAt(s, x, z);
			if (y >= 5 && s.get(x, y + 1, z) == null) {
				s.set(x, y + 1, z, b(i % 2 == 0 ? Blocks.SOUL_FIRE : Blocks.FIRE));
			}
		}
		s.camera(new double[]{22, 10.5, 4}, new double[]{21, 7.5, 24}, 66.0);
		s.time(0.04F);
		return s.caption("A nether cavern with a portal, a lava lake and glowstone overhead.").build();
	}

	private static int floorAt(Scene.Builder s, int x, int z) {
		for (int y = 12; y >= 0; y--) {
			BlockState state = s.get(x, y, z);
			if (state != null && !state.is(Blocks.LAVA)) {
				return y;
			}
		}
		return 0;
	}

	private static int ceilingAt(Scene.Builder s, int x, int z) {
		for (int y = s.height - 1; y >= 0; y--) {
			if (s.get(x, y, z) != null) {
				int low = y;
				while (low > 0 && s.get(x, low - 1, z) != null) {
					low--;
				}
				return low > 12 ? low : -1;
			}
		}
		return -1;
	}
}
